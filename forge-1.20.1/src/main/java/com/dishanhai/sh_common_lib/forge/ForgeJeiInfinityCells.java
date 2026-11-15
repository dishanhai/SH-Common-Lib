// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.compat.jei.JeiShortcuts;
import com.mojang.logging.LogUtils;
import mezz.jei.common.Internal;
import mezz.jei.gui.util.CommandUtil;
import mezz.jei.gui.util.GiveAmount;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

final class ForgeJeiInfinityCells {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CLICK_TOLERANCE = 4;

    private PendingClick pending;
    private boolean compatible = true;

    static ForgeJeiInfinityCells register() {
        var handler = new ForgeJeiInfinityCells();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, handler::mousePressed);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, handler::mouseReleased);
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Opening event) -> handler.pending = null);
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Closing event) -> handler.pending = null);
        return handler;
    }

    boolean hasHint(Object ingredient) {
        return compatible && InfinityCell.Target.fromIngredient(ingredient) != null;
    }

    private void mousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        pending = null;
        if (!Screen.hasControlDown() || !Screen.hasShiftDown() || !compatible) return;

        try {
            var player = Minecraft.getInstance().player;
            if (player == null || !Internal.getClientToggleState().isCheatItemsEnabled()) return;
            var configs = Internal.getOptionalJeiClientConfigs().orElse(null);
            if (configs == null) return;

            var target = targetUnderMouse(event.getScreen(), event.getMouseX(), event.getMouseY());
            if (target == null) return;
            var command = new CommandUtil(configs.getClientConfig(), Internal.getServerConnection());
            pending = new PendingClick(event.getScreen(), event.getMouseX(), event.getMouseY(), target, command);
            event.setCanceled(true);
        } catch (LinkageError error) {
            disable(error);
        }
    }

    private void mouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || pending == null) return;
        PendingClick click = pending;
        pending = null;
        if (click.screen() != event.getScreen()) return;
        event.setCanceled(true);

        try {
            var player = Minecraft.getInstance().player;
            if (player == null || !Internal.getClientToggleState().isCheatItemsEnabled()
                    || Math.abs(event.getMouseX() - click.x()) > CLICK_TOLERANCE
                    || Math.abs(event.getMouseY() - click.y()) > CLICK_TOLERANCE
                    || !click.target().equals(targetUnderMouse(event.getScreen(), event.getMouseX(), event.getMouseY()))) {
                return;
            }
            click.command().giveStack(InfinityCell.create(click.target()), GiveAmount.ONE);
            player.displayClientMessage(Component.translatable("message.sh_common_lib.jei.cell_requested",
                    click.target().name()), true);
        } catch (LinkageError error) {
            disable(error);
        }
    }

    private void disable(LinkageError error) {
        compatible = false;
        LOGGER.warn("Disabling the JEI infinity cell shortcut because the installed JEI or AE2 API is incompatible", error);
    }

    private static InfinityCell.Target targetUnderMouse(Screen screen, double x, double y) {
        return JeiShortcuts.hovered(screen, x, y)
                .map(typed -> InfinityCell.Target.fromIngredient(typed.getIngredient())).orElse(null);
    }

    private record PendingClick(Screen screen, double x, double y, InfinityCell.Target target, CommandUtil command) {}
}
