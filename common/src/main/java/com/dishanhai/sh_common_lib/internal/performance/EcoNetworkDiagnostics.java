// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.performance;

import appeng.api.networking.IGrid;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Function;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Bounded, read-only sampling of the grid already reached by a wireless terminal. */
public final class EcoNetworkDiagnostics {
    private static final String PREFIX = "cn.dancingsnow.neoecoae.";
    private static final int MAX_NODES = 1024;

    private EcoNetworkDiagnostics() {}

    public record Network(int state, int nodes, int active, boolean truncated) {}

    public static Network network(IGrid grid) {
        if (grid == null) return new Network(InventoryHudSnapshot.ECO_NO_TERMINAL, 0, 0, false);
        int inspected = 0, nodes = 0, active = 0;
        for (var node : grid.getNodes()) {
            if (inspected++ >= MAX_NODES) return new Network(InventoryHudSnapshot.ECO_CONNECTED, nodes, active, true);
            if (ecoOwner(node.getOwner())) {
                nodes++;
                if (node.isActive()) active++;
            }
        }
        return new Network(InventoryHudSnapshot.ECO_CONNECTED, nodes, active, false);
    }

    public static EcoMachineSnapshot machines(IGrid grid, String dimension, int mask, Function<BlockEntity, String> dimensionOf) {
        if (grid == null) return new EcoMachineSnapshot(dimension, mask, EcoMachineSnapshot.NO_TERMINAL, false, List.of());
        var clusters = new IdentityHashMap<Object, Integer>();
        var found = new ArrayList<Object>();
        var online = new ArrayList<Boolean>();
        boolean truncated = false;
        int visited = 0;
        for (var node : grid.getNodes()) {
            if (visited++ >= MAX_NODES) { truncated = true; break; }
            Object owner = node.getOwner();
            if (!ecoOwner(owner)) continue;
            Object cluster = invoke(owner, "getCluster");
            int type = type(cluster);
            if (type < 0 || (mask & (1 << type)) == 0) continue;
            Integer index = clusters.get(cluster);
            if (index != null) {
                if (node.isActive()) online.set(index, true);
            } else if (found.size() < EcoMachineSnapshot.MAX_MACHINES) {
                clusters.put(cluster, found.size());
                found.add(cluster);
                online.add(node.isActive());
            } else truncated = true;
        }
        var machines = new ArrayList<EcoMachineSnapshot.Machine>();
        for (int i = 0; i < found.size(); i++) {
            Object cluster = found.get(i), controller = invoke(cluster, "getController");
            if (!(controller instanceof BlockEntity block) || block.getLevel() == null) { truncated = true; continue; }
            Object formed = invoke(controller, "isFormed");
            if (!(formed instanceof Boolean)) { truncated = true; continue; }
            if (!((Boolean) formed)) continue;
            int type = type(cluster);
            var pos = block.getBlockPos();
            String machineDimension = dimensionOf.apply(block);
            long a = -1, b = -1, c = -1, d = -1;
            switch (type) {
                case EcoMachineSnapshot.CRAFTING -> {
                    a = number(controller, "getLocalRunningThreadCount");
                    b = number(controller, "getLocalThreadCount");
                    c = number(controller, "getWorkerCount");
                }
                case EcoMachineSnapshot.COMPUTATION -> {
                    a = metric(cluster, "getActiveCPUCount", "getLocalActiveCpuCount");
                    if (a < 0) a = size(cluster, "getActiveCPUs");
                    b = metric(cluster, "getOwnMaxThreads", "getLocalMaxThreads");
                    c = metric(cluster, "getOwnUsedStorage", "getLocalActiveJobBytes");
                    d = metric(cluster, "getTotalStorage", "getLocalTotalStorageBytes");
                }
                case EcoMachineSnapshot.STORAGE -> {
                    a = size(cluster, "getDrives");
                    b = size(cluster, "getEnergyCells");
                    c = bool(controller, "isInfiniteMode");
                }
                case EcoMachineSnapshot.WORKSTATION -> {
                    a = number(controller, "getParallelism");
                    b = tenths(controller, "getTemperature");
                    c = bool(controller, "isWorking");
                    d = bool(controller, "isOverheated");
                }
                default -> { continue; }
            }
            machines.add(new EcoMachineSnapshot.Machine(type, machineDimension, pos.getX(), pos.getY(), pos.getZ(), online.get(i), a, b, c, d));
        }
        return new EcoMachineSnapshot(dimension, mask, EcoMachineSnapshot.CONNECTED, truncated, machines);
    }

    private static boolean ecoOwner(Object owner) {
        return owner != null && owner.getClass().getName().startsWith(PREFIX);
    }

    private static long metric(Object target, String current, String legacy) {
        long value = number(target, current);
        return value >= 0 ? value : number(target, legacy);
    }

    private static int type(Object cluster) {
        if (cluster == null) return -1;
        return switch (cluster.getClass().getName()) {
            case PREFIX + "multiblock.cluster.NECraftingCluster" -> EcoMachineSnapshot.CRAFTING;
            case PREFIX + "multiblock.cluster.NEComputationCluster" -> EcoMachineSnapshot.COMPUTATION;
            case PREFIX + "multiblock.cluster.NEStorageCluster" -> EcoMachineSnapshot.STORAGE;
            case PREFIX + "multiblock.cluster.NEIntegratedWorkingStationCluster" -> EcoMachineSnapshot.WORKSTATION;
            default -> -1;
        };
    }
    private static Object invoke(Object target, String name) {
        if (target == null) return null;
        try { Method method = target.getClass().getMethod(name); return method.invoke(target); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return null; }
    }
    private static long number(Object target, String name) {
        Object value = invoke(target, name);
        return value instanceof Number n && n.longValue() >= 0 ? n.longValue() : -1;
    }
    private static long size(Object target, String name) {
        Object value = invoke(target, name);
        return value instanceof java.util.Collection<?> collection ? collection.size() : -1;
    }
    private static long bool(Object target, String name) {
        Object value = invoke(target, name);
        return value instanceof Boolean b ? (b ? 1 : 0) : -1;
    }
    private static long tenths(Object target, String name) {
        Object value = invoke(target, name);
        if (!(value instanceof Number number)) return -1;
        double n = number.doubleValue();
        return Double.isFinite(n) && n >= 0 && n < Long.MAX_VALUE / 10.0 ? Math.round(n * 10) : -1;
    }
}
