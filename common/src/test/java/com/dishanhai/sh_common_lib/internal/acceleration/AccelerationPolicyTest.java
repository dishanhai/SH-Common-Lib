// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.acceleration;

public final class AccelerationPolicyTest {
    public static void main(String[] args) {
        expect(0, AccelerationPolicy.shift(2, -1));
        expect(2, AccelerationPolicy.shift(0, 1));
        expect(16, AccelerationPolicy.shift(4, 1));
        expect(1024, AccelerationPolicy.shift(1024, 1));
        expect(512, AccelerationPolicy.shift(1024, -1));
        if (AccelerationPolicy.validRate(8)) throw new AssertionError("x8 is not selectable");
        if (!AccelerationPolicy.allowed(false, false) || AccelerationPolicy.allowed(false, true))
            throw new AssertionError("blacklist does not exclude marked entries");
        if (AccelerationPolicy.allowed(true, false) || !AccelerationPolicy.allowed(true, true))
            throw new AssertionError("whitelist does not require marked entries");
    }

    private static void expect(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
