package buno.model;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Stack;

public class Partida {
    private int sentido = 1; //1 para sentido horário, -1 para anti-horário
    private int turno = 1; // Contador de turnos da jogada
    private int round = 1; // Contador de rodadas do jogo
    private Jogador jogadorAtual;
    private Stack<Carta> pilhaDescarte = new Stack<>();
    private ArrayList<Jogador> filaJogadores;
    private int cartasParaComprar = 0;
    private String jogadorObrigadoPath; //caminho do jogador que é obrigado a comprar
    private boolean emAndamento = false; //indica se a partida começou
    private String vencedorPath;
    private String vencedorNome;


    public Jogador proximoJogador(){
        int quant_jogadores = filaJogadores.size();
        int pos = filaJogadores.indexOf(jogadorAtual);
        pos += sentido;
        if (pos < 0){
            pos = quant_jogadores - 1;
        }
        else if(pos >= quant_jogadores){
            pos = 0;
        }
        jogadorAtual = filaJogadores.get(pos);
        return  jogadorAtual;
    }

    public String exportStatus(){
        JSONObject obj = new JSONObject();
        obj.put("round", round);
        obj.put("turno", turno);
        obj.put("sentido", sentido);
        obj.put("cartaTopo", pilhaDescarte.peek().export());
        obj.put("jogadorAtual", jogadorAtual.getPath());
        obj.put("cartasParaComprar", cartasParaComprar);
        obj.put("jogadorObrigadoPath", jogadorObrigadoPath == null ? JSONObject.NULL : jogadorObrigadoPath);
        obj.put("vencedorPath", vencedorPath == null ? JSONObject.NULL : vencedorPath);
        obj.put("vencedorNome", vencedorNome == null ? JSONObject.NULL : vencedorNome);


        return obj.toString();
    }

    public void importStatus(String json){
        if (json != null){
            JSONObject obj = new JSONObject(json);
            round = obj.getInt("round");
            turno = obj.getInt("turno");
            sentido = obj.getInt("sentido");
            //Seria bom comparar antes de ir adicionando
            pilhaDescarte.push(new Carta(obj.getString("cartaTopo")));
            String pathJogador = obj.getString("jogadorAtual");
            cartasParaComprar = obj.optInt("cartasParaComprar", 0);
            jogadorObrigadoPath = obj.isNull("jogadorObrigadoPath") ? null : obj.optString("jogadorObrigadoPath", null);
            vencedorPath = obj.isNull("vencedorPath") ? null : obj.optString("vencedorPath", null);
            vencedorNome = obj.isNull("vencedorNome") ? null : obj.optString("vencedorNome", null);


            for (Jogador jogador : filaJogadores){
                if (jogador.getPath().equals(pathJogador)){
                    jogadorAtual = jogador;
                    return;
                }
            }
        }
    }

    public void inverterDirecao(){
        sentido *= -1;
    }

    public void adicionarPilha(Carta carta){
        pilhaDescarte.push(carta);
    }

    public void passarTurno(){
        proximoJogador();
        turno+=1;
    }
    public Jogador proximoJogadorExcluindo(Jogador quemVaiSair) {
        if (filaJogadores == null || filaJogadores.size() <= 1) {
            return null;
        }
        int quant = filaJogadores.size();
        int posSaida = filaJogadores.indexOf(quemVaiSair);
        if (posSaida < 0) {
            return filaJogadores.get(0);
        }
        int pos = posSaida;
        do {
            pos += sentido;
            if (pos < 0) pos = quant - 1;
            else if (pos >= quant) pos = 0;
        } while (pos == posSaida);
        return filaJogadores.get(pos);
    }


    public Carta getCartaTopo(){
        if (pilhaDescarte.isEmpty()){
            return null;
        }
        return pilhaDescarte.peek();
    }

    public void zerarObrigacaoCompra() {
        cartasParaComprar = 0;
        jogadorObrigadoPath = null;
    }
    public void declararVencedor(Jogador jogador) {
        this.vencedorPath = jogador.getPath();
        this.vencedorNome = jogador.getNome();
    }

    public boolean temVencedor() {
        return vencedorPath != null;
    }

    public void incrementarTurno(){
        turno += 1;
    }

    // Olha quem seria o próximo jogador SEM mudar o estado da partida
    public Jogador espiarProximoJogador() {
        int quant = filaJogadores.size();
        int pos = filaJogadores.indexOf(jogadorAtual) + sentido;
        if (pos < 0) pos = quant - 1;
        else if (pos >= quant) pos = 0;
        return filaJogadores.get(pos);
    }

    public int getRound() {
        return round;
    }

    public int getSentido() {
        return sentido;
    }

    public int getTurno() {
        return turno;
    }

    public ArrayList<Jogador> getFilaJogadores() {
        return filaJogadores;
    }

    public void setFilaJogadores(ArrayList<Jogador> filaJogadores) {
        this.filaJogadores = filaJogadores;
    }

    public boolean isEmAndamento() {
        return emAndamento;
    }

    public void setEmAndamento(boolean emAndamento) {
        this.emAndamento = emAndamento;
    }

    public String getVencedorPath() {
        return vencedorPath;
    }

    public String getVencedorNome() {
        return vencedorNome;
    }


    public Jogador getJogadorAtual() {
        return jogadorAtual;
    }

    public void setJogadorAtual(Jogador jogadorAtual) {
        this.jogadorAtual = jogadorAtual;
    }

    public int getCartasParaComprar() { return cartasParaComprar; }
    public void setCartasParaComprar(int cartasParaComprar) { this.cartasParaComprar = cartasParaComprar; }

    public String getJogadorObrigadoPath() { return jogadorObrigadoPath; }
    public void setJogadorObrigadoPath(String path) { this.jogadorObrigadoPath = path; }

}
