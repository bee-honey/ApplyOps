package dev.applyops.orchestrator.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import dev.applyops.orchestrator.TaskQueues;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HelloWorkflowTest {

    /** Stands in for the Python worker. Registered ONLY on agents-tq, so a mis-routed call times out. */
    static class FakePythonEcho implements EchoActivity {
        @Override
        public EchoResult echo(EchoRequest request) {
            return new EchoResult(request.message(), "fake-worker", "python");
        }
    }

    private TestWorkflowEnvironment env;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        env.newWorker(TaskQueues.ORCHESTRATOR).registerWorkflowImplementationTypes(HelloWorkflowImpl.class);
        Worker agents = env.newWorker(TaskQueues.AGENTS);
        agents.registerActivitiesImplementations(new FakePythonEcho());
        env.start();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    @Test
    void routesEchoToAgentsQueueAndReturnsResult() {
        HelloWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                HelloWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TaskQueues.ORCHESTRATOR)
                        .setWorkflowExecutionTimeout(Duration.ofSeconds(10))
                        .build());

        EchoResult result = wf.hello("ApplyOps");

        assertThat(result.message()).isEqualTo("Hello, ApplyOps");
        assertThat(result.language()).isEqualTo("python");
    }
}
