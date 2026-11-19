package dev.applyops.orchestrator.discovery;

import dev.applyops.orchestrator.discovery.model.DiscoveredJob;
import dev.applyops.orchestrator.discovery.model.PendingApplication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JobRepository {

    private final JdbcTemplate jdbc;

    public JobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Idempotently records one sighting. Matching order: same provider job id, then same ATS posting,
     * then same fingerprint (company + title). Creates the job's application row in the same transaction.
     *
     * @return the job id (existing or new)
     */
    @Transactional
    public UUID upsert(DiscoveredJob job, Optional<AtsUrl> ats) {
        UUID jobId = findBySource(job.provider(), job.externalId())
                .or(() -> ats.flatMap(a -> findByAtsKey(a.key())))
                .orElseGet(() -> upsertByFingerprint(job, ats));

        ats.ifPresent(a -> jdbc.update("""
                UPDATE job SET ats_key = ?, ats_type = ?, ats_url = ?, updated_at = now()
                WHERE id = ? AND ats_key IS NULL
                  AND NOT EXISTS (SELECT 1 FROM job WHERE ats_key = ?)
                """, a.key(), a.type(), a.url(), jobId, a.key()));

        jdbc.update("""
                INSERT INTO job_source (job_id, provider, external_id, source_url, email_message_id)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (provider, external_id) DO NOTHING
                """, jobId, job.provider(), job.externalId(), job.url(), job.emailMessageId());

        jdbc.update("""
                INSERT INTO application (job_id, workflow_id, state)
                VALUES (?, ?, 'DISCOVERED')
                ON CONFLICT (job_id) DO NOTHING
                """, jobId, "app-" + jobId);
        return jobId;
    }

    private Optional<UUID> findBySource(String provider, String externalId) {
        return jdbc.queryForList(
                        "SELECT job_id FROM job_source WHERE provider = ? AND external_id = ?",
                        UUID.class, provider, externalId)
                .stream().findFirst();
    }

    private Optional<UUID> findByAtsKey(String atsKey) {
        return jdbc.queryForList("SELECT id FROM job WHERE ats_key = ?", UUID.class, atsKey).stream().findFirst();
    }

    private UUID upsertByFingerprint(DiscoveredJob job, Optional<AtsUrl> ats) {
        // DO UPDATE (no-op) instead of DO NOTHING so RETURNING yields the existing row's id.
        return jdbc.queryForObject("""
                INSERT INTO job (fingerprint, company, title, location)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (fingerprint) DO UPDATE SET updated_at = now()
                RETURNING id
                """, UUID.class, JobFingerprint.of(job.company(), job.title()), job.company(), job.title(),
                job.location());
    }

    /** Applications whose workflow has not been started (new, or a previous run crashed before starting it). */
    public List<PendingApplication> pendingWorkflowStarts(int limit) {
        return jdbc.query("""
                SELECT id, job_id, workflow_id FROM application
                WHERE workflow_started_at IS NULL
                ORDER BY created_at
                LIMIT ?
                """, (rs, i) -> new PendingApplication(
                        rs.getString("id"), rs.getString("job_id"), rs.getString("workflow_id")), limit);
    }

    public void markWorkflowsStarted(List<String> applicationIds) {
        if (applicationIds.isEmpty()) {
            return;
        }
        jdbc.update("UPDATE application SET workflow_started_at = now() WHERE workflow_started_at IS NULL AND id = ANY (?::uuid[])",
                (Object) applicationIds.toArray(String[]::new));
    }

    public Optional<String> loadCheckpoint(String account) {
        return jdbc.queryForList("SELECT history_id FROM gmail_checkpoint WHERE account = ?", String.class, account)
                .stream().findFirst();
    }

    public void saveCheckpoint(String account, String historyId) {
        jdbc.update("""
                INSERT INTO gmail_checkpoint (account, history_id) VALUES (?, ?)
                ON CONFLICT (account) DO UPDATE SET history_id = EXCLUDED.history_id, updated_at = now()
                """, account, historyId);
    }
}
