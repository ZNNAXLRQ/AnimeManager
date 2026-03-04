package com.example.animemanager.Util;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.OutputStream;
import java.io.PrintStream;

public class LogCollector {
    private static LogCollector instance;
    private final ObservableList<String> logLines = FXCollections.observableArrayList();

    private LogCollector() {
        // 重定向 System.out 和 System.err
        PrintStream standardOut = System.out;
        PrintStream standardErr = System.err;

        System.setOut(new PrintStream(new LogOutputStream(standardOut, false)));
        System.setErr(new PrintStream(new LogOutputStream(standardErr, true)));
    }

    public static LogCollector getInstance() {
        if (instance == null) {
            instance = new LogCollector();
        }
        return instance;
    }

    public ObservableList<String> getLogLines() {
        return logLines;
    }

    public void clear() {
        logLines.clear();
    }

    private class LogOutputStream extends OutputStream {
        private final PrintStream original;
        private final boolean isError;
        private StringBuilder buffer = new StringBuilder();

        public LogOutputStream(PrintStream original, boolean isError) {
            this.original = original;
            this.isError = isError;
        }

        @Override
        public void write(int b) {
            char c = (char) b;
            if (c == '\n') {
                String line = buffer.toString();
                buffer = new StringBuilder();
                // 输出到原始流
                original.println(line);
                // 收集到列表（JavaFX线程安全）
                Platform.runLater(() -> logLines.add((isError ? "[ERROR] " : "") + line));
            } else {
                buffer.append(c);
            }
        }
    }
}