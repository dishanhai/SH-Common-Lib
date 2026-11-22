// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.markdown;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/** Immutable, platform-independent Markdown content. Links carry data for the caller. */
public record MarkdownDocument(List<Block> blocks) {
    public MarkdownDocument {
        blocks = List.copyOf(blocks);
    }

    /** Returns visible text with LF separators, including the contents of closed disclosures. */
    public String plainText() {
        var result = new StringJoiner("\n");
        appendPlainText(result, blocks);
        return result.toString();
    }

    private static void appendPlainText(StringJoiner result, List<Block> blocks) {
        for (var block : blocks) {
            switch (block) {
                case Line line -> result.add(line.content().plainText());
                case Heading heading -> result.add(heading.content().plainText());
                case Quote quote -> result.add("│ " + quote.content().plainText());
                case ListItem item -> result.add(item.prefix() + item.content().plainText());
                case CodeBlock code -> result.add(code.text());
                case ThematicBreak ignored -> result.add("───");
                case Anchor ignored -> {}
                case Details details -> {
                    if (!details.summary().spans().isEmpty()) result.add(details.summary().plainText());
                    appendPlainText(result, details.children());
                }
            }
        }
    }

    public sealed interface Block permits Line, Heading, Quote, ListItem, CodeBlock, ThematicBreak, Anchor, Details {}

    /** A physical line of prose, including a blank line. */
    public record Line(Inline content) implements Block {
        public Line { Objects.requireNonNull(content, "content"); }
    }

    /** Anchor is empty when the heading has no usable fragment. */
    public record Heading(int level, Inline content, String anchor) implements Block {
        public Heading {
            if (level < 1 || level > 6) throw new IllegalArgumentException("Invalid Markdown heading level");
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(anchor, "anchor");
        }
    }

    public record Quote(Inline content) implements Block {
        public Quote { Objects.requireNonNull(content, "content"); }
    }

    /** checked is null for an ordinary list item. The marker preserves the source punctuation. */
    public record ListItem(int indent, String marker, Boolean checked, Inline content) implements Block {
        public ListItem {
            if (indent < 0) throw new IllegalArgumentException("Negative Markdown list indent");
            Objects.requireNonNull(marker, "marker");
            Objects.requireNonNull(content, "content");
        }

        public String prefix() {
            String bullet = switch (marker) {
                case "-", "+", "*" -> "• ";
                default -> marker.replace(')', '.') + " ";
            };
            String task = checked == null ? "" : checked ? "[x] " : "[ ] ";
            return " ".repeat(indent) + bullet + task;
        }
    }

    public record CodeBlock(String info, String text) implements Block {
        public CodeBlock {
            Objects.requireNonNull(info, "info");
            Objects.requireNonNull(text, "text");
        }
    }

    public record ThematicBreak() implements Block {}

    public record Anchor(String id) implements Block {
        public Anchor { Objects.requireNonNull(id, "id"); }
    }

    /** A disclosure's id is its zero-based opening line within the source. */
    public record Details(int id, Inline summary, boolean initiallyOpen, List<Block> children) implements Block {
        public Details {
            Objects.requireNonNull(summary, "summary");
            children = List.copyOf(children);
        }
    }

    public record Inline(List<Span> spans) {
        public Inline { spans = List.copyOf(spans); }

        public static Inline literal(String text) {
            return new Inline(text.isEmpty() ? List.of() : List.of(new Span(text, Set.of(), null)));
        }

        public String plainText() {
            var result = new StringBuilder();
            for (var span : spans) result.append(span.text());
            return result.toString();
        }
    }

    public enum Format { BOLD, ITALIC, STRIKETHROUGH, CODE }

    /** Link is null for an ordinary span. */
    public record Span(String text, Set<Format> formats, Link link) {
        public Span {
            Objects.requireNonNull(text, "text");
            formats = Set.copyOf(formats);
        }
    }

    public sealed interface Link permits WebLink, AnchorLink {}

    public record WebLink(URI uri) implements Link {
        public WebLink {
            if (!Markdown.isWebUri(Objects.requireNonNull(uri, "uri"))) {
                throw new IllegalArgumentException("Invalid Markdown web link");
            }
        }
    }

    public record AnchorLink(String id) implements Link {
        public AnchorLink { Objects.requireNonNull(id, "id"); }
    }
}
