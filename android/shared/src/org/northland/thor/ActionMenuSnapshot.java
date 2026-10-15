package org.northland.thor;

import java.util.*;

/** One explicit menu check. Its contents remain fixed until another refresh. */
public final class ActionMenuSnapshot {
    public enum Status { READY, UNAVAILABLE, BUSY }
    public final long request;
    public final Status status;
    public final List<VillagerAction> actions;
    public final int unknown;

    public ActionMenuSnapshot(long request,Status status,List<VillagerAction> actions,int unknown) {
        if(request<=0 || status==null || unknown<0 || actions.size()+unknown>64 || actions.contains(null)
                || new HashSet<>(actions).size()!=actions.size()
                || (status!=Status.READY && (!actions.isEmpty() || unknown!=0)))
            throw new IllegalArgumentException("Invalid action snapshot");
        this.request=request;this.status=status;this.unknown=unknown;
        this.actions=Collections.unmodifiableList(new ArrayList<>(actions));
    }
    public static ActionMenuSnapshot failure(long request,Status status) {
        return new ActionMenuSnapshot(request,status,Collections.emptyList(),0);
    }
    public String encode() {
        StringJoiner ids=new StringJoiner(",");
        for(VillagerAction action:actions) ids.add(action.id);
        return "actions "+request+" "+status.name()+" "+unknown+" "+(actions.isEmpty()?"-":ids.toString());
    }
    public static ActionMenuSnapshot decode(String line) {
        if(line.length()>4096) throw new IllegalArgumentException("Oversized action snapshot");
        String[] fields=line.split(" ");
        if(fields.length!=5 || !fields[0].equals("actions")) throw new IllegalArgumentException("Invalid action message");
        List<VillagerAction> actions=new ArrayList<>();
        if(!fields[4].equals("-")) for(String id:fields[4].split(",",-1)) actions.add(VillagerAction.fromId(id));
        return new ActionMenuSnapshot(Long.parseLong(fields[1]),Status.valueOf(fields[2]),actions,Integer.parseInt(fields[3]));
    }
}
