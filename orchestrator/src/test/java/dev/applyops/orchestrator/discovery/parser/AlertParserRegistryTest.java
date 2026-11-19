package dev.applyops.orchestrator.discovery.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AlertParserRegistryTest {

    private final AlertParserRegistry registry = new AlertParserRegistry(List.of(
            new LinkedInAlertParser(List.of("jobalerts-noreply@linkedin.com", "jobs-noreply@linkedin.com")),
            new JobrightAlertParser(List.of("jobright.ai"))));

    @Test
    void buildsGmailQueryFromAllSenders() {
        assertThat(registry.gmailFromQuery())
                .isEqualTo("from:(jobalerts-noreply@linkedin.com OR jobs-noreply@linkedin.com OR jobright.ai)");
    }

    @Test
    void routesBySender() {
        assertThat(registry.forSender("noreply@jobright.ai")).get().extracting(AlertParser::provider).isEqualTo("jobright");
        assertThat(registry.forSender("jobs-noreply@linkedin.com")).get().extracting(AlertParser::provider).isEqualTo("linkedin");
        assertThat(registry.forSender("friend@example.com")).isEmpty();
    }
}
