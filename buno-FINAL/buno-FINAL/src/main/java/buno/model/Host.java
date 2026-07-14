package buno.model;

public class Host extends Jogador {
    private Baralho baralho;

    public Host(String nome, Sala salaAtual) {
        super(nome, salaAtual);

        baralho = new Baralho();
        baralho.preencherBaralho();
        baralho.embaralhar();
    }

    //construtor para a elição de lider
    public Host(Jogador jogadorExistente) {
        super(jogadorExistente.getNome(), jogadorExistente.getSalaAtual());
        this.setPath(jogadorExistente.getPath());
        this.setConexaoPath(jogadorExistente.getConexaoPath());
        this.importMao(jogadorExistente.exportMao());
        this.setQuantidadeCartas(jogadorExistente.getQuantidadeCartas());
        this.setPenalidadeEmAndamento(jogadorExistente.isPenalidadeEmAndamento());
    }

    public Baralho getBaralho() {return baralho;}
}
