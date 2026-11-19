package dev.applyops.orchestrator.discovery;

import dev.applyops.orchestrator.discovery.model.DiscoveryResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** One discovery pass. Run by the "job-discovery" Temporal Schedule (overlap: skip). */
@WorkflowInterface
public interface JobDiscoveryWorkflow {
    @WorkflowMethod
    DiscoveryResult discover();
}
