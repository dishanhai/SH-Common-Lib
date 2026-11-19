// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.block;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Server-owned, session-scoped head animation and its state for tracking clients. */
public final class FumoHeadJump {
    private static final String MESSAGE = "command.sh_common_lib.fumojump.";

    private final Map<ServerPlayer, State> jumping = new HashMap<>();
    private final Map<ServerPlayer, Set<ServerPlayer>> trackers = new HashMap<>();
    private final BiConsumer<ServerPlayer, State> send;

    public FumoHeadJump(BiConsumer<ServerPlayer, State> send) {
        this.send = send;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean winejumpAlias) {
        dispatcher.register(Commands.literal("fumojump")
                .executes(context -> execute(context.getSource(), null))
                .then(Commands.literal("bgm")
                        .executes(context -> execute(context.getSource(), true))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> execute(context.getSource(),
                                        BoolArgumentType.getBool(context, "enabled"))))));
        if (winejumpAlias && dispatcher.getRoot().getChild("winejump") == null) {
            var command = dispatcher.getRoot().getChild("fumojump");
            dispatcher.register(Commands.literal("winejump").executes(command.getCommand()).redirect(command));
        }
    }

    private int execute(CommandSourceStack source, Boolean bgm) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        tick(player);
        if (!player.isAlive() || player.isSpectator() || wornFumo(player) == null) {
            source.sendFailure(Component.translatable(MESSAGE + "no_fumo"));
            return 0;
        }

        State current = jumping.get(player);
        String feedback;
        if (current == null) {
            boolean playMusic = Boolean.TRUE.equals(bgm);
            update(player, new State(player.getId(), player.level().getGameTime(), playMusic));
            feedback = playMusic ? "started_bgm" : "started";
        } else if (bgm == null) {
            stop(player);
            feedback = "stopped";
        } else {
            if (current.bgm() != bgm) update(player, new State(player.getId(), current.startTick(), bgm));
            feedback = bgm ? "bgm_on" : "bgm_off";
        }
        source.sendSuccess(() -> Component.translatable(MESSAGE + feedback), false);
        return 1;
    }

    public void tick(Player player) {
        if (player instanceof ServerPlayer server && jumping.containsKey(server)
                && (!server.isAlive() || server.isSpectator() || wornFumo(server) == null)) {
            stop(server);
        }
    }

    public void startTracking(Player observer, Entity target) {
        if (!(observer instanceof ServerPlayer viewer) || !(target instanceof ServerPlayer wearer)) return;
        trackers.computeIfAbsent(wearer, ignored -> new HashSet<>()).add(viewer);
        State state = jumping.get(wearer);
        if (state != null) send.accept(viewer, state);
    }

    public void stopTracking(Player observer, Entity target) {
        if (!(target instanceof ServerPlayer wearer)) return;
        var viewers = trackers.get(wearer);
        if (viewers != null && viewers.remove(observer) && viewers.isEmpty()) trackers.remove(wearer);
    }

    public void stop(Player player) {
        if (player instanceof ServerPlayer server && jumping.remove(server) != null) {
            broadcast(server, new State(server.getId(), -1, false));
        }
    }

    public void logout(Player player) {
        jumping.remove(player);
        trackers.remove(player);
        trackers.values().removeIf(viewers -> {
            viewers.remove(player);
            return viewers.isEmpty();
        });
    }

    public void clear() {
        jumping.clear();
        trackers.clear();
    }

    static FumoHeadItem wornFumo(LivingEntity entity) {
        var helmet = entity.getItemBySlot(EquipmentSlot.HEAD);
        return helmet.getItem() instanceof FumoHeadItem fumo ? fumo : FumoHeadPlatform.findCurio(entity);
    }

    private void update(ServerPlayer player, State state) {
        jumping.put(player, state);
        broadcast(player, state);
    }

    private void broadcast(ServerPlayer player, State state) {
        send.accept(player, state);
        for (ServerPlayer viewer : trackers.getOrDefault(player, Set.of())) send.accept(viewer, state);
    }

    public record State(int entityId, long startTick, boolean bgm) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeLong(startTick);
            buf.writeBoolean(bgm);
        }

        public static State read(FriendlyByteBuf buf) {
            return new State(buf.readVarInt(), buf.readLong(), buf.readBoolean());
        }
    }
}
