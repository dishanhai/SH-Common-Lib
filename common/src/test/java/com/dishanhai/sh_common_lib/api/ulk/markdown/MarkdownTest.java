// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.markdown;

import com.dishanhai.sh_common_lib.api.ulk.UnifiedLibKernel;
import com.dishanhai.sh_common_lib.api.ulk.markdown.MarkdownDocument.*;

import java.util.List;
import java.util.Set;

public final class MarkdownTest {
    public static void main(String[] args) {
        parsesDocumentStructure();
        parsesInlineStylesAndEscapes();
        keepsLinksPassive();
        preservesAnnouncementAnchorsAndDetails();
        handlesMalformedInputAndLimits();
        keepsResultsImmutable();
        System.out.println("MarkdownTest: 6 scenarios passed");
    }

    private static void parsesDocumentStructure() {
        var document = UnifiedLibKernel.parseMarkdown(
                "# 标题 ##\r\n\r\n正文\r> 引用\n  2) [x] 完成\n- 条目\n- - -\n"
                        + "~~~java\n  **literal**\n\n~~~~\n尾行");
        var blocks = document.blocks();
        check(blocks.size() == 9, "block count");
        var heading = (Heading) blocks.get(0);
        check(heading.level() == 1 && heading.anchor().equals("标题"), "heading level and anchor");
        check(((Line) blocks.get(1)).content().spans().isEmpty(), "blank line");
        check(((Quote) blocks.get(3)).content().plainText().equals("引用"), "quote");
        var task = (ListItem) blocks.get(4);
        check(task.indent() == 2 && task.marker().equals("2)") && Boolean.TRUE.equals(task.checked()), "ordered task");
        check(((ListItem) blocks.get(5)).checked() == null, "ordinary list item");
        check(blocks.get(6) instanceof ThematicBreak, "thematic break precedes list");
        var code = (CodeBlock) blocks.get(7);
        check(code.info().equals("java") && code.text().equals("  **literal**\n"), "literal fenced code");
        check(document.plainText().equals("标题\n\n正文\n│ 引用\n  2. [x] 完成\n• 条目\n───\n  **literal**\n\n尾行"),
                "whole-document text projection");
    }

    private static void parsesInlineStylesAndEscapes() {
        var content = Markdown.parseInline("**粗体 _斜体_** / ~~删除~~ / `a*b` / \\* / file_name_id");
        check(content.plainText().equals("粗体 斜体 / 删除 / a*b / * / file_name_id"), "visible text");
        check(content.spans().contains(new Span("斜体", Set.of(Format.BOLD, Format.ITALIC), null)), "nested styles");
        check(content.spans().contains(new Span("删除", Set.of(Format.STRIKETHROUGH), null)), "strike style");
        check(content.spans().contains(new Span("a*b", Set.of(Format.CODE), null)), "literal code style");
        check(Markdown.parseInline("`` `**` ``").plainText().equals("`**`"), "matching multi-backtick code");
    }

    private static void keepsLinksPassive() {
        var spans = Markdown.parseInline("[**网站**](https://example.com/a(b)) [目录](#%E7%AB%A0)"
                + " [命令](javascript:alert(1)) [凭据](https://user@example.com) ![图片](https://example.com/img)"
                + " <https://example.org> <b>字面</b> `[原样](#章)`").spans();
        check(spans.stream().filter(span -> span.link() instanceof WebLink).count() == 2, "allowed web targets");
        check(spans.contains(new Span("目录", Set.of(), new AnchorLink("章"))), "decoded local fragment");
        check(spans.contains(new Span("[原样](#章)", Set.of(Format.CODE), null)), "code has no link metadata");
        check(spans.stream().anyMatch(span -> span.link() == null && span.text().contains("命令")), "unsupported link label");
        check(spans.stream().anyMatch(span -> span.link() == null && span.text().contains("图片")), "image alt text");
        check(spans.stream().anyMatch(span -> span.link() == null && span.text().contains("<b>字面</b>")), "literal HTML");
    }

    private static void preservesAnnouncementAnchorsAndDetails() {
        var document = UnifiedLibKernel.parseMarkdown("""
                <a name="重复"></a>
                ## 重复
                <details open>
                <summary>**更多**
                信息</summary>
                ## 重复
                <details>
                ## [更新 内容](#更新-内容)
                </details>
                </details>
                ## _兼容_
                [跳转](#_兼容_)
                """);
        var blocks = document.blocks();
        check(blocks.get(0).equals(new Anchor("重复")), "explicit anchor");
        check(((Heading) blocks.get(1)).anchor().equals("重复-1"), "anchor collision");
        var outer = (Details) blocks.get(2);
        check(outer.id() == 2 && outer.initiallyOpen() && outer.summary().plainText().equals("更多 信息"), "disclosure");
        check(((Heading) outer.children().get(0)).anchor().equals("重复-2"), "unique heading inside disclosure");
        var inner = (Details) outer.children().get(1);
        check(!inner.initiallyOpen() && ((Heading) inner.children().get(0)).anchor().equals("更新-内容"), "nested disclosure");
        var styled = (Heading) blocks.get(3);
        check(styled.anchor().equals("_兼容_") && styled.content().plainText().equals("兼容"), "legacy styled heading anchor");
        var jump = (Line) blocks.get(4);
        check(jump.content().spans().get(0).link().equals(new AnchorLink(styled.anchor())), "legacy styled heading link");
    }

    private static void handlesMalformedInputAndLimits() {
        check(UnifiedLibKernel.parseMarkdown("").blocks().isEmpty(), "empty source");
        check(Markdown.parseInline("*未闭合 [链接]( `code").plainText().equals("*未闭合 [链接]( `code"), "unclosed inline");
        check(UnifiedLibKernel.parseMarkdown("```\n## 代码").plainText().equals("## 代码"), "unclosed fence consumes to EOF");
        expect(IllegalArgumentException.class, () -> UnifiedLibKernel.parseMarkdown("a".repeat(Markdown.MAX_LENGTH + 1)));
        String escapes = "\\*".repeat(20_000);
        check(Markdown.parseInline(escapes).plainText().equals("*".repeat(20_000)), "long escape sequence");
        check(Markdown.parseInline("**" + escapes + "a**").spans().equals(
                List.of(new Span("*".repeat(20_000) + "a", Set.of(Format.BOLD), null))), "long styled escape sequence");
    }

    private static void keepsResultsImmutable() {
        var document = UnifiedLibKernel.parseMarkdown("<details>**摘要**\n正文\n</details>");
        var details = (Details) document.blocks().get(0);
        expect(UnsupportedOperationException.class, () -> document.blocks().clear());
        expect(UnsupportedOperationException.class, () -> details.children().clear());
        expect(UnsupportedOperationException.class, () -> details.summary().spans().clear());
        expect(UnsupportedOperationException.class, () -> details.summary().spans().get(0).formats().clear());
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            if (type.isInstance(failure)) return;
            throw failure;
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
