// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

final class InfinityCellDecoration {
    private static final int ICON_SIZE = 8;

    private InfinityCellDecoration() {}

    static void register(RegisterItemDecorationsEvent event) {
        event.register(InfinityCell.ITEM.get(), (graphics, font, stack, x, y) -> {
            var target = InfinityCell.target(stack);
            if (target == null) return false;
            drawMaterial(graphics, target, x, y, ICON_SIZE);
            return false;
        });
    }

    static void registerTooltip(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(InfinityCellTooltip.class, Tooltip::new);
    }

    private static void drawMaterial(GuiGraphicsExtractor graphics, InfinityCell.Target target, int x, int y, int size) {
        switch (target.kind()) {
            case ITEM -> drawItem(graphics, target, x, y, size);
            case FLUID -> drawFluid(graphics, target, x, y, size);
            case CHEMICAL -> { }
        }
    }

    private static void drawItem(GuiGraphicsExtractor graphics, InfinityCell.Target target, int x, int y, int size) {
        var item = target.itemStack();
        if (item.isEmpty()) return;
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y).scale(size / 16F);
            graphics.item(item, 0, 0);
        } finally {
            pose.popMatrix();
        }
    }

    private static void drawFluid(GuiGraphicsExtractor graphics, InfinityCell.Target target, int x, int y, int size) {
        var fluid = target.fluidStack();
        if (fluid.isEmpty()) return;
        var state = fluid.getFluid().defaultFluidState();
        var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(state);
        var tintSource = model.fluidTintSource();
        int tint = tintSource == null ? 0xFFFFFFFF : tintSource.colorAsStack(fluid);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, model.stillMaterial().sprite(), x, y, size, size, tint);
    }

    private record Tooltip(InfinityCellTooltip content, RichLayoutSnapshot layout) implements ClientTooltipComponent {
        private Tooltip(InfinityCellTooltip content) {
            this(content, content.layout(Minecraft.getInstance().font));
        }

        @Override public int getHeight(Font font) { return layout.height(); }
        @Override public int getWidth(Font font) { return layout.width(); }

        @Override
        public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
            for (var line : layout.lines()) {
                for (var fragment : line.fragments()) {
                    var box = fragment.bounds();
                    drawMaterial(graphics, content.target(), x + box.x(), y + box.y(), box.width());
                }
            }
        }
    }
}
