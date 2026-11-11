package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.compat.jei.JeiShortcuts;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;

final class NeoForgeJeiShortcuts {
    private static final String DATA_KIND = "components";

    private NeoForgeJeiShortcuts() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ScreenEvent.KeyPressed.Pre event) -> {
            if (JeiShortcuts.keyPressed(event.getScreen(), event.getKeyCode(), event.getModifiers(),
                    true, DATA_KIND, NeoForgeJeiShortcuts::serializeData)) {
                event.setCanceled(true);
            }
        });
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
