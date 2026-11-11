package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Supplier;

/** A single target stored with the item on both the client and the server. */
final class InfinityCell extends Item {
    private static final String ITEM_ID = "infinity_cell";
    private static final String TEXT = "item.sh_common_lib.infinity_cell";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShCommonLib.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ShCommonLib.MOD_ID);
    static final DeferredItem<InfinityCell> ITEM = ITEMS.register(ITEM_ID, InfinityCell::new);
    private static final Supplier<DataComponentType<Target>> TARGET = COMPONENTS.registerComponentType(
            "infinity_cell_target", builder -> builder.persistent(Target.CODEC).networkSynchronized(Target.STREAM_CODEC));

    private InfinityCell() {
        super(new Properties().stacksTo(1));
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
    }

    static ItemStack create(Target target) {
        var stack = new ItemStack(ITEM.get());
        stack.set(TARGET.get(), target);
        return stack;
    }

    static Target target(ItemStack stack) {
        return stack.is(ITEM.get()) ? stack.get(TARGET.get()) : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        var target = target(stack);
        return target == null ? super.getName(stack) : Component.translatable(TEXT + ".bound", target.name());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String key = target(stack) == null ? ".unbound" : ".single_target";
        lines.add(Component.translatable(TEXT + key).withStyle(ChatFormatting.GRAY));
    }

    record Target(boolean fluid, ResourceLocation id, DataComponentPatch components) {
        private static final Codec<Target> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("fluid").forGetter(Target::fluid),
                ResourceLocation.CODEC.fieldOf("id").forGetter(Target::id),
                DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                        .forGetter(Target::components)
        ).apply(instance, Target::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, Target> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Target::fluid,
                ResourceLocation.STREAM_CODEC, Target::id,
                DataComponentPatch.STREAM_CODEC, Target::components,
                Target::new);

        static Target fromIngredient(Object ingredient) {
            if (ingredient instanceof ItemStack stack && !stack.isEmpty() && !stack.is(ITEM.get())) {
                return new Target(false, BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getComponentsPatch());
            }
            if (ingredient instanceof FluidStack stack && !stack.isEmpty()) {
                return new Target(true, BuiltInRegistries.FLUID.getKey(stack.getFluid()), stack.getComponentsPatch());
            }
            return null;
        }

        ItemStack itemStack() {
            return BuiltInRegistries.ITEM.getHolder(id)
                    .filter(holder -> holder.value() != ITEM.get())
                    .map(holder -> new ItemStack(holder, 1, components)).orElse(ItemStack.EMPTY);
        }

        FluidStack fluidStack() {
            return BuiltInRegistries.FLUID.getHolder(id)
                    .map(holder -> new FluidStack(holder, 1, components)).orElse(FluidStack.EMPTY);
        }

        Component name() {
            if (fluid) {
                var stack = fluidStack();
                if (!stack.isEmpty()) return stack.getHoverName();
            } else {
                var stack = itemStack();
                if (!stack.isEmpty()) return stack.getHoverName();
            }
            return Component.literal(id.toString());
        }
    }
}
