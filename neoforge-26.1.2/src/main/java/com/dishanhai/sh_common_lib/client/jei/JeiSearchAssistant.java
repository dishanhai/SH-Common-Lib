// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiHudPanel;
import com.dishanhai.sh_better_ui.client.gui.scaled.PopupMotion;
import com.dishanhai.sh_better_ui.client.gui.layout.PopupArea;
import com.dishanhai.sh_common_lib.api.client.ulk.AsyncClientSearch;
import com.dishanhai.sh_common_lib.client.ClientPanels;
import com.dishanhai.sh_common_lib.api.client.ulk.ClientSearch;
import com.dishanhai.sh_common_lib.api.client.ulk.SearchHistory;
import com.dishanhai.sh_common_lib.api.client.ulk.SearchMatch;
import com.dishanhai.sh_common_lib.api.client.ulk.SearchRanking;
import com.dishanhai.sh_common_lib.api.client.ulk.SearchSources;
import com.dishanhai.sh_common_lib.compat.jei.JeiShortcuts;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Optional client-side presentation around JEI's own search box. */
public final class JeiSearchAssistant {
    private static final Logger LOGGER = LoggerFactory.getLogger("sh_common_lib:jei_search");
    private static final int MAX_ROWS = 5;
    private static final int MAX_SUGGESTIONS = 500;
    private static final int MAX_PREVIEWS = 128;
    private static final SearchCompletionUndo UNDO = new SearchCompletionUndo();
    private static final PopupMotion MOTION = new PopupMotion();
    private static IJeiRuntime failureRuntime;
    private static boolean failed;
    private static Object overlay;
    private static Field searchField;
    private static boolean fieldChecked;
    private static Method hideNativeCompletion;
    private static boolean completionMethodChecked;
    private static Screen screen;
    private static String observed = "";
    private static String focusInitial = "";
    private static boolean wasFocused;
    private static boolean dismissed;
    private static SearchHistory history;
    private static int historyIndex = -1;
    private static String historyDraft = "";
    private static long revision = Long.MIN_VALUE;
    private static Language language;
    private static final ExecutorService SEARCH_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "shclib-search");
        thread.setDaemon(true);
        return thread;
    });
    private static final AsyncClientSearch<JeiSearchBridge.Ingredient> SEARCH = new AsyncClientSearch<>(SEARCH_EXECUTOR, true);
    private static List<ClientSearch.Suggestion<JeiSearchBridge.Ingredient>> suggestions = List.of();
    private static List<String> historyItems = List.of();
    private static Map<String, JeiSearchBridge.Ingredient> ingredientsByName = Map.of();
    private static boolean historyMode;
    private static boolean helpExpanded;
    private static int historyGroup;
    private static final SearchSources SOURCES = new SearchSources(128, new Random());
    private static final FtbQuestSearchSource QUESTS = FtbQuestSearchSource.create();
    private static Object sourceConnection;
    private static Object rankedSource;
    private static SearchHistory rankedHistory;
    private static long rankedHistoryRevision = Long.MIN_VALUE;
    private static Component feedback;
    private static long feedbackUntil;
    private static String suggestionQuery;
    private static int x, y, width, height;
    private static int clipX, clipY, clipWidth, clipHeight;
    private static boolean contentVisible;
    private static float scale;
    private static PopupArea.Rect area;
    private static UiHudPanel lastPanel;
    private static int lastSelectedRow = -1;
    private static int rowsDrawn;
    private static int selected;
    private static String previewKey;
    private static JeiSearchBridge.Recipe preview;
    private static List<JeiSearchBridge.Recipe> previews = List.of();
    private static int previewIndex;
    private static JeiSearchBridge.RecipeLayout previewLayout;

    private JeiSearchAssistant() {}

    /** Close JEI's own popup only while our matching candidates are shown. */
    public static void beforeRender(Screen current) {
        if (!available()) return;
        try { beforeRenderUnchecked(current); } catch (RuntimeException | LinkageError error) { fail(error); }
    }

    private static void beforeRenderUnchecked(Screen current) {
        var options = JeiSearchPreferences.current();
        if (!options.prediction() || !options.hud() || current != Minecraft.getInstance().screen) return;
        var box = activeBox();
        if (box == null) return;
        String query = box.getValue();
        if (!query.equals(observed)) { observed = query; dismissed = false; }
        if (dismissed) return;
        if (query.isBlank()) {
            suggestions = List.of();
            suggestionQuery = query;
            return;
        }
        refreshIndex();
        updateSuggestions(query);
        if (suggestions.isEmpty() || rowsDrawn == 0) return;
        if (!completionMethodChecked) {
            completionMethodChecked = true;
            try { hideNativeCompletion = box.getClass().getMethod("hideCompletion"); }
            catch (NoSuchMethodException | SecurityException ignored) { }
        }
        if (hideNativeCompletion != null) {
            try { hideNativeCompletion.invoke(box); }
            catch (ReflectiveOperationException | SecurityException error) { hideNativeCompletion = null; }
        }
    }

    public static void render(Screen current, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!available()) return;
        try { renderUnchecked(current, graphics, mouseX, mouseY); } catch (RuntimeException | LinkageError error) { fail(error); }
    }

    private static void renderUnchecked(Screen current, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var box = activeBox();
        if (screen != current) {
            finish();
            screen = current;
        }
        if (box == null) {
            if (wasFocused) finish();
            return;
        }
        var options = JeiSearchPreferences.current();
        String query = box.getValue();
        if (!wasFocused) focusInitial = query;
        wasFocused = true;
        UNDO.observe(query);
        if (!query.equals(observed)) {
            observed = query;
            dismissed = false;
        }
        if (!options.hud() || dismissed) {
            rowsDrawn = 0;
            drawClosing(graphics, options.glass());
            return;
        }
        var groups = enabledGroups(options);
        if (!groups.isEmpty() && query.isBlank()) {
            if (!historyMode) selected = 0;
            historyMode = true;
            if (!groups.contains(historyGroup)) { historyGroup = groups.get(0); selected = 0; }
            if (historyGroup < 3) {
                loadHistory();
                historyItems = history == null ? List.of() : history.browse(SearchHistory.Source.values()[historyGroup]);
            } else historyItems = switch (historyGroup) {
                case 3 -> SOURCES.random();
                case 4 -> SOURCES.quest();
                default -> SOURCES.chat();
            };
            suggestions = List.of();
            suggestionQuery = query;
            selected = Math.min(selected, Math.max(0, historyItems.size() - 1));
        } else if (options.prediction() && !query.isBlank()) {
            if (historyMode) { historyMode = false; historyItems = List.of(); selected = 0; }
            refreshIndex();
            updateSuggestions(query);
        } else {
            historyMode = false;
            historyItems = List.of();
            suggestions = List.of();
            suggestionQuery = null;
        }
        int itemCount = itemCount();
        if (!historyMode && itemCount > 0) drawGhost(graphics, box, query);
        boolean showHints = options.hints() && (itemCount > 0 || !groups.isEmpty());
        boolean showFeedback = feedback != null && System.nanoTime() < feedbackUntil;
        if (itemCount == 0 && !showHints && !showFeedback) {
            rowsDrawn = 0;
            drawClosing(graphics, options.glass());
            return;
        }
        var font = Minecraft.getInstance().font;
        var configured = UiPreferences.configured();
        var preferences = options.glass() ? configured : new UiPreferences(configured.highContrast(),
                configured.reducedMotion(), false, configured.feedback());
        var rows = new ArrayList<UiHudPanel.Row>();
        if (options.history()) loadHistory();
        int firstRow = SearchPaging.start(selected, MAX_ROWS);
        int visibleRows = SearchPaging.rows(selected, itemCount, MAX_ROWS);
        for (int i = firstRow; i < firstRow + visibleRows; i++) {
            String label = historyMode ? historyItems.get(i) : suggestions.get(i).label();
            boolean favorite = history != null && options.history() && history.isFavorite(completion(i));
            Component text = highlight(label, historyMode ? "" : query);
            if (!historyMode && label.startsWith("@") && !suggestions.get(i).value().modName().isBlank())
                text = Component.translatable("sh_common_lib.jei.search.mod_display", text, suggestions.get(i).value().modName());
            rows.add(new UiHudPanel.Row(UiHudPanel.icon(favorite ? "favorite" : historyMode ? "history" : "search"), favorite ? 0xffffd175 : 0xffa9caff, i == selected
                    ? Component.translatable("sh_common_lib.jei.search.first", text) : text));
        }
        if (showFeedback) rows.add(new UiHudPanel.Row(UiHudPanel.icon("info"), 0xffa9caff, feedback));
        if (showHints && itemCount > 0) rows.add(new UiHudPanel.Row(UiHudPanel.icon("keyboard"), 0xffd5deeb,
                Component.translatable("sh_common_lib.jei.search.hints")));
        if (showHints && helpExpanded && itemCount > 0) rows.add(new UiHudPanel.Row(UiHudPanel.icon("keyboard"), 0xffd5deeb,
                Component.translatable("sh_common_lib.jei.search.page_hint", SearchPaging.page(selected, MAX_ROWS) + 1, SearchPaging.pages(itemCount, MAX_ROWS))));
        if (showHints && helpExpanded && !historyMode && itemCount > 0) rows.add(new UiHudPanel.Row(UiHudPanel.icon("keyboard"), 0xffd5deeb,
                Component.translatable("sh_common_lib.jei.search.recipe_hint")));
        if (showHints && helpExpanded && itemCount > 0) rows.add(new UiHudPanel.Row(UiHudPanel.icon("reset"), 0xffd5deeb,
                Component.translatable("sh_common_lib.jei.search.undo_hint")));
        if (showHints && (options.history() || historyMode)) rows.add(new UiHudPanel.Row(UiHudPanel.icon("history"), 0xffd5deeb,
                historyMode ? Component.translatable(switch (historyGroup) {
                    case 1 -> "sh_common_lib.jei.search.group_favorites";
                    case 2 -> "sh_common_lib.jei.search.group_frequent";
                    case 3 -> "sh_common_lib.jei.search.group_random";
                    case 4 -> "sh_common_lib.jei.search.group_quest";
                    case 5 -> "sh_common_lib.jei.search.group_chat";
                    default -> "sh_common_lib.jei.search.group_recent";
                }) : Component.translatable("sh_common_lib.jei.search.history_only")));
        if (showHints && helpExpanded && options.history() && itemCount > 0 && (!historyMode || historyGroup < 3)) rows.add(new UiHudPanel.Row(UiHudPanel.icon("history"), 0xffd5deeb,
                Component.translatable(historyMode ? "sh_common_lib.jei.search.manage_history" : "sh_common_lib.jei.search.manage_candidate")));
        if (showHints) rows.add(new UiHudPanel.Row(UiHudPanel.icon("keyboard"), 0xffd5deeb,
                Component.translatable(helpExpanded ? "sh_common_lib.jei.search.help_close" : "sh_common_lib.jei.search.help_open")));
        var panel = new UiHudPanel(font, rows);
        lastPanel = panel;
        lastSelectedRow = visibleRows == 0 ? -1 : selected - firstRow;
        PopupArea.Rect host = current instanceof AbstractContainerScreen<?> container
                ? new PopupArea.Rect(container.getLeftPos(), container.getTopPos(), container.getImageWidth(), container.getImageHeight()) : null;
        var desired = panel.fit(current.width, current.height, 1f);
        area = PopupArea.leftOf(current.width, current.height, box.getX(), host, 4, desired.width(), desired.height());
        if (area.width() < 64 || area.height() < 30) { rowsDrawn = 0; return; }
        var placement = panel.fit(area.width() + 8, area.height() + 8, 1f);
        width = placement.width();
        height = placement.height();
        scale = placement.scale();
        x = area.x() + area.width() - width;
        y = Math.max(area.y(), Math.min(box.getY() + box.getHeight(), area.y() + area.height()) - height);
        var frame = MOTION.sample(true, x, y, width, height, System.nanoTime() / 1_000_000L, preferences.reducedMotion());
        rowsDrawn = visibleRows;
        int fx = Math.max(area.x(), Math.round(frame.x())), fy = Math.max(area.y(), Math.round(frame.y()));
        int fw = Math.min(area.x() + area.width(), Math.round(frame.x() + frame.width())) - fx;
        int fh = Math.min(area.y() + area.height(), Math.round(frame.y() + frame.height())) - fy;
        clipX = fx; clipY = fy; clipWidth = fw; clipHeight = fh;
        contentVisible = frame.progress() >= 0.5f;
        if (fw <= 0 || fh <= 0) return;
        panel.drawReveal(graphics, font, preferences, x, y, scale, lastSelectedRow, fx, fy, fw, fh, frame.progress());
        if (frame.acceptsInput() && options.preview() && selectedIngredient() != null) {
            try { drawPreview(graphics, current, preferences, mouseX, mouseY); }
            catch (RuntimeException | LinkageError error) {
                previewKey = selectedIngredient().key();
                preview = null;
                previews = List.of();
                previewIndex = 0;
                previewLayout = null;
                LOGGER.warn("Could not render JEI recipe preview", error);
            }
        }
    }

    public static boolean keyPressed(Screen current, int key, int modifiers) {
        if (!available()) return false;
        try { return keyPressedUnchecked(current, key, modifiers); }
        catch (RuntimeException | LinkageError error) { fail(error); return false; }
    }

    private static boolean keyPressedUnchecked(Screen current, int key, int modifiers) {
        var box = activeBox();
        if (box == null || current != Minecraft.getInstance().screen) return false;
        var options = JeiSearchPreferences.current();
        if (!dismissed && options.hud() && options.hints() && key == GLFW.GLFW_KEY_F1 && modifiers == 0) {
            helpExpanded = !helpExpanded;
            return true;
        }
        boolean hasSelection = !dismissed && rowsDrawn > 0 && itemCount() > 0
                && box.getValue().equals(suggestionQuery) && selected < itemCount();
        if (modifiers == 0 && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)
                && options.hud() && (options.prediction() || !enabledGroups(options).isEmpty())) {
            if (hasSelection) selected = SearchPaging.step(selected, itemCount(), key == GLFW.GLFW_KEY_UP ? -1 : 1);
            return true;
        }
        if (historyMode && options.hud() && !dismissed
                && modifiers == GLFW.GLFW_MOD_CONTROL && key == GLFW.GLFW_KEY_TAB) {
            var groups = enabledGroups(options);
            if (groups.isEmpty()) return false;
            historyGroup = groups.get((groups.indexOf(historyGroup) + 1) % groups.size());
            selected = 0;
            return true;
        }
        if (hasSelection && options.history() && modifiers == (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT)
                && key == GLFW.GLFW_KEY_F) {
            toggleFavorite(selected);
            return true;
        }
        if (hasSelection && historyMode && historyGroup < 3 && options.history() && modifiers == GLFW.GLFW_MOD_SHIFT
                && key == GLFW.GLFW_KEY_DELETE) {
            removeHistory(selected);
            return true;
        }
        if (hasSelection && selectedIngredient() != null && modifiers == GLFW.GLFW_MOD_ALT && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            if (JeiSearchBridge.open(selectedIngredient())) {
                remember(box.getValue());
                return true;
            }
        }
        if (hasSelection && modifiers == GLFW.GLFW_MOD_ALT && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            selected = SearchPaging.movePage(selected, itemCount(), MAX_ROWS, key == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            return true;
        }
        if (hasSelection && selectedIngredient() != null && options.preview() && modifiers == GLFW.GLFW_MOD_CONTROL && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            ensurePreview();
            if (previews.size() > 1) {
                previewIndex = Math.floorMod(previewIndex + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1), previews.size());
                preview = previews.get(previewIndex);
                previewLayout = preview.layout().orElse(null);
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_SUPER)) == 0) {
            if (historyMode && options.history()) {
                loadHistory();
                if (history != null && history.undoRemove()) {
                    if (saveHistory()) setFeedback("sh_common_lib.jei.search.restored");
                    return true;
                }
            }
            var previous = UNDO.undo(box.getValue());
            if (previous.isPresent()) {
                box.setValue(previous.get());
                observed = box.getValue();
                dismissed = true;
                rowsDrawn = 0;
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE
                || ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && (key == GLFW.GLFW_KEY_V || key == GLFW.GLFW_KEY_X))) {
            UNDO.clear();
        }
        if (hasSelection && modifiers == 0
                && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            complete(box, completion(selected));
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            remember(box.getValue());
            return false;
        }
        if (options.history() && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_SUPER)) == 0
                && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            loadHistory();
            if (history == null || history.entries().isEmpty()) return false;
            var entries = history.entries();
            if (historyIndex < 0) historyDraft = box.getValue();
            historyIndex = Math.max(-1, Math.min(entries.size() - 1,
                    historyIndex + (key == GLFW.GLFW_KEY_UP ? 1 : -1)));
            box.setValue(historyIndex < 0 ? historyDraft : entries.get(historyIndex));
            return true;
        }
        if (modifiers == 0 && key == GLFW.GLFW_KEY_ESCAPE && rowsDrawn > 0 && !dismissed) {
            dismissed = true;
            rowsDrawn = 0;
            return true;
        }
        if (modifiers == 0 && key == GLFW.GLFW_KEY_TAB && options.hud() && (historyMode || options.prediction())
                && !dismissed && rowsDrawn > 0 && itemCount() > 0
                && box.getValue().equals(suggestionQuery)) {
            complete(box, completion(selected));
            return true;
        }
        if (key != GLFW.GLFW_KEY_LEFT_CONTROL && key != GLFW.GLFW_KEY_RIGHT_CONTROL) historyIndex = -1;
        return false;
    }

    public static void characterTyped(Screen current) {
        if (!available()) return;
        try {
            if (current == Minecraft.getInstance().screen && activeBox() != null) UNDO.clear();
        } catch (RuntimeException | LinkageError error) { fail(error); }
    }

    public static void tick() {
        if (!available()) return;
        try {
            var options = JeiSearchPreferences.current();
            Object connection = Minecraft.getInstance().getConnection();
            SOURCES.session(connection);
            if (sourceConnection != connection) {
                sourceConnection = connection;
                if (QUESTS != null) QUESTS.reset(SOURCES);
            }
            if (!options.chatSource()) SOURCES.clearChat();
            if (QUESTS != null) {
                if (connection != null && options.questSource()) QUESTS.tick(SOURCES);
                else QUESTS.reset(SOURCES);
            }
            if (options.prediction() || options.randomSource() || options.questSource() || options.chatSource()) refreshIndex();
        } catch (RuntimeException | LinkageError error) { fail(error); return; }
        if (previewLayout == null || !JeiSearchPreferences.current().preview()) return;
        try {
            if (activeBox() != null) previewLayout.tick();
        } catch (RuntimeException | LinkageError error) {
            preview = null;
            previewLayout = null;
            LOGGER.warn("Could not update JEI recipe preview", error);
        }
    }

    public static void chatReceived(String message) {
        if (!JeiSearchPreferences.current().chatSource() || !available()) return;
        SOURCES.session(Minecraft.getInstance().getConnection());
        SOURCES.acceptChat(message);
    }

    private static List<Integer> enabledGroups(JeiSearchSettings options) {
        var groups = new ArrayList<Integer>();
        if (options.history()) { groups.add(0); groups.add(1); groups.add(2); }
        if (options.randomSource()) groups.add(3);
        if (options.questSource()) groups.add(4);
        if (options.chatSource()) groups.add(5);
        return groups;
    }

    public static boolean mouseClicked(Screen current, double mouseX, double mouseY, int button) {
        if (!available()) return false;
        try { return mouseClickedUnchecked(current, mouseX, mouseY, button); }
        catch (RuntimeException | LinkageError error) { fail(error); return false; }
    }

    public static boolean mouseScrolled(Screen current, double mouseX, double mouseY, double delta) {
        if (!available() || delta == 0) return false;
        try {
            var box = activeBox();
            if (box == null || current != screen || rowsDrawn == 0 || dismissed || !contentVisible
                    || itemCount() == 0 || !box.getValue().equals(suggestionQuery)) return false;
            boolean overPanel = mouseX >= clipX && mouseX < clipX + clipWidth
                    && mouseY >= clipY && mouseY < clipY + clipHeight;
            boolean overBox = mouseX >= box.getX() && mouseX < box.getX() + box.getWidth()
                    && mouseY >= box.getY() && mouseY < box.getY() + box.getHeight();
            if (!overPanel && !overBox) return false;
            selected = SearchPaging.step(selected, itemCount(), delta > 0 ? -1 : 1);
            return true;
        } catch (RuntimeException | LinkageError error) { fail(error); return false; }
    }

    private static boolean mouseClickedUnchecked(Screen current, double mouseX, double mouseY, int button) {
        var box = activeBox();
        if (box == null || current != screen || (button != 0 && button != 1 && button != 2)
                || rowsDrawn == 0 || dismissed || !contentVisible
                || !box.getValue().equals(suggestionQuery) || scale <= 0) return false;
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
        if (mouseX < clipX || mouseX >= clipX + clipWidth || mouseY < clipY || mouseY >= clipY + clipHeight) return false;
        int localY = (int) ((mouseY - y) / scale) - 6;
        int row = localY / (Math.max(8, Minecraft.getInstance().font.lineHeight) + 3);
        if (localY < 0 || row >= rowsDrawn) return false;
        int item = SearchPaging.start(selected, MAX_ROWS) + row;
        if (button == 2) {
            if (!JeiSearchPreferences.current().history()) return false;
            toggleFavorite(item);
        } else if (button == 1) {
            String value = historyMode ? completion(item) : suggestions.get(item).label().startsWith("@")
                    || suggestions.get(item).label().startsWith("#")
                    ? suggestions.get(item).label().substring(1) : suggestions.get(item).value().id().toString();
            Minecraft.getInstance().keyboardHandler.setClipboard(value);
            setFeedback("sh_common_lib.jei.search.copied");
        } else complete(box, completion(item));
        return true;
    }

    private static void complete(EditBox box, String value) {
        String previous = box.getValue();
        box.setValue(value);
        observed = box.getValue();
        UNDO.accepted(previous, observed);
        dismissed = true;
        rowsDrawn = 0;
        historyIndex = -1;
    }

    public static void close(Screen current) {
        if (!available()) return;
        try { closeUnchecked(current); } catch (RuntimeException | LinkageError error) { fail(error); }
    }

    /** Flush the current query and release JEI-owned objects before its runtime is discarded. */
    public static void runtimeUnavailable() {
        if (screen != null) close(screen);
        overlay = null;
        searchField = null;
        hideNativeCompletion = null;
        SEARCH.replace(List.of());
        SOURCES.session(null);
        SOURCES.replaceAvailable(List.of());
        ingredientsByName = Map.of();
        sourceConnection = null;
        if (QUESTS != null) QUESTS.reset(SOURCES);
        SEARCH.request("", MAX_ROWS);
        suggestions = List.of();
        historyItems = List.of();
        historyMode = false;
        preview = null;
        previews = List.of();
        previewLayout = null;
        previewKey = null;
        suggestionQuery = null;
        revision = Long.MIN_VALUE;
        language = null;
        screen = null;
        wasFocused = false;
        rowsDrawn = 0;
        UNDO.clear();
        JeiSearchBridge.clear();
    }

    private static void closeUnchecked(Screen current) {
        if (screen == current) {
            var box = activeBox();
            if (box != null) observed = box.getValue();
            finish();
            screen = null;
        }
    }

    private static boolean available() {
        IJeiRuntime runtime = JeiShortcuts.runtime();
        if (runtime != failureRuntime) {
            failureRuntime = runtime;
            failed = false;
            rowsDrawn = 0;
            wasFocused = false;
            screen = null;
        }
        return runtime != null && !failed;
    }

    private static void fail(Throwable error) {
        failed = true;
        rowsDrawn = 0;
        LOGGER.warn("JEI search enhancement disabled until JEI reloads", error);
    }

    private static void finish() {
        if (wasFocused && !observed.equals(focusInitial)) remember(observed);
        wasFocused = false;
        rowsDrawn = 0;
        historyIndex = -1;
        dismissed = false;
        UNDO.clear();
        MOTION.reset();
        lastPanel = null;
    }

    private static void drawClosing(GuiGraphicsExtractor graphics, boolean glass) {
        if (lastPanel == null) return;
        var configured = UiPreferences.configured();
        var preferences = glass ? configured : new UiPreferences(configured.highContrast(),
                configured.reducedMotion(), false, configured.feedback());
        var frame = MOTION.sample(false, x, y, width, height, System.nanoTime() / 1_000_000L,
                preferences.reducedMotion());
        if (frame.progress() <= 0) { lastPanel = null; return; }
        int fx = Math.round(frame.x()), fy = Math.round(frame.y());
        int fw = Math.round(frame.width()), fh = Math.round(frame.height());
        if (fw <= 0 || fh <= 0) return;
        lastPanel.drawReveal(graphics, Minecraft.getInstance().font, preferences, x, y, scale,
                lastSelectedRow, fx, fy, fw, fh, frame.progress());
    }

    private static void loadHistory() {
        history = JeiSearchPreferences.history();
    }

    private static int itemCount() { return historyMode ? historyItems.size() : suggestions.size(); }
    private static String completion(int item) { return historyMode ? historyItems.get(item) : suggestions.get(item).completion(); }
    private static boolean isIngredientSuggestion(int item) {
        String label = suggestions.get(item).label();
        return !label.startsWith("@") && !label.startsWith("#");
    }

    private static JeiSearchBridge.Ingredient selectedIngredient() {
        if (selected < 0 || selected >= itemCount()) return null;
        if (historyMode) return historyGroup < 3 ? null : ingredientsByName.get(completion(selected));
        return isIngredientSuggestion(selected) ? suggestions.get(selected).value() : null;
    }

    private static Component highlight(String label, String query) {
        var match = SearchMatch.literal(label, query);
        if (match.isEmpty()) return Component.literal(label);
        var range = match.get();
        return Component.empty().append(Component.literal(label.substring(0, range.start())))
                .append(Component.literal(label.substring(range.start(), range.end())).withStyle(net.minecraft.ChatFormatting.AQUA))
                .append(Component.literal(label.substring(range.end())));
    }

    private static void drawGhost(GuiGraphicsExtractor graphics, EditBox box, String query) {
        if (selected >= suggestions.size() || box.getCursorPosition() != query.length()
                || !box.getHighlighted().isEmpty()) return;
        String suffix = SearchMatch.suffix(query, completion(selected));
        if (suffix.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        int inputWidth = font.width(query);
        if (inputWidth >= box.getWidth()) return;
        int textX = box.getX() + inputWidth;
        graphics.enableScissor(box.getX(), box.getY(), box.getX() + box.getWidth(), box.getY() + box.getHeight());
        try { graphics.text(font, suffix, textX, box.getY(), 0xff8a96a4); }
        finally { graphics.disableScissor(); }
    }

    private static void toggleFavorite(int item) {
        loadHistory();
        if (history == null) return;
        boolean added = history.toggleFavorite(completion(item));
        if (saveHistory()) setFeedback(added ? "sh_common_lib.jei.search.favorite_added" : "sh_common_lib.jei.search.favorite_removed");
    }

    private static void removeHistory(int item) {
        loadHistory();
        if (history == null) return;
        if (historyGroup == 1) history.removeFavorite(completion(item));
        else history.remove(completion(item));
        selected = Math.max(0, Math.min(selected, itemCount() - 2));
        if (saveHistory()) setFeedback("sh_common_lib.jei.search.removed");
    }

    private static boolean saveHistory() {
        try { history.save(); return true; }
        catch (IOException error) {
            LOGGER.warn("Could not save JEI search history", error);
            setFeedback("sh_common_lib.jei.search.save_failed");
            return false;
        }
    }

    private static void setFeedback(String translationKey) {
        feedback = Component.translatable(translationKey);
        feedbackUntil = System.nanoTime() + 2_000_000_000L;
    }

    private static void remember(String query) {
        if (!JeiSearchPreferences.current().history() || query == null || query.isBlank()) return;
        loadHistory();
        if (history == null) return;
        history.record(query);
        saveHistory();
    }

    private static EditBox activeBox() {
        var runtime = JeiShortcuts.runtime();
        if (runtime == null) return null;
        var current = runtime.getIngredientListOverlay();
        if (current != overlay) {
            overlay = current; fieldChecked = false; searchField = null;
            completionMethodChecked = false; hideNativeCompletion = null;
        }
        if (!current.hasKeyboardFocus()) return null;
        if (!fieldChecked) {
            fieldChecked = true;
            try {
                Field field = current.getClass().getDeclaredField("searchField");
                if (field.trySetAccessible()) searchField = field;
            } catch (ReflectiveOperationException | SecurityException error) {
                LOGGER.debug("JEI search field is not available for this version", error);
            }
        }
        if (searchField == null) return null;
        try {
            Object value = searchField.get(current);
            return value instanceof EditBox box && box.isVisible() ? box : null;
        } catch (IllegalAccessException error) {
            searchField = null;
            return null;
        }
    }

    private static void refreshIndex() {
        long nextRevision = JeiSearchBridge.revision();
        Language nextLanguage = Language.getInstance();
        if (revision == nextRevision && language == nextLanguage) return;
        var entries = new ArrayList<ClientSearch.Entry<JeiSearchBridge.Ingredient>>();
        for (var ingredient : JeiSearchBridge.ingredients()) {
            if (!ingredient.visible() || ingredient.name().isBlank()) continue;
            entries.add(new ClientSearch.Entry<>(ingredient, ingredient.name(), List.of(ingredient.id().toString()),
                    JeiSearchPreferences.aliases(ingredient.id().toString(), ingredient.aliases()),
                    ingredient.modName(), ingredient.tags().stream().map(Object::toString).toList()));
        }
        SEARCH.replace(entries);
        SOURCES.replaceAvailable(entries.stream().map(ClientSearch.Entry::name).toList());
        var byName = new LinkedHashMap<String, JeiSearchBridge.Ingredient>();
        var ambiguous = new java.util.HashSet<String>();
        for (var entry : entries) {
            var previous = byName.putIfAbsent(entry.name(), entry.value());
            if (previous != null && !previous.key().equals(entry.value().key())) ambiguous.add(entry.name());
        }
        ambiguous.forEach(byName::remove);
        ingredientsByName = Map.copyOf(byName);
        revision = nextRevision;
        language = nextLanguage;
        suggestionQuery = null;
        previewKey = null;
    }

    private static void updateSuggestions(String query) {
        if (!query.equals(suggestionQuery)) {
            suggestionQuery = query;
            suggestions = List.of();
            rankedSource = null;
            selected = 0;
            previewKey = null;
        }
        SEARCH.request(query, MAX_SUGGESTIONS);
        var result = SEARCH.result();
        if (!query.equals(result.query())) return;
        var ranked = result.suggestions();
        SearchHistory activeHistory = null;
        if (JeiSearchPreferences.current().history()) {
            loadHistory();
            activeHistory = history;
        }
        long currentHistoryRevision = activeHistory == null ? 0 : activeHistory.revision();
        if (rankedSource == ranked && rankedHistory == activeHistory && rankedHistoryRevision == currentHistoryRevision) return;
        rankedSource = ranked;
        rankedHistory = activeHistory;
        rankedHistoryRevision = currentHistoryRevision;
        if (activeHistory != null) {
            var usage = activeHistory;
            ranked = SearchRanking.withUsage(ranked, candidate -> candidate.matchRank(),
                    candidate -> usage.count(candidate.completion()), candidate -> usage.recency(candidate.completion()));
        }
        if (!suggestions.equals(ranked)) {
            suggestions = ranked;
            selected = Math.min(selected, Math.max(0, suggestions.size() - 1));
            previewKey = null;
        }
    }

    private static void ensurePreview() {
        var ingredient = selectedIngredient();
        if (ingredient == null) return;
        if (!ingredient.key().equals(previewKey)) {
            previewKey = ingredient.key();
            previews = JeiSearchBridge.recipes(ingredient, JeiSearchBridge.Direction.OBTAINING, MAX_PREVIEWS);
            previewIndex = 0;
            preview = previews.isEmpty() ? null : previews.get(0);
            previewLayout = preview == null ? null : preview.layout().orElse(null);
        }
    }

    private static void drawPreview(GuiGraphicsExtractor graphics, Screen current, UiPreferences preferences, int mouseX, int mouseY) {
        ensurePreview();
        if (previewLayout == null || previewLayout.width() <= 0 || previewLayout.height() <= 0) return;
        var font = Minecraft.getInstance().font;
        var bounds = RecipePreviewBounds.of(previewLayout.outerWidth(), previewLayout.outerHeight(),
                previewLayout.contentOffsetX(), previewLayout.contentOffsetY(), font.lineHeight);
        int previewWidth = bounds.width();
        int previewHeight = bounds.height();
        int left = x - previewWidth - 4;
        int top = Math.max(area.y(), Math.min(y, area.y() + area.height() - previewHeight));
        if (left < area.x() || top + previewHeight > area.y() + area.height()) return;
        if (JeiSearchPreferences.current().glass()) ClientPanels.panel(graphics, left, top, previewWidth, previewHeight);
        else ClientPanels.surface(graphics, new com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds(left, top, previewWidth, previewHeight), null, 0xFF18202C);
        graphics.enableScissor(left + bounds.clipX(), top + bounds.clipY(),
                left + bounds.clipX() + bounds.clipWidth(), top + bounds.clipY() + bounds.clipHeight());
        try { previewLayout.render(graphics, left + bounds.recipeX(), top + bounds.recipeY(), mouseX, mouseY); }
        finally { graphics.disableScissor(); }
        graphics.nextStratum();
        var title = previews.size() > 1 ? Component.translatable("sh_common_lib.jei.search.recipe_title",
                preview.title(), previewIndex + 1, previews.size()) : preview.title();
        graphics.text(font, font.plainSubstrByWidth(title.getString(), previewWidth - 12),
                left + 6, top + 5, ClientPanels.TEXT);
        if (mouseX >= left + bounds.clipX() && mouseX < left + bounds.clipX() + bounds.clipWidth()
                && mouseY >= top + bounds.clipY() && mouseY < top + bounds.clipY() + bounds.clipHeight()) {
            previewLayout.renderOverlays(graphics, mouseX, mouseY);
        }
    }
}

