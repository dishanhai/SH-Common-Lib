// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.client.beacon.BeaconClient;
import com.dishanhai.sh_common_lib.client.control.ControlCenterModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.File;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

public final class PhoneScreen extends PhoneScreenLayer {
    private static final int PADDING = 10;
    private static final int GUTTER = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int COLUMNS = 3;
    private static final int MAP_SIZE = 25;
    private static final App[] APPS = App.values();
    private static final PhoneNotes NOTES = new PhoneNotes();

    private static long stopwatchStarted;
    private static long stopwatchElapsed;
    private static boolean stopwatchRunning;
    private static PhoneScreen pendingPhoto;
    private static double previousMusicVolume = 1;

    private Bounds panel;
    private App page;
    private App returnFocus;
    private UiButton shutter;
    private UiButton save;
    private UiButton stopwatchAction;
    private UiButton stopwatchReset;
    private UiButton playerButton;
    private boolean returnToPlayerButton;
    private boolean takingPhoto;
    private int framesToWait;
    private long shownTenths = -1;
    private Component stopwatchTime;
    private final LocalPlayer owner;
    private YearMonth month = YearMonth.now();
    private File[] images = {};
    private int imageIndex;
    private int[] map;
    private BlockPos mapCenter;
    private Component cameraStatus = text("camera.ready");

    private PhoneScreen(LocalPlayer owner) {
        super(text("title"));
        this.owner = owner;
    }

