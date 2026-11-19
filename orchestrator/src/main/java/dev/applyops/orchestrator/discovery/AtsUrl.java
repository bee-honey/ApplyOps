package dev.applyops.orchestrator.discovery;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A posting on a known applicant tracking system, identified by a stable key. */
public record AtsUrl(String type, String key, String url) {

    private static final Pattern GREENHOUSE_PATH = Pattern.compile("^/([^/]+)/jobs/(\\d+)");
    private static final Pattern LEVER_PATH = Pattern.compile("^/([^/]+)/([0-9a-f-]{36})");
    private static final Pattern ASHBY_PATH = Pattern.compile("^/([^/]+)/([0-9a-f-]{36})");
    private static final Pattern QUERY_PARAM = Pattern.compile("(?:^|&)(%s)=([^&]+)");

    /** Recognizes Greenhouse, Lever, Ashby and Workday posting URLs. */
    public static Optional<AtsUrl> parse(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();

        if (host.equals("boards.greenhouse.io") || host.equals("job-boards.greenhouse.io")) {
            Matcher m = GREENHOUSE_PATH.matcher(path);
            if (m.find()) {
                return Optional.of(of("greenhouse", m.group(1), m.group(2), url));
            }
            String board = param(query, "for");
            String token = param(query, "token");
            if (board != null && token != null) {
                return Optional.of(of("greenhouse", board, token, url));
            }
        }
        String ghJid = param(query, "gh_jid");
        if (ghJid != null) {
            // Company-hosted page embedding a Greenhouse board; board slug unknown, key by host.
            return Optional.of(of("greenhouse", host, ghJid, url));
        }
        if (host.equals("jobs.lever.co") || host.equals("jobs.eu.lever.co")) {
            Matcher m = LEVER_PATH.matcher(path);
            if (m.find()) {
                return Optional.of(of("lever", m.group(1), m.group(2), url));
            }
        }
        if (host.equals("jobs.ashbyhq.com")) {
            Matcher m = ASHBY_PATH.matcher(path);
            if (m.find()) {
                return Optional.of(of("ashby", m.group(1), m.group(2), url));
            }
        }
        if (host.endsWith(".myworkdayjobs.com")) {
            String tenant = host.substring(0, host.indexOf('.'));
            String last = path.replaceAll("/+$", "");
            last = last.substring(last.lastIndexOf('/') + 1);
            if (!last.isBlank()) {
                return Optional.of(of("workday", tenant, last, url));
            }
        }
        return Optional.empty();
    }

    private static AtsUrl of(String type, String board, String id, String url) {
        return new AtsUrl(type, type + ":" + board.toLowerCase(Locale.ROOT) + ":" + id.toLowerCase(Locale.ROOT), url);
    }

    private static String param(String query, String name) {
        Matcher m = Pattern.compile(String.format(QUERY_PARAM.pattern(), Pattern.quote(name))).matcher(query);
        return m.find() ? m.group(2) : null;
    }
}
