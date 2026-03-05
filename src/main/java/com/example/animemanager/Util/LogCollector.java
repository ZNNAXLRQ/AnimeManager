package com.example.animemanager.Util;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class LogCollector {
    private static LogCollector instance;
    private final ObservableList<String> logLines = FXCollections.observableArrayList();
    private final BlockingQueue<String> pendingLogs = new LinkedBlockingQueue<>();
    private volatile boolean flushScheduled = false;
    private final String charset;
    private static final int MAX_LOG_LINES = 1000;

    private LogCollector() {
        charset = System.getProperty("sun.stdout.encoding", System.getProperty("file.encoding", "UTF-8"));
        PrintStream standardOut = System.out;
        PrintStream standardErr = System.err;
        System.setOut(new PrintStream(new LogOutputStream(standardOut, false, charset)));
        System.setErr(new PrintStream(new LogOutputStream(standardErr, true, charset)));
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
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final String charsetName;

        public LogOutputStream(PrintStream original, boolean isError, String charsetName) {
            this.original = original;
            this.isError = isError;
            this.charsetName = charsetName;
        }

        @Override
        public void write(int b) {
            if (b == '\n') {
                try {
                    String line = buffer.toString(charsetName);
                    buffer.reset();
                    original.println(line);
                    String taggedLine = (isError ? "[ERROR] " : "") + line;
                    pendingLogs.offer(taggedLine);
                    scheduleFlush();
                } catch (UnsupportedEncodingException e) {
                    // fallback
                }
            } else {
                buffer.write(b);
            }
        }
    }

    private void scheduleFlush() {
        if (!flushScheduled) {
            flushScheduled = true;
            Platform.runLater(this::flushLogs);
        }
    }

    private void flushLogs() {
        List<String> batch = new ArrayList<>();
        pendingLogs.drainTo(batch);
        if (!batch.isEmpty()) {
            logLines.addAll(batch);
            // 限制列表大小，避免内存无限增长
            if (logLines.size() > MAX_LOG_LINES) {
                logLines.remove(0, logLines.size() - MAX_LOG_LINES);
            }
        }
        flushScheduled = false;
        if (!pendingLogs.isEmpty()) {
            scheduleFlush(); // 如果还有积压，再次调度
        }
    }
}