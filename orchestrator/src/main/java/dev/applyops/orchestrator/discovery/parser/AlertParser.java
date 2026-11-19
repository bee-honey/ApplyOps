package dev.applyops.orchestrator.discovery.parser;

import dev.applyops.orchestrator.discovery.model.AlertEmail;
import dev.applyops.orchestrator.discovery.model.DiscoveredJob;
import java.util.List;

/** Extracts job postings from one provider's alert email. */
public interface AlertParser {

    String provider();

    /** Gmail search terms for this provider's senders (addresses or domains). */
    List<String> senders();

    default boolean supports(String fromAddress) {
        if (fromAddress == null) {
            return false;
        }
        String from = fromAddress.toLowerCase();
        return senders().stream().anyMatch(s -> s.contains("@") ? from.equals(s) : from.endsWith("@" + s) || from.endsWith("." + s));
    }

    List<DiscoveredJob> parse(AlertEmail email);
}
