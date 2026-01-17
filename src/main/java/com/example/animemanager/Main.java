package com.example.animemanager;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/example/animemanager/FXML/main.fxml"));
        // 适当调大窗口尺寸以容纳图表
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
        stage.setTitle("Anime Scorer - JavaFX");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}