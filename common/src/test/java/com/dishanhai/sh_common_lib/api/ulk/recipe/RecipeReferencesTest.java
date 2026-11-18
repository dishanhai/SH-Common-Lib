// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static com.dishanhai.sh_common_lib.api.ulk.recipe.RecipeReferences.Kind.*;
import static com.dishanhai.sh_common_lib.api.ulk.recipe.RecipeReferences.Presence.*;

/** Runs without a game/client bootstrap: validates caller-supplied authoritative lookup semantics. */
public final class RecipeReferencesTest {
    public static void main(String[] args) {
        var calls = new ArrayList<String>();
        RecipeReferences.Lookup good = (kind, id) -> { calls.add(kind + ":" + id); return PRESENT; };
        var shaped = doc(RecipeType.SHAPED, "#test:wood", "test:result");
        check(RecipeReferences.validate(shaped, RecipeTarget.FORGE_1_20_1, good).isEmpty(), "known fields accepted");
        check(calls.equals(List.of("ITEM_TAG:test:wood", "ITEM:test:result")), "exact known field lookup");
        for (var target : RecipeTarget.values()) {
            var missing = RecipeReferences.validate(shaped, target, (kind, id) -> MISSING);
            check(missing.equals(List.of(new RecipeIssue("empty_tag", "input0"), new RecipeIssue("unknown_item", "result"))), "field errors stable");
            var unavailable = RecipeReferences.validate(shaped, target, (kind, id) -> UNAVAILABLE);
            check(unavailable.stream().allMatch(issue -> issue.key().equals("registry_unavailable")), "missing registry is not missing content");
        }
        check(RecipeReferences.validate(doc(RecipeType.BREWING_CONTAINER,"test:solid","test:bottle"),
                RecipeTarget.NEOFORGE_1_21_1,(kind,id)->kind==POTION_CONTAINER?MISSING:PRESENT)
                .equals(List.of(new RecipeIssue("invalid_container","input0"),new RecipeIssue("invalid_container","result"))), "non potion containers rejected");
        check(RecipeReferences.validate(doc(RecipeType.BREWING_CONTAINER,"test:missing","test:bottle"),
                RecipeTarget.NEOFORGE_1_21_1,(kind,id)->MISSING).getFirst().key().equals("unknown_item"), "unknown container reports item absence first");
        calls.clear();
        RecipeReferences.validate(doc(RecipeType.BREWING,"test:water","test:awkward"),RecipeTarget.NEOFORGE_1_21_1,good);
        check(calls.equals(List.of("POTION:test:water", "POTION:test:awkward")), "potion references not items");
        calls.clear();
        var trim=doc(RecipeType.SMITHING_TRIM,"test:template","test:pattern");
        RecipeReferences.validate(trim,RecipeTarget.FORGE_1_20_1,good);
        check(calls.equals(List.of("ITEM:test:template")), "legacy trim does not check unused result");
        calls.clear();
        RecipeReferences.validate(trim,RecipeTarget.NEOFORGE_26_1_2,good);
        check(calls.equals(List.of("ITEM:test:template","TRIM_PATTERN:test:pattern")), "26 trim checks pattern registry");
        check(RecipeReferences.validate(trim,RecipeTarget.NEOFORGE_26_1_2,(kind,id)->kind==TRIM_PATTERN?MISSING:PRESENT)
                .equals(List.of(new RecipeIssue("unknown_pattern","result"))), "unknown trim pattern");
        check(RecipeReferences.validate(doc(RecipeType.CUSTOM,"test:unused","test:unused"),RecipeTarget.NEOFORGE_26_1_2,
                (kind,id)-> {throw new AssertionError("CUSTOM must not pretend to understand serializer fields");}).isEmpty(), "custom opaque");
        calls.clear();
        RecipeReferences.validate(doc(RecipeType.SHAPED,"bad#id","#test:invalid_result"),RecipeTarget.FORGE_1_20_1,good);
        check(calls.isEmpty(), "invalid structural references do not reach lookup");
        check(RecipeReferences.validate(doc(RecipeType.SHAPED,"minecraft:air","test:result"),RecipeTarget.FORGE_1_20_1,good)
                .equals(List.of(new RecipeIssue("unknown_item","input0"))), "air cannot become ingredient");
        try { RecipeReferences.validate(shaped,RecipeTarget.FORGE_1_20_1,good).add(new RecipeIssue("x","y"));throw new AssertionError("mutable"); }
        catch (UnsupportedOperationException expected) {}
        try { RecipeReferences.validate(shaped,RecipeTarget.FORGE_1_20_1,(kind,id)->{throw new IllegalStateException("lookup failed");});throw new AssertionError("failure swallowed"); }
        catch (IllegalStateException expected) {check(expected.getMessage().equals("lookup failed"),"propagate original failure");}
        System.out.println("RecipeReferencesTest passed");
    }
    private static RecipeDocument doc(RecipeType type,String input,String result) {
        var inputs=new ArrayList<>(Collections.nCopies(9,""));inputs.set(0,input);
        return new RecipeDocument("test:recipe",type,inputs,result,1,0,200,"","");
    }
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
