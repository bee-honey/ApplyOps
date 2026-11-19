package dev.applyops.orchestrator.discovery.parser;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AlertParserRegistry {

    private final List<AlertParser> parsers;

    public AlertParserRegistry(List<AlertParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public Optional<AlertParser> forSender(String fromAddress) {
        return parsers.stream().filter(p -> p.supports(fromAddress)).findFirst();
    }

    /** Gmail search query matching every supported sender, e.g. {@code from:(a@x.com OR y.com)}. */
    public String gmailFromQuery() {
        return parsers.stream()
                .flatMap(p -> p.senders().stream())
                .distinct()
                .collect(Collectors.joining(" OR ", "from:(", ")"));
    }
}
