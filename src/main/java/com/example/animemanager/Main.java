package com.example.animemanager;

import com.example.animemanager.Util.LogCollector;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.Getter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.io.InputStream;

@SpringBootApplication
@EnableScheduling // 开启定时任务
public class Main extends Application {

    @Getter
    private static ConfigurableApplicationContext context;

    @Override
    public void init() throws Exception {
        LogCollector.getInstance();
        SpringApplication application = new SpringApplication(Main.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        context = application.run();
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/example/animemanager/FXML/main.fxml"));
        // 关键：让 Spring 接管 Controller 的创建
        fxmlLoader.setControllerFactory(context::getBean);

        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
        InputStream iconStream = getClass().getResourceAsStream("/Data/icon.png");
        if (iconStream != null) {
            stage.getIcons().add(new Image(iconStream));
        } else {
            System.err.println("图标文件未找到，使用默认图标");
        }
        stage.setTitle("あにめManager");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        context.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

