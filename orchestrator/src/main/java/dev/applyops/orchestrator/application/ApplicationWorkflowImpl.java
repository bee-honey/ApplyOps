package dev.applyops.orchestrator.application;

import dev.applyops.orchestrator.TaskQueues;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

/**
 * M1 stub: records that the job was discovered and completes. M2 adds analysis and scoring;
 * M4 adds the human approval waits. Kept short-lived until then so changing this code later
 * doesn't break in-flight executions.
 */
@WorkflowImpl(taskQueues = TaskQueues.ORCHESTRATOR)
public class ApplicationWorkflowImpl implements ApplicationWorkflow {

    private String state = "DISCOVERED";

    @Override
    public String run(ApplicationInput input) {
        Workflow.getLogger(ApplicationWorkflowImpl.class)
                .info("Application {} for job {}: {}", input.applicationId(), input.jobId(), state);
        return state;
    }

    @Override
    public String state() {
        return state;
    }
}
