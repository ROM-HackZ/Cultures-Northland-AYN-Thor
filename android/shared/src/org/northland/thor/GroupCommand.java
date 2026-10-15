package org.northland.thor;

/** Only native numbered-group operations cross the companion boundary. */
public final class GroupCommand {
    public enum Operation { SELECT, SET, ADD }
    public final Operation operation;
    public final int slot;
    public GroupCommand(Operation operation,int slot) {
        if(operation==null || slot<0 || slot>9) throw new IllegalArgumentException("Invalid group command");
        this.operation=operation;this.slot=slot;
    }
    public String encode() { return "group "+operation.name()+" "+slot; }
    public static GroupCommand decode(String line) {
        String[] parts=line.split(" ",-1);
        if(parts.length!=3 || !parts[0].equals("group") || !parts[2].matches("[0-9]"))
            throw new IllegalArgumentException("Invalid group command");
        return new GroupCommand(Operation.valueOf(parts[1]),Integer.parseInt(parts[2]));
    }
    public static int slotAt(int index) {
        if(index<0 || index>9) throw new IllegalArgumentException("Invalid group index");
        return (index+1)%10;
    }
}
