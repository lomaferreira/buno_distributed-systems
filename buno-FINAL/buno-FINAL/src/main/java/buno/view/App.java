package buno.view;

import buno.controller.GameController;
import buno.controller.ZookeeperService;
import buno.model.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class App {

    // =========================
    // ANSI COLORS
    // =========================
    public static final String RESET = "\u001B[0m";

    public static final String PRETO = "\u001B[30m";
    public static final String VERMELHO = "\u001B[31m";
    public static final String VERDE = "\u001B[32m";
    public static final String AMARELO = "\u001B[33m";
    public static final String AZUL = "\u001B[34m";
    public static final String ROXO = "\u001B[35m";
    public static final String CIANO = "\u001B[36m";
    public static final String BRANCO = "\u001B[37m";

    public static final String NEGRITO = "\u001B[1m";

    static java.util.Scanner scanner = new java.util.Scanner(System.in);
    public static GameController gameController;
    public static ZookeeperService conexao;

    private static final java.util.concurrent.ExecutorService uiExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    public static void executarNaUi(Runnable tarefa) {
        uiExecutor.submit(tarefa);
    }
    public static void main(String[] args) {
        try {
            String enderecoZk = args.length >0 ?args[0] : "localhost:2181";
            conexao = new ZookeeperService(enderecoZk);

            telaInicial();

            // Mantém aplicação viva
            new CountDownLatch(1).await();

        } catch (Exception e) {
            erro(e.getMessage());
        } finally {
            conexao.desconectar();
        }
    }

    // =========================
    // TELA INICIAL
    // =========================
    public static void telaInicial() {

        limparTela();

        titulo();

        System.out.println(AZUL + "┌──────────────────────────────┐" + RESET);
        System.out.println(AZUL + "│" + RESET + "  [C] Criar Sala              " + AZUL + "│" + RESET);
        System.out.println(AZUL + "│" + RESET + "  [E] Entrar em uma Sala      " + AZUL + "│" + RESET);
        System.out.println(AZUL + "│" + RESET + "  [S] Sair                    " + AZUL + "│" + RESET);
        System.out.println(AZUL + "└──────────────────────────────┘" + RESET);

        System.out.print(CIANO + "\nEscolha uma opção: " + RESET);

        String opcao = scanner.nextLine().toUpperCase();

        switch (opcao) {

            case "C":
                telaCriarSala();
                break;

            case "E":
                entrarSala();
                break;

            case "S":
                sucesso("Encerrando aplicação...");
                break;

            default:
                erro("Entrada inválida!");
                pausar();
                telaInicial();
        }
    }

    // =========================
    // CRIAR SALA
    // =========================
    public static void telaCriarSala() {

        limparTela();

        cabecalho("CRIAR SALA");

        System.out.print(CIANO + "Nome da sala: " + RESET);
        String nome = scanner.nextLine();;

        System.out.print(CIANO + "Senha da sala (ENTER = sem senha): " + RESET);
        String senha = scanner.nextLine();;

        System.out.print(CIANO + "Quantidade máxima de jogadores (2-6): " + RESET);

        int maxJogadores;

        try {
            maxJogadores = Integer.parseInt(scanner.nextLine());

            if (maxJogadores < 2 || maxJogadores > 6) {
                erro("Número inválido de jogadores.");
                pausar();
                telaCriarSala();
                return;
            }

        } catch (Exception e) {
            erro("Digite um número válido.");
            pausar();
            telaCriarSala();
            return;
        }
        Sala novaSala = new Sala(nome, senha, maxJogadores);
        String nomeHost = telaCriaNome();

        Host host = conexao.criarSala(nomeHost, novaSala);
        conexao.criarNodesPartida(novaSala);
        // Transfere o baralho local (já embaralhado no construtor de Host) para o /baralho
        // remoto no ZooKeeper, com ACL restrita ao host. A partir daqui, tanto a carta inicial
        // da pilha quanto as compras dos jogadores acontecem através do ZooKeeper.
        conexao.criarBaralhoRemoto(host);
        conexao.entrarNaSala(host);
        conexao.escutarConexoes(host);
        telaLobbyJogador(host);

    }

    // =========================
    // ENTRAR SALA
    // =========================
    public static void entrarSala() {
        limparTela();
        cabecalho("SALAS DISPONÍVEIS");

        List<Sala> salas = conexao.listar_salas();

        if (salas.isEmpty()) {
            erro("Nenhuma sala disponível.");
            pausar();
            telaInicial();
            return;
        }

        // Lista todas as salas encontradas no nó /salas
        for (int i = 0; i < salas.size(); i++) {
            Sala sala = salas.get(i);
            System.out.println(
                    AZUL + "[" + i + "] " + RESET +
                            NEGRITO + sala.getNome() + RESET +
                            " | Jogadores Máx: " + sala.getLimiteJogadores()
            );
        }

        System.out.print(CIANO + "\nEscolha o índice da sala: " + RESET);

        int entrada;

        try {
            entrada = Integer.parseInt(scanner.nextLine());

            if (entrada < 0 || entrada >= salas.size()) {
                erro("Índice inválido.");
                pausar();
                entrarSala();
                return;
            }

        } catch (Exception e) {
            erro("Digite um número válido.");
            pausar();
            entrarSala();
            return;
        }

        Sala salaEscolhida = salas.get(entrada);

        limparTela();

        cabecalho("ENTRAR NA SALA");

        System.out.println("Sala selecionada: "
                + VERDE + salaEscolhida.getNome() + RESET);

        boolean senhaValida = false;

        // Loop de validação de senha utilizando ACL Digest
        do {
            System.out.print(CIANO + "Digite a senha: " + RESET);
            try {
                String senha = scanner.nextLine();;
                senhaValida = conexao.validarSenha(salaEscolhida, senha);
                if (senhaValida) {
                    salaEscolhida.setSenha(senha);
                } else {
                    erro("Senha incorreta!");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } while (!senhaValida);

        String nome = telaCriaNome();
        Jogador jogador = new Jogador(nome, salaEscolhida);
        // Cria o nó efêmero de conexão e o nó persistente do jogador
        conexao.entrarNaSala(jogador);

        telaLobbyJogador(jogador);
    }

    public static void telaLobbyJogador(Jogador jogador){
        Sala sala = jogador.getSalaAtual();
        gameController = new GameController(jogador);

        Partida partida = sala.getPartida();
        int atual = (partida.getFilaJogadores() != null) ? partida.getFilaJogadores().size() : 1;

        if (jogador instanceof Host host) {
            scanner.nextLine();
            conexao.comecarPartida(host);
        }
    }

    public static void atualizarLobby(Jogador jogador, int atual, int max) {
        Sala sala = jogador.getSalaAtual();

        limparTela();
        //cabecalho("SALA: " + sala.getNome().toUpperCase());

        System.out.println(NEGRITO + "Jogadores na sala: " + RESET + VERDE + atual + "/" + max + RESET);
        System.out.println();

            if (jogador instanceof Host) {
                System.out.println(AMARELO + "Você é o HOST desta sala." + RESET);
                System.out.println("Pressione [ENTER] a qualquer momento para INICIAR a partida.\n");
            } else {
                System.out.println(AMARELO + "Você entrou na sala com sucesso!" + RESET);
                System.out.println("Aguardando o Host iniciar a partida...\n Não feche esta janela.\n");
            }

    }

    // =========================
    // TELA DE VITÓRIA
    // =========================
    public static void telaVitoria(Jogador jogador, Partida partida) {
        limparTela();
        titulo();
        cabecalho("FIM DE JOGO");

        boolean venceu = jogador.getPath() != null && jogador.getPath().equals(partida.getVencedorPath());

        if (venceu) {
            sucesso("Parabéns! Você venceu a partida ficando sem cartas na mão!");
        } else {
            System.out.println(AMARELO + NEGRITO + "\n🏆 " + partida.getVencedorNome()
                    + " venceu a partida ficando sem cartas na mão!" + RESET + "\n");
        }

        pausar();

        // Limpa os nós da sala no ZooKeeper (o host apaga tudo; jogadores comuns só
        // removem sua própria conexão) antes de voltar para a tela inicial.
        if (jogador instanceof Host) {
            conexao.removerNode(jogador.getSalaAtual().getPath());
        } else {
            conexao.sairDaSala(jogador);
        }

        telaInicial();
    }

    public static String telaCriaNome() {
        limparTela();
        cabecalho("NOME DO JOGADOR");

        System.out.print(CIANO + "Digite seu nome: " + RESET);
        String nome = scanner.nextLine();

        while (nome == null || nome.trim().isEmpty()) {
            erro("O nome não pode estar vazio.");
            System.out.print(CIANO + "Digite seu nome: " + RESET);
            nome = scanner.nextLine();
        }
        return nome.trim();
    }


    public static void telaGame(Jogador jogador) {
        Partida partida = jogador.getSalaAtual().getPartida();

        if (partida.temVencedor()) {
            return;
        }

        limparTela();
        titulo();
        cabecalho("PARTIDA");

        // =========================
        // STATUS
        // =========================

        System.out.println(AZUL + "┌────────────────────────────────────┐" + RESET);
        System.out.println("  ROUND: " + AMARELO + partida.getRound() + RESET);
        System.out.println("  TURNO: " + VERDE + partida.getTurno() + RESET);

        String sentido = partida.getSentido() == 1 ? "HORÁRIO" : "ANTI-HORÁRIO";

        System.out.println("  SENTIDO: " + CIANO + sentido + RESET);
        System.out.println(AZUL + "└────────────────────────────────────┘" + RESET);

        System.out.println();

        // =========================
        // CARTA DA MESA
        // =========================

        Carta cartaTopo = partida.getCartaTopo();
        if (cartaTopo != null) {
            System.out.println(NEGRITO + "CARTA DA MESA" + RESET);
            System.out.println(ROXO + "╔════════════════════╗" + RESET);
            int tamanhoTexto = (20 - cartaTopo.getSimbolo().name().length()) / 2;
            System.out.printf(espacos(tamanhoTexto)+ "%s\n", cartaTopo);
            System.out.println(ROXO + "╚════════════════════╝" + RESET);
        }
        System.out.println();

        // =========================
        // JOGADORES
        // =========================
        System.out.println(NEGRITO + "JOGADORES" + RESET);
        System.out.println(AZUL + "┌────────────────────────────────────┐" + RESET);

        for (Jogador j : partida.getFilaJogadores()) {
            boolean atual = j.equals(partida.getJogadorAtual());
            String marcador = atual ? VERDE + "-> " + RESET: "  ";
            String nome = atual ? NEGRITO + j.getNome() + RESET : j.getNome();
            int qtdCartas = j.equals(jogador) ? jogador.getMao().size() : j.getQuantidadeCartas();
            String cartas = "(" + qtdCartas + " cartas)";
            System.out.printf("%s%-20s %-10s\n",marcador, nome, cartas);
        }

        System.out.println(AZUL + "└────────────────────────────────────┘" + RESET);
        System.out.println();

        // =========================
        // MÃO
        // =========================
        System.out.println(NEGRITO + "SUA MÃO" + RESET);
        System.out.println(AZUL + "┌────────────────────┐" + RESET);
        for (int i = 0; i < jogador.getMao().size(); i++) {
            System.out.printf(AMARELO + "  [%02d] " + RESET + "%-28s\n", i, jogador.getMao().get(i));
        }
        System.out.println(AZUL + "└────────────────────┘" + RESET);
        System.out.println();

        // =========================
        // COMANDOS
        // =========================
        if (partida.getJogadorAtual().equals(jogador)) {
            if (partida.getCartasParaComprar() > 0
                    && jogador.getPath() != null
                    && jogador.getPath().equals(partida.getJogadorObrigadoPath())) {
                if (!jogador.isPenalidadeEmAndamento()) {
                    jogador.setPenalidadeEmAndamento(true);
                    System.out.println(VERMELHO + "\nVocê foi penalizado! Comprando "
                            + partida.getCartasParaComprar() + " carta(s) e perdendo a vez..." + RESET);
                    conexao.comprarCartasForcadas(jogador, partida.getCartasParaComprar());
                }
                return; // não mostra o menu, a compra forçada cuida do resto
            }


            while (true) {

                System.out.println(NEGRITO + "COMANDOS" + RESET);
                System.out.println(CIANO + "[NUMERO]" + RESET + " Jogar carta");
                System.out.println(CIANO + "[C]" + RESET + " Comprar carta");
                System.out.println(CIANO + "[S]" + RESET + " Sair da partida");
                System.out.print(AMARELO + "\nEscolha: " + RESET);

                String escolha = scanner.nextLine().trim();

                if (escolha.equalsIgnoreCase("C")) {
                    conexao.pedirCompra(jogador);
                    break;
                }

                if (escolha.equalsIgnoreCase("S")) {
                    conexao.sairDaSala(jogador);
                    sucesso("Você saiu da partida. Suas cartas voltarão ao baralho.");
                    pausar();
                    telaInicial();
                    break;
                }

                try {
                    int idxCarta = Integer.parseInt(escolha);

                    if (idxCarta < 0 || idxCarta >= jogador.getMao().size()) {
                        erro("Número da carta inválido.");
                        continue;
                    }

                    Carta cartaEscolhida = jogador.getMao().get(idxCarta);

                    if (gameController.jogarCarta(jogador, cartaEscolhida)) {
                        conexao.atualizarMao(jogador);
                        conexao.atualizarStatus(jogador);
                        break;
                    } else {
                        erro("Essa carta não pode ser jogada.");
                    }

                } catch (NumberFormatException e) {
                    erro("Digite um número da carta, C ou S.");
                }
            }
        }
        else{
            System.out.println("Aguardando " + AMARELO + NEGRITO + partida.getJogadorAtual().getNome() + RESET + " jogar");
        }

    }


    // =========================
    // UTILITÁRIOS
    // =========================
    public static void titulo() {

        System.out.println(ROXO + NEGRITO);
        System.out.println("██████╗ ██╗   ██╗███╗   ██╗ ██████╗ ");
        System.out.println("██╔══██╗██║   ██║████╗  ██║██╔═══██╗");
        System.out.println("██████╔╝██║   ██║██╔██╗ ██║██║   ██║");
        System.out.println("██╔══██╗██║   ██║██║╚██╗██║██║   ██║");
        System.out.println("██████╔╝╚██████╔╝██║ ╚████║╚██████╔╝");
        System.out.println("╚═════╝  ╚═════╝ ╚═╝  ╚═══╝ ╚═════╝ ");
        System.out.println(RESET);
    }

    public static Cor escolherCor() {
        Cor corEscolhida = null;
        while (corEscolhida == null) {
            System.out.println(NEGRITO + "\nEscolha a nova cor da mesa:" + RESET);
            System.out.println(VERDE    + "[1] VERDE"    + RESET);
            System.out.println(AMARELO  + "[2] AMARELO"  + RESET);
            System.out.println(AZUL     + "[3] AZUL"     + RESET);
            System.out.println(VERMELHO + "[4] VERMELHO" + RESET);
            System.out.print(CIANO + "Escolha: " + RESET);

            switch (scanner.nextLine()) {
                case "1": corEscolhida = Cor.VERDE; break;
                case "2": corEscolhida = Cor.AMARELO; break;
                case "3": corEscolhida = Cor.AZUL; break;
                case "4": corEscolhida = Cor.VERMELHO; break;
                default: erro("Opção inválida.");
            }
        }
        return corEscolhida;
    }

    public static void cabecalho(String titulo) {

        System.out.println(AZUL + "════════════════════════════════════" + RESET);
        System.out.println(NEGRITO + "           " + titulo + RESET);
        System.out.println(AZUL + "════════════════════════════════════" + RESET + "\n");
    }

    public static void limparTela() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    public static void pausar() {
        System.out.print(AMARELO + "\nPressione ENTER para continuar..." + RESET);
        scanner.nextLine();;
    }

    public static void sucesso(String msg) {
        System.out.println(VERDE + "\n[✔] " + msg + RESET);
    }

    public static void erro(String msg) {
        System.out.println(VERMELHO + "\n[✖] " + msg + RESET);
    }

    public static String espacos(int quantidade) {
        return " ".repeat(Math.max(0, quantidade));
    }
}