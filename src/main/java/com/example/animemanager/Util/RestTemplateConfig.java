package com.example.animemanager.Util;

import com.example.animemanager.Util.JsonConfigUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(60000);

        String proxyConfig = JsonConfigUtil.readProxy("Data/config.json");
        if (proxyConfig != null && !proxyConfig.trim().isEmpty()) {
            try {
                URL proxyUrl = new URL(proxyConfig);
                String host = proxyUrl.getHost();
                int port = proxyUrl.getPort();
                if (port == -1) {
                    port = 7890;
                    System.out.println("[Proxy]      代理未指定端口，使用默认端口 7890");
                }
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
                factory.setProxy(proxy);
                System.out.println("[Proxy]      RestTemplate 代理已配置：" + host + ":" + port);
            } catch (Exception e) {
                System.err.println("[Proxy]      代理配置解析失败，将使用直连模式：" + e.getMessage());
            }
        } else {
            System.out.println("[Proxy]      未配置代理，使用直连模式");
        }

        return new RestTemplate(factory);
    }
}