package dev.applyops.orchestrator.discovery.model;

/** A fetched alert email (internal to the fetch activity; never a Temporal payload). */
public record AlertEmail(String id, String fromAddress, String subject, long internalDateMillis, String html) {}
