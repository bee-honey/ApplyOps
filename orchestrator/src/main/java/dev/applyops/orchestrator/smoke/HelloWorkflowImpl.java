package dev.applyops.orchestrator.smoke;

import dev.applyops.orchestrator.TaskQueues;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import java.time.Duration;

@WorkflowImpl(taskQueues = TaskQueues.ORCHESTRATOR)
public class HelloWorkflowImpl implements HelloWorkflow {

    // The activity is implemented in Python (agents/), so route it to the agents task queue.
    private final EchoActivity echo = Workflow.newActivityStub(
            EchoActivity.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.AGENTS)
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
                    .build());

    @Override
    public EchoResult hello(String name) {
        return echo.echo(new EchoRequest("Hello, " + name));
    }
}
