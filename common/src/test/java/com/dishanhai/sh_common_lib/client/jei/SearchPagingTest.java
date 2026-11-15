// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

public final class SearchPagingTest {
    public static void main(String[] args) {
        require(SearchPaging.pages(12, 5) == 3, "page count");
        require(SearchPaging.movePage(4, 12, 5, 1) == 9, "keep row across pages");
        require(SearchPaging.movePage(9, 12, 5, 1) == 11, "short last page");
        require(SearchPaging.movePage(11, 12, 5, -1) == 6, "move back");
        require(SearchPaging.moveRow(5, 12, 5, -1) == 5, "row stays on page");
        require(SearchPaging.rows(10, 12, 5) == 2, "last page size");
        require(SearchPaging.movePage(11, 12, 5, 1) == 1, "last page wraps to first");
        require(SearchPaging.movePage(1, 12, 5, -1) == 11, "first page wraps to last");
        require(SearchPaging.step(4, 12, 1) == 5 && SearchPaging.step(5, 12, -1) == 4, "step changes page at edge");
        require(SearchPaging.step(11, 12, 1) == 0 && SearchPaging.step(0, 12, -1) == 11, "step wraps at ends");
        require(SearchPaging.step(0, 0, 1) == 0, "empty list is safe");
        var preview = new SearchPreviewGate(150);
        require(!preview.ready("iron", 1000), "new selection waits before loading a recipe");
        require(!preview.ready("gold", 1100), "changing selection restarts the delay");
        require(!preview.ready("gold", 1249), "rapid navigation does not load intermediate recipes");
        require(preview.ready("gold", 1250), "stable selection eventually loads");
        preview.reset();
        require(!preview.ready("gold", 2000), "closing the view resets the delay");
        var guard = new RecipeKeyGuard();
        var screen = new Object();
        guard.arm(screen, 82, 1000);
        require(guard.take(screen, 82, true, 1001), "handled recipe key suppresses its character");
        require(!guard.take(screen, 82, true, 1002), "only one character is suppressed");
        guard.arm(screen, 82, 2000);
        require(!guard.take(screen, 82, false, 2001), "released key does not swallow later typing");
        guard.arm(screen, 82, 3000);
        require(!guard.take(new Object(), 82, true, 3001), "a different screen is not affected");
        guard.arm(screen, 82, 4000);
        require(!guard.take(screen, 82, true, 4300), "stale interception expires");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
