package dev.applyops.orchestrator.smoke;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** M0 polyglot smoke test: a Java workflow that calls a Python activity. */
@WorkflowInterface
public interface HelloWorkflow {
    @WorkflowMethod
    EchoResult hello(String name);
}
