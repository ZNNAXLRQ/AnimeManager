package com.example.animemanager.Util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;

public final class JsonConfigUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonConfigUtil() {
        throw new AssertionError("工具类，禁止实例化");
    }

    private static File getAppBaseDir() {
        // 1. 优先使用 jpackage 的系统属性
        String appPath = System.getProperty("app.path");
        if (appPath != null && !appPath.isEmpty()) {
            File exeFile = new File(appPath);
            if (exeFile.exists()) {
                return exeFile.getParentFile();
            }
        }

        // 2. 通过 CodeSource 解析 jar 文件的实际路径
        try {
            java.security.CodeSource codeSource = JsonConfigUtil.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                java.net.URL location = codeSource.getLocation();
                if (location != null) {
                    String path = null;
                    // 处理 jar 协议
                    if ("jar".equals(location.getProtocol())) {
                        // 获取完整 URL 字符串
                        String urlStr = location.toString();
                        // 找到 "file:" 部分的起始
                        int fileIdx = urlStr.indexOf("file:");
                        if (fileIdx >= 0) {
                            // 提取从 file: 开始到第一个 "!/" 之前的部分
                            int bangIdx = urlStr.indexOf("!/", fileIdx);
                            if (bangIdx > 0) {
                                path = urlStr.substring(fileIdx, bangIdx);
                            }
                        }
                    } else if ("file".equals(location.getProtocol())) {
                        path = location.getPath();
                    }

                    if (path != null) {
                        // 去掉 "file:" 前缀
                        if (path.startsWith("file:")) {
                            path = path.substring(5);
                        }
                        // URL 解码（处理中文、空格）
                        path = java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8.name());
                        File jarFile = new File(path);
                        File jarDir = jarFile.getParentFile();
                        if (jarDir != null) {
                            // 检查是否在 jpackage 的 app 子目录中
                            if ("app".equals(jarDir.getName())) {
                                File exeDir = jarDir.getParentFile();
                                if (exeDir != null && exeDir.exists()) {
                                    return exeDir;
                                }
                            }
                            return jarDir;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("解析 CodeSource 失败: " + e.getMessage());
        }

        // 3. 回退到用户工作目录
        return new File(System.getProperty("user.dir"));
    }

    public static Map<String, Object> readConfig(String fileName) {
        File baseDir = getAppBaseDir();
        File externalFile = new File(baseDir, fileName);
        System.out.println("[readConfig] 尝试读取: " + externalFile.getAbsolutePath());

        if (externalFile.exists() && externalFile.isFile()) {
            try {
                System.out.println("[readConfig] 成功读取外部文件");
                return MAPPER.readValue(externalFile, new TypeReference<>() {});
            } catch (IOException e) {
                System.err.println("[readConfig] 外部文件读取失败: " + e.getMessage());
            }
        } else {
            System.out.println("[readConfig] 外部文件不存在，使用内部资源");
        }

        // 回退到类路径资源（打包在 jar 内的文件）
        try (InputStream inputStream = JsonConfigUtil.class.getClassLoader()
                .getResourceAsStream(fileName)) {
            if (inputStream == null) {
                throw new IOException("找不到配置文件: " + fileName);
            }
            return MAPPER.readValue(inputStream, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException("读取配置文件失败: " + fileName, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Double> readAnimeWeights(String fileName) {
        System.out.println("权重设置读取中");
        Map<String, Object> config = readConfig(fileName);
        Object weights = config.get("anime_weights");
        if (weights == null) {
            throw new RuntimeException("配置文件不包含weights字段: " + fileName);
        }
        return Collections.unmodifiableMap((Map<String, Double>) weights);
    }

    @SuppressWarnings("unckecked")
    public static String readUser(String fileName) {
        System.out.println("用户设置读取中");
        Map<String, Object> config = readConfig(fileName);
        String account = (String) config.get("username");
        if (account == null) {
            throw new RuntimeException("未配置账号: " + fileName);
        }
        return account;
    }

    @SuppressWarnings("unchecked")
    public static String readToken(String fileName) {
        System.out.println("令牌设置读取中");
        Map<String, Object> config = readConfig(fileName);
        String token = (String) config.get("token");
        if (token == null) {
            throw new RuntimeException("未配置个人令牌: " + fileName);
        }
        return token;
    }

    @SuppressWarnings("unchecked")
    public static String readProxy(String fileName) {
        System.out.println("代理设置读取中");
        Map<String, Object> config = readConfig(fileName);
        Object proxy = config.get("proxy");
        return proxy != null ? proxy.toString() : null;
    }
}