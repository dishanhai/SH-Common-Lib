// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

/** SHCLIB's information categories; layout is supplied by the shared UI kernel. */
public final class HudGroups {
    private HudGroups() {}

    public enum Group { PLAYER, WORLD, LOCATION, SERVER, COMPUTER, NETWORK, AE2, NEOECO, ULK, FTB, RUNTIME, CONTROLS }

    /** Whether an optional integration can provide this item on the current loader. */
    public static boolean available(PlayerInfoItem item, java.util.function.Predicate<String> modLoaded,
                                    boolean ecoSupported) {
        return switch (item) {
            case AE2_STATUS, AE2_CHANNELS, AE2_POWER -> modLoaded.test("ae2");
            case NEOECO_NETWORK, NEOECO_CRAFTING, NEOECO_COMPUTATION, NEOECO_STORAGE, NEOECO_WORKSTATION ->
                    ecoSupported && modLoaded.test("ae2") && modLoaded.test("neoecoae");
            case FTB_QUESTS -> modLoaded.test("ftbquests");
            case FTB_TEAM -> modLoaded.test("ftbteams");
            case FTB_CHUNKS, FTB_CHUNK_INFO -> modLoaded.test("ftbchunks");
            default -> true;
        };
    }

    public static boolean available(Group group, java.util.function.Predicate<String> modLoaded,
                                    boolean ecoSupported) {
        for (var item : PlayerInfoItem.values()) {
            if (group(item) == group && available(item, modLoaded, ecoSupported)) return true;
        }
        return group == Group.CONTROLS;
    }

    public static Group group(PlayerInfoItem item) {
        return switch (item) {
            case PLAYER, FPS, PING, LOCAL_TIME, HEALTH, FOOD, ARMOR, EXPERIENCE, AIR, EFFECTS -> Group.PLAYER;
            case WORLD_TIME, WEATHER, MOON_PHASE, DIMENSION, CHUNKS, ENTITIES, BIOME -> Group.WORLD;
            case POSITION, CHUNK, LIGHT -> Group.LOCATION;
            case TPS, MSPT -> Group.SERVER;
            case MEMORY, CPU, GPU, DISK -> Group.COMPUTER;
            case CONNECTION_TRAFFIC, NETWORK_TRAFFIC -> Group.NETWORK;
            case AE2_STATUS, AE2_CHANNELS, AE2_POWER -> Group.AE2;
            case NEOECO_NETWORK, NEOECO_CRAFTING, NEOECO_COMPUTATION, NEOECO_STORAGE, NEOECO_WORKSTATION -> Group.NEOECO;
            case ULK_SUMMARY, ULK_SOURCES -> Group.ULK;
            case FTB_QUESTS, FTB_TEAM, FTB_CHUNKS, FTB_CHUNK_INFO -> Group.FTB;
            case SYSTEM_VERSION, JAVA_VERSION, MINECRAFT_VERSION, FORGE_VERSION, NEOFORGE_VERSION,
                    SHADER_PACK, MOD_STATUS -> Group.RUNTIME;
        };
    }
}
