package buno.view;

import javafx.application.Platform;
import javafx.event.ActionEvent;

public class MainController {

    public void criarSala(ActionEvent event) {

        System.out.println("Criar sala");

    }

    public void entrarSala(ActionEvent event) {

        System.out.println("Entrar sala");

    }

    public void sair(ActionEvent event) {

        Platform.exit();

    }
}