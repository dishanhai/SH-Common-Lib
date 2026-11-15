// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Optional;

/** A single material target persisted in Forge 1.20.1 item NBT. */
final class InfinityCell extends Item {
    private static final String TEXT = "item.sh_common_lib.infinity_cell";
    private static final String TARGET = "InfinityCellTarget";
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ShCommonLib.MOD_ID);
    static final RegistryObject<InfinityCell> ITEM = ITEMS.register("infinity_cell", InfinityCell::new);

    private InfinityCell() {
        super(new Properties().stacksTo(1));
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    static ItemStack create(Target target) {
        var stack = new ItemStack(ITEM.get());
        var data = new CompoundTag();
        data.putString("kind", target.kind().id);
        data.putString("id", target.id().toString());
        if (target.tag() != null) data.put("tag", target.tag().copy());
        stack.addTagElement(TARGET, data);
        return stack;
    }

    static Target target(ItemStack stack) {
        if (!stack.is(ITEM.get())) return null;
        var data = stack.getTagElement(TARGET);
        if (data == null) return null;
        var id = ResourceLocation.tryParse(data.getString("id"));
        if (id == null || id.getPath().isEmpty()) return null;
        var tag = data.contains("tag", Tag.TAG_COMPOUND) ? data.getCompound("tag").copy() : null;
        return switch (data.getString("kind")) {
            case "item" -> new Target(Kind.ITEM, id, tag);
            case "fluid" -> new Target(Kind.FLUID, id, tag);
            case "chemical" -> new Target(Kind.CHEMICAL, id, tag);
            default -> null;
        };
    }

    static boolean supportsChemicals() {
        var mods = ModList.get();
        return mods.isLoaded("ae2") && mods.isLoaded("mekanism") && mods.isLoaded("appmek");
    }

    @Override
    public Component getName(ItemStack stack) {
        var target = target(stack);
        return target == null ? super.getName(stack) : Component.translatable(TEXT + ".bound", target.name());
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        var target = target(stack);
        if (target == null) return Optional.empty();
        boolean available = switch (target.kind()) {
            case ITEM -> !target.itemStack().isEmpty();
            case FLUID -> !target.fluidStack().isEmpty();
            case CHEMICAL -> supportsChemicals() && AppliedMekanisticsCells.hasChemical(target);
        };
        return available ? Optional.of(new MaterialTooltip(target)) : Optional.empty();
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        String key = target(stack) == null ? ".unbound" : ".single_target";
        lines.add(Component.translatable(TEXT + key).withStyle(ChatFormatting.GRAY));
    }

    record MaterialTooltip(Target target) implements TooltipComponent {}

    enum Kind {
        ITEM("item"), FLUID("fluid"), CHEMICAL("chemical");

        private final String id;

        Kind(String id) { this.id = id; }
    }

    record Target(Kind kind, ResourceLocation id, CompoundTag tag) {
        static Target fromIngredient(Object ingredient) {
            if (ingredient instanceof ItemStack stack && !stack.isEmpty() && !stack.is(ITEM.get())) {
                var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                return id == null ? null : new Target(Kind.ITEM, id, copyTag(stack.getTag()));
            }
            if (ingredient instanceof FluidStack stack && !stack.isEmpty()) {
                var id = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
                return id == null ? null : new Target(Kind.FLUID, id, copyTag(stack.getTag()));
            }
            return supportsChemicals() ? AppliedMekanisticsCells.fromIngredient(ingredient) : null;
        }

        ItemStack itemStack() {
            var item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == ITEM.get()) return ItemStack.EMPTY;
            var stack = new ItemStack(item);
            stack.setTag(copyTag(tag));
            return stack;
        }

        FluidStack fluidStack() {
            var type = ForgeRegistries.FLUIDS.getValue(id);
            return type == null ? FluidStack.EMPTY : new FluidStack(type, 1, copyTag(tag));
        }

        Component name() {
            if (kind == Kind.CHEMICAL) {
                return supportsChemicals() ? AppliedMekanisticsCells.name(this) : Component.literal(id.toString());
            }
            if (kind == Kind.FLUID) {
                var stack = fluidStack();
                return stack.isEmpty() ? Component.literal(id.toString()) : stack.getDisplayName();
            }
            var stack = itemStack();
            return stack.isEmpty() ? Component.literal(id.toString()) : stack.getHoverName();
        }

        private static CompoundTag copyTag(CompoundTag tag) {
            return tag == null ? null : tag.copy();
        }
    }
}
