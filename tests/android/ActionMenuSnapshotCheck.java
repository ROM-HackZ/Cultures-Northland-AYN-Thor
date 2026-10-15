package org.northland.thor;

import java.util.*;

public final class ActionMenuSnapshotCheck {
    static void reject(String wire) {
        try { ActionMenuSnapshot.decode(wire); }
        catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Accepted invalid snapshot: "+wire);
    }
    public static void main(String[] ignored) {
        List<VillagerAction> observed=new ArrayList<>(Arrays.asList(VillagerAction.PROFESSION,VillagerAction.HOME));
        ActionMenuSnapshot a=new ActionMenuSnapshot(42,ActionMenuSnapshot.Status.READY,observed,1);
        observed.clear();
        ActionMenuSnapshot b=ActionMenuSnapshot.decode(a.encode());
        if(b.request!=42 || b.unknown!=1 || !a.actions.equals(b.actions)) throw new AssertionError("Snapshot changed");
        try { b.actions.clear();throw new AssertionError("Mutable snapshot"); }
        catch(UnsupportedOperationException expected) { }
        reject("actions 0 READY 0 go-to");
        reject("actions 42 READY -1 go-to");
        reject("actions 42 READY 0 go-to,go-to");
        reject("actions 42 READY 0 invalid-command");
        reject("actions 42 UNAVAILABLE 0 go-to");
        reject("actions 42 READY 64 go-to");
        reject("actions 42 READY 0 go-to,");
        for(VillagerAction action:VillagerAction.values()) {
            String command="choose-action "+Long.MAX_VALUE+" "+action.id;
            if(command.length()>96) throw new AssertionError("Command exceeds IPC boundary");
            ActionMenuSnapshot value=new ActionMenuSnapshot(42,ActionMenuSnapshot.Status.READY,Arrays.asList(action),0);
            if(ActionMenuSnapshot.decode(value.encode()).actions.get(0)!=action) throw new AssertionError("Lost action");
        }
        System.out.println("Snapshot immutability, invalid-message rejection and all "+VillagerAction.values().length+" command round trips passed.");
    }
}
