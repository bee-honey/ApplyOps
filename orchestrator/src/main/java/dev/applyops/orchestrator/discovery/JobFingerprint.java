package dev.applyops.orchestrator.discovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Source-independent identity for a posting: sha256(normalized company | normalized title).
 *
 * <p>Location is deliberately excluded: providers format it differently ("San Francisco, CA" vs
 * "SF Bay Area"), and the same req listed in several locations is one application anyway.
 */
public final class JobFingerprint {

    private static final Pattern PARENS = Pattern.compile("\\([^)]*\\)|\\[[^]]*]");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9+# ]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern COMPANY_SUFFIX =
            Pattern.compile("\\s+(inc|llc|ltd|limited|corp|corporation|co|gmbh|plc|pbc|sa|ag)$");
    private static final Pattern TITLE_REMOTE_SUFFIX = Pattern.compile("\\s+-\\s+(remote|hybrid|onsite)$");

    private JobFingerprint() {}

    public static String of(String company, String title) {
        String key = normalizeCompany(company) + "|" + normalizeTitle(title);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String normalizeCompany(String company) {
        String s = base(company).replace("&", " and ");
        s = clean(s);
        String prev;
        do {
            prev = s;
            s = COMPANY_SUFFIX.matcher(s).replaceAll("");
        } while (!s.equals(prev));
        return s;
    }

    static String normalizeTitle(String title) {
        String s = base(title);
        s = TITLE_REMOTE_SUFFIX.matcher(s).replaceAll("");
        s = PARENS.matcher(s).replaceAll(" ");
        s = s.replaceAll("\\bsr\\.?(?=\\s|$)", "senior").replaceAll("\\bjr\\.?(?=\\s|$)", "junior");
        return clean(s);
    }

    private static String base(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).strip();
    }

    private static String clean(String s) {
        s = NON_WORD.matcher(s).replaceAll(" ");
        return SPACES.matcher(s).replaceAll(" ").strip();
    }
}
