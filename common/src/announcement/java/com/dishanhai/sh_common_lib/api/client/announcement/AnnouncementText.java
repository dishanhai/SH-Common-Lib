// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.announcement;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.dishanhai.sh_common_lib.api.client.announcement.FullscreenAnnouncements.label;

final class AnnouncementText extends AbstractWidget {
    private static final int TRACK = 9;
    private static final int INSET = 7;
    private static final int ICON_SPACE = GraphicIcon.PIXELS + 5;
    private static final int MARKDOWN_LIMIT = 65_536;
    private static final Pattern LOCAL_LINK = Pattern.compile(
            "(?<!\\x60)(\\x60+).*?\\1(?!\\x60)|(?<![!\\\\])\\[([^\\]\\r\\n]+)\\]\\(#([^\\s)]+)\\)");

    private final UiScreen owner;
    private final Font font = Minecraft.getInstance().font;
    private final Bounds area;
    private final List<Row> rows = new ArrayList<>();
    private final Map<Action, Hit> actions = new LinkedHashMap<>();
    private final Map<String, List<AnnouncementMarkdown.Details>> ancestors = new HashMap<>();
    private final Map<String, Integer> anchorPositions = new HashMap<>();
    private final Map<Integer, Boolean> expanded = new HashMap<>();
    private final List<AnnouncementMarkdown.Block> blocks;
    private final List<AnnouncementCatalog.Link> links;
    private final boolean english;
    private final Supplier<AnnouncementText> current;
    private final Predicate<String> otherAnchor;
    private final int lineHeight = font.lineHeight + 3;
    private int height;
    private int offset;
    private Action selected;
    private int dragOffset = -1;

    AnnouncementText(UiScreen owner, Bounds area, Component title, String markdown,
                     List<AnnouncementCatalog.Link> links, boolean english, AnnouncementText previous,
                     Supplier<AnnouncementText> current, Predicate<String> otherAnchor) {
        super(area.x(), area.y(), area.width(), area.height(), title);
        this.owner = owner;
        this.area = area;
        this.links = links;
        this.english = english;
        this.current = current;
        this.otherAnchor = otherAnchor;
        blocks = AnnouncementMarkdown.parse(markdown);
        indexAnchors(blocks, new ArrayList<>());
        if (previous != null) {
            expanded.putAll(previous.expanded);
            offset = previous.offset;
            selected = previous.selected;
        }
        rebuildRows();
    }

    private void indexAnchors(List<AnnouncementMarkdown.Block> content, List<AnnouncementMarkdown.Details> parents) {
        for (var block : content) {
            switch (block) {
                case AnnouncementMarkdown.Heading heading -> ancestors.put(heading.anchor(), List.copyOf(parents));
                case AnnouncementMarkdown.Anchor anchor -> ancestors.put(anchor.id(), List.copyOf(parents));
                case AnnouncementMarkdown.Details details -> {
                    parents.add(details);
                    indexAnchors(details.children(), parents);
                    parents.removeLast();
                }
                case AnnouncementMarkdown.Text ignored -> {}
            }
        }
    }

    boolean jumpTo(String anchor) {
        var parents = ancestors.get(anchor);
        if (parents == null || anchor.isEmpty()) return false;
        boolean changed = false;
        for (var parent : parents) {
            if (!isOpen(parent)) {
                expanded.put(parent.id(), true);
                changed = true;
            }
        }
        if (changed) rebuildRows();
        scrollTo(anchorPositions.get(anchor) - INSET);
        return true;
    }

    private void rebuildRows() {
        rows.clear();
        actions.clear();
        anchorPositions.clear();
        height = INSET;
        add(AnnouncementIcons.decorate(getMessage().copy().withStyle(style -> style.withBold(true))), 1.65f, 0, GraphicIcon.INFO, Tone.TITLE, null);
        height += 7;
        for (var link : links) {
            // Older announcements often repeat the address as the label. Keep the description readable.
            String name = link.label(english);
            String prefix = link.url() + " - ";
            if (name.startsWith(prefix)) name = name.substring(prefix.length());
            var text = Component.literal(name).withStyle(style -> style.withUnderlined(true));
            add(AnnouncementIcons.decorate(text), 1, 0, GraphicIcon.LINK, Tone.BODY, new Action(Kind.WEB, link.url(), -1));
        }
        if (!links.isEmpty()) height += 8;
        layout(blocks, 0);
        height += INSET;
        if (!actions.containsKey(selected)) selected = null;
        scrollTo(offset);
    }

