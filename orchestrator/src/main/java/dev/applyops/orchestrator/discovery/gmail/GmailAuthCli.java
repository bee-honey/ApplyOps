package dev.applyops.orchestrator.discovery.gmail;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.services.gmail.Gmail;
import java.nio.file.Path;

/**
 * One-time consent flow, run on the host: {@code make gmail-auth}. Opens a browser, asks for
 * read-only Gmail access, and stores the refresh token under data/gmail-token (gitignored).
 */
public final class GmailAuthCli {

    public static void main(String[] args) throws Exception {
        Path credentials = Path.of(env("GMAIL_CREDENTIALS_FILE", "data/gmail-credentials.json"));
        Path tokenDir = Path.of(env("GMAIL_TOKEN_DIR", "data/gmail-token"));
        HttpTransport transport = new NetHttpTransport();

        var flow = GmailAuth.flow(transport, credentials, tokenDir);
        var receiver = new LocalServerReceiver.Builder().setHost("localhost").setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize(GmailAuth.USER);

        Gmail gmail = new Gmail.Builder(transport, GmailAuth.JSON, credential)
                .setApplicationName("ApplyOps")
                .build();
        var profile = gmail.users().getProfile("me").execute();
        System.out.printf("Authorized read-only access. Messages in mailbox: %d. Token stored in %s%n",
                profile.getMessagesTotal(), tokenDir.toAbsolutePath());
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }
}
