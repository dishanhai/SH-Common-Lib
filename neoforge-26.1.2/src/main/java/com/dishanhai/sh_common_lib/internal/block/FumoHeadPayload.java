// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.block;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record FumoHeadPayload(FumoHeadJump.State state) implements CustomPacketPayload {
    static final Type<FumoHeadPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ShCommonLib.MOD_ID, "fumo_head"));
    static final StreamCodec<FriendlyByteBuf, FumoHeadPayload> CODEC = StreamCodec.of(
            (buf, payload) -> payload.state().write(buf), buf -> new FumoHeadPayload(FumoHeadJump.State.read(buf)));

    @Override
    public Type<FumoHeadPayload> type() {
        return TYPE;
    }
}
