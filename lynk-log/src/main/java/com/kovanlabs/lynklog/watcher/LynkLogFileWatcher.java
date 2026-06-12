package com.kovanlabs.lynklog.watcher;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import com.kovanlabs.lynklog.config.LynkLogProperties;
import com.kovanlabs.lynklog.queue.LynkLogQueue;

public class LynkLogFileWatcher implements DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(LynkLogFileWatcher.class);

    private final LynkLogProperties properties;
    private final LynkLogQueue lynkLogQueue;
    private final ExecutorService executorService;
    private volatile boolean running = false;

    public LynkLogFileWatcher(LynkLogProperties properties, LynkLogQueue lynkLogQueue) {
        this.properties = properties;
        this.lynkLogQueue = lynkLogQueue;
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "lynk-log-watcher");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void start() {
        if (running) {
            return;
        }

        String path = properties.getLogFilePath();
        if (path == null || path.isBlank()) {
            LOGGER.warn("[LYNK] No log file path configured. File watcher will not start.");
            return;
        }

        running = true;
        executorService.submit(() -> runWatcher(path));
    }

    private void runWatcher(String filePath) {
        File file = new File(filePath);
        long lastKnownPosition = 0;

        // Try to locate and open the file on startup, ignoring existing log entries
        try {
            if (file.exists()) {
                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    lastKnownPosition = raf.length();
                    LOGGER.info("[LYNK] Started watching file: {}, initial size: {} bytes", filePath, lastKnownPosition);
                }
            } else {
                LOGGER.warn("[LYNK] Log file does not exist on startup: {}. Waiting for file creation.", filePath);
            }
        } catch (Exception e) {
            LOGGER.error("[LYNK] Error initializing file pointer: {}", e.getMessage());
        }

        while (running) {
            try {
                if (!file.exists()) {
                    Thread.sleep(500);
                    continue;
                }

                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    long length = raf.length();
                    if (length < lastKnownPosition) {
                        // File was truncated/rotated
                        LOGGER.info("[LYNK] Log file rotated or truncated. Resetting position to start.");
                        lastKnownPosition = 0;
                    }

                    if (length > lastKnownPosition) {
                        raf.seek(lastKnownPosition);
                        String line;
                        while ((line = raf.readLine()) != null) {
                            // Decode ISO-8859-1 readLine back to UTF-8
                            String decodedLine = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                            lynkLogQueue.offer(decodedLine);
                        }
                        lastKnownPosition = raf.getFilePointer();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("[LYNK] Error during file watching: {}", e.getMessage());
            }

            try {
                Thread.sleep(200); // Poll every 200ms for responsiveness in tailing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public synchronized void stop() {
        running = false;
        executorService.shutdownNow();
    }

    @Override
    public void destroy() {
        stop();
    }

    // Package-private helper to check if running (for tests)
    boolean isRunning() {
        return running;
    }
}
