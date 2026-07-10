package buno.model;

public enum Simbolo {
    ZERO,
    UM,
    DOIS,
    TRES,
    QUATRO,
    CINCO,
    SEIS,
    SETE,
    OITO,
    NOVE,
    INVERTER,
    BLOQUEAR,
    MAISDOIS,
    MUDARCOR,
    MAISQUATRO;

    public static Simbolo fromString(String texto) {
        for (Simbolo simbolo : Simbolo.values()) {
            if (simbolo.name().equalsIgnoreCase(texto)) {
                return simbolo;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return this.name();
    }
}
