// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.log;

/** Diagnostic severity and its default sorting weight. Higher weights have priority. */
public enum LogType {
    NORMAL(10),
    WARNING(40),
    ERROR(70),
    CRASH(100);

    private final int weight;

    LogType(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