    public static boolean openIfHeld(InteractionHand hand) {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || !player.isAlive() || player.isSpectator() || client.screen != null
                || client.getOverlay() != null || !isPhone(player.getItemInHand(hand))) return false;
        client.setScreen(new PhoneScreen(player));
        return true;
    }

    /** Called by the SHCLIB client module for its user-global note. */
    public static void bindNotes(ModuleContext module) {
        NOTES.bind(module);
    }

    private static boolean isPhone(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ShCommonLib.MOD_ID.equals(id.getNamespace()) && "phone".equals(id.getPath());
    }

    private boolean stillHeld(Minecraft client) {
        return client.player == owner && owner.isAlive() && !owner.isSpectator()
                && (isPhone(owner.getMainHandItem()) || isPhone(owner.getOffhandItem()));
    }

    static Component text(String key) {
        return Component.translatable("screen.sh_common_lib.phone." + key);
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(width - 2 * GUTTER, Math.max(132, Math.min(width / 2 - GUTTER, 170)));
        int panelHeight = Math.min(height - 2 * GUTTER, 186);
        panel = new Bounds(width - panelWidth - GUTTER, (height - panelHeight) / 2, panelWidth, panelHeight);
        save = null;
        shutter = null;
        if (page == null) {
            initHome();
        } else {
            switch (page) {
                case CAMERA -> initCamera();
                case PHOTOS -> initPhotos();
                case NOTES -> initNotes();
                case MAP -> initMap();
                case CALENDAR -> initCalendar();
                case MUSIC -> initMusic();
                case TOOLS -> initStopwatch();
                case SETTINGS, MESSAGES -> throw new IllegalStateException("App opens its own screen");
            }
            button(left(), top(), BUTTON_HEIGHT, BUTTON_HEIGHT, text("back"), GraphicIcon.BACK, false, this::onClose);
        }
        button(panel.x() + panel.width() - PADDING - BUTTON_HEIGHT, top(), BUTTON_HEIGHT, BUTTON_HEIGHT,
                text("close"), GraphicIcon.CLOSE, false, this::dismiss);
    }

    private void initHome() {
        for (var app : APPS) {
            Bounds tile = tile(app);
            int size = Math.max(14, Math.min(tile.width(), tile.height() - 13));
            var button = button(tile.x() + (tile.width() - size) / 2, tile.y(), size, size,
                    app.label(), app.icon, false, () -> open(app));
            if (app == App.NOTES && !NOTES.available()) button.active = false;
            if (app == returnFocus) focus(button);
        }
        returnFocus = null;
    }

    private void open(App app) {
        if (app == App.SETTINGS || app == App.MESSAGES) {
            returnFocus = app;
            if (app == App.SETTINGS) ControlCenterModule.openFromPhone(this);
            else openMessages(this);
            return;
        }
        if (app == App.PHOTOS) {
            var files = gallery().listFiles(file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".png"));
            images = files == null ? new File[0] : files;
            Arrays.sort(images, Comparator.comparing(File::getName).reversed());
            imageIndex = 0;
        }
        if (app == App.MAP) sampleMap();
        if (app == App.CALENDAR) month = YearMonth.now();
        page = app;
        rebuildWidgets();
    }

    private Bounds tile(App app) {
        int column = app.ordinal() % COLUMNS;
        int row = app.ordinal() / COLUMNS;
        int rows = (APPS.length + COLUMNS - 1) / COLUMNS;
        int columnGap = 3;
        int tileWidth = (innerWidth() - (COLUMNS - 1) * columnGap) / COLUMNS;
        int areaHeight = panel.height() - 2 * PADDING - BUTTON_HEIGHT - GUTTER;
        int tileHeight = (areaHeight - (rows - 1) * GUTTER) / rows;
        return new Bounds(left() + column * (tileWidth + columnGap), contentTop() + row * (tileHeight + GUTTER),
                tileWidth, tileHeight);
    }

    private void initCamera() {
        shutter = button(left(), footer(), innerWidth(), BUTTON_HEIGHT,
                text("camera.shutter"), null, true, this::takePhoto);
        shutter.active = !takingPhoto;
        if (shutter.active) focus(shutter);
    }

    private File gallery() { return new File(minecraft.gameDirectory, Screenshot.SCREENSHOT_DIR); }

    private void initPhotos() {
        var view = button(left(), footer() - BUTTON_HEIGHT - GUTTER, innerWidth(), BUTTON_HEIGHT,
                text("photos.view"), null, true, () -> openFile(images[imageIndex]));
        view.active = images.length > 0;
        var folder = button(left(), footer(), innerWidth(), BUTTON_HEIGHT, text("photos.folder"), null, false,
                () -> openFile(gallery()));
        folder.active = gallery().isDirectory();
        var previous = button(left(), contentTop(), BUTTON_HEIGHT, BUTTON_HEIGHT, text("previous"),
                GraphicIcon.PREVIOUS, false, () -> imageIndex = (imageIndex + images.length - 1) % images.length);
        var next = button(panel.x() + panel.width() - PADDING - BUTTON_HEIGHT, contentTop(), BUTTON_HEIGHT,
                BUTTON_HEIGHT, text("next"), GraphicIcon.NEXT, false, () -> imageIndex = (imageIndex + 1) % images.length);
        previous.active = next.active = images.length > 1;
    }

    private void initMap() {
        button(left(), footer(), innerWidth(), BUTTON_HEIGHT, text("map.beacons"), null, false,
                () -> BeaconClient.openDestinations(this));
    }

    private void sampleMap() {
        map = new int[MAP_SIZE * MAP_SIZE];
        var level = minecraft.level;
        mapCenter = owner.blockPosition();
        if (level == null) return;
        var at = new BlockPos.MutableBlockPos();
        for (int dz = 0; dz < MAP_SIZE; dz++) {
            for (int dx = 0; dx < MAP_SIZE; dx++) {
                int x = mapCenter.getX() + dx - MAP_SIZE / 2;
                int z = mapCenter.getZ() + dz - MAP_SIZE / 2;
                if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) continue;
                at.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                map[dz * MAP_SIZE + dx] = 0xFF000000 | level.getBlockState(at).getMapColor(level, at).col;
            }
        }
    }

    private void initCalendar() {
        button(left(), footer(), BUTTON_HEIGHT, BUTTON_HEIGHT, text("calendar.previous"),
                GraphicIcon.PREVIOUS, false, () -> month = month.minusMonths(1));
        button(left() + BUTTON_HEIGHT + GUTTER, footer(), innerWidth() - 2 * (BUTTON_HEIGHT + GUTTER),
                BUTTON_HEIGHT, text("calendar.today"), null, false, () -> month = YearMonth.now());
        button(panel.x() + panel.width() - PADDING - BUTTON_HEIGHT, footer(), BUTTON_HEIGHT, BUTTON_HEIGHT,
                text("calendar.next"), GraphicIcon.NEXT, false, () -> month = month.plusMonths(1));
    }

    private void initMusic() {
        var option = minecraft.options.getSoundSourceOptionInstance(SoundSource.MUSIC);
        playerButton = button(left(), footer() - BUTTON_HEIGHT - GUTTER, innerWidth(), BUTTON_HEIGHT,
                text("music.player"), null, true, () -> {
                    returnToPlayerButton = true;
                    ShBetterUiApi.settings().onlineMusicScreen(this, () -> playerButton).open();
                });
        var music = button(left(), footer(), innerWidth(), BUTTON_HEIGHT, musicAction(option.get()), null, false, () -> {
            if (option.get() > 0) {
                previousMusicVolume = option.get();
                option.set(0.0);
            } else {
                option.set(previousMusicVolume);
            }
            minecraft.options.save();
            rebuildWidgets();
        });
        focus(returnToPlayerButton ? playerButton : music);
        returnToPlayerButton = false;
    }

    private static Component musicAction(double volume) {
        return text(volume > 0 ? "music.mute" : "music.enable");
    }

    private void takePhoto() {
        if (takingPhoto) return;
        takingPhoto = true;
        cameraStatus = text("camera.taking");
        framesToWait = 1;
        pendingPhoto = this;
        dismiss();
    }

    /** Called after world rendering by each loader so the phone stays out of the screenshot. */
    public static void afterFrame() {
        var phone = pendingPhoto;
        if (phone == null || phone.framesToWait-- > 0) return;
        pendingPhoto = null;
        var client = Minecraft.getInstance();
        if (client.screen != null || client.getOverlay() != null || !phone.stillHeld(client)) return;

        client.setScreen(phone);
        try {
            // This frame was rendered before reopening the phone.
            Screenshot.grab(client.gameDirectory, client.getMainRenderTarget(), result -> client.execute(() -> {
                if (result != null && client.player == phone.owner) showScreenshotResult(client, result);
                phone.finishPhoto(result == null ? text("camera.failed") : result);
            }));
        } catch (RuntimeException failure) {
            phone.finishPhoto(text("camera.failed"));
        }
    }

    private void finishPhoto(Component result) {
        takingPhoto = false;
        cameraStatus = result;
        if (minecraft.screen == this && page == App.CAMERA) rebuildWidgets();
    }

    private void initNotes() {
        var note = noteField(new Bounds(left(), contentTop(), innerWidth(), footer() - contentTop() - 14),
                text("notes.placeholder"), text("notes"), PhoneNotes.LIMIT, NOTES.draft(), value -> {
                    NOTES.edit(value);
                    updateSave();
                });
        addRenderableWidget(note);
        save = button(left(), footer(), innerWidth(), BUTTON_HEIGHT, text("notes.save"), null, true, () -> {
            NOTES.save();
            updateSave();
        });
        updateSave();
        focus(note);
    }

    private void updateSave() {
        if (save != null) save.active = NOTES.canSave();
    }

    private void initStopwatch() {
        int half = (innerWidth() - GUTTER) / 2;
        stopwatchAction = button(left(), footer(), half, BUTTON_HEIGHT, Component.empty(), null, true, () -> {
            long now = System.nanoTime();
            if (stopwatchRunning) stopwatchElapsed += now - stopwatchStarted;
            else stopwatchStarted = now;
            stopwatchRunning = !stopwatchRunning;
            updateStopwatchButtons();
        });
        stopwatchReset = button(left() + half + GUTTER, footer(), innerWidth() - half - GUTTER, BUTTON_HEIGHT,
                text("stopwatch.reset"), null, false, () -> {
                    stopwatchRunning = false;
                    stopwatchElapsed = 0;
                    updateStopwatchButtons();
                    focus(stopwatchAction);
                });
        updateStopwatchButtons();
        focus(stopwatchAction);
    }

    private void updateStopwatchButtons() {
        stopwatchAction.setMessage(text(stopwatchRunning ? "stopwatch.pause"
                : stopwatchElapsed == 0 ? "stopwatch.start" : "stopwatch.resume"));
        stopwatchReset.active = stopwatchRunning || stopwatchElapsed > 0;
    }

    private UiButton button(int x, int y, int w, int h, Component label, GraphicIcon icon, boolean primary,
                            Runnable action) {
        var button = new UiButton(x, y, w, h, label, ignored -> action.run(), UiPreferences.configured(), primary);
        if (icon != null) button.setIcon(icon);
        return addRenderableWidget(button);
    }

    private void focus(GuiEventListener widget) {
        setFocused(widget);
        widget.setFocused(true);
    }

    @Override
    public void onClose() {
        if (page == null) {
            dismiss();
            return;
        }
        returnFocus = page;
        page = null;
        rebuildWidgets();
    }

    private void dismiss() {
        minecraft.setScreen(null);
    }

    @Override
    public void tick() {
        if (!stillHeld(minecraft)) {
            dismiss();
            return;
        }
        if (page == App.MAP && owner.blockPosition().distManhattan(mapCenter) >= 4) sampleMap();
        updateSave();
    }

    @Override
    protected Bounds panel() {
        return panel;
    }

    private int left() { return panel.x() + PADDING; }
    private int top() { return panel.y() + PADDING; }
    private int contentTop() { return top() + BUTTON_HEIGHT + GUTTER; }
    private int innerWidth() { return panel.width() - 2 * PADDING; }
    private int footer() { return top() + panel.height() - 2 * PADDING - BUTTON_HEIGHT; }

    @Override
    protected void drawPage(TextDraw draw, RectDraw fill, int foreground, int secondary) {
        int center = panel.x() + panel.width() / 2;
        var heading = page == null ? title : page.label();
        centered(draw, heading.copy().withStyle(ChatFormatting.BOLD), center, top() + 6,
                innerWidth() - 2 * (BUTTON_HEIGHT + 2), foreground);
        if (page == null) {
            for (var app : APPS) {
                Bounds tile = tile(app);
                centered(draw, app.label(), tile.x() + tile.width() / 2, tile.y() + tile.height() - 10, tile.width() + 4,
                        app == App.NOTES && !NOTES.available() ? secondary : foreground);
            }
        } else if (page == App.CAMERA) {
            centered(draw, cameraStatus, center, contentTop() + (footer() - contentTop()) / 2 - 4,
                    innerWidth(), secondary);
        } else if (page == App.PHOTOS) {
            var count = images.length == 0 ? text("photos.empty")
                    : Component.literal((imageIndex + 1) + " / " + images.length);
            centered(draw, count, center, contentTop() + 6, innerWidth() - 2 * BUTTON_HEIGHT, secondary);
            if (images.length > 0) centered(draw, Component.literal(images[imageIndex].getName()), center,
                    contentTop() + (footer() - contentTop()) / 2, innerWidth(), foreground);
        } else if (page == App.MAP) {
            drawMap(fill, center);
        } else if (page == App.CALENDAR) {
            drawCalendar(draw, fill, center, foreground, secondary);
        } else if (page == App.MUSIC) {
            double volume = minecraft.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get();
            centered(draw, text(volume > 0 ? "music.on" : "music.off"), center,
                    contentTop() + (footer() - contentTop() - BUTTON_HEIGHT - GUTTER) / 2 - 4, innerWidth(), secondary);
        } else if (page == App.NOTES && NOTES.status() != null) {
            centered(draw, NOTES.status(), center, footer() - 11, innerWidth(), secondary);
        } else if (page == App.TOOLS) {
            centered(draw, text("stopwatch"), center, contentTop() + 5, innerWidth(), secondary);
            long elapsed = stopwatchElapsed + (stopwatchRunning ? System.nanoTime() - stopwatchStarted : 0);
            long tenths = elapsed / 100_000_000L;
            if (tenths != shownTenths) {
                shownTenths = tenths;
                stopwatchTime = Component.literal(String.format(Locale.ROOT, "%02d:%02d.%d",
                        tenths / 600, tenths / 10 % 60, tenths % 10)).withStyle(ChatFormatting.BOLD);
            }
            centered(draw, stopwatchTime, center, contentTop() + (footer() - contentTop() - 9) / 2, innerWidth(), foreground);
        }
    }

    private void drawMap(RectDraw fill, int center) {
        int cell = Math.max(1, Math.min(innerWidth(), footer() - contentTop() - GUTTER) / MAP_SIZE);
        int size = MAP_SIZE * cell;
        int x = center - size / 2;
        int y = contentTop() + (footer() - contentTop() - size) / 2;
        fill.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF263444);
        for (int z = 0; z < MAP_SIZE; z++) {
            for (int dx = 0; dx < MAP_SIZE; dx++) {
                int color = map[z * MAP_SIZE + dx];
                if (color != 0) fill.fill(x + dx * cell, y + z * cell,
                        x + (dx + 1) * cell, y + (z + 1) * cell, color);
            }
        }
        int middle = y + size / 2;
        fill.fill(center - 3, middle - 3, center + 4, middle + 4, 0xFF1C3C65);
        fill.fill(center - 2, middle - 2, center + 3, middle + 3, 0xFFE6F2FF);
    }

    private void drawCalendar(TextDraw draw, RectDraw fill, int center, int foreground, int secondary) {
        centered(draw, Component.translatable("screen.sh_common_lib.phone.calendar.month", Integer.toString(month.getYear()),
                month.getMonthValue()), center, contentTop(), innerWidth(), foreground);
        int cellWidth = innerWidth() / 7;
        int cellHeight = Math.max(9, (footer() - contentTop() - 16) / 7);
        int gridX = center - 7 * cellWidth / 2;
        int gridY = contentTop() + 13;
        for (int day = 0; day < 7; day++) {
            centered(draw, text("calendar.weekday." + day), gridX + day * cellWidth + cellWidth / 2, gridY,
                    cellWidth, secondary);
        }
        int start = month.atDay(1).getDayOfWeek().getValue() - 1;
        var today = LocalDate.now();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            int index = start + day - 1;
            int x = gridX + index % 7 * cellWidth;
            int y = gridY + (index / 7 + 1) * cellHeight;
            if (today.equals(month.atDay(day))) {
                fill.fill(x, y, x + cellWidth, y + cellHeight, 0xFF29528D);
                centered(draw, Component.literal(Integer.toString(day)), x + cellWidth / 2, y, cellWidth, 0xFFFFFFFF);
            } else {
                centered(draw, Component.literal(Integer.toString(day)), x + cellWidth / 2, y, cellWidth, foreground);
            }
        }
    }

    private void centered(TextDraw draw, Component value, int center, int y, int maxWidth, int color) {
        if (font.width(value) > maxWidth) {
            var clipped = font.plainSubstrByWidth(value.getString(), maxWidth - font.width("…"));
            value = Component.literal(clipped + "…");
        }
        draw.draw(value, center - font.width(value) / 2, y, color);
    }

    @Override public boolean isPauseScreen() { return false; }

    private enum App {
        CAMERA("camera", GraphicIcon.RENDER),
        PHOTOS("photos", GraphicIcon.COLLECTION),
        MESSAGES("messages", GraphicIcon.CHAT),
        MAP("map", GraphicIcon.MAP),
        CALENDAR("calendar", GraphicIcon.CALENDAR),
        NOTES("notes", GraphicIcon.DOCUMENT),
        SETTINGS("settings", GraphicIcon.SETTINGS),
        MUSIC("music", GraphicIcon.MUSIC),
        TOOLS("tools", GraphicIcon.TOOL);

        private final String key;
        private final GraphicIcon icon;

        App(String key, GraphicIcon icon) {
            this.key = key;
            this.icon = icon;
        }

        Component label() { return text(key); }
    }
}
