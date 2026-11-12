// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.announcement;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Adds SHBUI's bitmap icons to the logical runs before Minecraft wraps and hit-tests them. */
final class AnnouncementIcons {
    private static final String NAMESPACE = "sh_common_lib_announcement";
    private static final ResourceLocation INLINE_FONT = ResourceLocation.tryParse(NAMESPACE + ":inline_icons");
    private static final ResourceLocation QUOTED_FONT = ResourceLocation.tryParse(NAMESPACE + ":quoted_icons");
    private static final Map<String, Glyph> AUTOMATIC = automaticGlyphs();
    private static final Map<String, Glyph> QUOTED = quotedGlyphs();
    private static final Map<Integer, String> NAMES = glyphNames();
    private static final Pattern TOKENS = Pattern.compile(
            "https?://[^\\s<>，。；、！？]+|<quote:[ \\t]*([^<>\\r\\n]+)>|(?<![A-Za-z0-9_])("
                    + AUTOMATIC.keySet().stream()
                            .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                            .map(Pattern::quote).collect(Collectors.joining("|"))
                    + ")(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);

    private AnnouncementIcons() {}

    static Component decorate(Component source) {
        var text = new StringBuilder();
        var runs = new ArrayList<Run>();
        source.visit((style, part) -> {
            if (!part.isEmpty()) {
                runs.add(new Run(text.length(), part, style));
                text.append(part);
            }
            return Optional.empty();
        }, Style.EMPTY);

        var decorations = decorations(text);
        if (decorations.isEmpty()) return source;

        var result = Component.empty();
        int index = 0;
        int consumed = 0;
        for (var run : runs) {
            int end = run.start() + run.text().length();
            int start = Math.max(consumed, run.start());
            while (index < decorations.size() && decorations.get(index).start() < end) {
                var decoration = decorations.get(index++);
                if (decoration.start() > start) result.append(run.slice(start, decoration.start()));
                var glyph = decoration.glyph();
                var iconStyle = run.style().withFont(glyph.font()).withBold(false).withItalic(false)
                        .withUnderlined(false).withStrikethrough(false).withObfuscated(false).withInsertion(null);
                result.append(Component.literal(glyph.text()).setStyle(iconStyle));
                start = decoration.end();
                consumed = start;
            }
            if (start < end) result.append(run.slice(start, end));
        }
        return result;
    }

    private static ArrayList<Decoration> decorations(CharSequence text) {
        var result = new ArrayList<Decoration>();
        var matcher = TOKENS.matcher(text);
        while (matcher.find()) {
            String reference = matcher.group(1);
            String term = matcher.group(2);
            if (reference != null) {
                var glyph = QUOTED.get(reference.strip().toLowerCase(Locale.ROOT));
                if (glyph != null) result.add(new Decoration(matcher.start(), matcher.end(), glyph));
            } else if (term != null) {
                var glyph = AUTOMATIC.get(term.toLowerCase(Locale.ROOT));
                if (glyph != null) result.add(new Decoration(matcher.start(), matcher.start(), glyph));
            }
        }
        return result;
    }

    static boolean isIcon(Style style) {
        return INLINE_FONT.equals(style.getFont()) || QUOTED_FONT.equals(style.getFont());
    }

    static String name(int codePoint) {
        return NAMES.getOrDefault(codePoint, "");
    }

    private static Map<String, Glyph> automaticGlyphs() {
        var glyphs = loadGlyphs(INLINE_FONT);
        alias(glyphs, "github_issue", "issue", "issues", "github issues");
        alias(glyphs, "github_repo", "github repository", "github repositories", "github 仓库");
        alias(glyphs, "github_pull_request", "pull request", "pull requests", "github pull requests");
        alias(glyphs, "github_discussion", "github discussions");
        alias(glyphs, "github_release", "github releases");
        alias(glyphs, "neoforge", "neo forge", "neo-forge");
        alias(glyphs, "ae2", "applied energistics 2", "应用能源2", "应用能源 2");
        alias(glyphs, "jei", "just enough items");
        alias(glyphs, "mekanism", "通用机械");
        alias(glyphs, "gregtech", "gregtech ceu", "gtceu", "格雷科技");
        alias(glyphs, "ftb_quests", "ftbquests");
        alias(glyphs, "shbui", "sh-better-ui", "sh better ui");
        alias(glyphs, "mcmod", "mc 百科", "mc百科");
        alias(glyphs, "afdian", "爱发电");
        alias(glyphs, "bilibili", "哔哩哔哩");
        alias(glyphs, "wechat", "微信");
        alias(glyphs, "file_java", "java");
        return Map.copyOf(glyphs);
    }

    private static Map<String, Glyph> quotedGlyphs() {
        var glyphs = loadGlyphs(QUOTED_FONT);
        glyphs.putAll(AUTOMATIC);
        alias(glyphs, "input", "输入", "intput");
        alias(glyphs, "output", "输出");
        alias(glyphs, "settings", "设置");
        return Map.copyOf(glyphs);
    }

    private static Map<Integer, String> glyphNames() {
        var names = new HashMap<Integer, String>();
        for (var glyph : QUOTED.values()) names.put(glyph.text().codePointAt(0), glyph.name());
        return Map.copyOf(names);
    }

    private static HashMap<String, Glyph> loadGlyphs(ResourceLocation font) {
        var glyphs = new HashMap<String, Glyph>();
        try (var resource = AnnouncementIcons.class.getResourceAsStream(
                "/assets/" + font.getNamespace() + "/font/" + font.getPath() + ".json")) {
            if (resource == null) throw new IOException("Missing announcement icon font " + font);
            var reader = new InputStreamReader(resource, StandardCharsets.UTF_8);
            var providers = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("providers");
            for (var entry : providers) {
                var provider = entry.getAsJsonObject();
                String file = provider.get("file").getAsString();
                String id = file.substring(file.lastIndexOf('/') + 1, file.lastIndexOf('.'));
                String text = provider.getAsJsonArray("chars").get(0).getAsString();
                var glyph = new Glyph(text, font, id.replace('_', ' '));
                glyphs.put(id, glyph);
                glyphs.put(glyph.name(), glyph);
            }
        } catch (IOException | JsonParseException error) {
            System.getLogger(AnnouncementIcons.class.getName()).log(
                    System.Logger.Level.WARNING, "Could not load announcement icons", error);
        }
        return glyphs;
    }

    private static void alias(Map<String, Glyph> glyphs, String icon, String... names) {
        var glyph = glyphs.get(icon);
        if (glyph != null) {
            for (String name : names) glyphs.put(name, glyph);
        }
    }

    private record Glyph(String text, ResourceLocation font, String name) {}
    private record Decoration(int start, int end, Glyph glyph) {}
    private record Run(int start, String text, Style style) {
        Component slice(int from, int to) {
            return Component.literal(text.substring(from - start, to - start)).setStyle(style);
        }
    }
}
