// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.markdown;

import com.dishanhai.sh_common_lib.api.ulk.markdown.MarkdownDocument.*;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Parses a passive, line-oriented Markdown subset using only the JDK. */
public final class Markdown {
    public static final int MAX_LENGTH = 65_536;
    private static final int MAX_DETAILS_DEPTH = 31;
    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})(?:[ \t]+(.*)|$)");
    private static final Pattern HEADING_END = Pattern.compile("(?:^|(?<![ \t])[ \t]++)#++[ \t]*+$");
    private static final Pattern HEADING_LINK = Pattern.compile("!?\\[([^\\[\\]]*+)\\]\\(");
    private static final Pattern QUOTE = Pattern.compile("^ {0,3}>[ \t]?(.*)$");
    private static final Pattern LIST = Pattern.compile("^( *)([-+*]|[0-9]{1,9}[.)])[ \t]+(.*)$");
    private static final Pattern TASK = Pattern.compile("^\\[([ xX])\\](?:[ \t]+(.*)|$)");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(\\x60{3,}+|~{3,}+)(.*)$");
    private static final Pattern DETAILS = Pattern.compile(
            "^<details(\\s+open(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))?)?\\s*>\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUMMARY = Pattern.compile("(?i)^<summary\\s*>");
    private static final Pattern SUMMARY_END = Pattern.compile("(?i)</summary\\s*>$");
    private static final Pattern ANCHOR = Pattern.compile(
            "^<a\\s+(?:id|name)\\s*=\\s*['\"]([^'\"]+)['\"]\\s*>\\s*</a>$", Pattern.CASE_INSENSITIVE);

    private Markdown() {}

    /**
     * Parses headings, lines, list items, quotes, fences, rules, and announcement disclosures/anchors.
     * @throws IllegalArgumentException if the source exceeds {@link #MAX_LENGTH} UTF-16 code units
     */
    public static MarkdownDocument parse(String source) {
        checkLength(source);
        return new Parser(source.lines().toList()).parse();
    }

    /**
     * Parses inline content without consuming any block markers.
     * @throws IllegalArgumentException if the source exceeds {@link #MAX_LENGTH} UTF-16 code units
     */
    public static Inline parseInline(String source) {
        checkLength(source);
        return MarkdownInline.parse(source.replace("\r\n", "\n").replace('\r', '\n'));
    }

    /** Normalizes a fragment without its leading #, using the same slug rules as headings. */
    public static String fragment(String value) {
        try {
            value = URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // Keep malformed percent escapes as literal text before normalization.
        }
        return slug(value);
    }

    /** Checks an absolute HTTP(S) address, including links supplied outside the Markdown body. */
    public static Optional<URI> webUri(String target) {
        try {
            var uri = URI.create(target);
            if (isWebUri(uri)) return Optional.of(uri);
        } catch (IllegalArgumentException ignored) {
            // Malformed links remain text.
        }
        return Optional.empty();
    }

    static boolean isWebUri(URI uri) {
        return uri.getHost() != null && uri.getUserInfo() == null
                && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
    }

    private static void checkLength(String source) {
        Objects.requireNonNull(source, "source");
        if (source.length() > MAX_LENGTH) throw new IllegalArgumentException("Markdown input is too large");
    }

    private static String slug(String value) {
        var result = new StringBuilder();
        value.strip().toLowerCase(Locale.ROOT).codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            if (Character.isLetterOrDigit(cp) || cp == '_' || cp == '-'
                    || (type == Character.NON_SPACING_MARK && cp != 0xFE0E && cp != 0xFE0F)) result.appendCodePoint(cp);
            else if (Character.isWhitespace(cp)) result.append('-');
        });
        int start = 0;
        int end = result.length();
        while (start < end && result.charAt(start) == '-') start++;
        while (end > start && result.charAt(end - 1) == '-') end--;
        return result.substring(start, end);
    }

    /** Keep existing announcement fragments, including underscores in emphasized headings. */
    private static String headingSlug(String title) {
        var link = HEADING_LINK.matcher(title);
        var visible = new StringBuilder();
        int start = 0;
        while (link.find()) {
            int end = title.indexOf(')', link.end());
            if (end < 0) break;
            visible.append(title, start, link.start()).append(link.group(1));
            start = end + 1;
            link.region(start, title.length());
        }
        return slug(visible.append(title, start, title.length()).toString());
    }

    private static boolean isRule(String line) {
        int index = 0;
        while (index < line.length() && line.charAt(index) == ' ') index++;
        if (index > 3 || index == line.length()) return false;
        char marker = line.charAt(index);
        if (marker != '-' && marker != '*' && marker != '_') return false;
        int count = 0;
        for (; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == marker) count++;
            else if (current != ' ' && current != '\t') return false;
        }
        return count >= 3;
    }

    private record Group(int id, Inline summary, boolean open, List<Block> blocks) {}

    private static final class Parser {
        private final List<String> lines;
        private final ArrayDeque<Group> groups = new ArrayDeque<>();
        private final HashSet<String> anchors = new HashSet<>();
        private final HashMap<String, Integer> nextSuffix = new HashMap<>();
        private int disclosureOverflow;
        private int index;

        Parser(List<String> lines) {
            this.lines = lines;
            groups.push(new Group(-1, Inline.literal(""), true, new ArrayList<>()));
        }

        MarkdownDocument parse() {
            for (; index < lines.size(); index++) parseLine(lines.get(index));
            while (groups.size() > 1) closeDetails();
            return new MarkdownDocument(groups.pop().blocks());
        }

        private void parseLine(String line) {
            var fence = FENCE.matcher(line);
            if (fence.matches() && (fence.group(1).charAt(0) != '\u0060' || !fence.group(2).contains("\u0060"))) {
                add(readFence(fence.group(1), fence.group(2).strip()));
                return;
            }
            String tag = line.strip();
            var details = DETAILS.matcher(tag);
            if (details.matches()) {
                if (groups.size() > MAX_DETAILS_DEPTH) {
                    disclosureOverflow++;
                    add(new Line(Inline.literal(line)));
                } else {
                    int id = index;
                    var summary = readSummary(details.group(2));
                    groups.push(new Group(id, MarkdownInline.parse(summary), details.group(1) != null, new ArrayList<>()));
                }
                return;
            }
            if ("</details>".equalsIgnoreCase(tag) && groups.size() > 1) {
                if (disclosureOverflow == 0) closeDetails();
                else {
                    disclosureOverflow--;
                    add(new Line(Inline.literal(line)));
                }
                return;
            }
            var anchor = ANCHOR.matcher(tag);
            if (anchor.matches()) {
                String id = fragment(anchor.group(1));
                if (!id.isEmpty() && anchors.add(id)) add(new Anchor(id));
                return;
            }
            add(textBlock(line));
        }

        private Block textBlock(String line) {
            var heading = HEADING.matcher(line);
            if (heading.matches()) {
                String source = Objects.requireNonNullElse(heading.group(2), "");
                String title = HEADING_END.matcher(source).replaceFirst("").strip();
                return new Heading(heading.group(1).length(), MarkdownInline.parse(title), reserveAnchor(headingSlug(title)));
            }
            if (isRule(line)) return new ThematicBreak();
            var quote = QUOTE.matcher(line);
            if (quote.matches()) return new Quote(MarkdownInline.parse(quote.group(1)));
            var list = LIST.matcher(line);
            if (list.matches()) {
                String source = list.group(3);
                var task = TASK.matcher(source);
                Boolean checked = null;
                if (task.matches()) {
                    checked = !task.group(1).equals(" ");
                    source = Objects.requireNonNullElse(task.group(2), "");
                }
                return new ListItem(list.group(1).length(), list.group(2), checked, MarkdownInline.parse(source));
            }
            return new Line(MarkdownInline.parse(line));
        }

        private CodeBlock readFence(String marker, String info) {
            var code = new StringBuilder();
            int first = ++index;
            for (; index < lines.size(); index++) {
                String line = lines.get(index);
                var close = FENCE.matcher(line);
                if (close.matches() && close.group(1).startsWith(marker) && close.group(2).isBlank()) break;
                if (index > first) code.append('\n');
                code.append(line);
            }
            return new CodeBlock(info, code.toString());
        }

        private String readSummary(String summary) {
            if (summary.isBlank()) {
                int next = index + 1;
                while (next < lines.size() && lines.get(next).isBlank()) next++;
                if (next < lines.size() && SUMMARY.matcher(lines.get(next).strip()).find()) {
                    index = next;
                    summary = lines.get(index).strip();
                }
            }
            var start = SUMMARY.matcher(summary);
            if (!start.find()) return summary;
            String part = summary.substring(start.end());
            var content = new StringBuilder(part);
            while (!SUMMARY_END.matcher(part).find() && index + 1 < lines.size()
                    && !lines.get(index + 1).strip().equalsIgnoreCase("</details>")) {
                part = lines.get(++index).strip();
                content.append(' ').append(part);
            }
            return SUMMARY_END.matcher(content).replaceFirst("").strip();
        }

        private String reserveAnchor(String base) {
            if (base.isEmpty() || anchors.add(base)) return base;
            int suffix = nextSuffix.getOrDefault(base, 1);
            String id;
            do id = base + "-" + suffix++; while (!anchors.add(id));
            nextSuffix.put(base, suffix);
            return id;
        }

        private void closeDetails() {
            var closed = groups.pop();
            add(new Details(closed.id(), closed.summary(), closed.open(), closed.blocks()));
        }

        private void add(Block block) {
            groups.peek().blocks().add(block);
        }
    }
}
