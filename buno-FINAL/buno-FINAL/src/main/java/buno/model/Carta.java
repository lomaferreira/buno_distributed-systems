package buno.model;

public class Carta {
    private Simbolo simbolo;
    private Cor cor;

    public Carta(String codigo) {
        String[] partes = codigo.split(",");
        cor = Cor.fromString(partes[0]);
        simbolo = Simbolo.fromString(partes[1]);
        if (cor == null || simbolo == null){
            throw new IllegalArgumentException("Código de carta inválido: " + codigo);
        }
    }

    public boolean podeSerJogada(Carta topoDescarte){
        return (topoDescarte.simbolo == this.simbolo || topoDescarte.cor == this.cor || this.cor == Cor.PRETO) &&
                (this.simbolo != null) && (this.cor != null);
    }

    public Simbolo getSimbolo() {
        return simbolo;
    }

    public Cor getCor() {
        return cor;
    }

    public String export() {
        return String.format("%s,%s",cor,simbolo);
    }

    @Override
    public String toString() {
        return String.format("\u001B[38;2;%d;%d;%dm %s \u001B[0m", cor.getR(), cor.getG(), cor.getB(), simbolo);
    }
    public void setCor(Cor cor) {
        this.cor = cor;
    }
}
