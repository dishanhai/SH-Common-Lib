// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.InlineNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichObjectNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.List;
import java.util.Optional;

record InfinityCellTooltip(InfinityCell.Target target) implements TooltipComponent {
    private static final int SIZE = 16;
    private static final UiId MATERIAL = UiId.parse("sh_common_lib:infinity_cell/material");
    private static final UiId CHEMICAL = UiId.parse("sh_common_lib:chemical");

    static Optional<TooltipComponent> create(InfinityCell.Target target) {
        if (target == null) return Optional.empty();
        return switch (target.kind()) {
            case ITEM -> target.itemStack().isEmpty() ? Optional.empty() : Optional.of(new InfinityCellTooltip(target));
            case FLUID -> target.fluidStack().isEmpty() ? Optional.empty() : Optional.of(new InfinityCellTooltip(target));
            case CHEMICAL -> Optional.of(new InfinityCellTooltip(target));
        };
    }

    RichLayoutSnapshot layout(Font font) {
        UiId id = UiId.parse(target.id().toString());
        String alt = target.name().getString();
        InlineNode material = switch (target.kind()) {
            case ITEM -> new RichObjectNode(MATERIAL, new RichObjectNode.Item(id, 1), SIZE, SIZE, SIZE, alt);
            case FLUID -> new RichObjectNode(MATERIAL, new RichObjectNode.Fluid(id), SIZE, SIZE, SIZE, alt);
            case CHEMICAL -> new RichExtensionNode(MATERIAL, CHEMICAL, 1, SIZE, SIZE, SIZE, alt, id.toString());
        };
        return RichTextLayout.layout(new RichDocument(List.of(material)), SIZE, new RichTextLayout.FontMetrics() {
            @Override public int ascent() { return font.lineHeight - 2; }
            @Override public int descent() { return 2; }
            @Override public int width(String text) { return font.width(text); }
        });
    }
}
