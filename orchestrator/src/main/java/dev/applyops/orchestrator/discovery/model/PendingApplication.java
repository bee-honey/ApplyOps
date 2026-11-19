package dev.applyops.orchestrator.discovery.model;

/** An application row whose ApplicationWorkflow has not been started yet. */
public record PendingApplication(String applicationId, String jobId, String workflowId) {}
