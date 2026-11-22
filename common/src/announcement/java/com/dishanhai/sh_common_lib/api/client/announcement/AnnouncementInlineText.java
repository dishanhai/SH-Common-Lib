// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.announcement;

import com.dishanhai.sh_common_lib.api.ulk.UnifiedLibKernel;
import com.dishanhai.sh_common_lib.api.ulk.markdown.Markdown;
import com.dishanhai.sh_common_lib.api.ulk.markdown.MarkdownDocument.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;

/** Projects ULK's passive Markdown model into the announcement widget's native text. */
final class AnnouncementInlineText {
    private static final Style CODE = Style.EMPTY.withColor(ChatFormatting.GRAY);

    private AnnouncementInlineText() {}

    static List<Block> blocks(String markdown) {
        if (markdown.length() <= Markdown.MAX_LENGTH) return UnifiedLibKernel.parseMarkdown(markdown).blocks();
        return markdown.lines().<Block>map(line -> new Line(Inline.literal(line))).toList();
    }

    static MutableComponent body(Block block) {
        return switch (block) {
            case Line line -> render(line.content());
            case Quote quote -> Component.literal("│ ").append(render(quote.content()));
            case ListItem list -> Component.literal(list.prefix()).append(render(list.content()));
            case CodeBlock code -> Component.literal(code.text()).setStyle(CODE);
            case ThematicBreak ignored -> Component.literal("───");
            default -> throw new IllegalArgumentException("Expected a Markdown body block");
        };
    }

    static MutableComponent render(Inline inline) {
        var result = Component.empty();
        for (var span : inline.spans()) {
            var formats = span.formats();
            var style = formats.contains(Format.CODE) ? CODE : Style.EMPTY;
            if (formats.contains(Format.BOLD)) style = style.withBold(true);
            if (formats.contains(Format.ITALIC)) style = style.withItalic(true);
            if (formats.contains(Format.STRIKETHROUGH)) style = style.withStrikethrough(true);
            if (span.link() != null) style = style.withUnderlined(true).withClickEvent(AnnouncementPlatform.link(span.link()));
            result.append(Component.literal(span.text()).setStyle(style));
        }
        return result;
    }
}
