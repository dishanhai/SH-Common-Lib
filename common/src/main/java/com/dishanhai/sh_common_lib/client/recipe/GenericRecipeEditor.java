// Author: dishanhai
package com.dishanhai.sh_common_lib.client.recipe;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiWorkbench;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCodeEditor;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScrollingRows;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextField;
import com.dishanhai.sh_common_lib.api.ulk.recipe.RecipeJsonTree;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static com.dishanhai.sh_common_lib.client.recipe.RecipeEditor.builder;
import static com.dishanhai.sh_common_lib.client.recipe.RecipeEditor.button;
import static com.dishanhai.sh_common_lib.client.recipe.RecipeEditor.line;
import static com.dishanhai.sh_common_lib.client.recipe.RecipeEditor.text;

/** A local edit session. Only Apply publishes the edited recipe to document history. */
final class GenericRecipeEditor {
    private static final int LIMIT = 1_048_576;
    private final UiScreen parent;
    private final Consumer<String> apply;
    private final String original;
    private String source;
    private RecipeJsonTree tree;

    private GenericRecipeEditor(UiScreen parent, String json, Consumer<String> apply) {
        this.parent = parent;
        this.apply = apply;
        this.original = this.source = json.isEmpty() ? "{\"type\":\"\"}" : json;
    }

    static void open(UiScreen parent, String json, Consumer<String> apply) {
        var session = new GenericRecipeEditor(parent, json, apply);
        try {
            session.tree = RecipeJsonTree.parse(session.source);
        } catch (IllegalArgumentException unreadable) {
            session.sourceEditor(true);
            return;
        }
        session.browse(List.of(), 0);
    }

    static void openField(UiScreen parent, String json, String key, Consumer<String> apply) {
        var session = new GenericRecipeEditor(parent, json, apply);
        try {
            session.tree = RecipeJsonTree.parse(session.source);
        } catch (IllegalArgumentException invalid) {
            session.sourceEditor(true);
            return;
        }
        var keys = session.tree.keys(List.of());
        int index = keys.indexOf(key);
        if (index >= 0 && session.tree.isContainer(List.of(key))) session.browse(List.of(key), 0);
        else {
            session.focusKey = key;
            session.browse(List.of(), Math.max(0, index));
        }
    }

    private record Pending(String text, boolean plain) {
        String json() { return plain ? new JsonPrimitive(text).toString() : text; }
    }

    private final Map<List<String>, Pending> pending = new LinkedHashMap<>();
    private List<String> invalidPath;
    private long previewAfter;
    private String focusKey;

    /** Parse settled values once, and flush immediately before navigation or Apply. */
    private boolean flush() {
        previewAfter = Long.MAX_VALUE;
        if (pending.isEmpty()) return true;
        var next = tree;
        for (var entry : pending.entrySet()) {
            try { next = next.replace(entry.getKey(), entry.getValue().json()); }
            catch (IllegalArgumentException invalid) { invalidPath = entry.getKey(); return false; }
        }
        pending.clear();
        invalidPath = null;
        update(next);
        return true;
    }

    private void browse(List<String> path, int offset) {
        new Table(path, offset).open();
    }

    private final class Table {
        private final List<String> path;
        private int offset;
        private final List<String> keys;
        private final Map<List<String>, UiTextField> inputs = new LinkedHashMap<>();
        private int x, width, tableWidth, keyWidth, rows, start, end;
        private boolean split;
        private RecipeJsonTree shown;
        private List<String> displayedError;
        private Component[] preview = {};
        private boolean previewLight, previewContrast;
        private Component heading;

        Table(List<String> path, int offset) {
            this.path = path;
            this.offset = offset;
            keys = tree.keys(path);
        }

        void open() {
            builder(text("type.custom"), parent).onInit(this::init).onForeground(this::draw).open();
        }

