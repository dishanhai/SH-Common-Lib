package com.dishanhai.sh_common_lib.api.parcel;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** NeoForge registration and data component adapter. Use {@link Parcels} to access parcels. */
public final class ParcelStorage {
    private static final String SEAL = "ShCommonLibSeal";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShCommonLib.MOD_ID);
    private static final DeferredItem<ParcelItem> PARCEL = ITEMS.register(Parcels.ITEM_ID, ParcelItem::new);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ShCommonLib.MOD_ID);
    private static final Supplier<MenuType<ParcelMenu>> EDITOR =
            MENUS.register(Parcels.ITEM_ID, () -> IMenuTypeExtension.create(ParcelMenu::fromNetwork));

    private ParcelStorage() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(item());
        });
        modBus.addListener((RegisterPayloadHandlersEvent event) ->
                event.registrar("1").playToServer(OpenPayload.TYPE, OpenPayload.CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) payload.request.handle(player);
                }));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ParcelClient.register(modBus, request -> PacketDistributor.sendToServer(new OpenPayload(request)));
        }
    }

    static Item item() {
        return PARCEL.get();
    }

    static MenuType<ParcelMenu> menuType() {
        return EDITOR.get();
    }

    static void openEditor(ServerPlayer player, InteractionHand hand, ItemStack parcel) {
        int slot = ParcelMenu.heldSlot(player.getInventory(), hand);
        player.openMenu(ParcelMenu.provider(slot, parcel), buffer -> buffer.writeVarInt(slot));
    }

    static UUID sealId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(SEAL)) return null;
        CompoundTag tag = data.copyTag();
        return tag.hasUUID(SEAL) ? tag.getUUID(SEAL) : null;
    }

    static void seal(ItemStack stack, UUID sealId) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(SEAL, sealId));
    }

    static List<ItemStack> read(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).stream().toList();
    }

    static void write(ItemStack stack, List<ItemStack> contents) {
        ItemContainerContents stored = ItemContainerContents.fromItems(contents);
        if (stored == ItemContainerContents.EMPTY) stack.remove(DataComponents.CONTAINER);
        else stack.set(DataComponents.CONTAINER, stored);
    }

    private record OpenPayload(OpenParcelRequest request) implements CustomPacketPayload {
        private static final Type<OpenPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(ShCommonLib.MOD_ID, "open_parcel"));
        private static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC = StreamCodec.of(
                (buffer, payload) -> payload.request.write(buffer),
                buffer -> new OpenPayload(OpenParcelRequest.read(buffer)));

        @Override
        public Type<OpenPayload> type() {
            return TYPE;
        }
    }
}
