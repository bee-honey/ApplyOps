package dev.applyops.orchestrator.application;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** Lifecycle of one application (docs/DESIGN.md §3.2, §4). Workflow id: app-{jobId}. */
@WorkflowInterface
public interface ApplicationWorkflow {

    @WorkflowMethod
    String run(ApplicationInput input);

    @QueryMethod
    String state();
}