        void init(UiScreen owner) {
            inputs.clear();
            displayedError = null;
            shown = null;
            x = Math.max(10, (owner.width - 880) / 2);
            width = owner.width - x * 2;
            split = width >= 510;
            tableWidth = split ? Math.round(width * 0.59f) : width;
            keyWidth = Math.min(142, Math.max(70, tableWidth / 3));
            rows = Math.max(1, (owner.height - 108) / 24);
            if (focusKey != null) {
                int focused = keys.indexOf(focusKey);
                if (focused >= 0) offset = Math.min(focused, Math.max(offset, focused - rows + 1));
            }
            heading = path.isEmpty() ? text("generic.fields") : Component.literal(String.join(" › ", path));
            owner.onCloseRequest(() -> {
                if (!path.isEmpty()) {
                    if (flush()) {
                        var up = path.subList(0, path.size() - 1);
                        browse(up, Math.max(0, tree.keys(up).indexOf(path.get(path.size() - 1))));
                    } else confirmDiscard(() -> { pending.clear(); invalidPath = null; browse(path.subList(0, path.size() - 1), 0); });
                    return false;
                }
                if (source.equals(original) && pending.isEmpty()) return true;
                confirmDiscard(parent::open);
                return false;
            });
            button(owner, x, 8, 24, 22, text("back"), owner::onClose).setIcon(GraphicIcon.BACK);
            button(owner, x + width - 132, 8, 76, 22, text("generic.open_source"), () -> {
                if (flush()) sourceEditor(false);
            }).setLeadingIcon(GraphicIcon.CODE);
            button(owner, x + width - 52, 8, 52, 22, text("apply"), () -> {
                if (flush()) apply.accept(source);
            }).setSelected(true);
            button(owner, x + tableWidth - 48, 41, 48, 18, text("generic.add"), () -> {
                if (flush()) addField(owner, path, start);
            }).setLeadingIcon(GraphicIcon.ADD);
            var list = new UiScrollingRows(owner, new Bounds(x, 73, tableWidth, rows * 24), heading,
                    24, rows, keys.size(), offset, first -> {
                offset = start = first;
                end = Math.min(keys.size(), start + rows);
                inputs.clear();
                displayedError = null;
                for (int i = start; i < end; i++) row(owner, keys.get(i), 73 + (i - start) * 24);
            });
            owner.onScroll((mouseX, mouseY, horizontal, vertical) -> list.scroll(mouseX, mouseY, vertical));
            focusKey = null;
        }

        private void row(UiScreen owner, String key, int y) {
            var child = child(path, key);
            boolean container = tree.isContainer(child);
            int contentWidth = tableWidth - UiScrollingRows.TRACK_SPACE;
            int vx = x + keyWidth, vw = contentWidth - keyWidth - 28;
            var edit = button(owner, x + contentWidth - 22, y + 1, 22, 19, text("generic.field", key), () -> {
                if (flush()) member(path, key, start, null);
            });
            edit.setIcon(GraphicIcon.EDIT);
            edit.setCornerRadius(4);
            if (container) {
                var label = text(tree.isArray(child) ? "generic.elements" : "generic.members", tree.childCount(child));
                var open = button(owner, vx, y + 1, vw, 19, label, () -> { if (flush()) browse(child, 0); });
                open.setTrailingIcon(GraphicIcon.FORWARD);
                open.setPreview(text("generic.field", key));
                return;
            }
            String json = tree.value(child);
            boolean plain = json.startsWith("\"");
            String value = plain ? tree.stringValue(child) : json;
            if (value.length() > 4096 || value.contains("\n") || value.contains("\r")) {
                button(owner, vx, y + 1, vw, 19, text("generic.long_text"), () -> {
                    if (flush()) member(path, key, start, null);
                });
                return;
            }
            var field = owner.add(new UiTextField(Minecraft.getInstance().font, new Bounds(vx, y, vw, 21),
                    text("generic.field", key), 4096, UiWorkbench.preferences(owner)));
            field.setAppearance(UiTextField.Appearance.QUIET);
            field.setValue(pending.getOrDefault(child, new Pending(value, plain)).text());
            field.setOnChange(next -> {
                pending.put(child, new Pending(next, plain));
                invalidPath = null;
                previewAfter = System.nanoTime() + 220_000_000L;
            });
            field.setOnSubmit(ignored -> flush());
            inputs.put(child, field);
            if (key.equals(focusKey)) owner.setFocused(field);
        }

