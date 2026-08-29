package com.devanshbhosale.marginwatch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MarginWatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarginWatchApplication.class, args);
    }

    /**
     * Dedicated single-thread executor for background price-file parsing.
     * Spring infers shutdown() as the destroy method; tests set
     * app.files.async=false and never touch it.
     */
    @Bean
    public ExecutorService parseExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "price-file-parse");
            thread.setDaemon(true);
            return thread;
        });
    }
}
