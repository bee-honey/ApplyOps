package dev.applyops.orchestrator.discovery.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.applyops.orchestrator.discovery.model.AlertEmail;
import dev.applyops.orchestrator.discovery.model.DiscoveredJob;
import dev.applyops.orchestrator.testing.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pinned to a real (scrubbed) Jobright alert: company precedes the title; salary is optional. */
class JobrightAlertParserTest {

    private final JobrightAlertParser parser = new JobrightAlertParser(List.of("jobright.ai"));

    @Test
    void parsesEveryCardAndSkipsSalaryWhenFindingLocation() throws Exception {
        var email = new AlertEmail("m9", "noreply@jobright.ai", "matches", 0, Fixtures.email("jobright-alert.html"));

        List<DiscoveredJob> jobs = parser.parse(email);

        assertThat(jobs)
                .extracting(DiscoveredJob::title, DiscoveredJob::company, DiscoveredJob::location)
                .containsExactly(
                        tuple("Senior Software Engineer, Backend - Infrastructure", "Rippling", "San Francisco, CA"),
                        tuple("Senior Software Developer", "AHEAD", "Remote"),
                        tuple("Software Engineer, Core AI/ML", "FloQast", "San Jose, CA"),
                        tuple("Sr. Engineer, Cloud - CTIO (Hybrid)", "CrowdStrike", "Sunnyvale, CA"),
                        tuple("Senior Software Engineer, Edge", "Metropolis Technologies", "Los Angeles, CA"),
                        tuple("Software Engineer II, Backend - Platform Team", "Rippling", "San Francisco, CA"));
        assertThat(jobs.get(0)).isEqualTo(new DiscoveredJob(
                "jobright", "6aff3279c1c02a9acd809c67", "Senior Software Engineer, Backend - Infrastructure",
                "Rippling", "San Francisco, CA", "https://jobright.ai/jobs/info/6aff3279c1c02a9acd809c67", "m9"));
    }

    @Test
    void matchesSenderDomainAndSubdomains() {
        assertThat(parser.supports("noreply@jobright.ai")).isTrue();
        assertThat(parser.supports("hello@mail.jobright.ai")).isTrue();
        assertThat(parser.supports("noreply@notjobright.ai")).isFalse();
    }
}
