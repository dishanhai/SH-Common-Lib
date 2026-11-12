package com.dishanhai.sh_common_lib.neoforge;

import appeng.api.client.AEKeyRendering;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.FastColor;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

final class InfinityCellDecoration {
    private static final int ICON_SIZE = 8;
    private static final int ICON_DEPTH = 200;

    private InfinityCellDecoration() {}

    static void register(RegisterItemDecorationsEvent event) {
        event.register(InfinityCell.ITEM.get(), (graphics, font, stack, x, y) -> {
            var target = InfinityCell.target(stack);
            return target != null && renderMaterial(graphics, target, x, y, ICON_SIZE, ICON_DEPTH);
        });
    }

    static void registerTooltip(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(InfinityCellTooltip.class, Tooltip::new);
    }

    private static boolean renderMaterial(GuiGraphics graphics, InfinityCell.Target target,
                                          int x, int y, int size, int depth) {
        return switch (target.kind()) {
            case ITEM -> renderItem(graphics, target, x, y, size, depth);
            case FLUID -> renderFluid(graphics, target, x, y, size, depth);
            case CHEMICAL -> InfinityCell.supportsChemicals() && Chemical.render(graphics, target, x, y, size, depth);
        };
    }

    private static boolean renderItem(GuiGraphics graphics, InfinityCell.Target target,
                                      int x, int y, int size, int depth) {
        var item = target.itemStack();
        if (item.isEmpty()) return false;

        var pose = graphics.pose();
        pose.pushPose();
        try {
            // GuiGraphics renders item models at z=150 before applying the pose.
            pose.translate(x, y, depth - 150);
            float scale = size / 16F;
            pose.scale(scale, scale, 1);
            graphics.renderItem(item, 0, 0);
        } finally {
            pose.popPose();
        }
        return true;
    }

    private static boolean renderFluid(GuiGraphics graphics, InfinityCell.Target target,
                                       int x, int y, int size, int depth) {
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

    private static final class Chemical {
        private static boolean render(GuiGraphics graphics, InfinityCell.Target target,
                                      int x, int y, int size, int depth) {
            var key = AppliedMekanisticsCells.key(target.id());
            if (key == null || AEKeyRendering.get(key.getType()) == null) return false;

            var pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(x, y, depth);
                float scale = size / 16F;
                pose.scale(scale, scale, 1);
                AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, 0, 0, key);
            } finally {
                pose.popPose();
            }
            return true;
        }
    }

    private record Tooltip(InfinityCellTooltip content, RichLayoutSnapshot layout) implements ClientTooltipComponent {
        private Tooltip(InfinityCellTooltip content) {
            this(content, content.layout(Minecraft.getInstance().font));
        }

        @Override public int getHeight() { return layout.height(); }
        @Override public int getWidth(Font font) { return layout.width(); }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            for (var line : layout.lines()) {
                for (var fragment : line.fragments()) {
                    var box = fragment.bounds();
                    renderMaterial(graphics, content.target(), x + box.x(), y + box.y(), box.width(), 1);
                }
            }
        }
    }
}
