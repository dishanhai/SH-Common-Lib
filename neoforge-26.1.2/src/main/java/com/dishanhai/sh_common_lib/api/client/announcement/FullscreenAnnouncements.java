// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.announcement;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_common_lib.client.ClientPanels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.dishanhai.sh_common_lib.api.client.announcement.AnnouncementCatalog.Document;

/** Client-side full-screen release notes. Call the open methods on the Minecraft client thread. */
public final class FullscreenAnnouncements {
    private static final String KEY = "sh_common_lib.announcement.";
    private static final int MARGIN = 8;
    private static final int GAP = 4;
    private static final int ROW = 22;
    private final AnnouncementCatalog catalog;

    public FullscreenAnnouncements(Path directory) {
        catalog = new AnnouncementCatalog(directory);
    }

    /** Opens the newest announcement when it is enabled and has not been dismissed. */
    public boolean openUnread(Screen parent) {
        var documents = catalog.documents();
        if (documents.isEmpty()) return false;
        var latest = documents.get(0);
        if (!latest.enabled() || latest.version().equals(catalog.seenVersion())) return false;
        new AnnouncementPage(documents, latest).open(parent, null);
        return true;
    }

    /** Opens the newest announcement on demand, regardless of its automatic display setting. */
    public boolean openLatest(Screen parent) {
        var documents = catalog.documents();
        if (documents.isEmpty()) return false;
        new AnnouncementPage(documents, documents.get(0)).open(parent, null);
        return true;
    }

    /** Opens a specific document, for example one returned by AnnouncementCatalog. */
    public void openDocument(Screen parent, Document document) {
        new AnnouncementPage(catalog.documents(), Objects.requireNonNull(document)).open(parent, null);
    }

    /** Opens the complete version history, including the current release. */
    public void openHistory(Screen parent) {
        new HistoryPage(catalog.documents()).open(parent, null);
    }

    private final class AnnouncementPage {
        private final List<Document> documents;
        private final Document document;
        private boolean english;
        private boolean writeFailed;
        private AnnouncementText body;
        private UiButton historyButton;
        private UiButton languageButton;
        private UiButton dismissButton;
        private UiButton appearanceButton;

        private AnnouncementPage(List<Document> documents, Document document) {
            this.documents = documents;
            this.document = document;
            english = document.hasEnglish() && prefersEnglish();
        }

        private void open(Screen parent, HistoryPage history) {
            new AnnouncementScreen(title(document, english), parent,
                    history == null ? null : () -> history.selectedButton,
                    this::layout, FullscreenAnnouncements::drawPanel).open();
        }

        private void layout(AnnouncementScreen screen) {
            var panel = panel(screen);
            int x = panel.x() + MARGIN;
            int width = Math.max(1, panel.width() - 2 * MARGIN);
            boolean latest = !documents.isEmpty() && document.equals(documents.get(0));
            var actions = new ArrayList<Action>();
            actions.add(new Action(label("close"), GraphicIcon.SUCCESS, screen::onClose));
            dismissButton = null;
            if (latest) actions.add(new Action(label("dismiss"), GraphicIcon.HIDE, () -> {
                if (catalog.markSeen(document)) screen.onClose();
                else {
                    writeFailed = true;
                    dismissButton.setTooltip(Tooltip.create(label("write_failed")));
                }
            }));
            int historyIndex = actions.size();
            if (documents.size() > 1) actions.add(new Action(label("history"), GraphicIcon.HISTORY,
                    () -> new HistoryPage(documents).open(screen, this)));
            int languageIndex = actions.size();
            if (document.hasEnglish()) actions.add(new Action(label(english ? "switch_zh" : "switch_en"), GraphicIcon.TRANSLATE, () -> {
                english = !english;
                body = null;
                screen.rebuild(() -> screen.setFocused(languageButton));
            }));
            int appearanceIndex = actions.size();
            if (AnnouncementAppearance.available()) actions.add(new Action(label("appearance"), GraphicIcon.SETTINGS,
                    () -> AnnouncementAppearance.open(screen, () -> appearanceButton)));

            var font = Minecraft.getInstance().font;
            int ideal = actions.stream().mapToInt(action -> font.width(action.label()) + GraphicIcon.PIXELS + 16).max().orElseThrow();
            int columns = Math.max(1, Math.min(actions.size(), (width + GAP) / (ideal + GAP)));
            int rows = (actions.size() + columns - 1) / columns;
            int buttonWidth = Math.max(1, (width - (columns - 1) * GAP) / columns);
            int buttonHeight = Math.min(ROW, Math.max(1, (panel.height() - 2 * MARGIN) / (rows + 1) - GAP));
            int footer = panel.bottom() - MARGIN - rows * (buttonHeight + GAP) + GAP;
            int y = panel.y() + MARGIN;
            var textArea = new Bounds(x, y, width, Math.max(0, footer - GAP - y));
            body = screen.add(new AnnouncementText(screen, textArea, title(document, english),
                    document.body(english), document.links(), english, body,
                    anchor -> followLanguageAnchor(screen, anchor)));

            screen.setFocused(body);
            historyButton = null;
            languageButton = null;
            appearanceButton = null;
            for (int i = 0; i < actions.size(); i++) {
                var action = actions.get(i);
                var button = screen.button(x + i % columns * (buttonWidth + GAP),
                        footer + i / columns * (buttonHeight + GAP), buttonWidth, buttonHeight,
                        action.label(), action.run());
                button.setSampleBackground(false);
                if (buttonWidth >= 40) button.setLeadingIcon(action.icon());
                if (font.width(action.label()) + GraphicIcon.PIXELS + 12 > buttonWidth) {
                    button.setTooltip(Tooltip.create(action.label()));
                }
                if (latest && i == 1) {
                    dismissButton = button;
                    if (writeFailed) button.setTooltip(Tooltip.create(label("write_failed")));
                }
                if (documents.size() > 1 && i == historyIndex) historyButton = button;
                if (document.hasEnglish() && i == languageIndex) languageButton = button;
                if (i == appearanceIndex) appearanceButton = button;
            }
        }