    private void layout(List<AnnouncementMarkdown.Block> content, int indent) {
        for (var block : content) {
            switch (block) {
                case AnnouncementMarkdown.Anchor anchor -> anchorPositions.put(anchor.id(), height);
                case AnnouncementMarkdown.Heading heading -> {
                    height += 4;
                    if (!heading.anchor().isEmpty()) anchorPositions.put(heading.anchor(), height);
                    float scale = heading.level() == 1 ? 1.4f : heading.level() == 2 ? 1.25f : 1.1f;
                    add(AnnouncementIcons.decorate(inline(heading.markdown()).withStyle(style -> style.withBold(true))), scale, indent,
                            heading.level() <= 2 ? GraphicIcon.BOOKMARK : null, Tone.HEADING, null);
                    height += 2;
                }
                case AnnouncementMarkdown.Details details -> {
                    boolean open = isOpen(details);
                    var action = new Action(Kind.DETAILS, "", details.id());
                    var title = details.summary().isBlank() ? label("details") : inline(details.summary());
                    add(AnnouncementIcons.decorate(title.copy().withStyle(style -> style.withBold(true))), 1, indent,
                            open ? GraphicIcon.COLLAPSE : GraphicIcon.EXPAND, Tone.HEADING, action);
                    if (open) layout(details.children(), Math.min(indent + 14, Math.max(0, area.width() / 4)));
                }
                case AnnouncementMarkdown.Text text -> {
                    if (text.markdown().isBlank()) height += 5;
                    else add(text.fenced() ? markdown(text.markdown()) : AnnouncementIcons.decorate(inline(text.markdown())),
                            1, indent, null, Tone.BODY, null);
                }
            }
        }
    }

    private boolean isOpen(AnnouncementMarkdown.Details details) {
        return expanded.computeIfAbsent(details.id(), ignored -> details.initiallyOpen());
    }

    private static MutableComponent markdown(String source) {
        return source.length() <= MARKDOWN_LIMIT ? ShBetterUiApi.markdown().parse(source) : Component.literal(source);
    }

    private static MutableComponent inline(String source) {
        var result = Component.empty();
        var matcher = LOCAL_LINK.matcher(source);
        int start = 0;
        while (matcher.find()) {
            if (matcher.group(2) == null) continue;
            result.append(markdown(source.substring(start, matcher.start())));
            String id = AnnouncementMarkdown.fragment(matcher.group(3));
            var action = new ClickEvent(ClickEvent.Action.CHANGE_PAGE, id);
            result.append(markdown(matcher.group(2)).withStyle(style -> style.withUnderlined(true).withClickEvent(action)));
            start = matcher.end();
        }
        return result.append(markdown(source.substring(start)));
    }

    private void add(Component text, float scale, int indent, GraphicIcon icon, Tone tone, Action fallback) {
        int x = area.x() + INSET + indent;
        if (icon != null && textRight() - x > 2 * ICON_SPACE) x += ICON_SPACE;
        else icon = null;
        int width = Math.max(1, (int) ((textRight() - x - INSET) / scale));
        for (var line : font.split(text, width)) {
            int rowHeight = Math.max((int) Math.ceil(font.lineHeight * scale) + 3, icon == null ? 0 : 18);
            var row = new Row(line, x, height, rowHeight, scale, icon, tone, fallback);
            rows.add(row);
            collectActions(row);
            height += rowHeight;
            icon = null;
        }
    }

    private void collectActions(Row row) {
        var labels = new LinkedHashMap<Action, StringBuilder>();
        var iconNames = new HashMap<Action, String>();
        row.text().accept((index, style, cp) -> {
            var action = row.fallback() != null ? row.fallback() : fromStyle(style);
            if (action != null) {
                var text = labels.computeIfAbsent(action, ignored -> new StringBuilder());
                if (AnnouncementIcons.isIcon(style)) iconNames.putIfAbsent(action, AnnouncementIcons.name(cp));
                else text.appendCodePoint(cp);
            }
            return true;
        });
        labels.forEach((action, text) -> {
            String name = text.toString();
            actions.putIfAbsent(action, new Hit(row, name.isBlank() ? iconNames.getOrDefault(action, name) : name));
        });
    }

    private static Action fromStyle(Style style) {
        if (style == null || style.getClickEvent() == null) return null;
        var event = style.getClickEvent();
        return switch (event.getAction()) {
            case OPEN_URL -> new Action(Kind.WEB, event.getValue(), -1);
            case CHANGE_PAGE -> new Action(Kind.ANCHOR, event.getValue(), -1);
            default -> null;
        };
    }

