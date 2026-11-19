package dev.applyops.orchestrator.discovery.parser;

import dev.applyops.orchestrator.discovery.model.AlertEmail;
import dev.applyops.orchestrator.discovery.model.DiscoveredJob;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * Shared heuristics for alert emails that render one "card" per job.
 *
 * <ol>
 *   <li>Every link whose href matches {@link #jobIdPattern()} belongs to that job id.
 *   <li>The title is the longest <em>innermost</em> job link that is not a call to action. Real
 *       templates wrap the whole card in an outer link, so "longest link" alone picks the card.
 *   <li>The card is the largest ancestor of the title link that links to no other job.
 *   <li>The card's other text, split into fragments before and after the title, goes to
 *       {@link #details} where each provider says where company and location sit.
 * </ol>
 *
 * <p>Email templates change; parsers are pinned by fixture tests built from real emails.
 */
abstract class CardAlertParser implements AlertParser {

    private static final Set<String> CALLS_TO_ACTION = Set.of(
            "apply", "apply now", "easy apply", "view job", "view", "see job", "see more", "save", "learn more");
    private static final Pattern SEPARATOR = Pattern.compile("\\s*[·•|]\\s+");

    /** Regex with one capture group: the provider's job id. */
    protected abstract Pattern jobIdPattern();

    /** Canonical URL for a job id (tracking parameters stripped). */
    protected abstract String canonicalUrl(String jobId, String rawHref);

    /**
     * Company and location from the card's text fragments (already split on " · ", calls to
     * action removed). Default: "Title / Company · Location".
     */
    protected Details details(List<String> before, List<String> after) {
        return new Details(at(after, 0), at(after, 1));
    }

    protected record Details(String company, String location) {}

    protected static String at(List<String> list, int i) {
        return i < list.size() ? list.get(i) : null;
    }

    @Override
    public List<DiscoveredJob> parse(AlertEmail email) {
        Document doc = Jsoup.parse(email.html());
        Map<String, Element> titleLinks = new LinkedHashMap<>();
        Map<String, String> hrefs = new LinkedHashMap<>();

        for (Element a : doc.select("a[href]")) {
            String id = jobId(a);
            if (id == null) {
                continue;
            }
            hrefs.putIfAbsent(id, a.attr("href"));
            String text = a.text().strip();
            if (!isTitleCandidate(text) || containsJobLink(a)) {
                continue;
            }
            Element best = titleLinks.get(id);
            if (best == null || text.length() > best.text().strip().length()) {
                titleLinks.put(id, a);
            }
        }

        List<DiscoveredJob> jobs = new ArrayList<>();
        for (var e : titleLinks.entrySet()) {
            String id = e.getKey();
            Element titleLink = e.getValue();
            String title = titleLink.text().strip();
            List<String> before = new ArrayList<>();
            List<String> after = new ArrayList<>();
            fragments(card(titleLink, id), titleLink, before, after);
            Details d = details(before, after);
            if (d.company() == null || d.company().isBlank()) {
                continue; // Not enough information to dedup reliably.
            }
            jobs.add(new DiscoveredJob(
                    provider(), id, title, d.company(), d.location(), canonicalUrl(id, hrefs.get(id)), email.id()));
        }
        return jobs;
    }

    private String jobId(Element a) {
        Matcher m = jobIdPattern().matcher(a.attr("href"));
        return m.find() ? m.group(1) : null;
    }

    private boolean containsJobLink(Element a) {
        for (Element inner : a.select("a[href]")) {
            if (inner != a && jobId(inner) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTitleCandidate(String text) {
        return text.length() >= 3 && !CALLS_TO_ACTION.contains(text.toLowerCase());
    }

    /** Walk up while the parent still only links to this job. */
    private Element card(Element link, String id) {
        Element card = link;
        while (card.parent() != null && onlyLinksTo(card.parent(), id)) {
            card = card.parent();
        }
        return card;
    }

    private boolean onlyLinksTo(Element el, String id) {
        if (el.tagName().equals("body")) {
            return false;
        }
        for (Element a : el.select("a[href]")) {
            String other = jobId(a);
            if (other != null && !other.equals(id)) {
                return false;
            }
        }
        return true;
    }

    /** Non-blank text fragments of the card outside the title link, split on " · ". */
    private static void fragments(Element card, Element titleLink, List<String> before, List<String> after) {
        int[] state = {0}; // 0 = before title, 1 = inside, 2 = after
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node == titleLink) {
                    state[0] = 1;
                } else if (state[0] != 1 && node instanceof TextNode t && !t.isBlank()) {
                    split(t.text().strip(), state[0] == 0 ? before : after);
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node == titleLink) {
                    state[0] = 2;
                }
            }
        }, card);
    }

    private static void split(String text, List<String> into) {
        if (CALLS_TO_ACTION.contains(text.toLowerCase())) {
            return;
        }
        for (String p : SEPARATOR.split(text)) {
            if (!p.isBlank()) {
                into.add(p.strip());
            }
        }
    }
}
