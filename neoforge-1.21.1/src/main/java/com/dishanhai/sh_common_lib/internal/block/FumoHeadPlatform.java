// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.block;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import top.theillusivec4.curios.api.CuriosApi;

public final class FumoHeadPlatform {
    private FumoHeadPlatform() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").optional()
                .playToClient(FumoHeadPayload.TYPE, FumoHeadPayload.CODEC,
                        (message, context) -> FumoHeadClient.receive(message.state())));

        var jump = new FumoHeadJump(FumoHeadPlatform::send);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.LOWEST, (RegisterCommandsEvent event) ->
                jump.register(event.getDispatcher(), !ModList.get().isLoaded("gtlcore")));
        bus.addListener((PlayerTickEvent.Post event) -> jump.tick(event.getEntity()));
        bus.addListener((PlayerEvent.StartTracking event) -> jump.startTracking(event.getEntity(), event.getTarget()));
        bus.addListener((PlayerEvent.StopTracking event) -> jump.stopTracking(event.getEntity(), event.getTarget()));
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> jump.stop(event.getEntity()));
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> jump.logout(event.getEntity()));
        bus.addListener((ServerStoppedEvent event) -> jump.clear());
    }

    public static void registerClient() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> FumoHeadClient.tick());
    }

    private static void send(ServerPlayer viewer, FumoHeadJump.State state) {
        var connection = viewer.connection.getConnection();
        if (connection.isConnected() && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, FumoHeadPayload.TYPE.id())) {
            viewer.connection.send(new ClientboundCustomPayloadPacket(new FumoHeadPayload(state)));
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
