package dev.applyops.orchestrator.discovery.gmail;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import dev.applyops.orchestrator.ApplyOpsProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Dev tool, run on the host: {@code make gmail-dump Q='from:jobright.ai' N=10}. Saves raw alert
 * emails under data/alert-samples (gitignored) for parser development. Samples contain personal
 * data: scrub them before turning any into a committed test fixture.
 */
public final class GmailDumpCli {

    public static void main(String[] args) throws Exception {
        String query = args.length > 0 && !args[0].isBlank() ? args[0] : "from:(linkedin.com OR jobright.ai)";
        int max = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        Path credentials = Path.of(env("GMAIL_CREDENTIALS_FILE", "data/gmail-credentials.json"));
        Path tokenDir = Path.of(env("GMAIL_TOKEN_DIR", "data/gmail-token"));
        Path out = Path.of("data/alert-samples");
        Files.createDirectories(out);

        HttpTransport transport = new NetHttpTransport();
        if (GmailAuth.load(transport, credentials, tokenDir) == null) {
            throw new IllegalStateException("No Gmail token. Run: make gmail-auth");
        }
        var props = new ApplyOpsProperties(
                new ApplyOpsProperties.Gmail(credentials.toString(), tokenDir.toString()), null);
        var client = new GoogleGmailClient(props);

        List<String> ids = client.searchMessageIds(query, max);
        for (String id : ids) {
            var email = client.get(id);
            String header = "<!-- id: %s%n     from: %s%n     subject: %s -->%n"
                    .formatted(email.id(), email.fromAddress(), email.subject().replace("--", "-"));
            Files.writeString(out.resolve(id + ".html"), header + email.html(), StandardCharsets.UTF_8);
            System.out.printf("%s  %-32s  %s%n", id, email.fromAddress(), email.subject());
        }
        System.out.printf("Saved %d emails to %s%n", ids.size(), out.toAbsolutePath());
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }
}
