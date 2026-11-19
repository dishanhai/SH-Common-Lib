// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.block;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import com.dishanhai.sh_common_lib.internal.block.DishanhaiFumoBlock.DanceEntity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** A shared playback cursor and spatial mix for each audible music file. */
final class FumoMusic extends AbstractTickableSoundInstance {
    private static final int RANGE = 16;
    private static final float SINGLE_VOLUME = 0.5F;
    private static final Map<String, FumoMusic> PLAYING = new HashMap<>();
    private final String track;
    private final Set<DanceEntity> sources = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<LivingEntity, FumoHeadItem> headSources = new WeakHashMap<>();

    private FumoMusic(String track, SoundEvent event, long seed) {
        super(event, SoundSource.RECORDS, RandomSource.create(seed));
        this.track = track;
        attenuation = Attenuation.NONE;
        looping = true;
    }

    static void playNearby(DanceEntity plush) {
        playNearby(plush.getBlockState().getBlock(), plush.getBlockPos().asLong(),
                Vec3.atCenterOf(plush.getBlockPos()), music -> music.sources.add(plush));
    }

    static void playNearby(LivingEntity wearer, FumoHeadItem fumo) {
        playNearby(fumo.getBlock(), wearer.getUUID().getLeastSignificantBits(), headPosition(wearer),
                music -> music.headSources.put(wearer, fumo));
    }

    private static void playNearby(Block block, long seed, Vec3 position, Consumer<FumoMusic> addSource) {
        var client = Minecraft.getInstance();
        var camera = client.getCameraEntity();
        if (camera == null || position.distanceToSqr(camera.getEyePosition()) >= RANGE * RANGE
                || client.options.getSoundSourceVolume(SoundSource.RECORDS) <= 0
                || client.options.getSoundSourceVolume(SoundSource.MASTER) <= 0) {
            return;
        }
        var sounds = client.getSoundManager();
        var id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) return;
        var event = sounds.getSoundEvent(id);
        if (event == null) return;
        // Resolve the same weighted choice when SoundManager starts this stream.
        var sound = event.getSound(RandomSource.create(seed));
        if (sound == SoundManager.EMPTY_SOUND || sound == SoundManager.INTENTIONALLY_EMPTY_SOUND) return;
        String track = sound.getPath().toString();
        var music = PLAYING.get(track);
        if (music != null && !music.isStopped() && sounds.isActive(music)) {
            addSource.accept(music);
            return;
        }
        music = new FumoMusic(track, SoundEvent.createVariableRangeEvent(id), seed);
        addSource.accept(music);
        if (!music.updateMix(client)) return;
        sounds.play(music);
        if (sounds.isActive(music)) PLAYING.put(track, music);
        else PLAYING.remove(track);
    }

    @Override
    public void tick() {
        if (!updateMix(Minecraft.getInstance())) {
            PLAYING.remove(track, this);
            stop();
        }
    }

    private boolean updateMix(Minecraft client) {
        var camera = client.getCameraEntity();
        if (camera == null) return false;
        var mix = new Mix(camera.getEyePosition());
        sources.removeIf(plush -> plush.isRemoved() || plush.getLevel() != client.level
                || !plush.getBlockState().getValue(DishanhaiFumoBlock.DANCING)
                || !mix.add(Vec3.atCenterOf(plush.getBlockPos())));
        headSources.entrySet().removeIf(entry -> FumoHeadClient.playingItem(entry.getKey()) != entry.getValue()
                || !mix.add(headPosition(entry.getKey())));
        if (mix.power == 0) return false;
        // Sum the acoustic contributions once, then let the sound engine pan the mixed source.
        volume = Math.min(1, SINGLE_VOLUME * (float) Math.sqrt(mix.power));
        x = mix.centerX / mix.power;
        y = mix.centerY / mix.power;
        z = mix.centerZ / mix.power;
        return true;
    }

    private static Vec3 headPosition(LivingEntity wearer) {
        return wearer.getEyePosition().add(0, 0.35, 0);
    }

    private static final class Mix {
        private final Vec3 listener;
        private double power;
        private double centerX;
        private double centerY;
        private double centerZ;

        private Mix(Vec3 listener) {
            this.listener = listener;
        }

        private boolean add(Vec3 position) {
            double distance = listener.distanceTo(position);
            if (distance >= RANGE) return false;
            double gain = 1 - distance / RANGE;
            double weight = gain * gain;
            power += weight;
            centerX += position.x * weight;
            centerY += position.y * weight;
            centerZ += position.z * weight;
            return true;
        }
    }
}
