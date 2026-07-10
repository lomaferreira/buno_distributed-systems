package buno.model;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

public class Sala {
    private String path;
    private String nome;
    private String senha;
    private Jogador host;
    private Partida partida;
    private int limiteJogadores;
    private List<Jogador> jogadores;

    public Sala(String nome, String senha, int limiteJogadores){
        this.nome = nome;
        this.senha = senha;
        this.partida = new Partida();
        this.limiteJogadores = limiteJogadores;
    }

    public Sala(String json, String path){
        JSONObject obj = new JSONObject(json);
        this.nome = obj.getString("nome");
        this.path = path;
        this.jogadores = new ArrayList<>();
        this.limiteJogadores = obj.getInt("limite_jogadores");
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getNome() {
        return nome;
    }

    public String getSenha() {
        return senha;
    }

    public int getLimiteJogadores() {
        return limiteJogadores;
    }

    public String exportJson(){
        JSONObject obj = new JSONObject();
        obj.put("nome", this.nome);
        obj.put("limite_jogadores", this.limiteJogadores);
        return obj.toString();
    }

    public String getPath() {
        return path;
    }

    public Partida getPartida() {
        return partida;
    }

    public void setPartida(Partida partida) {
        this.partida = partida;
    }

    @Override
    public String toString() {
        return String.format("%s (%d/%d)",this.nome, this.jogadores.size(), this.limiteJogadores);
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }
}

