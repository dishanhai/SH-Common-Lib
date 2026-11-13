// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client;

import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.system.ComputerStatusService;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sun.management.OperatingSystemMXBean;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Client-side SHCLIB entry point for local computer telemetry. */
public final class ComputerStatus {
    private static final AsyncMetric<ComputerStatusService.Memory> MEMORY =
            new AsyncMetric<>(TimeUnit.SECONDS.toNanos(1), Util.ioPool(), System::nanoTime);
    private static final AsyncMetric<ComputerStatusService.Cpu> CPU =
            new AsyncMetric<>(TimeUnit.SECONDS.toNanos(1), Util.ioPool(), System::nanoTime);
    private static final AsyncMetric<ComputerStatusService.Space> DISK =
            new AsyncMetric<>(TimeUnit.SECONDS.toNanos(5), Util.ioPool(), System::nanoTime);

    private ComputerStatus() {}

    public static ComputerStatusService.Snapshot read(Set<ComputerStatusService.Metric> metrics) {
        if (UnifiedUI.isAvailable()) {
            var service = UnifiedUI.get().find(ComputerStatusService.KEY);
            if (service.isPresent()) return service.get().read(metrics);
        }

        var memory = metrics.contains(ComputerStatusService.Metric.MEMORY)
                ? MEMORY.read(ComputerStatus::sampleMemory)
                : Optional.<ComputerStatusService.Memory>empty();
        var cpu = metrics.contains(ComputerStatusService.Metric.CPU)
                ? CPU.read(ComputerStatus::sampleCpu)
                : Optional.<ComputerStatusService.Cpu>empty();
        var gpu = metrics.contains(ComputerStatusService.Metric.GPU) && RenderSystem.isOnRenderThread()
                ? RenderSystem.tryGetDevice() : null;
        var graphics = gpu == null ? Optional.<ComputerStatusService.Gpu>empty()
                : Optional.of(new ComputerStatusService.Gpu(List.of(
                        new ComputerStatusService.GraphicsAdapter(gpu.getRenderer(), OptionalLong.empty())),
                        OptionalDouble.empty()));
        var directory = Minecraft.getInstance().gameDirectory;
        var disk = metrics.contains(ComputerStatusService.Metric.DISK)
                ? DISK.read(() -> sampleDisk(directory)) : Optional.<ComputerStatusService.Space>empty();
        return new ComputerStatusService.Snapshot(memory, cpu, graphics, disk);
    }

    private static ComputerStatusService.Memory sampleMemory() {
        var runtime = Runtime.getRuntime();
        var os = operatingSystem();
        return new ComputerStatusService.Memory(
                new ComputerStatusService.Space(runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory()),
                os == null ? Optional.empty() : space(os.getTotalMemorySize(), os.getFreeMemorySize()));
    }

    private static ComputerStatusService.Cpu sampleCpu() {
        var os = operatingSystem();
        return new ComputerStatusService.Cpu(Runtime.getRuntime().availableProcessors(),
                os == null ? OptionalDouble.empty() : load(os.getCpuLoad()),
                os == null ? OptionalDouble.empty() : load(os.getProcessCpuLoad()));
    }

    private static ComputerStatusService.Space sampleDisk(File directory) {
        return space(directory.getTotalSpace(), directory.getFreeSpace()).orElse(null);
    }

    private static OperatingSystemMXBean operatingSystem() {
        return ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os ? os : null;
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
