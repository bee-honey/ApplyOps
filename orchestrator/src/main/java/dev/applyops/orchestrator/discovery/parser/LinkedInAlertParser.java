package dev.applyops.orchestrator.discovery.parser;

import dev.applyops.orchestrator.ApplyOpsProperties;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** LinkedIn job alert emails. Links look like linkedin.com/comm/jobs/view/{id}/?trackingId=... */
@Component
public class LinkedInAlertParser extends CardAlertParser {

    private static final Pattern JOB_ID = Pattern.compile("linkedin\\.com/(?:comm/)?jobs/view/(\\d+)");
    private final List<String> senders;

    @Autowired
    public LinkedInAlertParser(ApplyOpsProperties props) {
        this(props.discovery().linkedinSenders());
    }

    LinkedInAlertParser(List<String> senders) {
        this.senders = List.copyOf(senders);
    }

    @Override
    public String provider() {
        return "linkedin";
    }

    @Override
    public List<String> senders() {
        return senders;
    }

    @Override
    protected Pattern jobIdPattern() {
        return JOB_ID;
    }

    @Override
    protected String canonicalUrl(String jobId, String rawHref) {
        return "https://www.linkedin.com/jobs/view/" + jobId + "/";
    }
}
