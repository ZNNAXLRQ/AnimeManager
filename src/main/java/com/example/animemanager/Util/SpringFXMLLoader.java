package com.example.animemanager.Util;

import com.example.animemanager.Main;
import javafx.fxml.FXMLLoader;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

public class SpringFXMLLoader {
    private ConfigurableApplicationContext context;

    public SpringFXMLLoader() {
        this.context = Main.getContext();
    }

    public <T> T load(String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        loader.setControllerFactory(context::getBean);
        return loader.load();
    }
}
