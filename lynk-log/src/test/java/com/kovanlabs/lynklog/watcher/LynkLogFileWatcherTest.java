package com.kovanlabs.lynklog.watcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kovanlabs.lynklog.config.LynkLogProperties;
import com.kovanlabs.lynklog.queue.LynkLogQueue;

@ExtendWith(MockitoExtension.class)
class LynkLogFileWatcherTest {

    private LynkLogProperties properties;
    private LynkLogFileWatcher fileWatcher;

    @Mock
    private LynkLogQueue logQueue;

    @BeforeEach
    void setUp() {
        properties = new LynkLogProperties();
        fileWatcher = new LynkLogFileWatcher(properties, logQueue);
    }

    @AfterEach
    void tearDown() {
        if (fileWatcher != null) {
            fileWatcher.stop();
        }
    }

    @Test
    void watcher_doesNotStartIfFilePathNullOrBlank() {
        properties.setLogFilePath(null);
        fileWatcher.start();
        assertThat(fileWatcher.isRunning()).isFalse();

        properties.setLogFilePath("   ");
        fileWatcher.start();
        assertThat(fileWatcher.isRunning()).isFalse();
    }

    @Test
    void watcher_ignoresExistingLogsAndDetectsNewLogs(@TempDir Path tempDir) throws Exception {
        File logFile = tempDir.resolve("application.log").toFile();
        
        // Write existing logs before startup
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile))) {
            writer.println("existing log line 1");
            writer.println("existing log line 2");
        }

        properties.setLogFilePath(logFile.getAbsolutePath());
        
        // Start watcher
        fileWatcher.start();
        assertThat(fileWatcher.isRunning()).isTrue();

        // Give it some time to register initial size
        Thread.sleep(300);

        // Verify no logs were offered to the queue (existing logs should be ignored)
        verifyNoInteractions(logQueue);

        // Write new log lines after startup
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println("new log line 1");
            writer.flush();
        }

        // Verify new log is detected and offered
        verify(logQueue, timeout(1000).times(1)).offer("new log line 1");

        // Write another new log line
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println("new log line 2");
            writer.flush();
        }

        verify(logQueue, timeout(1000).times(1)).offer("new log line 2");
    }

    @Test
    void watcher_handlesTruncationOrRotation(@TempDir Path tempDir) throws Exception {
        File logFile = tempDir.resolve("application.log").toFile();
        
        properties.setLogFilePath(logFile.getAbsolutePath());
        
        // Write some lines and start
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile))) {
            writer.println("existing line");
        }

        fileWatcher.start();
        Thread.sleep(300);

        // Truncate the file (resetting it to 0 bytes)
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, false))) {
            writer.flush();
        }
        
        // Let the watcher poll and see the length decreased
        Thread.sleep(300);

        // Now write the new line after truncation
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println("rotated line 1");
            writer.flush();
        }

        // Verify that it detected the truncation and read from the beginning
        verify(logQueue, timeout(1000).times(1)).offer("rotated line 1");
    }

    @Test
    void watcher_handlesMissingFileInitiallyAndReadsWhenCreated(@TempDir Path tempDir) throws Exception {
        File logFile = tempDir.resolve("nonexistent.log").toFile();
        properties.setLogFilePath(logFile.getAbsolutePath());

        // Start watcher when file does not exist
        fileWatcher.start();
        assertThat(fileWatcher.isRunning()).isTrue();

        Thread.sleep(300);
        verifyNoInteractions(logQueue);

        // Create the file and write a line
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile))) {
            writer.println("first line in new file");
            writer.flush();
        }

        // Verify it was detected and offered
        verify(logQueue, timeout(1500).times(1)).offer("first line in new file");
    }

    @Test
    void watcher_neverCrashesOnInvalidPath() {
        // Use an invalid directory path as a file path
        properties.setLogFilePath("/nonexistent_directory/invalid_file.log");
        
        // Starting should not throw any exception
        fileWatcher.start();
        assertThat(fileWatcher.isRunning()).isTrue();

        // Let it run for a bit
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Watcher is still running despite the file not being found
        assertThat(fileWatcher.isRunning()).isTrue();
        fileWatcher.stop();
        verifyNoInteractions(logQueue);
    }
}