        void draw(UiScreen owner, UiFrame frame) {
            if (System.nanoTime() >= previewAfter) flush();
            var theme = UiTheme.current();
            int ink = UiWorkbench.textColor(), muted = UiWorkbench.secondaryTextColor();
            var font = Minecraft.getInstance().font;
            int available = width - 174;
            frame.drawScaledText(font, UiRichText.fit(font, text("type.custom").copy().withStyle(s -> s.withBold(true)),
                    (int)(available / 1.25f)), x + 32, 12, ink, 1.25f);
            RoundedCorners.draw(frame.graphics(), x, 35, width, 1, 0, 0, 0, theme.border());
            line(frame, heading.copy().withStyle(s -> s.withBold(true)), x + 2, 45, tableWidth - 58, ink);
            line(frame, text("generic.key"), x + 4, 62, keyWidth - 8, muted);
            line(frame, text("generic.column_value"), x + keyWidth + 4, 62, tableWidth - keyWidth - 12, muted);
            for (int i = start; i < end; i++) {
                int y = 73 + (i - start) * 24;
                String key = keys.get(i);
                line(frame, Component.literal(key), x + 4, y + 6, keyWidth - 8, ink);
                RoundedCorners.draw(frame.graphics(), x + 3, y + 22, tableWidth - UiScrollingRows.TRACK_SPACE - 6, 1, 0, 0, 0, theme.border());
            }
            if (!Objects.equals(displayedError, invalidPath)) {
                displayedError = invalidPath;
                inputs.forEach((key, field) -> field.setError(key.equals(invalidPath) ? text("generic.invalid_value") : null));
            }
            if (keys.isEmpty()) line(frame, text("generic.empty"), x + 4, 80, tableWidth - 12, muted);
            int foot = 80 + Math.max(1, end - start) * 24;
            var count = keys.size() > rows ? text("generic.range", start + 1, end, keys.size())
                    : text("generic.total", keys.size());
            line(frame, invalidPath == null ? count : text("generic.invalid_value"), x + 4, foot,
                    tableWidth - 8, invalidPath == null ? muted : ink);
            if (split) drawPreview(owner, frame, ink, muted);
        }

        private void drawPreview(UiScreen owner, UiFrame frame, int ink, int muted) {
            boolean light = UiTheme.current().light(), contrast = frame.preferences().highContrast();
            if (shown != tree || previewLight != light || previewContrast != contrast) {
                shown = tree;
                previewLight = light;
                previewContrast = contrast;
                preview = source.length() <= 65536 ? tree.formattedValue(path).lines()
                        .limit(Math.max(1, owner.height / 12))
                        .map(line -> jsonLine(line, light, contrast)).toArray(Component[]::new) : new Component[0];
            }
            int sx = x + tableWidth + 16, sw = width - tableWidth - 16;
            line(frame, text("generic.preview").copy().withStyle(s -> s.withBold(true)), sx, 45, sw, ink);
            int lines = Math.min(preview.length, Math.max(1, (owner.height - 86) / 12));
            int h = Math.max(44, lines * 12 + 16);
            RoundedCorners.draw(frame.graphics(), sx, 60, sw, h, 7, 0, 0, UiTheme.current().surface());
            for (int i = 0; i < lines; i++) {
                int y = 68 + i * 12;
                line(frame, Component.literal(Integer.toString(i + 1)), sx + 6, y, 23, muted);
                line(frame, preview[i], sx + 30, y, sw - 36, ink);
            }
            if (preview.length == 0) line(frame, text("generic.large"), sx + 8, 68, sw - 16, muted);
        }
    }

