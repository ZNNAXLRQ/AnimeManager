package com.example.animemanager;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

@SpringBootApplication
public class Main extends Application {

    private static ConfigurableApplicationContext springContext;
    private FXMLLoader fxmlLoader;
    private Parent root;

    @Override
    public void init() throws Exception {
        springContext = new SpringApplicationBuilder(Main.class)
                .web(WebApplicationType.NONE)
                .run();
        fxmlLoader = new FXMLLoader();
        fxmlLoader.setControllerFactory(springContext::getBean);
        fxmlLoader.setLocation(getClass().getResource("/com/example/animemanager/FXML/main.fxml"));
        root = fxmlLoader.load();
    }

    @Override
    public void start(Stage stage) throws IOException {
        Scene scene = new Scene(root);
        stage.setTitle("Anime Scorer - JavaFX");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    @Override
    public void stop() {
        if (springContext != null && springContext.isActive()) {
            springContext.close();
        }
        Platform.exit();
        System.exit(0);
    }

    public static ConfigurableApplicationContext getSpringContext() {
        return springContext;
    }

    public static void main(String[] args) {
        launch();
    }
}