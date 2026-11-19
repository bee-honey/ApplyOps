package dev.applyops.orchestrator.discovery.gmail;

import dev.applyops.orchestrator.discovery.model.AlertEmail;
import java.util.List;

/** The slice of Gmail that discovery needs. Read-only. */
public interface GmailClient {

    /** Current mailbox history id: the sync position "now". */
    String currentHistoryId();

    /** Message ids matching a Gmail search query, newest first, at most {@code max}. */
    List<String> searchMessageIds(String query, int max);

    /**
     * Ids of messages added since {@code historyId}.
     *
     * @throws HistoryExpiredException if Gmail no longer has history that old (full sync needed)
     */
    HistoryPage messagesAddedSince(String historyId);

    /** Sender address only (cheap metadata fetch) to filter before downloading bodies. */
    String fromAddress(String messageId);

    AlertEmail get(String messageId);

    record HistoryPage(List<String> messageIds, String historyId) {}

    class HistoryExpiredException extends RuntimeException {
        public HistoryExpiredException(String historyId) {
            super("Gmail history " + historyId + " is no longer available");
        }
    }

    class GmailNotAuthorizedException extends RuntimeException {
        public GmailNotAuthorizedException(String message) {
            super(message);
        }
    }
}
