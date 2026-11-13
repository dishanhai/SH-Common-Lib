package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.compat.jei.JeiShortcuts;
import com.dishanhai.sh_common_lib.client.jei.JeiSearchAssistant;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;

final class NeoForgeJeiShortcuts {
    private static final String DATA_KIND = "components";

    private NeoForgeJeiShortcuts() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ClientChatReceivedEvent event) -> {
            if (!event.isSystem()) JeiSearchAssistant.chatReceived(event.getMessage().getString());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ScreenEvent.KeyPressed.Pre event) -> {
            if (JeiSearchAssistant.keyPressed(event.getScreen(), event.getKeyCode(), event.getModifiers())
                    || JeiShortcuts.keyPressed(event.getScreen(), event.getKeyCode(), event.getModifiers(),
                    true, DATA_KIND, NeoForgeJeiShortcuts::serializeData)) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ScreenEvent.MouseButtonPressed.Pre event) -> {
            if (JeiSearchAssistant.mouseClicked(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton()))
                event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ScreenEvent.MouseScrolled.Pre event) -> {
            if (JeiSearchAssistant.mouseScrolled(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getScrollDeltaY()))
                event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ScreenEvent.CharacterTyped.Pre event) ->
                JeiSearchAssistant.characterTyped(event.getScreen()));
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ScreenEvent.Render.Pre event) ->
                JeiSearchAssistant.beforeRender(event.getScreen()));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ScreenEvent.Render.Post event) ->
                JeiSearchAssistant.render(event.getScreen(), event.getGuiGraphics(), event.getMouseX(), event.getMouseY()));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Closing event) -> JeiSearchAssistant.close(event.getScreen()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> JeiSearchAssistant.tick());
        var infinityCells = ModList.get().isLoaded("ae2") ? NeoForgeJeiInfinityCells.register() : null;
        NeoForge.EVENT_BUS.addListener((RenderTooltipEvent.GatherComponents event) ->
                JeiShortcuts.appendTooltipHints(event.getItemStack(), event.getTooltipElements(), true, DATA_KIND,
                        ingredient -> ingredient instanceof ItemStack || ingredient instanceof FluidStack,
                        ingredient -> infinityCells != null && infinityCells.hasHint(ingredient)));
    }

    private static String serializeData(Object ingredient) {
        DataComponentPatch patch;
        if (ingredient instanceof ItemStack stack) {
            patch = stack.getComponentsPatch();
        } else if (ingredient instanceof FluidStack stack) {
            patch = stack.getComponentsPatch();
        } else {
            return null;
        }
        if (patch.isEmpty()) return "";
        var player = Minecraft.getInstance().player;
        if (player == null) return "";
        var ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        return DataComponentPatch.CODEC.encodeStart(ops, patch).result()
                .filter(tag -> tag instanceof CompoundTag compound && !compound.isEmpty())
                .map(Object::toString).orElse("");
    }
}
