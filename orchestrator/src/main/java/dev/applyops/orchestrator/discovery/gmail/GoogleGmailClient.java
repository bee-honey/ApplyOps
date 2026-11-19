package dev.applyops.orchestrator.discovery.gmail;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.ListHistoryResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import dev.applyops.orchestrator.ApplyOpsProperties;
import dev.applyops.orchestrator.discovery.model.AlertEmail;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Gmail API implementation. Connects lazily so the app starts before `make gmail-auth` is run. */
@Component
public class GoogleGmailClient implements GmailClient {

    private static final String ME = "me";
    private static final Pattern ANGLE_ADDRESS = Pattern.compile("<([^>]+)>");
    private final Path credentialsFile;
    private final Path tokenDir;
    private volatile Gmail gmail;

    public GoogleGmailClient(ApplyOpsProperties props) {
        this.credentialsFile = Path.of(props.gmail().credentialsFile());
        this.tokenDir = Path.of(props.gmail().tokenDir());
    }

    private Gmail gmail() {
        Gmail g = gmail;
        if (g != null) {
            return g;
        }
        synchronized (this) {
            if (gmail == null) {
                try {
                    HttpTransport transport = new NetHttpTransport();
                    Credential credential = GmailAuth.load(transport, credentialsFile, tokenDir);
                    if (credential == null) {
                        throw new GmailNotAuthorizedException("No Gmail token in " + tokenDir + ". Run: make gmail-auth");
                    }
                    gmail = new Gmail.Builder(transport, GmailAuth.JSON, credential)
                            .setApplicationName("ApplyOps")
                            .build();
                } catch (IOException e) {
                    throw new IllegalStateException("Could not initialize Gmail client", e);
                }
            }
            return gmail;
        }
    }

    @Override
    public String currentHistoryId() {
        return call(() -> gmail().users().getProfile(ME).execute().getHistoryId().toString());
    }

    @Override
    public List<String> searchMessageIds(String query, int max) {
        return call(() -> {
            List<String> ids = new ArrayList<>();
            String pageToken = null;
            do {
                ListMessagesResponse res = gmail().users().messages().list(ME)
                        .setQ(query)
                        .setMaxResults((long) Math.min(500, max - ids.size()))
                        .setPageToken(pageToken)
                        .execute();
                if (res.getMessages() != null) {
                    res.getMessages().forEach(m -> ids.add(m.getId()));
                }
                pageToken = res.getNextPageToken();
            } while (pageToken != null && ids.size() < max);
            return ids;
        });
    }

    @Override
    public HistoryPage messagesAddedSince(String historyId) {
        try {
            Set<String> ids = new LinkedHashSet<>();
            String pageToken = null;
            String latest = historyId;
            do {
                ListHistoryResponse res = gmail().users().history().list(ME)
                        .setStartHistoryId(new BigInteger(historyId))
                        .setHistoryTypes(List.of("messageAdded"))
                        .setPageToken(pageToken)
                        .execute();
                if (res.getHistory() != null) {
                    for (History h : res.getHistory()) {
                        if (h.getMessagesAdded() != null) {
                            h.getMessagesAdded().forEach(a -> ids.add(a.getMessage().getId()));
                        }
                    }
                }
                if (res.getHistoryId() != null) {
                    latest = res.getHistoryId().toString();
                }
                pageToken = res.getNextPageToken();
            } while (pageToken != null);
            return new HistoryPage(List.copyOf(ids), latest);
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                throw new HistoryExpiredException(historyId);
            }
            throw new UncheckedIOException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String fromAddress(String messageId) {
        return call(() -> {
            Message m = gmail().users().messages().get(ME, messageId)
                    .setFormat("metadata")
                    .setMetadataHeaders(List.of("From"))
                    .execute();
            return address(header(m.getPayload(), "From"));
        });
    }

    @Override
    public AlertEmail get(String messageId) {
        return call(() -> {
            Message m = gmail().users().messages().get(ME, messageId).setFormat("full").execute();
            MessagePart payload = m.getPayload();
            String html = body(payload, "text/html");
            if (html == null) {
                String text = body(payload, "text/plain");
                html = text == null ? "" : "<pre>" + text + "</pre>";
            }
            long date = m.getInternalDate() == null ? 0L : m.getInternalDate();
            return new AlertEmail(m.getId(), address(header(payload, "From")), header(payload, "Subject"), date, html);
        });
    }

    private static String header(MessagePart part, String name) {
        if (part == null || part.getHeaders() == null) {
            return null;
        }
        return part.getHeaders().stream()
                .filter(h -> name.equalsIgnoreCase(h.getName()))
                .map(MessagePartHeader::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String body(MessagePart part, String mimeType) {
        if (part == null) {
            return null;
        }
        if (mimeType.equalsIgnoreCase(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null) {
            return new String(Base64.getUrlDecoder().decode(part.getBody().getData()), StandardCharsets.UTF_8);
        }
        if (part.getParts() != null) {
            for (MessagePart child : part.getParts()) {
                String found = body(child, mimeType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** "LinkedIn Job Alerts <jobalerts-noreply@linkedin.com>" -> "jobalerts-noreply@linkedin.com" */
    static String address(String fromHeader) {
        if (fromHeader == null) {
            return null;
        }
        Matcher m = ANGLE_ADDRESS.matcher(fromHeader);
        String addr = m.find() ? m.group(1) : fromHeader;
        return addr.strip().toLowerCase(Locale.ROOT);
    }

    private interface IoCall<T> {
        T run() throws IOException;
    }

    private static <T> T call(IoCall<T> c) {
        try {
            return c.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
