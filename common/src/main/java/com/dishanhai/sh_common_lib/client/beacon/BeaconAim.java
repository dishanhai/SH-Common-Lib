// Author: dishanhai
package com.dishanhai.sh_common_lib.client.beacon;

import com.dishanhai.sh_common_lib.internal.beacon.CosmicBeacon;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

final class BeaconAim {
    static final double LABEL_HEIGHT = 1.35;
    private static final double AIM_COSINE = Math.cos(Math.toRadians(4.5));
    private static UUID highlighted;

    private BeaconAim() {}

    static void highlight(CosmicBeacon.View beacon) {
        highlighted = beacon == null ? null : beacon.id();
    }

    static boolean isHighlighted(CosmicBeacon.View beacon) {
        return beacon.id().equals(highlighted);
    }

    static CosmicBeacon.View select(ResourceKey<Level> dimension, List<CosmicBeacon.View> directory,
                                   Vec3 origin, Vec3 direction) {
        CosmicBeacon.View target = null;
        double bestCosine = AIM_COSINE;
        double closest = Double.POSITIVE_INFINITY;
        for (var beacon : directory) {
            if (!beacon.hud() || !dimension.equals(beacon.point().dimension())) continue;
            BlockPos pos = beacon.point().pos();
            double x = pos.getX() + 0.5 - origin.x;
            double y = pos.getY() + LABEL_HEIGHT - origin.y;
            double z = pos.getZ() + 0.5 - origin.z;
            double distance = x * x + y * y + z * z;
            if (distance == 0.0) continue;
            double cosine = (x * direction.x + y * direction.y + z * direction.z) / Math.sqrt(distance);
            if (cosine > bestCosine || cosine == bestCosine && distance < closest) {
                bestCosine = cosine;
                closest = distance;
                target = beacon;
            }
        }
        return target;
    }
}
