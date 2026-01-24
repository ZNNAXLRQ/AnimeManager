package com.example.animemanager.Config;

import com.example.animemanager.Util.SpringFXMLLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JavaFXConfig {

    @Bean
    public SpringFXMLLoader fxmlLoader() {
        return new SpringFXMLLoader();
    }
}
