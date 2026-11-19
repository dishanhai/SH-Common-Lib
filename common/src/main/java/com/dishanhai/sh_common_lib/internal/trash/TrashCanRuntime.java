// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

interface TrashCanRuntime {
    void tick();

    default void close() {}
}
