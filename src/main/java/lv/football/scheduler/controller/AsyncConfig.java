package lv.football.scheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService solverExecutor() {
        // Small pool is enough; increase if you want to allow parallel solves.
        return Executors.newFixedThreadPool(2);
    }
}