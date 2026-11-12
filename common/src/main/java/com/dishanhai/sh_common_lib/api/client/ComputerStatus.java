// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client;

import com.dishanhai.sh_better_ui.client.api.ComputerStatusApi;
import com.dishanhai.sh_better_ui.kernel.api.system.ComputerStatusService;

import java.util.Set;

/** Client-side SHCLIB entry point for local computer telemetry. */
public final class ComputerStatus {
    private ComputerStatus() {}

    public static ComputerStatusService.Snapshot read(Set<ComputerStatusService.Metric> metrics) {
        return ComputerStatusApi.read(metrics);
    }
}
