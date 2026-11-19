package dev.applyops.orchestrator.discovery.parser;

import static org.assertj.core.api.Assertions.assertThat;

import dev.applyops.orchestrator.discovery.model.AlertEmail;
import dev.applyops.orchestrator.testing.Fixtures;
import dev.applyops.orchestrator.discovery.model.DiscoveredJob;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobrightAlertParserTest {

    private final JobrightAlertParser parser = new JobrightAlertParser(List.of("jobright.ai"));

    @Test
    void parsesJobsAndSkipsNonJobLinks() throws Exception {
        var email = new AlertEmail("m9", "noreply@jobright.ai", "matches", 0, Fixtures.email("jobright-alert.synthetic.html"));

        List<DiscoveredJob> jobs = parser.parse(email);

        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0)).isEqualTo(new DiscoveredJob(
                "jobright", "66f1a2b3c4d5e6f7a8b9c0d1", "Senior Backend Engineer", "Acme Corp",
                "San Francisco, CA", "https://jobright.ai/jobs/info/66f1a2b3c4d5e6f7a8b9c0d1", "m9"));
        assertThat(jobs.get(1).company()).isEqualTo("Initech");
        assertThat(jobs.get(1).location()).isEqualTo("Austin, TX");
    }

    @Test
    void matchesSenderDomainAndSubdomains() {
        assertThat(parser.supports("noreply@jobright.ai")).isTrue();
        assertThat(parser.supports("hello@mail.jobright.ai")).isTrue();
        assertThat(parser.supports("noreply@notjobright.ai")).isFalse();
    }
}
