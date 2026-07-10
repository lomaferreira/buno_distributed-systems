package buno.controller;

import buno.model.Carta;
import buno.model.Cor;
import buno.model.Jogador;
import buno.model.Partida;
import buno.view.App;

public class GameController {
    private Jogador jogador;

    public GameController(Jogador jogador){
        this.jogador = jogador;
    }

    public boolean jogarCarta(Jogador jogador, Carta carta){
        Partida partida = jogador.getSalaAtual().getPartida();
        Carta topoPilha = partida.getCartaTopo();

        if (!carta.podeSerJogada(topoPilha)){
            return false;
        }

        partida.adicionarPilha(carta);
        jogador.removerCarta(carta);
        aplicarEfeitoCarta(partida, carta);

        if (jogador.getMao().isEmpty()) {
            partida.declararVencedor(jogador);
        } else {
            partida.passarTurno();
        }

        return true;
    }

    public void aplicarEfeitoCarta(Partida partida, Carta carta) {
        switch (carta.getSimbolo()) {
            case INVERTER:
                partida.inverterDirecao();
                break;
            case BLOQUEAR:
                partida.proximoJogador();
                break;
            case MAISDOIS:{
                Jogador alvo = partida.espiarProximoJogador();
                partida.setJogadorObrigadoPath(alvo.getPath());
                partida.setCartasParaComprar(partida.getCartasParaComprar() + 2);
                break;}
            case MUDARCOR:{
                Cor novaCor = App.escolherCor();
                carta.setCor(novaCor);
                break;}
            case MAISQUATRO:{
                Cor novaCor = App.escolherCor();
                carta.setCor(novaCor);
                Jogador alvo = partida.espiarProximoJogador();
                partida.setJogadorObrigadoPath(alvo.getPath());
                partida.setCartasParaComprar(partida.getCartasParaComprar() + 4);
                break;}
        }
    }
}
