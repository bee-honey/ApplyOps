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
 *   <li>The longest link text that is not a call to action is the title.
 *   <li>The card is the largest ancestor of the title link that links to no other job.
 *   <li>Text after the title, split on " · ", gives company then location.
 * </ol>
 *
 * <p>Email templates change; parsers are pinned by fixture tests built from real emails.
 */
abstract class CardAlertParser implements AlertParser {

    private static final Set<String> CALLS_TO_ACTION = Set.of(
            "apply", "apply now", "easy apply", "view job", "view", "see job", "see more", "save", "learn more");
    private static final Pattern SEPARATOR = Pattern.compile("\\s+[·•|]\\s+");

    /** Regex with one capture group: the provider's job id. */
    protected abstract Pattern jobIdPattern();

    /** Canonical URL for a job id (tracking parameters stripped). */
    protected abstract String canonicalUrl(String jobId, String rawHref);

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
            if (!isTitleCandidate(text)) {
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
            List<String> details = textsAfter(card(titleLink, id), titleLink);
            String company = details.size() > 0 ? details.get(0) : null;
            String location = details.size() > 1 ? details.get(1) : null;
            if (company == null || company.isBlank()) {
                continue; // Not enough information to dedup reliably.
            }
            jobs.add(new DiscoveredJob(
                    provider(), id, title, company, location, canonicalUrl(id, hrefs.get(id)), email.id()));
        }
        return jobs;
    }

    private String jobId(Element a) {
        Matcher m = jobIdPattern().matcher(a.attr("href"));
        return m.find() ? m.group(1) : null;
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

    /** Non-blank text fragments that come after the title link inside the card, split on " · ". */
    private static List<String> textsAfter(Element card, Element titleLink) {
        List<String> after = new ArrayList<>();
        boolean[] pastTitle = {false};
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (pastTitle[0] && node instanceof TextNode t && !t.isBlank()) {
                    after.add(t.text().strip());
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node == titleLink) {
                    pastTitle[0] = true;
                }
            }
        }, card);
        List<String> parts = new ArrayList<>();
        for (String t : after) {
            if (CALLS_TO_ACTION.contains(t.toLowerCase())) {
                continue;
            }
            for (String p : SEPARATOR.split(t)) {
                if (!p.isBlank()) {
                    parts.add(p.strip());
                }
            }
        }
        return parts;
    }
}
