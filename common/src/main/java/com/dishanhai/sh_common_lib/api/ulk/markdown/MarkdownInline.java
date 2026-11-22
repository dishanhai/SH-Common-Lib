// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.markdown;

import com.dishanhai.sh_common_lib.api.ulk.markdown.MarkdownDocument.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownInline {
    private static final int MAX_NESTING = 8;
    private static final String PUNCTUATION = "\\p{Punct}";
    private static final String DESTINATION = "(?:[^\\s()<>]++|\\([^\\s()<>]*+\\))++";
    private static final Pattern TOKENS = Pattern.compile(
            "(?<escape>\\\\" + PUNCTUATION + ")"
                    + "|(?<!\\x60)(?<ticks>\\x60++)(?<code>.+?)(?<!\\x60)\\k<ticks>(?!\\x60)"
                    + "|(?<image>!)?\\[(?<label>[^\\[\\]\\r\\n]++)\\]\\((?<target>" + DESTINATION + ")\\)"
                    + "|<(?<address>(?i:https?)://[^\\s<>]++)>"
                    + "|</?[A-Za-z][^<>\\r\\n]*+>"
                    + emphasis("***") + emphasis("___") + emphasis("**") + emphasis("__")
                    + emphasis("*") + emphasis("_") + emphasis("~~"));

    private MarkdownInline() {}

    static Inline parse(String source) {
        var result = new Runs();
        append(result, source, EnumSet.noneOf(Format.class), null, true, 0);
        result.flush();
        return new Inline(result.spans);
    }

    private static void append(Runs result, String source, EnumSet<Format> formats, Link link, boolean links, int depth) {
        if (depth >= MAX_NESTING) {
            result.add(source, formats, link);
            return;
        }
        var token = TOKENS.matcher(source);
        int start = 0;
        while (token.find()) {
            result.add(source.substring(start, token.start()), formats, link);
            if (token.group("escape") != null) {
                result.add(token.group().substring(1), formats, link);
            } else if (token.group("code") != null) {
                String code = token.group("code");
                if (code.startsWith(" ") && code.endsWith(" ") && !code.isBlank()) {
                    code = code.substring(1, code.length() - 1);
                }
                var codeFormats = formats.clone();
                codeFormats.add(Format.CODE);
                result.add(code, codeFormats, link);
            } else if (token.group("label") != null) {
                Link target = links && token.group("image") == null ? target(token.group("target")) : link;
                append(result, token.group("label"), formats, target, false, depth + 1);
            } else if (token.group("address") != null) {
                String address = token.group("address");
                Link target = links ? target(address) : link;
                result.add(target == null && links ? token.group() : address, formats, target);
            } else if (token.group().startsWith("<")) {
                result.add(token.group(), formats, link);
            } else {
                appendEmphasis(result, token, formats, link, links, depth);
            }
            start = token.end();
        }
        result.add(source.substring(start), formats, link);
    }

    private static void appendEmphasis(Runs result, Matcher token, EnumSet<Format> formats, Link link,
                                       boolean links, int depth) {
        String mark = token.group();
        char marker = mark.charAt(0);
        int length = 1;
        while (length < 3 && mark.charAt(length) == marker) length++;
        var emphasis = formats.clone();
        if (marker == '~') emphasis.add(Format.STRIKETHROUGH);
        else {
            if (length >= 2) emphasis.add(Format.BOLD);
            if (length != 2) emphasis.add(Format.ITALIC);
        }
        append(result, mark.substring(length, mark.length() - length), emphasis, link, links, depth + 1);
    }

    private static Link target(String value) {
        if (value.startsWith("#")) {
            String fragment = Markdown.fragment(value.substring(1));
            return fragment.isEmpty() ? null : new AnchorLink(fragment);
        }
        return Markdown.webUri(value).map(WebLink::new).orElse(null);
    }

    private static String emphasis(String marker) {
        String character = Pattern.quote(marker.substring(0, 1));
        String boundary = marker.startsWith("_") ? "[\\p{L}\\p{N}_]" : character;
        String delimiter = Pattern.quote(marker);
        return "|(?<!" + boundary + ")" + delimiter + "(?=\\S)"
                + "(?:\\\\" + PUNCTUATION + "|[^" + character + "\\\\\\r\\n]++|\\\\(?!" + PUNCTUATION + "))++"
                + "(?<=\\S)" + delimiter + "(?!" + boundary + ")";
    }

    private static final class Runs {
        private final ArrayList<Span> spans = new ArrayList<>();
        private final StringBuilder pending = new StringBuilder();
        private EnumSet<Format> formats;
        private Link link;

        void add(String text, EnumSet<Format> formats, Link link) {
            if (text.isEmpty()) return;
            if (!formats.equals(this.formats) || !Objects.equals(link, this.link)) {
                flush();
                this.formats = formats;
                this.link = link;
            }
            pending.append(text);
        }

        void flush() {
            if (pending.isEmpty()) return;
            spans.add(new Span(pending.toString(), formats, link));
            pending.setLength(0);
        }
    }
}
