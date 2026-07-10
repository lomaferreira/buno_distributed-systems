package buno.view;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader =
                new FXMLLoader(
                        getClass().getResource("/buno/view/Tela.fxml")
                );

        Scene scene = new Scene(loader.load());
        stage.setTitle("Buno - Jogo de Cartas Multiplayer");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