    private int textRight() { return area.right() - TRACK - 2; }

    private int maxScroll() { return Math.max(0, height - area.height()); }

    private boolean scrollTo(int next) {
        int clamped = Math.max(0, Math.min(maxScroll(), next));
        if (clamped == offset) return false;
        offset = clamped;
        return true;
    }

    private boolean hasTrack() { return area.width() > 2 * TRACK && area.height() > 18 && maxScroll() > 0; }

    private int thumbHeight() {
        return Math.min(area.height() - 1, Math.max(16, (int) ((long) area.height() * area.height() / height)));
    }

    private int thumbTop() {
        return area.y() + (int) ((long) (area.height() - thumbHeight()) * offset / maxScroll());
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (area.height() < 3 || textRight() <= area.x() + 1) return;
        var preferences = owner.context().preferences();
        var focus = new DarkModeTheme.State(0, 0, 0, isFocused(), true,
                preferences.reducedMotion(), preferences.highContrast());
        DarkModeTheme.input(graphics, area.x(), area.y(), area.width(), area.height(), focus);
        Action hovered = hit(mouseX, mouseY);
        int foreground = contrast(LiquidGlassTheme.textColor());
        int accent = preferences.highContrast() ? foreground : contrast(LiquidGlassTheme.accentColor());
        graphics.enableScissor(area.x() + 1, area.y() + 1, textRight(), area.bottom() - 1);
        try {
            for (var row : rows) {
                int y = area.y() + row.y() - offset;
                if (y + row.height() <= area.y()) continue;
                if (y >= area.bottom()) break;
                Action active = isFocused() ? selected : null;
                if (row.fallback() != null && row.fallback().kind() == Kind.DETAILS) {
                    LiquidGlassTheme.suggestionRow(graphics, row.x() - (row.icon() == null ? 0 : ICON_SPACE),
                            y, textRight() - row.x() + (row.icon() == null ? 0 : ICON_SPACE), row.height(),
                            row.fallback().equals(active), row.fallback().equals(hovered), preferences);
                }
                int color = row.tone() == Tone.HEADING ? accent : foreground;
                if (row.icon() != null) row.icon().draw(graphics, row.x() - ICON_SPACE,
                        y + (row.height() - GraphicIcon.PIXELS) / 2, 1, accent, preferences);
                FormattedCharSequence ink = sink -> row.text().accept((index, style, cp) -> {
                    var action = row.fallback() == null ? fromStyle(style) : row.fallback();
                    if (action != null && action.kind() != Kind.DETAILS) {
                        int linkColor = action.equals(hovered) || action.equals(active) ? foreground : accent;
                        style = style.withColor(linkColor & 0xFFFFFF).withUnderlined(!AnnouncementIcons.isIcon(style));
                    }
                    return sink.accept(index, style, cp);
                });
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(row.x(), y + (row.height() - font.lineHeight * row.scale()) / 2, 0);
                    graphics.pose().scale(row.scale(), row.scale(), 1);
                    ShBetterUiApi.graphics().drawFormattedText(graphics, font, ink, 0, 0, color, false, preferences);
                } finally {
                    graphics.pose().popPose();
                }
            }
        } finally {
            graphics.disableScissor();
        }
        if (hasTrack()) {
            boolean hover = mouseX >= textRight() && area.contains(mouseX, mouseY);
            var thumb = new DarkModeTheme.State(hover ? 1 : 0, hover ? 1 : 0, dragOffset >= 0 ? 1 : 0,
                    false, true, preferences.reducedMotion(), preferences.highContrast());
            DarkModeTheme.dragHandle(graphics, area.right() - TRACK - 1, thumbTop(), TRACK, thumbHeight(), thumb);
        }
    }

    private static int contrast(int preferred) {
        double fg = luminance(preferred);
        double bg = luminance(DarkModeTheme.surfaceColor());
        if ((Math.max(fg, bg) + .05) / (Math.min(fg, bg) + .05) >= 4.5) return preferred | 0xFF000000;
        return bg > .179 ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static double luminance(int color) {
        return .2126 * channel(color >> 16) + .7152 * channel(color >> 8) + .0722 * channel(color);
    }

    private static double channel(int value) {
        double normalized = (value & 255) / 255d;
        return normalized <= .04045 ? normalized / 12.92 : Math.pow((normalized + .055) / 1.055, 2.4);
    }

    private Action hit(double x, double y) {
        if (!area.contains(x, y) || x >= textRight()) return null;
        double position = y - area.y() + offset;
        for (var row : rows) {
            if (position < row.y()) return null;
            if (position >= row.y() + row.height()) continue;
            int left = row.x() - (row.icon() == null ? 0 : ICON_SPACE);
            if (row.fallback() != null && x >= left && (row.fallback().kind() == Kind.DETAILS
                    || x < row.x() + font.width(row.text()) * row.scale())) return row.fallback();
            if (x < row.x() || x >= row.x() + font.width(row.text()) * row.scale()) return null;
            int width = (int) ((x - row.x()) / row.scale());
            return fromStyle(font.getSplitter().componentStyleAtWidth(row.text(), width));
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (!area.contains(x, y)) return false;
        var action = hit(x, y);
        if (action != null && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                || (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action.kind() == Kind.WEB))) {
            selected = action;
            activate(button == GLFW.GLFW_MOUSE_BUTTON_LEFT);
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (hasTrack() && x >= textRight()) {
            int top = thumbTop();
            dragOffset = y >= top && y < top + thumbHeight() ? (int) y - top : thumbHeight() / 2;
            dragTo(y);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || dragOffset < 0) return false;
        dragTo(y);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || dragOffset < 0) return false;
        dragOffset = -1;
        return true;
    }

    private void dragTo(double y) {
        double fraction = (y - area.y() - dragOffset) / (area.height() - thumbHeight());
        scrollTo((int) Math.round(maxScroll() * Math.max(0, Math.min(1, fraction))));
    }

    public boolean mouseScrolled(double x, double y, double delta) {
        return mouseScrolled(x, y, 0, delta);
    }

    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return area.contains(x, y) && scrollTo(offset - (int) Math.round(vertical * lineHeight * 3));
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_HOME -> scrollTo(0);
            case GLFW.GLFW_KEY_END -> scrollTo(maxScroll());
            case GLFW.GLFW_KEY_UP -> scrollTo(offset - lineHeight);
            case GLFW.GLFW_KEY_DOWN -> scrollTo(offset + lineHeight);
            case GLFW.GLFW_KEY_PAGE_UP -> scrollTo(offset - area.height());
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollTo(offset + area.height());
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                if (actions.isEmpty()) return false;
                select(key == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                if (selected == null) return false;
                activate(true);
            }
            case GLFW.GLFW_KEY_C -> {
                if (selected == null || selected.kind() != Kind.WEB || !UiScreen.hasControlDown()) return false;
                activate(false);
            }
            default -> { return false; }
        }
        return true;
    }

    private void select(int direction) {
        var keys = new ArrayList<>(actions.keySet());
        int index = keys.indexOf(selected);
        int next = index < 0 && direction < 0 ? keys.size() - 1
                : Math.max(0, Math.min(keys.size() - 1, index + direction));
        selected = keys.get(next);
        var row = actions.get(selected).row();
        if (row.y() < offset || row.y() + row.height() > offset + area.height()) scrollTo(row.y() - INSET);
    }

    private void activate(boolean open) {
        switch (selected.kind()) {
            case WEB -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(selected.value());
                if (open) ShBetterUiApi.links().open(owner, selected.value(), current);
            }
            case ANCHOR -> {
                if (!otherAnchor.test(selected.value())) jumpTo(selected.value());
            }
            case DETAILS -> {
                expanded.computeIfPresent(selected.id(), (id, value) -> !value);
                rebuildRows();
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        var hit = actions.get(selected);
        if (hit == null) output.add(NarratedElementType.TITLE, getMessage());
        else {
            output.add(NarratedElementType.TITLE, Component.literal(hit.label()));
            if (selected.kind() == Kind.DETAILS) output.add(NarratedElementType.POSITION,
                    label(expanded.get(selected.id()) ? "expanded" : "collapsed"));
        }
        output.add(NarratedElementType.USAGE, label(actions.isEmpty() ? "scroll_hint" : "link_hint"));
    }

    private enum Tone { TITLE, HEADING, BODY }
    private enum Kind { WEB, ANCHOR, DETAILS }
    private record Action(Kind kind, String value, int id) {}
    private record Row(FormattedCharSequence text, int x, int y, int height, float scale, GraphicIcon icon,
                       Tone tone, Action fallback) {}
    private record Hit(Row row, String label) {}
}
