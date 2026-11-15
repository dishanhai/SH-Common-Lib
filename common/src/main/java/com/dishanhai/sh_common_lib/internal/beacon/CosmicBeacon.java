// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.beacon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

public record CosmicBeacon(GlobalPos point, UUID id, UUID owner, String name, boolean locked,
                           boolean crossDimension, boolean hud) {
    public static final String BLOCK_ID = "cosmic_beacon";
    public static final int NAME_LIMIT = 32;
    public static final int DIRECTORY_LIMIT = 128;

    static final Codec<CosmicBeacon> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.fieldOf("point").forGetter(CosmicBeacon::point),
            UUIDUtil.CODEC.fieldOf("id").forGetter(CosmicBeacon::id),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(CosmicBeacon::owner),
            Codec.STRING.fieldOf("name").forGetter(CosmicBeacon::name),
            Codec.BOOL.fieldOf("locked").forGetter(CosmicBeacon::locked),
            Codec.BOOL.fieldOf("cross_dimension").forGetter(CosmicBeacon::crossDimension),
            Codec.BOOL.fieldOf("hud").forGetter(CosmicBeacon::hud)
    ).apply(instance, CosmicBeacon::new));

    static String cleanName(String name) {
        var clean = new StringBuilder();
        name.codePoints().filter(c -> !Character.isISOControl(c) && c != 0xa7 && c != '{' && c != '}')
                .limit(NAME_LIMIT).forEach(clean::appendCodePoint);
        return clean.toString().strip();
    }

    public record View(GlobalPos point, UUID id, String name, boolean locked, boolean crossDimension, boolean hud) {
        static View of(CosmicBeacon beacon) {
            return new View(beacon.point(), beacon.id(), beacon.name(), beacon.locked(), beacon.crossDimension(), beacon.hud());
        }
    }
}
