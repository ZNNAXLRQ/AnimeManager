package com.example.animemanager;

import com.example.animemanager.Service.DataImportService;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.Getter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling; // 导入此包

import java.io.IOException;

@SpringBootApplication
@EnableScheduling // 1. 开启定时任务调度
public class Main extends Application {
    // 提供静态方法获取 Spring 上下文
    @Getter
    private static ConfigurableApplicationContext context;

    @Override
    public void init() throws Exception {
        // 创建 SpringApplication 实例，并设置为非 Web 应用
        SpringApplication application = new SpringApplication(Main.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        context = application.run();
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/example/animemanager/FXML/main.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle("Anime Scorer - JavaFX");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        // 关闭 Spring 上下文
        context.close();
    }

    public static void main(String[] args) {
        launch();
    }

    public static void startCollect() {
        Task<Void> importTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                ConfigurableApplicationContext context = getContext();
                DataImportService dataImportService = context.getBean(DataImportService.class);
                dataImportService.DataImport();
                return null;
            }
        };
        importTask.setOnSucceeded(event -> {
            System.out.println("数据导入完成");
        });
        importTask.setOnFailed(event -> {
            System.out.println("数据导入失败: " + importTask.getException().getMessage());
            importTask.getException().printStackTrace();
        });
        Thread importThread = new Thread(importTask);
        importThread.setDaemon(true);
        importThread.start();
    }
}
