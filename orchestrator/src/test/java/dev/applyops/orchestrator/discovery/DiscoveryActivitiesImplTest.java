package dev.applyops.orchestrator.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.applyops.orchestrator.ApplyOpsProperties;
import dev.applyops.orchestrator.discovery.gmail.GmailClient;
import dev.applyops.orchestrator.discovery.model.AlertEmail;
import dev.applyops.orchestrator.testing.Fixtures;
import dev.applyops.orchestrator.discovery.model.DiscoveredJob;
import dev.applyops.orchestrator.discovery.parser.AlertParserRegistry;
import dev.applyops.orchestrator.discovery.parser.JobrightAlertParser;
import dev.applyops.orchestrator.discovery.parser.LinkedInAlertParser;
import io.temporal.failure.ApplicationFailure;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscoveryActivitiesImplTest {

    /** In-memory Gmail. Records call order to verify the history id is captured before searching. */
    static class FakeGmail implements GmailClient {
        final Map<String, AlertEmail> messages = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        List<String> searchResult = List.of();
        HistoryPage history;
        boolean historyExpired;
        boolean authorized = true;

        @Override public String currentHistoryId() {
            if (!authorized) throw new GmailNotAuthorizedException("Run make gmail-auth");
            calls.add("currentHistoryId");
            return "500";
        }
        @Override public List<String> searchMessageIds(String query, int max) {
            calls.add("search:" + query);
            return searchResult;
        }
        @Override public HistoryPage messagesAddedSince(String historyId) {
            calls.add("history:" + historyId);
            if (historyExpired) throw new HistoryExpiredException(historyId);
            return history;
        }
        @Override public String fromAddress(String id) {
            calls.add("from:" + id);
            return messages.get(id).fromAddress();
        }
        @Override public AlertEmail get(String id) {
            calls.add("get:" + id);
            return messages.get(id);
        }
    }

    FakeGmail gmail;
    JobRepository repo;
    DiscoveryActivitiesImpl activities;

    @BeforeEach
    void setUp() throws Exception {
        gmail = new FakeGmail();
        gmail.messages.put("li", new AlertEmail("li", "jobalerts-noreply@linkedin.com", "alert", 0, Fixtures.email("linkedin-alert.html")));
        gmail.messages.put("jr", new AlertEmail("jr", "noreply@jobright.ai", "matches", 0, Fixtures.email("jobright-alert.html")));
        gmail.messages.put("spam", new AlertEmail("spam", "friend@example.com", "hi", 0, "<p>hi</p>"));
        gmail.messages.put("broken", new AlertEmail("broken", "noreply@jobright.ai", "?", 0, null));

        repo = mock(JobRepository.class);
        var props = new ApplyOpsProperties(
                new ApplyOpsProperties.Gmail("creds.json", "tokens"),
                new ApplyOpsProperties.Discovery(false, Duration.ofMinutes(15), 14, 100,
                        List.of("jobalerts-noreply@linkedin.com"), List.of("jobright.ai")));
        var registry = new AlertParserRegistry(List.of(
                new LinkedInAlertParser(props), new JobrightAlertParser(props)));
        activities = new DiscoveryActivitiesImpl(gmail, registry, repo, new LinkResolver(), props);
    }

    @Test
    void firstRunSearchesLookbackWindowAndCapturesHistoryIdFirst() {
        when(repo.loadCheckpoint("me")).thenReturn(Optional.empty());
        gmail.searchResult = List.of("li", "jr");

        var batch = activities.fetchAlerts();

        assertThat(batch.fullSync()).isTrue();
        assertThat(batch.newHistoryId()).isEqualTo("500");
        assertThat(batch.messagesScanned()).isEqualTo(2);
        assertThat(batch.jobs()).hasSize(12);
        assertThat(gmail.calls.get(0)).isEqualTo("currentHistoryId");
        assertThat(gmail.calls.get(1)).isEqualTo(
                "search:from:(jobalerts-noreply@linkedin.com OR jobright.ai) newer_than:14d");
    }

    @Test
    void incrementalRunSkipsOtherSendersWithoutDownloadingThem() {
        when(repo.loadCheckpoint("me")).thenReturn(Optional.of("400"));
        gmail.history = new GmailClient.HistoryPage(List.of("spam", "jr"), "450");

        var batch = activities.fetchAlerts();

        assertThat(batch.fullSync()).isFalse();
        assertThat(batch.newHistoryId()).isEqualTo("450");
        assertThat(batch.jobs()).extracting(DiscoveredJob::provider).containsOnly("jobright");
        assertThat(gmail.calls).contains("from:spam").doesNotContain("get:spam");
    }

    @Test
    void expiredHistoryFallsBackToFullSync() {
        when(repo.loadCheckpoint("me")).thenReturn(Optional.of("1"));
        gmail.historyExpired = true;
        gmail.searchResult = List.of("li");

        var batch = activities.fetchAlerts();

        assertThat(batch.fullSync()).isTrue();
        assertThat(batch.newHistoryId()).isEqualTo("500");
        assertThat(batch.jobs()).hasSize(6);
    }

    @Test
    void malformedEmailIsSkippedNotFatal() {
        when(repo.loadCheckpoint("me")).thenReturn(Optional.empty());
        gmail.searchResult = List.of("broken", "li");

        var batch = activities.fetchAlerts();

        assertThat(batch.jobs()).hasSize(6);
    }

    @Test
    void missingAuthorizationIsNonRetryable() {
        when(repo.loadCheckpoint("me")).thenReturn(Optional.empty());
        gmail.authorized = false;

        assertThatThrownBy(() -> activities.fetchAlerts())
                .isInstanceOf(ApplicationFailure.class)
                .satisfies(e -> assertThat(((ApplicationFailure) e).isNonRetryable()).isTrue());
    }

    @Test
    void upsertNeverRequestsLinkedInAndReturnsPending() {
        var job = new DiscoveredJob("linkedin", "1", "Engineer", "Acme", "Remote", "https://www.linkedin.com/jobs/view/1/", "m");
        when(repo.pendingWorkflowStarts(any(Integer.class))).thenReturn(List.of());

        activities.upsertJobs(List.of(job));

        verify(repo).upsert(job, Optional.empty());
        verify(repo).pendingWorkflowStarts(DiscoveryActivitiesImpl.PENDING_LIMIT);
    }
}
