package com.example.animemanager.Util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

public final class JsonConfigUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonConfigUtil() {
        throw new AssertionError("工具类，禁止实例化");
    }

    public static Map<String, Object> readConfig(String fileName) {
        try {
            InputStream inputStream = JsonConfigUtil.class.getClassLoader()
                    .getResourceAsStream(fileName);

            if (inputStream == null) {
                throw new IOException("找不到配置文件: " + fileName);
            }

            Map<String, Object> data = MAPPER.readValue(
                    inputStream,
                    new TypeReference<Map<String, Object>>() {}
            );
            return Collections.unmodifiableMap(data);
        } catch (IOException e) {
            throw new RuntimeException("读取配置文件失败: " + fileName, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Double> readAnimeWeights(String fileName) {
        Map<String, Object> config = readConfig(fileName);
        Object weights = config.get("anime_weights");
        if (weights == null) {
            throw new RuntimeException("配置文件不包含weights字段: " + fileName);
        }
        return Collections.unmodifiableMap((Map<String, Double>) weights);
    }

    @SuppressWarnings("unckecked")
    public static String readUser(String fileName) {
        Map<String, Object> config = readConfig(fileName);
        String account = (String) config.get("username");
        if (account == null) {
            throw new RuntimeException("未配置账号: " + fileName);
        }
        return account;
    }

    @SuppressWarnings("unchecked")
    public static String readToken(String fileName) {
        Map<String, Object> config = readConfig(fileName);
        String token = (String) config.get("token");
        if (token == null) {
            throw new RuntimeException("为配置个人令牌: " + fileName);
        }
        return token;
    }
}