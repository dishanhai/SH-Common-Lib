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
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