        private boolean followLanguageAnchor(AnnouncementScreen screen, String anchor) {
            if (!document.hasEnglish() || english || !anchor.equals("english")) return false;
            english = true;
            body = null;
            screen.rebuild(() -> body.jumpTo(anchor));
            return true;
        }

        private record Action(Component label, GraphicIcon icon, Runnable run) {}
    }

    private final class HistoryPage {
        private final List<Document> documents;
        private int first;
        private int selected = -1;
        private int pageSize;
        private int pages;
        private UiButton selectedButton;
        private UiButton previous;
        private UiButton next;

        private HistoryPage(List<Document> documents) {
            this.documents = documents;
        }

        private void open(Screen parent, AnnouncementPage announcement) {
            new AnnouncementScreen(label("history"), parent,
                    announcement == null ? null : () -> announcement.historyButton,
                    this::layout, this::render).open();
        }

        private void layout(AnnouncementScreen screen) {
            var panel = panel(screen);
            int x = panel.x() + MARGIN;
            int width = Math.max(1, panel.width() - 2 * MARGIN);
            int footer = panel.bottom() - MARGIN - ROW;
            int y = panel.y() + MARGIN + ROW;
            pageSize = Math.max(1, (footer - y) / (ROW + GAP));
            pages = Math.max(1, (documents.size() + pageSize - 1) / pageSize);
            first = Math.min(first / pageSize, pages - 1) * pageSize;
            selectedButton = null;
            boolean english = prefersEnglish();
            for (int i = first; i < Math.min(first + pageSize, documents.size()); i++) {
                int index = i;
                var document = documents.get(i);
                var name = Component.translatable(KEY + "history_entry", document.version(), title(document, english && document.hasEnglish()));
                var button = screen.button(x, y, width, ROW, name, () -> {
                    selected = index;
                    new AnnouncementPage(documents, document).open(screen, this);
                });
                button.setSampleBackground(false);
                button.setTooltip(Tooltip.create(name));
                if (i == selected) selectedButton = button;
                y += ROW + GAP;
            }
            int side = Math.max(1, (width - 2 * GAP) / 3);
            previous = screen.button(x, footer, side, ROW, label("previous"), () -> move(screen, -1));
            previous.active = first > 0;
            next = screen.button(x + width - side, footer, side, ROW, label("next"), () -> move(screen, 1));
            next.active = first / pageSize + 1 < pages;
            screen.button(x + side + GAP, footer, Math.max(1, width - 2 * (side + GAP)), ROW,
                    Component.translatable("gui.back"), screen::onClose);
        }

        private void move(AnnouncementScreen screen, int direction) {
            first += direction * pageSize;
            screen.rebuild(() -> {
                var target = direction < 0 ? previous : next;
                screen.setFocused(target.active ? target : (direction < 0 ? next : previous));
            });
        }

        private void render(AnnouncementScreen screen, GuiGraphicsExtractor frame) {
            drawPanel(screen, frame);
            var panel = panel(screen);
            var area = new Bounds(panel.x() + MARGIN, panel.y() + MARGIN, Math.max(1, panel.width() - 2 * MARGIN), ROW);
            var heading = pages > 1 ? Component.translatable(KEY + "history_page", first / pageSize + 1, pages)
                    : label("history");
            drawText(frame, heading, area);
            if (documents.isEmpty()) drawText(frame, label("empty"),
                    new Bounds(area.x(), area.y() + ROW, area.width(), ROW));
        }
    }

    private static void drawText(GuiGraphicsExtractor frame, Component text, Bounds area) {
        var font = Minecraft.getInstance().font;
        var textLine = net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(text, area.width()));
        frame.centeredText(font, textLine, area.x() + area.width() / 2,
                area.y() + (area.height() - font.lineHeight) / 2, ClientPanels.TEXT);
    }


    private static Bounds panel(AnnouncementScreen screen) {
        int margin = Math.min(MARGIN, Math.min(screen.width, screen.height) / 8);
        int width = Math.min(640, screen.width - 2 * margin);
        return new Bounds((screen.width - width) / 2, margin, width, screen.height - 2 * margin);
    }

    private static void drawPanel(AnnouncementScreen screen, GuiGraphicsExtractor frame) {
        AnnouncementAppearance.drawPanel(frame, panel(screen));
    }

    private static Component title(Document document, boolean english) {
        String title = document.title(english);
        return title.isBlank() ? Component.translatable(KEY + (english ? "title_en" : "title_zh"), document.version())
                : Component.literal(title);
    }

    private static boolean prefersEnglish() {
        return Minecraft.getInstance().getLanguageManager().getSelected().toLowerCase(Locale.ROOT).startsWith("en");
    }

    static Component label(String name) {
        return Component.translatable(KEY + name);
    }
}
