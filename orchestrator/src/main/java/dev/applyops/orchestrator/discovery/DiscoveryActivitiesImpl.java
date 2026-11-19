package dev.applyops.orchestrator.discovery;

import dev.applyops.orchestrator.ApplyOpsProperties;
import dev.applyops.orchestrator.TaskQueues;
import dev.applyops.orchestrator.discovery.gmail.GmailClient;
import dev.applyops.orchestrator.discovery.gmail.GmailClient.GmailNotAuthorizedException;
import dev.applyops.orchestrator.discovery.gmail.GmailClient.HistoryExpiredException;
import dev.applyops.orchestrator.discovery.model.AlertEmail;
import dev.applyops.orchestrator.discovery.model.DiscoveredJob;
import dev.applyops.orchestrator.discovery.model.DiscoveryBatch;
import dev.applyops.orchestrator.discovery.model.PendingApplication;
import dev.applyops.orchestrator.discovery.parser.AlertParser;
import dev.applyops.orchestrator.discovery.parser.AlertParserRegistry;
import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.ActivityImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ActivityImpl(taskQueues = TaskQueues.ORCHESTRATOR)
public class DiscoveryActivitiesImpl implements DiscoveryActivities {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryActivitiesImpl.class);
    static final String ACCOUNT = "me";
    static final int PENDING_LIMIT = 200;

    private final GmailClient gmail;
    private final AlertParserRegistry parsers;
    private final JobRepository repo;
    private final LinkResolver resolver;
    private final ApplyOpsProperties.Discovery config;

    public DiscoveryActivitiesImpl(
            GmailClient gmail,
            AlertParserRegistry parsers,
            JobRepository repo,
            LinkResolver resolver,
            ApplyOpsProperties props) {
        this.gmail = gmail;
        this.parsers = parsers;
        this.repo = repo;
        this.resolver = resolver;
        this.config = props.discovery();
    }

    @Override
    public DiscoveryBatch fetchAlerts() {
        try {
            Optional<String> checkpoint = repo.loadCheckpoint(ACCOUNT);
            if (checkpoint.isPresent()) {
                try {
                    var page = gmail.messagesAddedSince(checkpoint.get());
                    return parseAll(page.messageIds(), page.historyId(), false);
                } catch (HistoryExpiredException e) {
                    log.warn("{}; falling back to full sync", e.getMessage());
                }
            }
            // Capture the position BEFORE searching so mail arriving mid-sync is picked up next run.
            String historyId = gmail.currentHistoryId();
            String query = parsers.gmailFromQuery() + " newer_than:" + config.initialLookbackDays() + "d";
            return parseAll(gmail.searchMessageIds(query, config.maxInitialMessages()), historyId, true);
        } catch (GmailNotAuthorizedException e) {
            throw ApplicationFailure.newNonRetryableFailure(e.getMessage(), "GmailNotAuthorized");
        }
    }

    private DiscoveryBatch parseAll(List<String> messageIds, String newHistoryId, boolean fullSync) {
        List<DiscoveredJob> jobs = new ArrayList<>();
        int scanned = 0;
        for (String id : messageIds) {
            // History includes every new message; check the sender cheaply before downloading the body.
            if (!fullSync && parsers.forSender(gmail.fromAddress(id)).isEmpty()) {
                continue;
            }
            AlertEmail email = gmail.get(id);
            Optional<AlertParser> parser = parsers.forSender(email.fromAddress());
            if (parser.isEmpty()) {
                continue;
            }
            scanned++;
            try {
                List<DiscoveredJob> parsed = parser.get().parse(email);
                if (parsed.isEmpty()) {
                    log.warn("No jobs parsed from {} email {} ({})", parser.get().provider(), id, email.subject());
                }
                jobs.addAll(parsed);
            } catch (RuntimeException e) {
                // One malformed email must not block the checkpoint forever.
                log.error("Failed to parse {} email {}", parser.get().provider(), id, e);
            }
        }
        log.info("Scanned {} alert emails ({} sync): {} jobs", scanned, fullSync ? "full" : "incremental", jobs.size());
        return new DiscoveryBatch(jobs, newHistoryId, scanned, fullSync);
    }

    @Override
    public List<PendingApplication> upsertJobs(List<DiscoveredJob> jobs) {
        for (DiscoveredJob job : jobs) {
            String resolved = resolver.resolve(job.url());
            repo.upsert(job, AtsUrl.parse(resolved));
        }
        return repo.pendingWorkflowStarts(PENDING_LIMIT);
    }

    @Override
    public void markWorkflowsStarted(List<String> applicationIds) {
        repo.markWorkflowsStarted(applicationIds);
    }

    @Override
    public void saveCheckpoint(String historyId) {
        repo.saveCheckpoint(ACCOUNT, historyId);
    }
}
