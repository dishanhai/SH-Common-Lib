// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

public final class SearchCompletionUndoTest {
    public static void main(String[] args) {
        var undo = new SearchCompletionUndo();
        undo.accepted("dia", "diamond");
        if (!undo.undo("diamond").orElseThrow().equals("dia")) throw new AssertionError("restore previous query");
        if (undo.undo("diamond").isPresent()) throw new AssertionError("undo only once");
        undo.accepted("dia", "diamond");
        undo.observe("diamonds");
        if (undo.undo("diamond").isPresent()) throw new AssertionError("editing invalidates completion undo");
        undo.accepted("same", "same");
        if (undo.undo("same").isPresent()) throw new AssertionError("unchanged completion has no undo");
    }
}
