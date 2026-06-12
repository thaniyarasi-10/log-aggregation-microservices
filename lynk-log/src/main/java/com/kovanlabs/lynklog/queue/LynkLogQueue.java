package com.kovanlabs.lynklog.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class LynkLogQueue {
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(10000);

    public boolean offer(String log) {
        return queue.offer(log);
    }

    public String take() throws InterruptedException {
        return queue.take();
    }
}
