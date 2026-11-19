package dev.applyops.orchestrator.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.applyops.orchestrator.discovery.model.DiscoveredJob;
import dev.applyops.orchestrator.discovery.model.PendingApplication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the real Flyway migrations against Postgres; dedup lives in SQL constraints, so test it there. */
@JdbcTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JobRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JobRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired JobRepository repo;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE job, job_source, application, gmail_checkpoint CASCADE");
    }

    static DiscoveredJob linkedin(String id, String company, String title) {
        return new DiscoveredJob("linkedin", id, title, company, "Remote", "https://www.linkedin.com/jobs/view/" + id + "/", "m1");
    }

    static DiscoveredJob jobright(String id, String company, String title) {
        return new DiscoveredJob("jobright", id, title, company, "SF", "https://jobright.ai/jobs/info/" + id, "m2");
    }

    int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    void sameEmailTwiceIsIdempotent() {
        UUID a = repo.upsert(linkedin("1", "Acme", "Senior Engineer"), Optional.empty());
        UUID b = repo.upsert(linkedin("1", "Acme", "Senior Engineer"), Optional.empty());

        assertThat(a).isEqualTo(b);
        assertThat(count("job")).isEqualTo(1);
        assertThat(count("job_source")).isEqualTo(1);
        assertThat(count("application")).isEqualTo(1);
    }

    @Test
    void sameJobFromTwoProvidersIsOneJobWithTwoSources() {
        UUID a = repo.upsert(linkedin("1", "Acme Corp", "Sr. Backend Engineer"), Optional.empty());
        UUID b = repo.upsert(jobright("x9", "ACME", "Senior Backend Engineer"), Optional.empty());

        assertThat(a).isEqualTo(b);
        assertThat(count("job")).isEqualTo(1);
        assertThat(count("job_source")).isEqualTo(2);
        assertThat(count("application")).isEqualTo(1);
    }

    @Test
    void atsKeyMatchesEvenWhenTitlesDiffer() {
        var ats = AtsUrl.parse("https://boards.greenhouse.io/acme/jobs/42");
        UUID a = repo.upsert(jobright("x1", "Acme", "Backend Engineer, Payments"), ats);
        UUID b = repo.upsert(jobright("x2", "Acme", "Payments Backend Engineer"), ats);

        assertThat(a).isEqualTo(b);
        assertThat(jdbc.queryForObject("SELECT ats_key FROM job", String.class)).isEqualTo("greenhouse:acme:42");
    }

    @Test
    void laterSightingAttachesAtsToExistingJob() {
        UUID a = repo.upsert(linkedin("1", "Acme", "Engineer"), Optional.empty());
        repo.upsert(jobright("x1", "Acme", "Engineer"), AtsUrl.parse("https://jobs.lever.co/acme/0b5e7c1a-1234-4abc-9def-0123456789ab"));

        assertThat(jdbc.queryForObject("SELECT ats_type FROM job WHERE id = ?", String.class, a)).isEqualTo("lever");
    }

    @Test
    void differentJobsStaySeparate() {
        repo.upsert(linkedin("1", "Acme", "Senior Engineer"), Optional.empty());
        repo.upsert(linkedin("2", "Acme", "Staff Engineer"), Optional.empty());
        assertThat(count("job")).isEqualTo(2);
    }

    @Test
    void pendingUntilMarkedStarted() {
        UUID jobId = repo.upsert(linkedin("1", "Acme", "Engineer"), Optional.empty());
        repo.upsert(linkedin("2", "Globex", "Engineer"), Optional.empty());

        List<PendingApplication> pending = repo.pendingWorkflowStarts(10);
        assertThat(pending).hasSize(2);
        assertThat(pending.get(0).workflowId()).isEqualTo("app-" + jobId);

        repo.markWorkflowsStarted(List.of(pending.get(0).applicationId()));
        assertThat(repo.pendingWorkflowStarts(10)).extracting(PendingApplication::applicationId)
                .containsExactly(pending.get(1).applicationId());

        // Rediscovering a started job does not make it pending again.
        repo.upsert(jobright("x", "Acme", "Engineer"), Optional.empty());
        assertThat(repo.pendingWorkflowStarts(10)).hasSize(1);
    }

    @Test
    void checkpointUpserts() {
        assertThat(repo.loadCheckpoint("me")).isEmpty();
        repo.saveCheckpoint("me", "100");
        repo.saveCheckpoint("me", "250");
        assertThat(repo.loadCheckpoint("me")).contains("250");
    }
}
