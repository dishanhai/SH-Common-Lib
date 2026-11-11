package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.compat.jei.JeiShortcuts;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fluids.FluidStack;

final class ForgeJeiShortcuts {
    private static final String DATA_KIND = "nbt";

    private ForgeJeiShortcuts() {}

    static void register(boolean ownsShortcuts) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ScreenEvent.KeyPressed.Pre event) -> {
            if (JeiShortcuts.keyPressed(event.getScreen(), event.getKeyCode(), event.getModifiers(),
                    ownsShortcuts, DATA_KIND, ForgeJeiShortcuts::serializeData)) {
                event.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RenderTooltipEvent.GatherComponents event) ->
                JeiShortcuts.appendTooltipHints(event.getItemStack(), event.getTooltipElements(),
                        ownsShortcuts, DATA_KIND,
                        ingredient -> ingredient instanceof ItemStack || ingredient instanceof FluidStack,
                        ingredient -> false));
    }

    private static String serializeData(Object ingredient) {
        CompoundTag tag;
        if (ingredient instanceof ItemStack stack) {
            tag = stack.getTag();
        } else if (ingredient instanceof FluidStack stack) {
            tag = stack.getTag();
        } else {
            return null;
        }
        return tag == null || tag.isEmpty() ? "" : tag.toString();
    }
}
