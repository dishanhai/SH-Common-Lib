// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.block;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import top.theillusivec4.curios.api.CuriosApi;

public final class FumoHeadPlatform {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ShCommonLib.MOD_ID, "fumo_head"),
            () -> "1", NetworkRegistry.acceptMissingOr("1"), NetworkRegistry.acceptMissingOr("1"));

    private FumoHeadPlatform() {}

    public static void register() {
        CHANNEL.messageBuilder(FumoHeadJump.State.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FumoHeadJump.State::write)
                .decoder(FumoHeadJump.State::read)
                .consumerMainThread((message, context) -> FumoHeadClient.receive(message)).add();

        var jump = new FumoHeadJump(FumoHeadPlatform::send);
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener(EventPriority.LOWEST, (RegisterCommandsEvent event) ->
                jump.register(event.getDispatcher(), !ModList.get().isLoaded("gtlcore")));
        bus.addListener((TickEvent.PlayerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) jump.tick(event.player);
        });
        bus.addListener((PlayerEvent.StartTracking event) -> jump.startTracking(event.getEntity(), event.getTarget()));
        bus.addListener((PlayerEvent.StopTracking event) -> jump.stopTracking(event.getEntity(), event.getTarget()));
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> jump.stop(event.getEntity()));
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> jump.logout(event.getEntity()));
        bus.addListener((ServerStoppedEvent event) -> jump.clear());
    }

    public static void registerClient() {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) FumoHeadClient.tick();
        });
    }

    private static void send(ServerPlayer viewer, FumoHeadJump.State state) {
        var connection = viewer.connection.connection;
        if (connection.isConnected() && CHANNEL.isRemotePresent(connection)) {
            CHANNEL.sendTo(state, connection, NetworkDirection.PLAY_TO_CLIENT);
        }
    }

    static FumoHeadItem findCurio(LivingEntity wearer) {
        return ModList.get().isLoaded("curios") ? Curios.find(wearer) : null;
    }

    private static final class Curios {
        private static FumoHeadItem find(LivingEntity wearer) {
            var inventory = CuriosApi.getCuriosInventory(wearer).orElse(null);
            if (inventory != null) {
                for (var slot : inventory.findCurios("head")) {
                    if (slot.stack().getItem() instanceof FumoHeadItem fumo) return fumo;
                }
            }
            return null;
        }
    }
}
