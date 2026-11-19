package dev.applyops.orchestrator.discovery.gmail;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.GmailScopes;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** OAuth plumbing shared by the auth CLI and the runtime client. Scope: gmail.readonly only. */
final class GmailAuth {

    static final JsonFactory JSON = GsonFactory.getDefaultInstance();
    static final List<String> SCOPES = List.of(GmailScopes.GMAIL_READONLY);
    static final String USER = "applyops";

    private GmailAuth() {}

    static GoogleAuthorizationCodeFlow flow(HttpTransport transport, Path credentialsFile, Path tokenDir)
            throws IOException {
        if (!Files.exists(credentialsFile)) {
            throw new GmailClient.GmailNotAuthorizedException(
                    "Missing " + credentialsFile + ". Download the OAuth client JSON (Desktop app) from Google Cloud.");
        }
        GoogleClientSecrets secrets;
        try (Reader r = Files.newBufferedReader(credentialsFile)) {
            secrets = GoogleClientSecrets.load(JSON, r);
        }
        return new GoogleAuthorizationCodeFlow.Builder(transport, JSON, secrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(tokenDir.toFile()))
                .setAccessType("offline")
                .build();
    }

    /** Stored credential, or null if `make gmail-auth` has not been run. */
    static Credential load(HttpTransport transport, Path credentialsFile, Path tokenDir) throws IOException {
        return flow(transport, credentialsFile, tokenDir).loadCredential(USER);
    }
}
