package net.arksea.pusher.tools;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class TestToolMain extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        Parent root = FXMLLoader.load(getClass().getResource("/main.fxml"));
        primaryStage.setMinWidth(615);
        primaryStage.setMinHeight(440);
        primaryStage.setTitle("Push Test Tool");
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.show();
    }


    @Override
    public void init() throws Exception {
        // Do some heavy lifting
    }

    public static void main(String[] args) {
        launch(args);
    }
}
