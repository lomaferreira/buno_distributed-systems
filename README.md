# Buno

Um jogo de cartas estilo **UNO**, distribuído e multiplayer, construído em **Java** com **Apache ZooKeeper** como serviço de coordenação. Todo o estado da partida — baralho, mãos, turnos, penalidades e efeitos de carta — é sincronizado entre os clientes através de znodes, sem a necessidade de um servidor de jogo dedicado.

> Projeto acadêmico desenvolvido para a disciplina de Sistemas Distribuídos (UFMA).

---

## Visão geral

Em vez de um servidor central de jogo, o Buno usa o ZooKeeper como **fonte única da verdade**: cada sala, jogador e requisição de jogo é representado por um znode. Um dos jogadores assume o papel de **Host**, responsável por manter o baralho e resolver disputas (compras de carta, embaralhamento, etc.), enquanto os demais jogadores leem e escrevem apenas nos nós permitidos por suas credenciais.

### Principais características

- **Controle de acesso baseado em papéis (ACL Digest)** — credenciais `host:<senha>` e `jogador:<senha>` definem quem pode ler, escrever ou criar sub-nós em cada parte da árvore.
- **Padrão mediador para o baralho** — jogadores nunca acessam `/baralho` diretamente; toda compra de carta passa por uma requisição em `/requisicoes`, atendida pelo Host.
- **Leituras atômicas com watch** — uso de `getData(path, Watcher, Stat)` em vez de `create()` + `addWatch()` separados, eliminando condições de corrida na compra de cartas e na penalidade "+2".
- **Failover de Host (eleição de líder)** — se o Host cai, o jogador que entrou há mais tempo entre os sobreviventes assume automaticamente via `Host.promoverDe(Jogador)`.
- **Interface de terminal responsiva** — jogadas com timeout, atualização assíncrona de tela e proteção contra bloqueio da thread de eventos do ZooKeeper (`AtomicBoolean`, executor dedicado de UI).

---

## Arquitetura

### Módulos

| Pacote | Responsabilidade |
|---|---|
| `buno.model` | Entidades do jogo: `Carta`, `Cor`, `Simbolo`, `Jogador`, `Host`, `Sala`, `Partida`, `Baralho` |
| `buno.controller` | `ZookeeperService` (toda a comunicação com o ZooKeeper) e `GameController` (regras de jogo e efeitos de carta) |
| `buno.view` | `App` — interface de terminal (CLI) e loop principal da aplicação |

### Árvore de znodes

```
/salas
└── /sala-XXXXXXXXXX                 (dados: nome, senha?, limite de jogadores)
    ├── /status                      (estado da partida: turno, sentido, carta do topo, vencedor...)
    ├── /baralho                     (ACL: somente Host — sequência de cartas restantes)
    ├── /requisicoes                 (mediador: jogadores criam pedidos, Host responde)
    ├── /conexoes                    (nós efêmeros — 1 por sessão conectada, detecta desconexões)
    └── /jogadores
        └── /jogador-XXXXXXXXXX      (nome, quantidade de cartas)
            └── /cartas              (mão do jogador, serializada em JSON)
```

### Fluxo de uma jogada

1. O jogador escolhe uma carta válida (`Carta.podeSerJogada`).
2. `GameController.jogarCarta` atualiza a pilha de descarte e aplica o efeito da carta (`INVERTER`, `BLOQUEAR`, `MAISDOIS`, `MUDARCOR`, `MAISQUATRO`).
3. `ZookeeperService.atualizarStatus` publica o novo estado em `/status`.
4. Os demais clientes recebem o evento via *watch* em `/status` e atualizam sua tela.

### Compra de carta (mediador)

Como apenas o Host tem permissão de escrita em `/baralho`, um jogador comum não retira cartas diretamente. Em vez disso:

1. O jogador cria um nó efêmero sequencial em `/requisicoes` pedindo uma carta.
2. O Host observa `/requisicoes`, atende ao pedido, remove uma carta do baralho e escreve a resposta.
3. O jogador, que estava com um *watch* ativo, recebe a carta e atualiza sua mão local.

Esse desenho evita que qualquer cliente comum precise de credenciais de escrita sobre o baralho compartilhado.

### Tolerância a falhas: eleição de líder

Se a conexão do Host cai, seu znode efêmero em `/conexoes` desaparece. Os jogadores restantes disputam a liderança observando essa mudança; o jogador que está na sala há mais tempo (`atualizarListaJogadores`) assume o papel de Host, reautentica-se com as credenciais `host:<senha>` e reconstrói o objeto `Host` a partir do `Jogador` promovido (`Host.promoverDe`), preservando mão, path e estado de penalidade.

---

## Pré-requisitos

- **Java 17+**
- **Apache ZooKeeper** (servidor rodando, padrão `localhost:2181`)
- Dependências do projeto:
  - [ZooKeeper Java Client](https://zookeeper.apache.org/)
  - [org.json](https://mvnrepository.com/artifact/org.json/json)

> Ajuste esta seção conforme a ferramenta de build usada no projeto (Maven/Gradle).

## Como executar

1. Suba um servidor ZooKeeper local (ou aponte para um remoto):
   ```bash
   zkServer.sh start
   ```
2. Compile e execute a aplicação, informando o endereço do ZooKeeper (opcional, padrão `localhost:2181`):
   ```bash
   java -cp <classpath> buno.view.App [host:porta]
   ```
3. No menu inicial, escolha:
   - **[C]** para criar uma sala (você se torna o Host)
   - **[E]** para entrar em uma sala existente

## Como jogar

- Digite o **número** da carta na sua mão para jogá-la.
- Digite **C** para comprar uma carta do baralho.
- Digite **S** para sair da partida (suas cartas retornam ao baralho).
- Cada turno tem **20 segundos**; se o tempo esgotar, a vez passa automaticamente.
- Cartas especiais: `INVERTER` (inverte o sentido), `BLOQUEAR` (pula o próximo jogador), `MAISDOIS`/`MAISQUATRO` (próximo jogador compra e perde a vez), `MUDARCOR`/`MAISQUATRO` (escolha uma nova cor para a mesa).

