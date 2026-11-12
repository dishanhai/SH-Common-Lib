// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client;

import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.system.ComputerStatusService;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sun.management.OperatingSystemMXBean;
import net.minecraft.client.Minecraft;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;

/** Client-side SHCLIB entry point for local computer telemetry. */
public final class ComputerStatus {
    private ComputerStatus() {}

    public static ComputerStatusService.Snapshot read(Set<ComputerStatusService.Metric> metrics) {
        if (UnifiedUI.isAvailable()) {
            var service = UnifiedUI.get().find(ComputerStatusService.KEY);
            if (service.isPresent()) return service.get().read(metrics);
        }

        var runtime = Runtime.getRuntime();
        var operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        var os = operatingSystem instanceof OperatingSystemMXBean platform ? platform : null;
        var memory = metrics.contains(ComputerStatusService.Metric.MEMORY)
                ? Optional.of(new ComputerStatusService.Memory(
                        new ComputerStatusService.Space(runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory()),
                        os == null ? Optional.empty() : space(os.getTotalMemorySize(), os.getFreeMemorySize())))
                : Optional.<ComputerStatusService.Memory>empty();
        var cpu = metrics.contains(ComputerStatusService.Metric.CPU)
                ? Optional.of(new ComputerStatusService.Cpu(runtime.availableProcessors(),
                        os == null ? OptionalDouble.empty() : load(os.getCpuLoad()),
                        os == null ? OptionalDouble.empty() : load(os.getProcessCpuLoad())))
                : Optional.<ComputerStatusService.Cpu>empty();
        var gpu = metrics.contains(ComputerStatusService.Metric.GPU) && RenderSystem.isOnRenderThread()
                ? RenderSystem.tryGetDevice() : null;
        var graphics = gpu == null ? Optional.<ComputerStatusService.Gpu>empty()
                : Optional.of(new ComputerStatusService.Gpu(List.of(
                        new ComputerStatusService.GraphicsAdapter(gpu.getRenderer(), OptionalLong.empty())),
                        OptionalDouble.empty()));
        var directory = Minecraft.getInstance().gameDirectory;
        var disk = metrics.contains(ComputerStatusService.Metric.DISK)
                ? space(directory.getTotalSpace(), directory.getFreeSpace()) : Optional.<ComputerStatusService.Space>empty();
        return new ComputerStatusService.Snapshot(memory, cpu, graphics, disk);
    }

    private static Optional<ComputerStatusService.Space> space(long total, long free) {
        return total > 0 && free >= 0 && free <= total
                ? Optional.of(new ComputerStatusService.Space(total - free, total)) : Optional.empty();
    }

    private static OptionalDouble load(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1
                ? OptionalDouble.of(value) : OptionalDouble.empty();
    }
}
