package dev.applyops.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Orchestrator process: hosts Temporal workflows on {@link TaskQueues#ORCHESTRATOR}.
 * Workers are auto-discovered by temporal-spring-boot-starter from {@code @WorkflowImpl}.
 */
@SpringBootApplication
public class OrchestratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
