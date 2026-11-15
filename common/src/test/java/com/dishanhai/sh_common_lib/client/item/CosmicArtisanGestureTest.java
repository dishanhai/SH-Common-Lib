// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import static com.dishanhai.sh_common_lib.client.item.CosmicArtisanGesture.Action.*;

public final class CosmicArtisanGestureTest {
    public static void main(String[] args) {
        var gesture = new CosmicArtisanGesture();
        gesture.press(0);
        expect(NONE, gesture.poll(299));
        gesture.release(299);
        expect(OPEN_CONFIG, gesture.poll(450));
        expect(NONE, gesture.poll(451));

        gesture.press(500);
        gesture.press(550);
        expect(OPEN_WHEEL, gesture.poll(800));
        expect(NONE, gesture.poll(801));
        gesture.release(820);
        expect(CLOSE_WHEEL, gesture.poll(821));
        expect(NONE, gesture.poll(822));

        gesture.press(900);
        gesture.release(1200);
        expect(NONE, gesture.poll(1250));

        gesture.press(1300);
        expect(OPEN_WHEEL, gesture.poll(1600));
        gesture.cancel();
        gesture.release(1610);
        expect(NONE, gesture.poll(1611));

        gesture.press(1700);
        gesture.release(1720);
        gesture.cancel();
        expect(NONE, gesture.poll(1721));
    }

    private static void expect(CosmicArtisanGesture.Action expected, CosmicArtisanGesture.Action actual) {
        if (actual != expected) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
