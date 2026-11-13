// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.ulk;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Decodes the UUID/count triples used by 1.20.1 QIO drive items. */
public final class QioDriveRecords {
    private QioDriveRecords() {}

    public static boolean read(long[] data, BiConsumer<UUID, Long> consumer) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(consumer, "consumer");
        if (data.length % 3 != 0) return false;
        boolean valid = true;
        for (int index = 0; index < data.length; index += 3) {
            long amount = data[index + 2];
            if (amount <= 0) { valid = false; continue; }
            consumer.accept(new UUID(data[index], data[index + 1]), amount);
        }
        return valid;
    }
}
