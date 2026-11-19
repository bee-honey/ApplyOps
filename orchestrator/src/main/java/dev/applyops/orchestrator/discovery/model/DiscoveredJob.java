package dev.applyops.orchestrator.discovery.model;

/** A job as seen in one alert email, before dedup. Temporal payload. */
public record DiscoveredJob(
        String provider,
        String externalId,
        String title,
        String company,
        String location,
        String url,
        String emailMessageId) {}
