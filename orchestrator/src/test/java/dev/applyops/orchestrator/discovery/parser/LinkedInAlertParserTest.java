package dev.applyops.orchestrator.discovery.parser;

import static org.assertj.core.api.Assertions.assertThat;

import dev.applyops.orchestrator.discovery.model.AlertEmail;
import dev.applyops.orchestrator.testing.Fixtures;
import dev.applyops.orchestrator.discovery.model.DiscoveredJob;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkedInAlertParserTest {

    private final LinkedInAlertParser parser = new LinkedInAlertParser(List.of("jobalerts-noreply@linkedin.com"));

    @Test
    void parsesEachJobCardOnce() throws Exception {
        var email = new AlertEmail("m1", "jobalerts-noreply@linkedin.com", "alert", 0, Fixtures.email("linkedin-alert.synthetic.html"));

        List<DiscoveredJob> jobs = parser.parse(email);

        assertThat(jobs).extracting(DiscoveredJob::externalId).containsExactly("4012345678", "4098765432");
        DiscoveredJob first = jobs.get(0);
        assertThat(first.title()).isEqualTo("Senior Backend Engineer");
        assertThat(first.company()).isEqualTo("Acme Corp");
        assertThat(first.location()).isEqualTo("San Francisco, CA (Hybrid)");
        assertThat(first.url()).isEqualTo("https://www.linkedin.com/jobs/view/4012345678/");
        assertThat(first.provider()).isEqualTo("linkedin");
        assertThat(first.emailMessageId()).isEqualTo("m1");
    }

    @Test
    void titleSpanningNestedTagsAndCallToActionLinks() throws Exception {
        var email = new AlertEmail("m1", "x", "s", 0, Fixtures.email("linkedin-alert.synthetic.html"));

        DiscoveredJob second = parser.parse(email).get(1);

        assertThat(second.title()).isEqualTo("Staff Software Engineer, Platform");
        assertThat(second.company()).isEqualTo("Globex, Inc.");
        assertThat(second.location()).isEqualTo("Remote");
    }

    @Test
    void ignoresEmailsWithoutJobLinks() {
        var email = new AlertEmail("m2", "x", "s", 0, "<p>Hello <a href='https://www.linkedin.com/feed'>feed</a></p>");
        assertThat(parser.parse(email)).isEmpty();
    }

    @Test
    void matchesConfiguredSendersOnly() {
        assertThat(parser.supports("jobalerts-noreply@linkedin.com")).isTrue();
        assertThat(parser.supports("JOBALERTS-NOREPLY@LINKEDIN.COM")).isTrue();
        assertThat(parser.supports("messages-noreply@linkedin.com")).isFalse();
        assertThat(parser.supports(null)).isFalse();
    }
}