    /** Pretty JSON emits one member per line. Distinguish its key, string and numeric values. */
    private static Component jsonLine(String line, boolean light, boolean contrast) {
        if (contrast) return Component.literal(line);
        int start = line.length() - line.stripLeading().length();
        int keyEnd = start;
        if (start < line.length() && line.charAt(start) == '"') {
            for (int i = start + 1; i < line.length(); i++) {
                if (line.charAt(i) == '\\') i++;
                else if (line.charAt(i) == '"') {
                    if (line.startsWith(":", i + 1)) keyEnd = i + 1;
                    break;
                }
            }
        }
        var result = Component.literal(line.substring(0, start));
        if (keyEnd > start) {
            result.append(Component.literal(line.substring(start, keyEnd))
                    .withStyle(style -> style.withColor(light ? 0x514498 : 0xBDCBFD)));
            result.append(": ");
            start = keyEnd + 2;
        }
        String value = line.substring(start);
        if (value.isEmpty()) return result;
        char first = value.charAt(0);
        if (first == '"') return result.append(Component.literal(value)
                .withStyle(style -> style.withColor(light ? 0x23664F : 0xA7DDC3)));
        if (first == '-' || Character.isDigit(first) || Character.isLetter(first))
            return result.append(Component.literal(value).withStyle(style -> style.withColor(light ? 0x844B13 : 0xEDC69B)));
        return result.append(value);
    }

    private void addField(UiScreen previous, List<String> path, int offset) {
        String[] kinds = {"string", "number", "boolean", "object", "array", "null"};
        String[] examples = {"\"\"", "0", "true", "{}", "[]", "null"};
        UiWorkbench.popup(text("generic.add"), previous, 340, 148).onInit(owner -> {
            var panel = UiWorkbench.popupBounds(owner, 340, 148);
            int x = panel.x() + 12, w = panel.width() - 24;
            for (int i = 0; i < kinds.length; i++) {
                String value = examples[i];
                button(owner, x + (i % 2) * (w / 2), panel.y() + 39 + (i / 2) * 24, w / 2 - 4, 20,
                        text("generic.kind." + kinds[i]), () -> member(path, null, offset, value));
            }
            button(owner, x, panel.bottom() - 30, w, 20, text("back"), owner::onClose);
        }).onForeground((owner, frame) -> {
            var panel = UiWorkbench.popupBounds(owner, 340, 148);
            line(frame, text("generic.kind"), panel.x() + 12, panel.y() + 25,
                    panel.width() - 24, UiWorkbench.textColor());
        }).open();
    }

    private void member(List<String> path, String key, int page, String seed) {
        boolean adding = key == null;
        boolean nameNeeded = adding && !tree.isArray(path);
        boolean serializer = path.isEmpty() && "type".equals(key);
        String value = adding ? seed : tree.value(child(path, key));
        boolean plain = value.startsWith("\"");
        String initial = plain ? adding ? "" : tree.stringValue(child(path, key)) : value;
        Component valueLabel = text(plain ? serializer ? "generic.serializer" : "generic.kind.string" : "generic.value");
        String[] input = {initial}, name = {""};
        Component[] feedback = {Component.empty()};
        Component title = adding ? text("generic.add") : text("generic.field", key);
        builder(title, parent).onInit(owner -> {
            int x = left(owner), w = owner.width - 2 * x;
            owner.onCloseRequest(() -> {
                Runnable back = () -> browse(path, page);
                if (initial.equals(input[0]) && name[0].isEmpty()) back.run();
                else confirmDiscard(back);
                return false;
            });
            button(owner, x, 8, 64, 22, text("back"), owner::onClose);
            if (nameNeeded) {
                var field = owner.add(new UiTextField(Minecraft.getInstance().font, new Bounds(x, 39, w, 20),
                        text("generic.key"), 4096, UiWorkbench.preferences(owner)));
                field.setHint(text("generic.key")); field.setValue(name[0]); field.setOnChange(next -> name[0] = next);
            }
            int y = nameNeeded ? 77 : 52;
            code(owner, x, y, w, owner.height - y - 50, input, valueLabel);
            int half = (w - 4) / 2;
            if (!adding) button(owner, x, owner.height - 28, half, 20, text("generic.remove"), () -> {
                update(tree.remove(child(path, key)));
                browse(path, page);
            });
            button(owner, x + half + 4, owner.height - 28, w - half - 4, 20, text("generic.done"), () -> {
                if (nameNeeded && (name[0].isEmpty() || tree.keys(path).contains(name[0]))) {
                    feedback[0] = text("generic.invalid_key");
                    return;
                }
                try {
                    String json = plain ? new JsonPrimitive(input[0]).toString() : input[0];
                    var next = adding ? tree.add(path, nameNeeded ? name[0] : null, json) : tree.replace(child(path, key), json);
                    if (adding || !input[0].equals(initial)) update(next);
                    if (adding) focusKey = nameNeeded ? name[0] : Integer.toString(tree.childCount(path) - 1);
                    browse(path, adding ? tree.childCount(path) - 1 : page);
                } catch (IllegalArgumentException invalid) { feedback[0] = text("generic.invalid_value"); }
            }).setSelected(true);
        }).onForeground((owner, frame) -> {
            int x = left(owner), w = owner.width - x * 2;
            line(frame, title, x + 72, 15, w - 72, UiWorkbench.textColor());
            line(frame, valueLabel, x, nameNeeded ? 64 : 39, w, UiWorkbench.secondaryTextColor());
            line(frame, feedback[0], x, owner.height - 44, w, UiWorkbench.accentColor());
        }).open();
    }

