package buno.model;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

public class Sala {
    private String path; //caminho da sala
    private String nome;
    private String senha;
    private Partida partida;
    private int limiteJogadores;
    private boolean temSenha;

    public Sala(String nome, String senha, int limiteJogadores){
        this.nome = nome;
        this.senha = senha;
        this.temSenha = senha != null && !senha.isEmpty();
        this.partida = new Partida();
        this.limiteJogadores = limiteJogadores;
    }

    public Sala(String json, String path){
        JSONObject obj = new JSONObject(json);
        this.nome = obj.getString("nome");
        this.temSenha = obj.optBoolean("tem_senha", false);
        this.path = path;
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
        obj.put("tem_senha", this.temSenha);
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

    public boolean temSenha() {
        return temSenha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }
}

