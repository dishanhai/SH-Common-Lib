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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;
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
        if (target != null && target.kind() == Kind.CHEMICAL
                && (!supportsChemicals() || !AppliedMekanisticsCells.hasChemical(target.id()))) {
            return Optional.empty();
        }
        return InfinityCellTooltip.create(target);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String key = target(stack) == null ? ".unbound" : ".single_target";
        lines.add(Component.translatable(TEXT + key).withStyle(ChatFormatting.GRAY));
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

    record Target(Kind kind, ResourceLocation id, DataComponentPatch components) {
        private static final Codec<Target> TYPED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                StringRepresentable.fromEnum(Kind::values).fieldOf("kind").forGetter(Target::kind),
                ResourceLocation.CODEC.fieldOf("id").forGetter(Target::id),
                DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                        .forGetter(Target::components)
        ).apply(instance, Target::new));
        private static final Codec<Target> LEGACY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("fluid").forGetter((Target target) -> target.kind() == Kind.FLUID),
                ResourceLocation.CODEC.fieldOf("id").forGetter(Target::id),
                DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                        .forGetter(Target::components)
        ).apply(instance, (fluid, id, components) -> new Target(fluid ? Kind.FLUID : Kind.ITEM, id, components)));
        private static final Codec<Target> CODEC = Codec.withAlternative(TYPED_CODEC, LEGACY_CODEC);
        // ITEM and FLUID retain the old boolean wire values (0 and 1).
        private static final StreamCodec<FriendlyByteBuf, Kind> KIND_STREAM_CODEC =
                StreamCodec.of(FriendlyByteBuf::writeEnum, buffer -> buffer.readEnum(Kind.class));
        private static final StreamCodec<RegistryFriendlyByteBuf, Target> STREAM_CODEC = StreamCodec.composite(
                KIND_STREAM_CODEC, Target::kind,
                ResourceLocation.STREAM_CODEC, Target::id,
                DataComponentPatch.STREAM_CODEC, Target::components,
                Target::new);

        static Target fromIngredient(Object ingredient) {
            if (ingredient instanceof ItemStack stack && !stack.isEmpty() && !stack.is(ITEM.get())) {
                return new Target(Kind.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getComponentsPatch());
            }
            if (ingredient instanceof FluidStack stack && !stack.isEmpty()) {
                return new Target(Kind.FLUID, BuiltInRegistries.FLUID.getKey(stack.getFluid()), stack.getComponentsPatch());
            }
            return supportsChemicals() ? AppliedMekanisticsCells.fromIngredient(ingredient) : null;
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
            return switch (kind) {
                case ITEM -> {
                    var stack = itemStack();
                    yield stack.isEmpty() ? Component.literal(id.toString()) : stack.getHoverName();
                }
                case FLUID -> {
                    var stack = fluidStack();
                    yield stack.isEmpty() ? Component.literal(id.toString()) : stack.getHoverName();
                }
                case CHEMICAL -> supportsChemicals()
                        ? AppliedMekanisticsCells.name(id) : Component.literal(id.toString());
            };
        }
    }
}