    private void sourceEditor(boolean unreadable) {
        String[] input = {source};
        Component[] feedback = {unreadable ? text("generic.invalid_source") : Component.empty()};
        builder(text("generic.source"), parent).onInit(owner -> {
            int x = left(owner), w = owner.width - x * 2;
            owner.onCloseRequest(() -> {
                Runnable back = tree == null ? parent::open : () -> browse(List.of(), 0);
                if (source.equals(input[0])) back.run(); else confirmDiscard(back);
                return false;
            });
            button(owner, x, 8, 64, 22, text("back"), owner::onClose);
            UiCodeEditor editor = code(owner, x, 36, w, owner.height - 86, input, text("json"));
            button(owner, x + 70, 8, w - 70, 22, text("paste_json"), () -> {
                String pasted = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (pasted.length() > LIMIT) { feedback[0] = text("too_large"); return; }
                input[0] = pasted; editor.setValue(pasted);
            });
            int half = (w - 4) / 2;
            button(owner, x, owner.height - 28, half, 20, text("generic_edit"), () -> {
                try {
                    tree = RecipeJsonTree.parse(input[0]); source = input[0]; browse(List.of(), 0);
                } catch (IllegalArgumentException invalid) { feedback[0] = text("generic.invalid_source"); }
            });
            button(owner, x + half + 4, owner.height - 28, w - half - 4, 20, text("apply"), () -> apply.accept(input[0])).setSelected(true);
        }).onForeground((owner, frame) -> line(frame, feedback[0], left(owner), owner.height - 43,
                owner.width - 2 * left(owner), UiWorkbench.accentColor())).open();
    }

    private static UiCodeEditor code(UiScreen owner, int x, int y, int w, int h, String[] value, Component label) {
        var editor = owner.add(new UiCodeEditor(Minecraft.getInstance().font, new Bounds(x, y, w, h), label,
                LIMIT, UiWorkbench.preferences(owner)));
        editor.setValue(value[0]); editor.setOnChange(next -> value[0] = next);
        return editor;
    }

    private void update(RecipeJsonTree next) { tree = next; source = tree.json(); }

    private static void confirmDiscard(Runnable discard) {
        RecipeEditor.confirm(text("unsaved"), text("generic.discard"), text("discard"), discard);
    }

    private static int left(UiScreen owner) { return Math.max(12, (owner.width - 540) / 2); }

    private static List<String> child(List<String> path, String key) {
        var result = new ArrayList<>(path);
        result.add(key);
        return List.copyOf(result);
    }
}
