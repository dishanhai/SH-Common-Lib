// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.announcement;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Block structure for announcement headings, anchors, and disclosures. */
final class AnnouncementMarkdown {
    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})[ \\t]+(.+)$");
    private static final Pattern DETAILS = Pattern.compile(
            "^<details(\\s+open(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))?)?\\s*>\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUMMARY = Pattern.compile("(?i)^<summary\\s*>");
    private static final Pattern SUMMARY_END = Pattern.compile("(?i)</summary\\s*>$");
    private static final Pattern ANCHOR = Pattern.compile(
            "^<a\\s+(?:id|name)\\s*=\\s*['\"]([^'\"]+)['\"]\\s*>\\s*</a>$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(\\x60{3,}|~{3,})(.*)$");
    private static final Pattern INLINE_LINK = Pattern.compile("!?\\[([^\\]]*)\\]\\([^)]*\\)");

    sealed interface Block permits Text, Heading, Anchor, Details {}
    record Text(String markdown, boolean fenced) implements Block {}
    record Heading(String markdown, int level, String anchor) implements Block {}
    record Anchor(String id) implements Block {}
    record Details(int id, String summary, boolean initiallyOpen, List<Block> children) implements Block {}

    private record Group(int id, String summary, boolean open, List<Block> blocks) {
        Details details() { return new Details(id, summary, open, List.copyOf(blocks)); }
    }

    static List<Block> parse(String markdown) {
        var groups = new ArrayDeque<Group>();
        groups.push(new Group(-1, "", true, new ArrayList<>()));
        Set<String> anchors = new HashSet<>();
        List<String> lines = markdown.lines().toList();
        int overflow = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String tag = line.strip();
            var blocks = groups.peek().blocks();
            var fence = FENCE.matcher(line);
            if (fence.matches()) {
                String marker = fence.group(1);
                var code = new StringBuilder(line);
                while (++i < lines.size()) {
                    String content = lines.get(i);
                    code.append('\n').append(content);
                    var end = FENCE.matcher(content);
                    if (end.matches() && end.group(1).startsWith(marker) && end.group(2).isBlank()) break;
                }
                blocks.add(new Text(code.toString(), true));
                continue;
            }
            var details = DETAILS.matcher(tag);
            var heading = HEADING.matcher(line);
            var anchor = ANCHOR.matcher(tag);
            if (details.matches() && groups.size() >= 32) {
                overflow++;
                blocks.add(new Text(line, false));
            } else if ("</details>".equalsIgnoreCase(tag) && overflow > 0) {
                overflow--;
                blocks.add(new Text(line, false));
            } else if (details.matches()) {
                int id = i;
                String summary = details.group(2);
                if (summary.isBlank()) {
                    int next = i + 1;
                    while (next < lines.size() && lines.get(next).isBlank()) next++;
                    if (next < lines.size() && SUMMARY.matcher(lines.get(next).strip()).find()) {
                        i = next;
                        summary = lines.get(i).strip();
                    }
                }
                var start = SUMMARY.matcher(summary);
                if (start.find()) {
                    var content = new StringBuilder(summary.substring(start.end()));
                    while (!SUMMARY_END.matcher(content).find() && i + 1 < lines.size()
                            && !lines.get(i + 1).strip().startsWith("</details")) {
                        content.append(' ').append(lines.get(++i).strip());
                    }
                    summary = SUMMARY_END.matcher(content).replaceFirst("").strip();
                }
                groups.push(new Group(id, summary, details.group(1) != null, new ArrayList<>()));
            } else if ("</details>".equalsIgnoreCase(tag) && groups.size() > 1) {
                var closed = groups.pop();
                groups.peek().blocks().add(closed.details());
            } else if (anchor.matches()) {
                String id = fragment(anchor.group(1));
                if (!id.isEmpty() && anchors.add(id)) blocks.add(new Anchor(id));
            } else if (heading.matches()) {
                String text = heading.group(2).replaceFirst("[ \\t]+#+[ \\t]*$", "").strip();
                String base = slug(INLINE_LINK.matcher(text).replaceAll("$1"));
                String id = base;
                for (int suffix = 1; !id.isEmpty() && !anchors.add(id); suffix++) id = base + "-" + suffix;
                blocks.add(new Heading(text, heading.group(1).length(), id));
            } else {
                blocks.add(new Text(line, false));
            }
        }
        while (groups.size() > 1) {
            var closed = groups.pop();
            groups.peek().blocks().add(closed.details());
        }
        return List.copyOf(groups.pop().blocks());
    }

    static String fragment(String value) {
        try {
            value = URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // Keep malformed percent escapes as literal text.
        }
        return slug(value);
    }

    private static String slug(String value) {
        var slug = new StringBuilder();
        value.strip().toLowerCase(Locale.ROOT).codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            if (Character.isLetterOrDigit(cp) || cp == '_' || cp == '-'
                    || (type == Character.NON_SPACING_MARK && cp != 0xFE0E && cp != 0xFE0F)) slug.appendCodePoint(cp);
            else if (Character.isWhitespace(cp)) slug.append('-');
        });
        return slug.toString().replaceAll("^-+|-+$", "");
    }
}
