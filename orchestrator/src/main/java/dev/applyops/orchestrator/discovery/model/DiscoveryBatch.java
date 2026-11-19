package dev.applyops.orchestrator.discovery.model;

import java.util.List;

/**
 * Result of one Gmail fetch. {@code newHistoryId} is saved only after every job in the
 * batch is persisted, so a crash means re-reading (and deduplicating), never skipping.
 */
public record DiscoveryBatch(
        List<DiscoveredJob> jobs, String newHistoryId, int messagesScanned, boolean fullSync) {}
