// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

/** Paging operations shared by the JEI adapters. */
final class SearchPaging {
    private SearchPaging() {}
    static int pages(int count, int size) { return count <= 0 ? 0 : 1 + (count - 1) / size; }
    static int page(int selected, int size) { return Math.max(0, selected) / size; }
    static int start(int selected, int size) { return page(selected, size) * size; }
    static int rows(int selected, int count, int size) { return Math.max(0, Math.min(size, count - start(selected, size))); }
    static int movePage(int selected, int count, int size, int delta) {
        int pages = pages(count, size);
        if (pages == 0) return 0;
        int target = Math.floorMod(page(selected, size) + delta, pages);
        return Math.min(count - 1, target * size + selected % size);
    }
    static int moveRow(int selected, int count, int size, int delta) {
        int first = start(selected, size);
        return Math.max(first, Math.min(Math.min(count, first + size) - 1, selected + delta));
    }
    static int step(int selected, int count, int delta) {
        return count <= 0 ? 0 : Math.floorMod(selected + delta, count);
    }
}
