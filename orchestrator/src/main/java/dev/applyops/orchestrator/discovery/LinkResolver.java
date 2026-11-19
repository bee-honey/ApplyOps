package dev.applyops.orchestrator.discovery;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Follows tracking redirects in alert links to reach the final (ideally ATS) URL.
 *
 * <p>Never requests hosts whose terms forbid automated access (ADR-0003): LinkedIn links are
 * returned untouched. Only redirects are followed; page content is not scraped here.
 */
@Component
public class LinkResolver {

    private static final Logger log = LoggerFactory.getLogger(LinkResolver.class);
    static final List<String> NEVER_FETCH = List.of("linkedin.com");
    private static final int MAX_HOPS = 6;

    private final HttpClient http;

    public LinkResolver() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER) // hops are checked one by one below
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    LinkResolver(HttpClient http) {
        this.http = http;
    }

    public String resolve(String url) {
        String current = url;
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            // Stop before requesting a forbidden host (the URL itself is still useful), or once on an ATS.
            if (current == null || isNeverFetch(current) || AtsUrl.parse(current).isPresent()) {
                return current;
            }
            String next = nextHop(current);
            if (next == null) {
                return current;
            }
            current = next;
        }
        return current;
    }

    /** Returns the redirect target of {@code url}, or null if it does not redirect. */
    private String nextHop(String url) {
        try {
            URI uri = URI.create(url);
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0 (compatible; ApplyOps/0.1)")
                    .build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() / 100 != 3) {
                return null;
            }
            return res.headers().firstValue("Location").map(loc -> uri.resolve(loc).toString()).orElse(null);
        } catch (Exception e) {
            log.debug("Could not resolve {}: {}", url, e.toString());
            return null;
        }
    }

    static boolean isNeverFetch(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return true;
            }
            String h = host.toLowerCase(Locale.ROOT);
            return NEVER_FETCH.stream().anyMatch(d -> h.equals(d) || h.endsWith("." + d));
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
