// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.announcement;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;

/** Converts the inline syntax used by release notes into native styled runs. */
final class AnnouncementInlineText {
    static final Identifier ANCHOR = Identifier.fromNamespaceAndPath("sh_common_lib", "announcement_anchor");
    private static final int MAX_LENGTH = 65_536;
    private static final Style CODE = Style.EMPTY.withColor(ChatFormatting.GRAY);
    private static final Pattern PREFIX = Pattern.compile("^( {0,3}> ?| *(?:[-+*]|[0-9]{1,9}[.)])[ \\t]+)");
    private static final Pattern TOKENS = Pattern.compile(
            "\\\\(?<escaped>\\p{Punct})|(?<ticks>\u0060++)(?<code>.+?)\\k<ticks>(?!\u0060)"
                    + "|(?<image>!)?\\[(?<label>[^\\[\\]]++)\\]\\((?<target>(?:[^\\s()<>]++|\\([^\\s()<>]*+\\))++)\\)"
                    + "|<(?<address>(?i:https?)://[^\\s<>]++)>|</?[A-Za-z][^<>]*+>"
                    + "|(?<![\\p{L}\\p{N}_])(?<underscores>_{1,3})(?<underscored>[^_]+?)\\k<underscores>(?![\\p{L}\\p{N}_])"
                    + "|(?<!\\*)(?<stars>\\*{1,3})(?<starred>[^*]+?)\\k<stars>(?!\\*)"
                    + "|~~(?<strike>[^~]+?)~~");

    private AnnouncementInlineText() {}

    static MutableComponent parse(String line) {
        if (line.length() > MAX_LENGTH) return Component.literal(line);
        var result = Component.empty();
        var prefix = PREFIX.matcher(line);
        if (prefix.lookingAt()) {
            String mark = prefix.group().stripTrailing();
            String bullet = mark.stripLeading();
            result.append(switch (bullet) {
                case ">" -> "│ ";
                case "-", "+", "*" -> " ".repeat(mark.length() - bullet.length()) + "• ";
                default -> mark.replace(')', '.') + " ";
            });
            line = line.substring(prefix.end());
        }
        append(result, line, Style.EMPTY, 0);
        return result;
    }

    static Component codeBlock(String source) {
        if (source.length() > MAX_LENGTH) return Component.literal(source);
        var lines = source.lines().toList();
        String fence = lines.getFirst().stripLeading();
        int length = 0;
        while (length < fence.length() && fence.charAt(length) == fence.charAt(0)) length++;
        String marker = fence.substring(0, length);
        String last = lines.getLast().strip();
        int end = lines.size();
        if (end > 1 && last.startsWith(marker) && last.chars().allMatch(ch -> ch == marker.charAt(0))) end--;
        return Component.literal(String.join("\n", lines.subList(1, end))).setStyle(CODE);
    }

    private static void append(MutableComponent result, String source, Style style, int depth) {
        if (depth >= 8) {
            literal(result, source, style);
            return;
        }
        var token = TOKENS.matcher(source);
        int start = 0;
        while (token.find()) {
            literal(result, source.substring(start, token.start()), style);
            if (token.group("escaped") != null) {
                literal(result, token.group("escaped"), style);
            } else if (token.group("code") != null) {
                literal(result, token.group("code"), style.applyTo(CODE));
            } else if (token.group("label") != null) {
                Style link = token.group("image") == null ? linkStyle(style, token.group("target")) : style;
                append(result, token.group("label"), link, depth + 1);
            } else if (token.group("address") != null) {
                String address = token.group("address");
                literal(result, address, linkStyle(style, address));
            } else if (token.group("strike") != null) {
                append(result, token.group("strike"), style.withStrikethrough(true), depth + 1);
            } else if (token.group("stars") != null || token.group("underscores") != null) {
                boolean stars = token.group("stars") != null;
                int length = token.group(stars ? "stars" : "underscores").length();
                Style emphasis = length == 1 ? style.withItalic(true) : style.withBold(true);
                if (length == 3) emphasis = emphasis.withItalic(true);
                append(result, token.group(stars ? "starred" : "underscored"), emphasis, depth + 1);
            } else {
                literal(result, token.group(), style);
            }
            start = token.end();
        }
        literal(result, source.substring(start), style);
    }

    private static void literal(MutableComponent result, String text, Style style) {
        if (!text.isEmpty()) result.append(Component.literal(text).setStyle(style));
    }

    private static Style linkStyle(Style style, String target) {
        if (style.getClickEvent() != null) return style;
        if (target.startsWith("#")) {
            String anchor = AnnouncementMarkdown.fragment(target.substring(1));
            if (!anchor.isEmpty()) return style.withUnderlined(true).withClickEvent(
                    new ClickEvent.Custom(ANCHOR, Optional.of(StringTag.valueOf(anchor))));
        }
        var uri = webUri(target);
        return uri == null ? style : style.withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(uri));
    }

    static URI webUri(String target) {
        try {
            var uri = URI.create(target);
            if (uri.getHost() != null && uri.getUserInfo() == null && ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))) return uri;
        } catch (IllegalArgumentException ignored) {
            // Keep malformed links as text.
        }
        return null;
    }
}
