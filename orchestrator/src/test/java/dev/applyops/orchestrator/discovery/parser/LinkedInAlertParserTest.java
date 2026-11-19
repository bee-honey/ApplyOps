package dev.applyops.orchestrator.discovery.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.applyops.orchestrator.discovery.model.AlertEmail;
import dev.applyops.orchestrator.discovery.model.DiscoveredJob;
import dev.applyops.orchestrator.testing.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pinned to a real (scrubbed) LinkedIn job alert: cards are wrapped in an outer link. */
class LinkedInAlertParserTest {

    private final LinkedInAlertParser parser = new LinkedInAlertParser(List.of("jobalerts-noreply@linkedin.com"));

    private List<DiscoveredJob> parseFixture() throws Exception {
        return parser.parse(new AlertEmail(
                "m1", "jobalerts-noreply@linkedin.com", "alert", 0, Fixtures.email("linkedin-alert.html")));
    }

    @Test
    void parsesEveryCardWithTitleCompanyAndLocation() throws Exception {
        assertThat(parseFixture())
                .extracting(DiscoveredJob::title, DiscoveredJob::company, DiscoveredJob::location)
                .containsExactly(
                        tuple("Senior Software Engineer", "EliseAI", "San Francisco, CA (On-site)"),
                        tuple("Senior Software Engineer", "Fonzi AI", "San Francisco Bay Area (On-site)"),
                        tuple("Backend Engineer", "Fonzi AI", "San Francisco Bay Area (On-site)"),
                        tuple("Sr Staff Engineer Software — SaaS Security Posture Management (SSPM)",
                                "Palo Alto Networks", "Santa Clara, CA (On-site)"),
                        tuple("Software Engineer - AI Agent Infra", "ByteDance", "San Jose, CA (On-site)"),
                        tuple("Staff Software Engineer, IAM Control Plane", "Stripe", "San Francisco, CA (On-site)"));
    }

    @Test
    void usesCanonicalUrlWithoutTracking() throws Exception {
        DiscoveredJob first = parseFixture().get(0);

        assertThat(first.externalId()).isEqualTo("4491785951");
        assertThat(first.url()).isEqualTo("https://www.linkedin.com/jobs/view/4491785951/");
        assertThat(first.provider()).isEqualTo("linkedin");
        assertThat(first.emailMessageId()).isEqualTo("m1");
    }

    @Test
    void outerCardLinkIsNotMistakenForTheTitle() {
        String html = """
                <a href="https://www.linkedin.com/comm/jobs/view/111/?t=1">
                  <table><tr><td><a href="https://www.linkedin.com/comm/jobs/view/111/?t=2">Data Engineer</a></td></tr>
                  <tr><td><p>Initech · Austin, TX</p></td></tr></table>
                </a>
                <div><a href="https://www.linkedin.com/comm/jobs/view/222/">Platform Engineer</a><p>Globex · Remote</p></div>""";

        var jobs = parser.parse(new AlertEmail("m3", "x", "s", 0, html));

        assertThat(jobs).extracting(DiscoveredJob::externalId, DiscoveredJob::title, DiscoveredJob::company)
                .containsExactly(tuple("111", "Data Engineer", "Initech"), tuple("222", "Platform Engineer", "Globex"));
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
