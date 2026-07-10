package buno.model;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Objects;

public class Jogador {
    private String nome;
    private String path; //O caminho (ZNode) do jogador dentro do ZooKeeper
    private Sala salaAtual;
    private ArrayList<Carta> mao;
    private int quantidadeCartas; //Contagem pública da mão (para outros jogadores verem sem ler /cartas)
    private String conexaoPath; //O caminho do nó efêmero em /conexoes que representa esta sessão

    public Jogador(String nome, Sala salaAtual){
        this.salaAtual = salaAtual;
        mao = new ArrayList<>();
        this.nome = nome;
    }

    public void adicionarCarta(Carta carta){
        mao.add(carta);
    }

    public Carta removerCarta(Carta carta){
        if (mao.contains(carta)){
            mao.remove(carta);
            return carta;
        }
        return null;
    }

    // Serializa a mão do jogador em um JSON array de códigos de carta (ex: ["VERDE,NOVE", ...]),
    // usado para persistir/sincronizar a mão no nó /cartas do ZooKeeper.
    public String exportMao(){
        JSONArray arr = new JSONArray();
        for (Carta carta : mao){
            arr.put(carta.export());
        }
        return arr.toString();
    }

    // Reconstrói a mão local a partir do JSON guardado no nó /cartas do ZooKeeper.
    public void importMao(String json){
        mao.clear();
        if (json != null && !json.isEmpty()){
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++){
                mao.add(new Carta(arr.getString(i)));
            }
        }
    }


    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getConexaoPath() {
        return conexaoPath;
    }

    public void setConexaoPath(String conexaoPath) {
        this.conexaoPath = conexaoPath;
    }

    public int getQuantidadeCartas() {
        return quantidadeCartas;
    }

    public void setQuantidadeCartas(int quantidadeCartas) {
        this.quantidadeCartas = quantidadeCartas;
    }

    public ArrayList<Carta> getMao(){
        return mao;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Sala getSalaAtual() {
        return salaAtual;
    }
    public void setSalaAtual(Sala salaAtual) {
        this.salaAtual = salaAtual;
    }

    //Dois jogadores são considerados iguais caso tenham o mesmo caminho no zookeeper
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Jogador jogador)) return false;
        return Objects.equals(path, jogador.path);
    }
    @Override
    public int hashCode() {
        return Objects.hash(path);
    }
}
