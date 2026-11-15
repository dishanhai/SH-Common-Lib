// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import appeng.api.client.AEKeyRendering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.FastColor;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

final class InfinityCellDecoration {
    private static final int MATERIAL_SIZE = 16;

    private InfinityCellDecoration() {}

    static void register(RegisterItemDecorationsEvent event) {
        event.register(InfinityCell.ITEM.get(), (graphics, font, stack, x, y) -> {
            var target = InfinityCell.target(stack);
            return target != null && renderMaterial(graphics, target, x, y, MATERIAL_SIZE / 2, 200);
        });
    }

    static void registerTooltip(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(InfinityCell.MaterialTooltip.class, Tooltip::new);
    }

    private static boolean renderMaterial(GuiGraphics graphics, InfinityCell.Target target,
                                          int x, int y, int size, int depth) {
        if (target.kind() == InfinityCell.Kind.CHEMICAL) {
            return InfinityCell.supportsChemicals() && Chemical.render(graphics, target, x, y, size, depth);
        }
        if (target.kind() == InfinityCell.Kind.FLUID) {
            var fluid = target.fluidStack();
            if (fluid.isEmpty()) return false;
            var extension = IClientFluidTypeExtensions.of(fluid.getFluid());
            var texture = extension.getStillTexture(fluid);
            if (texture == null) return false;
            var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
            int tint = extension.getTintColor(fluid);
            graphics.blit(x, y, depth, size, size, sprite,
                    FastColor.ARGB32.red(tint) / 255F, FastColor.ARGB32.green(tint) / 255F,
                    FastColor.ARGB32.blue(tint) / 255F, FastColor.ARGB32.alpha(tint) / 255F);
            return true;
        }

        var item = target.itemStack();
        if (item.isEmpty()) return false;
        var pose = graphics.pose();
        pose.pushPose();
        try {
            // GuiGraphics draws the item model at z=150 before applying the pose.
            pose.translate(x, y, depth - 150);
            float scale = size / (float) MATERIAL_SIZE;
            pose.scale(scale, scale, 1);
            graphics.renderItem(item, 0, 0);
        } finally {
            pose.popPose();
        }
        return true;
    }

    private static final class Chemical {
        private static boolean render(GuiGraphics graphics, InfinityCell.Target target, int x, int y, int size, int depth) {
            var key = AppliedMekanisticsCells.key(target);
            if (key == null || AEKeyRendering.get(key.getType()) == null) return false;
            var pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(x, y, depth);
                float scale = size / (float) MATERIAL_SIZE;
                pose.scale(scale, scale, 1);
                AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, 0, 0, key);
            } finally {
                pose.popPose();
            }
            return true;
        }
    }

    private record Tooltip(InfinityCell.MaterialTooltip material) implements ClientTooltipComponent {
        @Override public int getHeight() { return MATERIAL_SIZE; }
        @Override public int getWidth(Font font) { return MATERIAL_SIZE; }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            renderMaterial(graphics, material.target(), x, y, MATERIAL_SIZE, 1);
        }
    }
}
