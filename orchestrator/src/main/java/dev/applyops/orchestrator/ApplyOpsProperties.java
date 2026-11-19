package dev.applyops.orchestrator;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("applyops")
public record ApplyOpsProperties(Gmail gmail, Discovery discovery) {

    public record Gmail(String credentialsFile, String tokenDir) {}

    public record Discovery(
            boolean scheduleEnabled,
            Duration interval,
            int initialLookbackDays,
            int maxInitialMessages,
            List<String> linkedinSenders,
            List<String> jobrightSenders) {}
}
