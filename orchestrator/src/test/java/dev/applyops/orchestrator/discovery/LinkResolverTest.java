package dev.applyops.orchestrator.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkResolverTest {

    private HttpServer server;
    private final List<String> requested = new CopyOnWriteArrayList<>();
    private String base;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        redirect("/click1", "/click2");
        redirect("/click2", "https://boards.greenhouse.io/acme/jobs/123");
        redirect("/to-linkedin", "https://www.linkedin.com/jobs/view/1/");
        server.createContext("/plain", ex -> {
            requested.add(ex.getRequestURI().getPath());
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void redirect(String from, String to) {
        server.createContext(from, ex -> {
            requested.add(ex.getRequestURI().getPath());
            ex.getResponseHeaders().add("Location", to);
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private final LinkResolver resolver = new LinkResolver();

    @Test
    void followsTrackingRedirectsAndStopsAtAtsWithoutRequestingIt() {
        assertThat(resolver.resolve(base + "/click1")).isEqualTo("https://boards.greenhouse.io/acme/jobs/123");
        assertThat(requested).containsExactly("/click1", "/click2");
    }

    @Test
    void neverRequestsLinkedIn() {
        assertThat(resolver.resolve("https://www.linkedin.com/comm/jobs/view/1/")).isEqualTo("https://www.linkedin.com/comm/jobs/view/1/");
        // A redirect INTO LinkedIn is reported but not followed.
        assertThat(resolver.resolve(base + "/to-linkedin")).isEqualTo("https://www.linkedin.com/jobs/view/1/");
        assertThat(requested).containsExactly("/to-linkedin");
    }

    @Test
    void returnsUrlWhenThereIsNoRedirect() {
        assertThat(resolver.resolve(base + "/plain")).isEqualTo(base + "/plain");
    }

    @Test
    void neverFetchMatchesSubdomainsOnly() {
        assertThat(LinkResolver.isNeverFetch("https://linkedin.com/x")).isTrue();
        assertThat(LinkResolver.isNeverFetch("https://www.linkedin.com/x")).isTrue();
        assertThat(LinkResolver.isNeverFetch("https://notlinkedin.com/x")).isFalse();
    }
}
