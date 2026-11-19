package dev.applyops.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.applyops.orchestrator.discovery.DiscoveryActivities;
import dev.applyops.orchestrator.discovery.parser.AlertParserRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Boots the full Spring context (real Postgres + in-process Temporal test server) to catch wiring errors. */
@SpringBootTest(properties = "spring.temporal.test-server.enabled=true")
@Testcontainers
class OrchestratorApplicationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired AlertParserRegistry parsers;
    @Autowired DiscoveryActivities discoveryActivities;

    @Test
    void contextLoadsWithConfiguredParsers() {
        assertThat(parsers.gmailFromQuery()).contains("jobalerts-noreply@linkedin.com").contains("jobright.ai");
        assertThat(discoveryActivities).isNotNull();
    }
}
