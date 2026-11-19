package dev.applyops.orchestrator.discovery;

import dev.applyops.orchestrator.discovery.model.DiscoveredJob;
import dev.applyops.orchestrator.discovery.model.DiscoveryBatch;
import dev.applyops.orchestrator.discovery.model.PendingApplication;
import io.temporal.activity.ActivityInterface;
import java.util.List;

@ActivityInterface
public interface DiscoveryActivities {

    /** Read new alert emails since the saved checkpoint (or a lookback window on first run) and parse them. */
    DiscoveryBatch fetchAlerts();

    /** Persist jobs idempotently and return every application still waiting for its workflow to start. */
    List<PendingApplication> upsertJobs(List<DiscoveredJob> jobs);

    void markWorkflowsStarted(List<String> applicationIds);

    void saveCheckpoint(String historyId);
}
