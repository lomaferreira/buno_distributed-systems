package buno.model;

public enum Cor {
    VERDE(0, 255, 0),
    AMARELO(255, 255, 0 ),
    AZUL(0, 0, 255),
    VERMELHO(255, 0, 0),
    PRETO(0,0,0);

    private final int r;
    private final int g;
    private final int b;

    Cor(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    //converter string em enum e compara
    public static Cor fromString(String texto) {
        for (Cor cor : Cor.values()) {
            if (cor.name().equalsIgnoreCase(texto)) {
                return cor;
            }
        }
        return null;
    }

    public int getR() {
        return r;
    }

    public int getG() {
        return g;
    }

    public int getB() {
        return b;
    }
}
