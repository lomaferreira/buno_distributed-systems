package buno.controller;

import buno.model.*;

import buno.view.App;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.server.auth.DigestAuthenticationProvider;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class ZookeeperService {
    private final String salas_node = "/salas"; //Nó raiz de todas as salas
    private ZooKeeper zookeeper;
    private final String enderecoZk;

//    private final ACL CLIENTEGERAL = new ACL(ZooDefs.Perms.READ, new Id("world", "anyone"));
    public ZookeeperService(String enderecoZk) throws IOException, InterruptedException, KeeperException {
        this.enderecoZk = enderecoZk;
        conectar(null, null);
        Stat stat = zookeeper.exists(salas_node, false);
        //Se o nó raiz /salas não existir cria ele de forma persistente e pública
        if (stat == null) {
            zookeeper.create(salas_node, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
    }

    //conexão com o servidor local do ZooKeeper na porta padrão 2181
    public void conectar(Watcher watcher, String senhaDaSala) throws IOException {
        //checar a conexão
        if (zookeeper != null){
            try{
                zookeeper.close();
            }catch (Exception ignored) {}

        }
        //arg: endereço IP, Timeout(ms), vincular monitor
        zookeeper = new ZooKeeper(enderecoZk, 15000, watcher);
        // Se tiver uma senha reinjeta as credenciais do Host
        if (senhaDaSala != null) {
            zookeeper.addAuthInfo("digest", ("host:" + senhaDaSala).getBytes());
            zookeeper.addAuthInfo("digest", ("jogador:" + senhaDaSala).getBytes());
        }
    }

    public ZooKeeper getZookeeper() {
        return zookeeper;
    }

    public void desconectar(){
        try {
            zookeeper.close();
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }
    }

    // Auxiliar para gerar credenciais no formato "esquema digest" (autenticação por usuário:senha)
    public Id autentificaoDigest(String username, String senha) throws NoSuchAlgorithmException {
        return new Id("digest",
                DigestAuthenticationProvider.generateDigest(
                        String.format("%s:%s", username, senha)));
    }

    public Host criarSala(String nomeJogador, Sala sala){
        try {
            ArrayList<ACL> aclLiberados = new ArrayList<>();
            // fornece autenticação de Host e de Jogador comum
            zookeeper.addAuthInfo("digest", ("host:" + sala.getSenha()).getBytes());
            zookeeper.addAuthInfo("digest", ("jogador:" + sala.getSenha()).getBytes());

            aclLiberados.add(new ACL(ZooDefs.Perms.READ, ZooDefs.Ids.ANYONE_ID_UNSAFE)); //qualquer um pode ler os dados da sala
            aclLiberados.add(new ACL(ZooDefs.Perms.ALL, autentificaoDigest("host", sala.getSenha()))); //host com senha tem controle total
            aclLiberados.add(new ACL(ZooDefs.Perms.READ | ZooDefs.Perms.WRITE | ZooDefs.Perms.CREATE,//jogador pode ler, escrever e crira sub-nós
                    autentificaoDigest("jogador", sala.getSenha())));

            //Cria o nó sequencial no ZooKeeper (ex: /salas/sala-0000000001) aplicando as regras de ACL
            sala.setPath(zookeeper.create(salas_node+"/sala-",
                    sala.exportJson().getBytes(),
                        aclLiberados,
                            CreateMode.PERSISTENT_SEQUENTIAL));
            return new Host(nomeJogador, sala);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    // Recupera do ZooKeeper e lista todas as salas criadas no servidor
    public List<Sala> listar_salas(){
        List<Sala> salas = new ArrayList<>();
        try {
            for (String path : zookeeper.getChildren(salas_node, false)){
                byte[] dados = zookeeper.getData(salas_node+'/'+path, false, null);
                Sala sala_disponivel = new Sala(new String(dados), salas_node+'/'+path); // Reconstrói o objeto Java Sala através do JSON baixado
                salas.add(sala_disponivel);
            }
        }
        catch (Exception e){
            System.out.println("DEU MUITO RUIM: "+e.getMessage());
        }
        return salas;
    }

    // Remove recursivamente um nó e seus sub-nós do servidor
    public void removerNode(String path){
        try {
            ZKUtil.deleteRecursive(zookeeper, path);
            System.out.println("O node no caminho "+ path+ " foi deletado");
        }
        catch (InterruptedException | KeeperException e) {
            System.out.println("Erro ao remover Sala: " + e.getMessage());
        }
    }

    public void criarNodesPartida(Sala sala) {
        String statusPath = sala.getPath()+"/status";
        String baralhoPath = sala.getPath()+"/baralho";
        String requisicoesPath = sala.getPath()+"/requisicoes";
        String conexoesPath = sala.getPath()+"/conexoes";
        String jogadoresPath = sala.getPath()+"/jogadores";
        try {
            zookeeper.create(statusPath, null, ZooDefs.Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT);

            // /baralho só pode ser lido/escrito por quem carrega a credencial de HOST.
            // Note que NÃO usamos CREATOR_ALL_ACL aqui: nesse ponto da sessão (dentro de criarSala)
            // já injetamos tanto "host:senha" quanto "jogador:senha" como credenciais do cliente,
            // e o CREATOR_ALL_ACL daria ALL a TODAS as credenciais presentes na sessão no momento
            // da criação — ou seja, um jogador comum acabaria com acesso total ao baralho, o que
            // quebraria exatamente a restrição que queremos.
            ArrayList<ACL> aclBaralho = new ArrayList<>();
            aclBaralho.add(new ACL(ZooDefs.Perms.ALL, autentificaoDigest("host", sala.getSenha())));
            zookeeper.create(baralhoPath, null, aclBaralho, CreateMode.PERSISTENT);

            // /requisicoes é o nó mediador entre jogadores e o baralho do host.
            // O host tem controle total (ALL); o jogador só pode CRIAR um pedido de compra —
            // ele não enxerga o /baralho diretamente, nem pode ler/escrever nos pedidos de outros.
            ArrayList<ACL> aclRequisicoes = new ArrayList<>();
            aclRequisicoes.add(new ACL(ZooDefs.Perms.ALL, autentificaoDigest("host", sala.getSenha())));
            aclRequisicoes.add(new ACL(ZooDefs.Perms.CREATE, autentificaoDigest("jogador", sala.getSenha())));
            zookeeper.create(requisicoesPath, null, aclRequisicoes, CreateMode.PERSISTENT);

            zookeeper.create(conexoesPath,null, ZooDefs.Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT);
            zookeeper.create(jogadoresPath,null, ZooDefs.Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT);
        } catch (KeeperException.InvalidACLException e) {
            System.out.println("Problema com ACL: " + e.getMessage());
        }
        catch (Exception e) {
            System.out.println("Não foi possível criar os nodes básicos da partida: "+e.getMessage());
        }
    }

    public void criarBaralhoRemoto(Host host) {
        Sala sala = host.getSalaAtual();
        String baralhoPath = sala.getPath() + "/baralho";
        Baralho baralho = host.getBaralho();

        try {
            ArrayList<ACL> aclBaralho = new ArrayList<>();
            aclBaralho.add(new ACL(ZooDefs.Perms.ALL, autentificaoDigest("host", sala.getSenha())));

            while (!baralho.isEmpty()) {
                Carta carta = baralho.comprar();
                zookeeper.create(baralhoPath + "/carta-", carta.export().getBytes(),
                        aclBaralho, CreateMode.PERSISTENT_SEQUENTIAL);
            }
        } catch (Exception e) {
            System.out.println("Não foi possível popular o baralho remoto: " + e.getMessage());
        }
    }

    public Carta comprarCartaZookeeper(Sala sala) {
        String baralhoPath = sala.getPath() + "/baralho";
        try {
            List<String> filhos = zookeeper.getChildren(baralhoPath, false);
            if (filhos.isEmpty()) {
                return null; // Baralho remoto vazio (aqui poderia reembaralhar a pilha de descarte)
            }

            Collections.sort(filhos); // Garante que pegamos sempre o menor sufixo sequencial (o "topo")
            String menorFilho = filhos.get(0);
            String cartaPath = baralhoPath + "/" + menorFilho;

            byte[] dados = zookeeper.getData(cartaPath, false, null);
            Carta carta = new Carta(new String(dados));

            zookeeper.delete(cartaPath, -1);
            return carta;
        } catch (Exception e) {
            System.out.println("Erro ao comprar carta do baralho remoto: " + e.getMessage());
            return null;
        }
    }
    public void escutarRequisicoes(Host host) {
        Sala sala = host.getSalaAtual();
        String requisicoesPath = sala.getPath() + "/requisicoes";
        try {
            zookeeper.addWatch(requisicoesPath, event -> {
                if (event.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
                    processarRequisicoesPendentes(sala);
                }
            }, AddWatchMode.PERSISTENT);
        } catch (Exception e) {
            System.out.println("Erro ao escutar requisições: " + e.getMessage());
        }
    }
    private void processarRequisicoesPendentes(Sala sala) {
        String requisicoesPath = sala.getPath() + "/requisicoes";
        try {
            List<String> requisicoes = zookeeper.getChildren(requisicoesPath, false);
            for (String req : requisicoes) {
                String reqPath = requisicoesPath + "/" + req;
                byte[] dados = zookeeper.getData(reqPath, false, null);
                String conteudo = new String(dados);

                // O nó é criado pelo jogador com o próprio path como conteúdo (ver pedirCompra).
                // Enquanto o conteúdo continuar sendo esse path, o pedido ainda não foi atendido;
                // depois de atendido, o conteúdo passa a ser os dados da carta (ver Carta.export()).
                if (conteudo.startsWith(sala.getPath() + "/jogadores")) {
                    Carta carta = comprarCartaZookeeper(sala);
                    if (carta != null) {
                        zookeeper.setData(reqPath, carta.export().getBytes(), -1);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao processar requisições: " + e.getMessage());
        }
    }
    public void pedirCompra(Jogador jogador) {
        Sala sala = jogador.getSalaAtual();
        Partida partida = sala.getPartida();
        String requisicoesPath = sala.getPath() + "/requisicoes";
        try {
            ArrayList<ACL> aclRequisicao = new ArrayList<>();
            // O host precisa de ALL para poder sobrescrever a resposta neste nó específico.
            aclRequisicao.add(new ACL(ZooDefs.Perms.ALL, autentificaoDigest("host", sala.getSenha())));
            // O jogador só precisa ler a resposta e apagar o próprio pedido depois de recebê-la.
            aclRequisicao.add(new ACL(ZooDefs.Perms.READ | ZooDefs.Perms.DELETE,
                    autentificaoDigest("jogador", sala.getSenha())));

            String reqPath = zookeeper.create(requisicoesPath + "/req-",
                    jogador.getPath().getBytes(),
                    aclRequisicao,
                    CreateMode.EPHEMERAL_SEQUENTIAL);

            java.util.concurrent.atomic.AtomicBoolean jaProcessado =
                    new java.util.concurrent.atomic.AtomicBoolean(false);

            byte[] dadosIniciais = zookeeper.getData(reqPath, event -> {
                if (event.getType() == Watcher.Event.EventType.NodeDataChanged
                        && jaProcessado.compareAndSet(false, true)) {
                    try {
                        byte[] dados = zookeeper.getData(reqPath, false, null);
                        processarCartaComprada(jogador, partida, reqPath, dados);
                    } catch (Exception e) {
                        System.out.println("Erro ao ler resposta da requisição: " + e.getMessage());
                    }
                }
            }, null);

            String conteudoInicial = new String(dadosIniciais);
            if (!conteudoInicial.equals(jogador.getPath()) && jaProcessado.compareAndSet(false, true)) {
                processarCartaComprada(jogador, partida, reqPath, dadosIniciais);
            }

            /*zookeeper.addWatch(reqPath, event -> {
                if (event.getType() == Watcher.Event.EventType.NodeDataChanged) {
                    try {
                        byte[] dados = zookeeper.getData(reqPath, false, null);
                        Carta cartaRecebida = new Carta(new String(dados));
                        jogador.adicionarCarta(cartaRecebida);
                        atualizarMao(jogador);
                        zookeeper.delete(reqPath, -1);
                        System.out.println(App.VERDE + "\nVocê comprou: " + cartaRecebida + App.RESET);

                        partida.passarTurno();
                        atualizarStatus(jogador);
                    } catch (Exception e) {
                        System.out.println("Erro ao ler resposta da requisição: " + e.getMessage());
                    }
                }
            }, AddWatchMode.PERSISTENT);*/

        } catch (Exception e) {
            System.out.println("Erro ao solicitar compra de carta: " + e.getMessage());
        }
    }

    private void processarCartaComprada(Jogador jogador, Partida partida, String reqPath, byte[] dados) throws Exception {
        Carta cartaRecebida = new Carta(new String(dados));
        jogador.adicionarCarta(cartaRecebida);
        atualizarMao(jogador);
        zookeeper.delete(reqPath, -1);
        System.out.println(App.VERDE + "\nVocê comprou: " + cartaRecebida + App.RESET);

        partida.passarTurno();
        atualizarStatus(jogador);
    }


    // Valida a senha digitada pelo cliente tentando ler o sub-nó restrito /conexoes
    public boolean validarSenha(Sala sala, String senha) throws InterruptedException {
        String conexoesPath = sala.getPath()+"/conexoes";
        // Tenta injetar a credencial de jogador no cliente atual
        zookeeper.addAuthInfo("digest", ("jogador:"+senha).getBytes());
        try {
            // Se conseguir ler os dados sem estourar exceção, significa que a senha está certa
            zookeeper.getData(conexoesPath, null, null);
            // Reseta a conexão de forma limpa
            desconectar();
            conectar(null, senha);
            zookeeper.addAuthInfo("digest", ("jogador:"+senha).getBytes());
            return true;
        } catch (KeeperException e) {
            return false; // Retorna falso se o ZooKeeper barrar por falta de permissão (senha errada)
        }
        catch (InterruptedException | IOException e){
            throw new InterruptedException();
        }
    }

    public Partida entrarNaSala(Jogador jogadorInicial){
        Sala sala = jogadorInicial.getSalaAtual();
        Partida partida = new Partida();
        sala.setPartida(partida);

        // Referência mutável: permite que este jogador seja promovido a Host durante a
        // partida (eleição de líder) sem precisar recriar os watchers já registrados
        // nesta sessão do ZooKeeper — eles passam a enxergar o novo objeto Host.
        java.util.concurrent.atomic.AtomicReference<Jogador> jogadorRef =
                new java.util.concurrent.atomic.AtomicReference<>(jogadorInicial);

        String jogadoresPath = sala.getPath()+"/jogadores";
        String conexoesPath = sala.getPath()+"/conexoes";
        String statusPath = sala.getPath()+"/status";

        //Adicionando Watchers para os nós de status e jogadores
        try {
            zookeeper.addWatch(statusPath, event -> {
                if (event.getType() == Watcher.Event.EventType.NodeDataChanged) {
                    byte[] dados = null;
                    try {
                        dados = zookeeper.getData(statusPath, null, null);
                    } catch (Exception e) {
                        System.out.println("DEU RUIM: " + e.getMessage());}
                    if (dados != null) {
                        partida.importStatus(new String(dados));
                        Jogador jogadorAtualLocal = jogadorRef.get();
                        if (partida.temVencedor()) {
                            App.executarNaUi(() -> {
                                App.telaVitoria(jogadorAtualLocal, partida);
                                if (jogadorAtualLocal instanceof Host) {
                                    removerNode(jogadorAtualLocal.getSalaAtual().getPath());
                                }
                                App.telaInicial();
                            });
                        } else {
                            partida.setFilaJogadores(atualizarListaJogadores(sala));
                            partida.importStatus(new String(dados));
                            App.executarNaUi(() -> App.telaGame(jogadorAtualLocal));
                        }

                    }
                }
            }, AddWatchMode.PERSISTENT);

            zookeeper.addWatch(jogadoresPath, event -> {
                if (event.getType() == Watcher.Event.EventType.NodeChildrenChanged /*|| event.getType() == Watcher.Event.EventType.NodeDataChanged*/) {
                    partida.setFilaJogadores(atualizarListaJogadores(sala));
                    if (!partida.isEmAndamento()) {
                        App.atualizarLobby(jogadorRef.get(), partida.getFilaJogadores().size(), sala.getLimiteJogadores());
                    }
                }
            },AddWatchMode.PERSISTENT);
        } catch (Exception e) {
            System.out.println("Erro ao adicionar watch: " +e.getMessage());
        }


        try {
            String senha = sala.getSenha();
            JSONObject dadosIniciais = new JSONObject();
            dadosIniciais.put("nome", jogadorInicial.getNome());
            dadosIniciais.put("quantidadeCartas", jogadorInicial.getMao().size());

            // Cria primeiro o nó do jogador para descobrir o path completo (que já inclui o
            // sequencial da SALA + o sequencial do JOGADOR, ex: "/salas/sala-0000000002/jogadores/jogador-0000000000")
            String jPath = zookeeper.create(jogadoresPath+"/jogador-", dadosIniciais.toString().getBytes(),
                    ZooDefs.Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT_SEQUENTIAL);
            jogadorInicial.setPath(jPath);

            partida.setFilaJogadores(atualizarListaJogadores(sala));

            // Usamos o PATH COMPLETO do jogador (e não só o sequencial "cru") como parte do
            // usuário da credencial digest. O sequencial do ZooKeeper só é único DENTRO do nó pai
            // que o gerou (aqui, /jogadores desta sala) — ou seja, o jogador-0000000000 da sala X
            // e o jogador-0000000000 da sala Y têm o MESMO sequencial. Se usássemos só o sequencial,
            // duas salas com a MESMA senha (bem provável, ex: senha vazia) fariam esse jogador de X
            // e esse jogador de Y caírem no MESMO hash digest, e um conseguiria ler/escrever a mão
            // do outro. Como o path completo já inclui o sequencial da sala (globalmente único
            // enquanto o nó /salas existir) + o sequencial do jogador, a string final nunca se repete,
            // mesmo que a senha coincida entre salas diferentes.
            String usuarioJogador = "jogador" + jPath;
            zookeeper.addAuthInfo("digest", (usuarioJogador + ":" + senha).getBytes());

            String cartasPath = jPath + "/cartas";
            ArrayList<ACL> aclCartas = new ArrayList<>();
            aclCartas.add(new ACL(ZooDefs.Perms.ALL, autentificaoDigest("host", senha)));
            aclCartas.add(new ACL(ZooDefs.Perms.ALL, autentificaoDigest(usuarioJogador, senha)));
            zookeeper.create(cartasPath, jogadorInicial.exportMao().getBytes(), aclCartas, CreateMode.PERSISTENT);

            // Observa o próprio nó de cartas: sempre que o host distribuir cartas iniciais,
            // ou o próprio jogador comprar/jogar uma carta, a mão local é ressincronizada a
            // partir do que está gravado no ZooKeeper.
            zookeeper.addWatch(cartasPath, event -> {
                if (event.getType() == Watcher.Event.EventType.NodeDataChanged) {
                    try {
                        Jogador jogadorAtualLocal = jogadorRef.get();
                        byte[] dados = zookeeper.getData(cartasPath, false, null);
                        jogadorAtualLocal.importMao(new String(dados));
                        if (jogadorAtualLocal.getSalaAtual().getPartida().isEmAndamento()) {
                            App.executarNaUi(() -> App.telaGame(jogadorAtualLocal));
                        }
                        System.out.println(App.VERDE + "\nSua mão foi atualizada." + App.RESET);
                    } catch (Exception e) {
                        System.out.println("Erro ao sincronizar mão: " + e.getMessage());
                    }
                }
            }, AddWatchMode.PERSISTENT);

            // O nó efêmero de /conexoes carrega como dado o path do jogador a que pertence.
            // É através dele (e não de um nó "/saidas" separado) que o host descobre quando um
            // jogador saiu: seja de forma voluntária (o jogador apaga esse nó ao clicar em "Sair"),
            // seja por queda de conexão/fechar o app (o próprio ZooKeeper remove o nó efêmero quando
            // a sessão morre). Nos dois casos o host recebe o mesmo evento em /conexoes.
            String conexaoPath = zookeeper.create(conexoesPath+"/conexao-", jPath.getBytes(),
                    ZooDefs.Ids.CREATOR_ALL_ACL, CreateMode.EPHEMERAL_SEQUENTIAL);
            jogadorInicial.setConexaoPath(conexaoPath);

            // TOLERÂNCIA A FALHAS: a partir daqui este cliente passa a monitorar se precisa
            // assumir o papel de host (ver 7.1 do documento do projeto).
            monitorarLideranca(jogadorRef);

        } catch (Exception e) {
            System.out.println("Não foi possível criar os nodes básicos da partida: "+e.getMessage());
        }
        return partida;

    }

    // =========================================================================
    // TOLERÂNCIA A FALHAS — ELEIÇÃO DE LÍDER
    // =========================================================================
    // Segue o algoritmo clássico de eleição de líder do ZooKeeper: em vez de todo
    // mundo observar o nó /conexoes inteiro (o que causaria uma "estampida" de
    // notificações toda vez que alguém entra ou sai — o chamado "herd effect"),
    // cada cliente observa APENAS o nó imediatamente anterior ao seu, na ordem dos
    // sequenciais. Quando esse nó desaparece, o cliente reavalia sua posição: se
    // virou o menor sequencial da lista, ele assume o papel de host.
    private void monitorarLideranca(java.util.concurrent.atomic.AtomicReference<Jogador> jogadorRef) {
        try {
            Jogador jogador = jogadorRef.get();
            String conexoesPath = jogador.getSalaAtual().getPath() + "/conexoes";
            List<String> filhos = zookeeper.getChildren(conexoesPath, false);
            Collections.sort(filhos);

            String meuNo = jogador.getConexaoPath().substring(conexoesPath.length() + 1);
            int minhaPos = filhos.indexOf(meuNo);
            if (minhaPos <= 0) {
                return; // já sou o menor sequencial (o host atual) -> nada a monitorar
            }

            String predecessorPath = conexoesPath + "/" + filhos.get(minhaPos - 1);
            Stat stat = zookeeper.exists(predecessorPath, event -> {
                if (event.getType() == Watcher.Event.EventType.NodeDeleted) {
                    reavaliarLideranca(jogadorRef);
                }
            });

            // O predecessor já tinha sumido entre o getChildren() e o exists() acima
            // (corrida rara, mas possível) -> reavalia imediatamente em vez de esperar
            // um evento que já não vai mais chegar.
            if (stat == null) {
                reavaliarLideranca(jogadorRef);
            }
        } catch (Exception e) {
            System.out.println("Erro ao monitorar liderança: " + e.getMessage());
        }
    }

    private void reavaliarLideranca(java.util.concurrent.atomic.AtomicReference<Jogador> jogadorRef) {
        try {
            Jogador jogador = jogadorRef.get();
            String conexoesPath = jogador.getSalaAtual().getPath() + "/conexoes";
            List<String> filhos = zookeeper.getChildren(conexoesPath, false);
            if (filhos.isEmpty()) {
                return; // sala vazia, nada a fazer
            }
            Collections.sort(filhos);

            String meuNo = jogador.getConexaoPath().substring(conexoesPath.length() + 1);
            if (filhos.get(0).equals(meuNo)) {
                assumirHost(jogadorRef);
            } else {
                monitorarLideranca(jogadorRef); // ainda não sou o menor, observa o novo predecessor
            }
        } catch (Exception e) {
            System.out.println("Erro ao reavaliar liderança: " + e.getMessage());
        }
    }

    // Promove o jogador local a Host: reaproveita path, conexão e mão (ver o novo
    // construtor Host(Jogador) em Host.java, que NÃO reembaralha o baralho, já que o
    // baralho real já existe no ZooKeeper e pode estar parcialmente consumido) e
    // retoma as responsabilidades de host (escutar conexões e requisições de compra).
    private void assumirHost(java.util.concurrent.atomic.AtomicReference<Jogador> jogadorRef) {
        Jogador jogadorAtual = jogadorRef.get();
        if (jogadorAtual instanceof Host) {
            return; // segurança: evita promover duas vezes
        }

        Host novoHost = new Host(jogadorAtual);
        jogadorRef.set(novoHost);

        try {
            escutarConexoes(novoHost);
            escutarRequisicoes(novoHost);
            System.out.println(App.VERMELHO + "\nO host caiu. Você assumiu o papel de host!" + App.RESET);
            App.executarNaUi(() -> App.telaGame(novoHost));
        } catch (Exception e) {
            System.out.println("Erro ao assumir papel de host: " + e.getMessage());
        }
    }

    public void comecarPartida(Host host){
        Sala sala = host.getSalaAtual();
        Partida partida = host.getSalaAtual().getPartida();
        Carta cartaTopo = comprarCartaZookeeper(host.getSalaAtual());

        if (cartaTopo.getCor() == Cor.PRETO) {
            cartaTopo.setCor(corAleatoria());
        }

        partida.setFilaJogadores(atualizarListaJogadores(sala));

        partida.setJogadorAtual(partida.getFilaJogadores().get(0));
        partida.adicionarPilha(cartaTopo);

        partida.setEmAndamento(true);
        distribuirCartasIniciais(host, 7);

        // A partir daqui o host passa a atender pedidos de compra feitos pelos jogadores
        escutarRequisicoes(host);

        //Quando os jogadores receberem do watcher que o nó de status foi atualizado a partida vai começar
        atualizarStatus(host);
    }
    private static final Cor[] CORES_VALIDAS = { Cor.VERDE, Cor.AMARELO, Cor.AZUL, Cor.VERMELHO };

    private Cor corAleatoria() {
        return CORES_VALIDAS[new java.util.Random().nextInt(CORES_VALIDAS.length)];
    }

    private void distribuirCartasIniciais(Host host, int quantidade) {
        Sala sala = host.getSalaAtual();
        Partida partida = sala.getPartida();

        for (Jogador jogador : partida.getFilaJogadores()) {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < quantidade; i++) {
                Carta carta = comprarCartaZookeeper(sala);
                if (carta == null) break; // baralho remoto acabou
                arr.put(carta.export());
            }
            String cartasPath = jogador.getPath() + "/cartas";
            try {
                zookeeper.setData(cartasPath, arr.toString().getBytes(), -1);
                JSONObject dadosJogador = new JSONObject();
                dadosJogador.put("nome", jogador.getNome());
                dadosJogador.put("quantidadeCartas", arr.length());
                zookeeper.setData(jogador.getPath(), dadosJogador.toString().getBytes(), -1);
            } catch (Exception e) {
                System.out.println("Erro ao distribuir cartas iniciais para " + jogador.getNome() + ": " + e.getMessage());
            }
        }
    }

    public boolean atualizarMao(Jogador jogador) {
        String cartasPath = jogador.getPath() + "/cartas";
        try {
            zookeeper.setData(cartasPath, jogador.exportMao().getBytes(), -1);
            JSONObject dadosJogador = new JSONObject();
            dadosJogador.put("nome", jogador.getNome());
            dadosJogador.put("quantidadeCartas", jogador.getMao().size());
            zookeeper.setData(jogador.getPath(), dadosJogador.toString().getBytes(), -1);
            return true;
        } catch (Exception e) {
            System.out.println("Erro ao atualizar mão no ZooKeeper: " + e.getMessage());
            return false;
        }
    }

    private final java.util.Map<String, String> conexoesJogadores = new java.util.HashMap<>();

    // Chamado pelo jogador que está saindo voluntariamente da sala/partida: apaga seu próprio
    // nó efêmero em /conexoes. Isso dispara, no host, o mesmo caminho de código que trataria
    // uma queda de conexão (sessão morrendo sozinha), então as cartas voltam ao baralho de
    // qualquer forma — não precisamos de um nó "/saidas" separado para esse aviso.
    public void sairDaSala(Jogador jogador) {
        try {
            Sala sala = jogador.getSalaAtual();

            if (jogador.getConexaoPath() != null &&
                    zookeeper.exists(jogador.getConexaoPath(), false) != null) {

                zookeeper.delete(jogador.getConexaoPath(), -1);
            }

            if (sala != null) {
                removerWatchesDaSala(sala, jogador);
            }
            jogador.getMao().clear();
            jogador.setSalaAtual(null);

        } catch (Exception e) {
            System.out.println("Erro ao sair da sala: " + e.getMessage());
        }
    }
    private void removerWatchesDaSala(Sala sala, Jogador jogador) {
        java.util.List<String> paths = new java.util.ArrayList<>();
        paths.add(sala.getPath() + "/status");
        paths.add(sala.getPath() + "/jogadores");
        if (jogador.getPath() != null) {
            paths.add(jogador.getPath() + "/cartas");
        }

        for (String path : paths) {
            try {
                zookeeper.removeAllWatches(path, Watcher.WatcherType.Any, false);
            } catch (Exception ignored) {
            }
        }
    }

    public void sairDaPartida(Jogador jogador) {
        Sala sala = jogador.getSalaAtual();
        Partida partida = sala.getPartida();

        if (partida.isEmAndamento() && partida.getFilaJogadores() != null) {
            int restantes = partida.getFilaJogadores().size() - 1;

            if (restantes >= 2) {
                // Ainda sobra gente suficiente: só passa a vez para o próximo e a
                // partida continua normalmente.
                Jogador proximo = partida.proximoJogadorExcluindo(jogador);
                if (proximo != null) {
                    partida.setJogadorAtual(proximo);
                    partida.incrementarTurno();
                    atualizarStatus(jogador);
                }
            } else if (restantes == 1) {
                // Só vai sobrar um jogador: ele vence por W.O. e a partida acaba,
                // todo mundo volta para a tela inicial (ver App.telaVitoria).
                Jogador ultimoJogador = partida.proximoJogadorExcluindo(jogador);
                if (ultimoJogador != null) {
                    partida.declararVencedor(ultimoJogador);
                    atualizarStatus(jogador);
                }
            }
            // restantes <= 0: não há mais ninguém para notificar.
        }

        sairDaSala(jogador);
    }


    // O host começa a acompanhar /conexoes assim que entra na sala (ver App.telaCriarSala),
    // guardando quem já está conectado e reagindo a entradas/saídas dali para frente.
    public void escutarConexoes(Host host) {
        Sala sala = host.getSalaAtual();
        String conexoesPath = sala.getPath() + "/conexoes";
        try {
            for (String filho : zookeeper.getChildren(conexoesPath, false)) {
                registrarConexao(conexoesPath, filho);
            }
            // Reconciliação usada quando um novo host assume via eleição de líder: o host
            // anterior pode ter caído ANTES deste cliente começar a observar /conexoes, então
            // o nó dele já não existe mais e jamais entraria no diff incremental que
            // processarMudancaConexoes faz a partir daqui pra frente. Aqui comparamos direto
            // quem está persistido em /jogadores contra quem tem uma conexão ativa agora, e
            // devolve ao baralho (removendo o nó /jogadores) quem ficou órfão.
            reconciliarJogadoresOrfaos(sala, host);
            zookeeper.addWatch(conexoesPath, event -> {
                if (event.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
                    processarMudancaConexoes(sala, host);
                }
            }, AddWatchMode.PERSISTENT);
        } catch (Exception e) {
            System.out.println("Erro ao escutar conexões: " + e.getMessage());
        }
    }

    private void reconciliarJogadoresOrfaos(Sala sala, Host host) {
        String jogadoresPath = sala.getPath() + "/jogadores";
        try {
            java.util.Set<String> pathsComConexaoAtiva = new java.util.HashSet<>(conexoesJogadores.values());
            for (String filho : zookeeper.getChildren(jogadoresPath, false)) {
                String pathJogador = jogadoresPath + "/" + filho;
                if (!pathsComConexaoAtiva.contains(pathJogador)) {
                    devolverCartasAoBaralho(sala, pathJogador, host);
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao reconciliar jogadores órfãos: " + e.getMessage());
        }
    }

    private void registrarConexao(String conexoesPath, String filho) {
        String path = conexoesPath + "/" + filho;
        try {
            byte[] dados = zookeeper.getData(path, false, null);
            if (dados != null) {
                conexoesJogadores.put(path, new String(dados));
            }
        } catch (Exception ignored) {
            // o nó pode ter desaparecido entre o getChildren e o getData; ignoramos, o próximo
            // evento de NodeChildrenChanged cuida da consistência
        }
    }

    // Compara a lista atual de filhos de /conexoes com o que sabíamos antes: quem sumiu, saiu
    // (voluntariamente ou por queda) e tem suas cartas devolvidas ao baralho; quem é novo, passa
    // a ser rastreado a partir de agora.
    private void processarMudancaConexoes(Sala sala, Host host) {
        String conexoesPath = sala.getPath() + "/conexoes";
        try {
            List<String> atuais = zookeeper.getChildren(conexoesPath, false);
            java.util.Set<String> atuaisPaths = new java.util.HashSet<>();
            for (String filho : atuais) {
                String path = conexoesPath + "/" + filho;
                atuaisPaths.add(path);
                if (!conexoesJogadores.containsKey(path)) {
                    registrarConexao(conexoesPath, filho);
                }
            }

            java.util.Iterator<java.util.Map.Entry<String, String>> it = conexoesJogadores.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<String, String> entry = it.next();
                if (!atuaisPaths.contains(entry.getKey())) {
                    devolverCartasAoBaralho(sala, entry.getValue(),host);
                    it.remove();
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao processar mudança em conexões: " + e.getMessage());
        }
    }

    // O host tem ACL de leitura/escrita tanto em /cartas de qualquer jogador quanto em /baralho,
    // então consegue, sozinho, ler a mão de quem saiu, devolver cada carta ao baralho remoto
    // (como novos nós sequenciais) e remover a árvore do jogador (/jogadores/jogador-XXXX).
    private void devolverCartasAoBaralho(Sala sala, String pathJogador, Host host) {
        String baralhoPath = sala.getPath() + "/baralho";
        String cartasPath = pathJogador + "/cartas";
        try {
            if(zookeeper.exists(cartasPath, false)==null){
                return;
            }
            ArrayList<ACL> aclBaralho = new ArrayList<>();
            aclBaralho.add(new ACL(ZooDefs.Perms.ALL, autentificaoDigest("host", sala.getSenha())));

            byte[] dados = zookeeper.getData(cartasPath, false, null);
            JSONArray arr = new JSONArray(new String(dados));
            for (int i = 0; i < arr.length(); i++) {
                Carta carta = new Carta(arr.getString(i));
                zookeeper.create(baralhoPath + "/carta-", carta.export().getBytes(),
                        aclBaralho, CreateMode.PERSISTENT_SEQUENTIAL);
            }

            ZKUtil.deleteRecursive(zookeeper, pathJogador);
            System.out.println(App.AMARELO + "\nUm jogador saiu da partida e suas cartas voltaram ao baralho." + App.RESET);

            Partida partida = sala.getPartida();

            // Se quem saiu estava com a vez, precisamos escolher o próximo ANTES de
            // reconstruir filaJogadores (senão ele já não estará mais na lista antiga e
            // proximoJogadorExcluindo perde a posição/ordem certa).
            boolean eraAVezDele = partida.getJogadorAtual() != null
                    && java.util.Objects.equals(partida.getJogadorAtual().getPath(), pathJogador);
            String proximoPath = null;
            if (eraAVezDele && partida.getFilaJogadores() != null) {
                for (Jogador j : partida.getFilaJogadores()) {
                    if (java.util.Objects.equals(j.getPath(), pathJogador)) {
                        Jogador proximo = partida.proximoJogadorExcluindo(j);
                        proximoPath = proximo == null ? null : proximo.getPath();
                        break;
                    }
                }
            }
            // Se o jogador que saiu era quem estava "obrigado" a comprar (+2/+4 pendurado),
            // essa obrigação não faz mais sentido — zera pra não travar o próximo turno.
            if (pathJogador.equals(partida.getJogadorObrigadoPath())) {
                partida.zerarObrigacaoCompra();
            }

            partida.setFilaJogadores(atualizarListaJogadores(sala));

            if (partida.getFilaJogadores().isEmpty()) {
                // Ninguém mais restou na sala: encerra e limpa os nodes no ZooKeeper.
                removerNode(sala.getPath());
            } else if (partida.getFilaJogadores().size() == 1 && partida.isEmAndamento() && !partida.temVencedor()) {
                // Só restou um jogador (por exemplo, alguém caiu da conexão em vez de
                // sair pelo menu): ele vence por W.O. e a partida acaba para todos.
                partida.declararVencedor(partida.getFilaJogadores().get(0));
                atualizarStatus(host);
            } else if (partida.isEmAndamento() && !partida.temVencedor()) {
                if (eraAVezDele && proximoPath != null) {
                    for (Jogador j : partida.getFilaJogadores()) {
                        if (java.util.Objects.equals(j.getPath(), proximoPath)) {
                            partida.setJogadorAtual(j);
                            break;
                        }
                    }
                    partida.incrementarTurno();
                }
                atualizarStatus(host);
            }



        } catch (Exception e) {
            System.out.println("Erro ao devolver cartas ao baralho: " + e.getMessage());
        }
    }

    public boolean atualizarStatus(Jogador jogador){
        String statusPath = jogador.getSalaAtual().getPath()+"/status";
        Partida partida = jogador.getSalaAtual().getPartida();

        try {
            zookeeper.setData(statusPath, partida.exportStatus().getBytes(), -1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public ArrayList<Jogador> atualizarListaJogadores(Sala sala){
        ArrayList<Jogador> jogadores = new ArrayList<>();
        String jogadoresPath = sala.getPath()+"/jogadores";

        try {
            List<String> filhos = zookeeper.getChildren(jogadoresPath, false);
            for (String filho : filhos) {
                String pathJogador = jogadoresPath + "/" + filho;
                byte[] dados = zookeeper.getData(pathJogador,false,null);
                if (dados != null) {
                    JSONObject obj = new JSONObject(new String(dados));
                    Jogador novoJogador = new Jogador(obj.getString("nome"), sala);
                    novoJogador.setPath(pathJogador);
                    novoJogador.setQuantidadeCartas(obj.optInt("quantidadeCartas", 0));
                    jogadores.add(novoJogador);
                }
            }
        }
        catch (Exception e) {
            System.out.println("Erro ao atulizar os jogadores: " + e.getMessage());
        }
        return jogadores;

    }

    public void comprarCartasForcadas(Jogador jogador, int quantidade) {
        if (quantidade <= 0) {
            Partida partida = jogador.getSalaAtual().getPartida();
            partida.zerarObrigacaoCompra();
            partida.passarTurno();
            jogador.setPenalidadeEmAndamento(false);
            atualizarStatus(jogador);
            return;
        }

        Sala sala = jogador.getSalaAtual();
        String requisicoesPath = sala.getPath() + "/requisicoes";
        try {
            ArrayList<ACL> aclRequisicao = new ArrayList<>();
            aclRequisicao.add(new ACL(ZooDefs.Perms.ALL, autentificaoDigest("host", sala.getSenha())));
            aclRequisicao.add(new ACL(ZooDefs.Perms.READ | ZooDefs.Perms.DELETE,
                    autentificaoDigest("jogador", sala.getSenha())));

            String reqPath = zookeeper.create(requisicoesPath + "/req-",
                    jogador.getPath().getBytes(),
                    aclRequisicao,
                    CreateMode.EPHEMERAL_SEQUENTIAL);

            java.util.concurrent.atomic.AtomicBoolean jaProcessado =
                    new java.util.concurrent.atomic.AtomicBoolean(false);


            /*zookeeper.addWatch(reqPath, event -> {
                if (event.getType() == Watcher.Event.EventType.NodeDataChanged) {
                    try {
                        byte[] dados = zookeeper.getData(reqPath, false, null);
                        Carta cartaRecebida = new Carta(new String(dados));
                        jogador.adicionarCarta(cartaRecebida);
                        atualizarMao(jogador);
                        zookeeper.delete(reqPath, -1);
                        System.out.println(App.VERMELHO + "Você comprou (penalidade): " + cartaRecebida + App.RESET);
                        comprarCartasForcadas(jogador, quantidade - 1); // encadeia até acabar
                    } catch (Exception e) {
                        System.out.println("Erro ao processar compra forçada: " + e.getMessage());
                    }
                }
            }, AddWatchMode.PERSISTENT);*/

            byte[] dadosIniciais = zookeeper.getData(reqPath, event -> {
                if (event.getType() == Watcher.Event.EventType.NodeDataChanged
                        && jaProcessado.compareAndSet(false, true)) {
                    try {
                        byte[] dados = zookeeper.getData(reqPath, false, null);
                        processarCartaDaPenalidade(jogador, reqPath, dados, quantidade);
                    } catch (Exception e) {
                        System.out.println("Erro ao processar compra forçada: " + e.getMessage());
                    }
                }
            }, null);

            // Se a resposta já estava lá quando registramos o watch (host foi mais rápido
            // que a rede até nós), processa imediatamente em vez de esperar um evento que
            // já não vai mais chegar.
            String conteudoInicial = new String(dadosIniciais);
            if (!conteudoInicial.equals(jogador.getPath()) && jaProcessado.compareAndSet(false, true)) {
                processarCartaDaPenalidade(jogador, reqPath, dadosIniciais, quantidade);
            }


        } catch (Exception e) {
            System.out.println("Erro ao solicitar compra forçada: " + e.getMessage());
        }
    }

    private void processarCartaDaPenalidade(Jogador jogador, String reqPath, byte[] dados, int quantidade) throws Exception {
        Carta cartaRecebida = new Carta(new String(dados));
        jogador.adicionarCarta(cartaRecebida);
        atualizarMao(jogador);
        zookeeper.delete(reqPath, -1);
        System.out.println(App.VERMELHO + "Você comprou (penalidade): " + cartaRecebida + App.RESET);
        comprarCartasForcadas(jogador, quantidade - 1); // encadeia até acabar
    }

}
