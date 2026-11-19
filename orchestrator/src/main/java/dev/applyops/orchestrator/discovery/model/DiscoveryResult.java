package dev.applyops.orchestrator.discovery.model;

public record DiscoveryResult(int messagesScanned, int jobsSeen, int workflowsStarted) {}
