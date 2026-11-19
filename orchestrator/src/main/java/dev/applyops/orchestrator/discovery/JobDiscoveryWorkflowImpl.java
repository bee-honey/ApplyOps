package dev.applyops.orchestrator.discovery;

import dev.applyops.orchestrator.TaskQueues;
import dev.applyops.orchestrator.application.ApplicationInput;
import dev.applyops.orchestrator.application.ApplicationWorkflow;
import dev.applyops.orchestrator.discovery.model.DiscoveryBatch;
import dev.applyops.orchestrator.discovery.model.DiscoveryResult;
import dev.applyops.orchestrator.discovery.model.PendingApplication;
import io.temporal.activity.ActivityOptions;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.ParentClosePolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ChildWorkflowFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Async;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

/**
 * fetch → upsert → start one ApplicationWorkflow per pending application → mark started → checkpoint.
 *
 * <p>Every step is safe to repeat: crash anywhere and the next run re-reads the same emails
 * (deduplicated by the DB), restarts only workflows not yet marked started (duplicates rejected by
 * workflow id), and only then advances the Gmail checkpoint.
 */
@WorkflowImpl(taskQueues = TaskQueues.ORCHESTRATOR)
public class JobDiscoveryWorkflowImpl implements JobDiscoveryWorkflow {

    private static final Logger log = Workflow.getLogger(JobDiscoveryWorkflowImpl.class);

    private final DiscoveryActivities activities = Workflow.newActivityStub(
            DiscoveryActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(5)
                            .setInitialInterval(Duration.ofSeconds(5))
                            .build())
                    .build());

    @Override
    public DiscoveryResult discover() {
        DiscoveryBatch batch = activities.fetchAlerts();
        List<PendingApplication> pending = activities.upsertJobs(batch.jobs());

        List<String> started = new ArrayList<>();
        List<Promise<WorkflowExecution>> starts = new ArrayList<>();
        for (PendingApplication p : pending) {
            ApplicationWorkflow child = Workflow.newChildWorkflowStub(
                    ApplicationWorkflow.class,
                    ChildWorkflowOptions.newBuilder()
                            .setWorkflowId(p.workflowId())
                            .setWorkflowIdReusePolicy(
                                    WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                            // Applications outlive this discovery run.
                            .setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_ABANDON)
                            .build());
            Async.function(child::run, new ApplicationInput(p.applicationId(), p.jobId()));
            starts.add(Workflow.getWorkflowExecution(child));
        }
        for (int i = 0; i < starts.size(); i++) {
            try {
                starts.get(i).get();
                started.add(pending.get(i).applicationId());
            } catch (ChildWorkflowFailure e) {
                if (e.getCause() instanceof WorkflowExecutionAlreadyStarted) {
                    started.add(pending.get(i).applicationId()); // Started by an earlier, interrupted run.
                } else {
                    throw e;
                }
            }
        }

        activities.markWorkflowsStarted(started);
        if (batch.newHistoryId() != null) {
            activities.saveCheckpoint(batch.newHistoryId());
        }
        log.info("Discovery: {} emails, {} jobs, {} applications started",
                batch.messagesScanned(), batch.jobs().size(), started.size());
        return new DiscoveryResult(batch.messagesScanned(), batch.jobs().size(), started.size());
    }
}
