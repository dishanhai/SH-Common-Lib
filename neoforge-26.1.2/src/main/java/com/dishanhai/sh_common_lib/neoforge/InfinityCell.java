package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A single target stored with the item on both the client and the server. */
final class InfinityCell extends Item {
    private static final String ITEM_ID = "infinity_cell";
    private static final String TEXT = "item.sh_common_lib.infinity_cell";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShCommonLib.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ShCommonLib.MOD_ID);
    static final DeferredItem<InfinityCell> ITEM = ITEMS.registerItem(ITEM_ID, InfinityCell::new, properties -> properties.stacksTo(1));
    private static final Supplier<DataComponentType<Target>> TARGET = COMPONENTS.registerComponentType(
            "infinity_cell_target", builder -> builder.persistent(Target.CODEC).networkSynchronized(Target.STREAM_CODEC));

    private InfinityCell(Properties properties) {
        super(properties);
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
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        var target = target(stack);
        return target != null && target.kind() == Kind.CHEMICAL
                ? Optional.empty() : InfinityCellTooltip.create(target);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        String key = target(stack) == null ? ".unbound" : ".single_target";
        lines.accept(Component.translatable(TEXT + key).withStyle(ChatFormatting.GRAY));
    }

    enum Kind implements StringRepresentable {
        ITEM("item"),
        FLUID("fluid"),
        CHEMICAL("chemical");

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    record Target(Kind kind, Identifier id, DataComponentPatch components) {
        private static final Codec<Target> TYPED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                StringRepresentable.fromEnum(Kind::values).fieldOf("kind").forGetter(Target::kind),
                Identifier.CODEC.fieldOf("id").forGetter(Target::id),
                DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                        .forGetter(Target::components)
        ).apply(instance, Target::new));
        private static final Codec<Target> LEGACY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("fluid").forGetter((Target target) -> target.kind() == Kind.FLUID),
                Identifier.CODEC.fieldOf("id").forGetter(Target::id),
                DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                        .forGetter(Target::components)
        ).apply(instance, (fluid, id, components) -> new Target(fluid ? Kind.FLUID : Kind.ITEM, id, components)));
        private static final Codec<Target> CODEC = Codec.withAlternative(TYPED_CODEC, LEGACY_CODEC);
        // ITEM and FLUID retain the old boolean wire values (0 and 1).
        private static final StreamCodec<FriendlyByteBuf, Kind> KIND_STREAM_CODEC =
                StreamCodec.of(FriendlyByteBuf::writeEnum, buffer -> buffer.readEnum(Kind.class));
        private static final StreamCodec<RegistryFriendlyByteBuf, Target> STREAM_CODEC = StreamCodec.composite(
                KIND_STREAM_CODEC, Target::kind,
                Identifier.STREAM_CODEC, Target::id,
                DataComponentPatch.STREAM_CODEC, Target::components,
                Target::new);

        static Target fromIngredient(Object ingredient) {
            if (ingredient instanceof ItemStack stack && !stack.isEmpty() && !stack.is(ITEM.get())) {
                return new Target(Kind.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getComponentsPatch());
            }
            if (ingredient instanceof FluidStack stack && !stack.isEmpty()) {
                return new Target(Kind.FLUID, BuiltInRegistries.FLUID.getKey(stack.getFluid()), stack.getComponentsPatch());
            }
            return null;
        }

        ItemStack itemStack() {
            return BuiltInRegistries.ITEM.get(id)
                    .filter(holder -> holder.value() != ITEM.get())
                    .map(holder -> new ItemStack(holder, 1, components)).orElse(ItemStack.EMPTY);
        }

        FluidStack fluidStack() {
            return BuiltInRegistries.FLUID.get(id)
                    .map(holder -> new FluidStack(holder, 1, components)).orElse(FluidStack.EMPTY);
        }

        Component name() {
            return switch (kind) {
                case ITEM -> {
                    var stack = itemStack();
                    yield stack.isEmpty() ? Component.literal(id.toString()) : stack.getHoverName();
                }
                case FLUID -> {
                    var stack = fluidStack();
                    yield stack.isEmpty() ? Component.literal(id.toString()) : stack.getHoverName();
                }
                case CHEMICAL -> Component.literal(id.toString());
            };
        }
    }
}
