package dev.applyops.orchestrator.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.applyops.orchestrator.TaskQueues;
import dev.applyops.orchestrator.application.ApplicationInput;
import dev.applyops.orchestrator.application.ApplicationWorkflow;
import dev.applyops.orchestrator.application.ApplicationWorkflowImpl;
import dev.applyops.orchestrator.discovery.model.DiscoveredJob;
import dev.applyops.orchestrator.discovery.model.DiscoveryBatch;
import dev.applyops.orchestrator.discovery.model.DiscoveryResult;
import dev.applyops.orchestrator.discovery.model.PendingApplication;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobDiscoveryWorkflowTest {

    /** Scripted activities that record the order of side effects. */
    static class FakeActivities implements DiscoveryActivities {
        final List<String> calls = new CopyOnWriteArrayList<>();
        List<PendingApplication> pending = List.of();
        String historyId = "900";

        @Override public DiscoveryBatch fetchAlerts() {
            calls.add("fetch");
            var job = new DiscoveredJob("linkedin", "1", "Engineer", "Acme", "Remote", "u", "m");
            return new DiscoveryBatch(List.of(job), historyId, 1, false);
        }
        @Override public List<PendingApplication> upsertJobs(List<DiscoveredJob> jobs) {
            calls.add("upsert:" + jobs.size());
            return pending;
        }
        @Override public void markWorkflowsStarted(List<String> ids) {
            calls.add("markStarted:" + ids);
        }
        @Override public void saveCheckpoint(String historyId) {
            calls.add("checkpoint:" + historyId);
        }
    }

    TestWorkflowEnvironment env;
    FakeActivities activities;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        activities = new FakeActivities();
        var worker = env.newWorker(TaskQueues.ORCHESTRATOR);
        worker.registerWorkflowImplementationTypes(JobDiscoveryWorkflowImpl.class, ApplicationWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        env.start();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    DiscoveryResult runDiscovery() {
        return env.getWorkflowClient().newWorkflowStub(JobDiscoveryWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TaskQueues.ORCHESTRATOR)
                        .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                        .build())
                .discover();
    }

    @Test
    void startsApplicationsThenMarksThenCheckpoints() {
        activities.pending = List.of(new PendingApplication("a1", "j1", "app-j1"), new PendingApplication("a2", "j2", "app-j2"));

        DiscoveryResult result = runDiscovery();

        assertThat(result).isEqualTo(new DiscoveryResult(1, 1, 2));
        assertThat(activities.calls).containsExactly("fetch", "upsert:1", "markStarted:[a1, a2]", "checkpoint:900");
        String state = env.getWorkflowClient()
                .newWorkflowStub(ApplicationWorkflow.class, "app-j1")
                .state();
        assertThat(state).isEqualTo("DISCOVERED");
    }

    @Test
    void workflowAlreadyStartedByCrashedRunCountsAsStarted() {
        // Simulate: a previous run started app-j1 but crashed before markWorkflowsStarted.
        WorkflowClient client = env.getWorkflowClient();
        ApplicationWorkflow existing = client.newWorkflowStub(ApplicationWorkflow.class,
                WorkflowOptions.newBuilder().setWorkflowId("app-j1").setTaskQueue(TaskQueues.ORCHESTRATOR).build());
        WorkflowClient.start(existing::run, new ApplicationInput("a1", "j1"));

        activities.pending = List.of(new PendingApplication("a1", "j1", "app-j1"), new PendingApplication("a2", "j2", "app-j2"));
        DiscoveryResult result = runDiscovery();

        assertThat(result.workflowsStarted()).isEqualTo(2);
        assertThat(activities.calls).contains("markStarted:[a1, a2]");
    }

    @Test
    void noNewMailStillCheckpoints() {
        activities.pending = new ArrayList<>();
        runDiscovery();
        assertThat(activities.calls).containsExactly("fetch", "upsert:1", "markStarted:[]", "checkpoint:900");
    }
}
