package dev.applyops.orchestrator.discovery.parser;

import dev.applyops.orchestrator.ApplyOpsProperties;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Jobright.ai recommendation emails. Links look like jobright.ai/jobs/info/{id}?utm_... Cards read
 * "Company / Industry · Stage / 86% / Title / [Salary] / Location / referrals · posted ago".
 */
@Component
public class JobrightAlertParser extends CardAlertParser {

    private static final Pattern SALARY = Pattern.compile("^\\$|/(?:yr|hr)\\b");
    private static final Pattern JOB_ID = Pattern.compile("jobright\\.ai/jobs/info/([A-Za-z0-9]+)");
    private final List<String> senders;

    @Autowired
    public JobrightAlertParser(ApplyOpsProperties props) {
        this(props.discovery().jobrightSenders());
    }

    JobrightAlertParser(List<String> senders) {
        this.senders = List.copyOf(senders);
    }

    @Override
    public String provider() {
        return "jobright";
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
    protected Details details(List<String> before, List<String> after) {
        String location = after.stream().filter(f -> !SALARY.matcher(f).find()).findFirst().orElse(null);
        return new Details(at(before, 0), location);
    }

    @Override
    protected String canonicalUrl(String jobId, String rawHref) {
        return "https://jobright.ai/jobs/info/" + jobId;
    }
}
