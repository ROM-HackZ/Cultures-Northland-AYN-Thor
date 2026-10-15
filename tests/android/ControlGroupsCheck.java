package org.northland.thor;

public final class ControlGroupsCheck {
    static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    static void reject(String line) {
        try { GroupCommand.decode(line);throw new AssertionError("Accepted "+line); }
        catch(IllegalArgumentException expected) { }
    }
    static void group(WheelState s,int index) {
        double angle=index*Math.PI/5;
        s.update(false,true,false,(float)Math.sin(angle),(float)-Math.cos(angle));
    }
    public static void main(String[] ignored) {
        for(GroupCommand.Operation op:GroupCommand.Operation.values()) for(int slot=0;slot<10;slot++) {
            GroupCommand g=GroupCommand.decode(new GroupCommand(op,slot).encode());
            check(g.slot==slot && g.operation==op,"Lost command");
        }
        for(String line:new String[]{"group SET 10","group SET -1","group SET 01","group SELECT 3 extra",
                "group SHELL 1","group SET  1","group SET 1\n","group SET 1 "}) reject(line);
        WheelState s=new WheelState();
        for(int i=0;i<10;i++) {
            s.reset();group(s,i);check(s.mode==WheelState.Mode.GROUPS && s.choice==i,"Wrong wheel slot "+i);
            check(GroupCommand.slotAt(i)==(i+1)%10,"Wrong native slot");
        }
        s.replace(false,100);check(!s.replace(true,101),"Instant replacement");
        check(!s.replace(true,800),"Early replacement");check(s.replace(true,801),"Hold not accepted");
        group(s,4);check(!s.replace(true,1900),"Changed slot replaced while held");
        s.replace(false,2000);check(!s.replace(true,2001),"Fresh hold skipped delay");
        s.finish();check(!s.replace(true,3000),"Cancelled hold completed");
        group(s,4);check(s.mode==WheelState.Mode.NONE && s.capturesInput,"Reopened without RB release");
        s.update(false,false,false,0,0);check(!s.capturesInput,"Wheel retained input after release");
        s.update(false,true,true,0,-1);check(s.mode==WheelState.Mode.NONE && !s.capturesInput,"Interrupted drag");
        s.update(false,true,false,0,-1);check(s.mode==WheelState.Mode.NONE,"Opened after drag without fresh press");
        s.update(false,false,false,0,0);group(s,0);
        s.update(true,true,false,0,-1);check(s.mode==WheelState.Mode.NONE,"Two wheels opened");
        s.update(true,false,false,0,-1);check(s.mode==WheelState.Mode.NONE,"Second wheel opened without neutral");
        s.reset();s.update(true,false,false,1,0);check(s.mode==WheelState.Mode.COMMANDS && s.choice==2,"RT wheel regressed");
        s.reset();s.update(true,true,false,0,-1);
        check(s.mode==WheelState.Mode.NONE && s.capturesInput,"Simultaneous openers leaked game input");
        s.reset();check(s.mode==WheelState.Mode.NONE && !s.capturesInput && s.progress(9000)==0,"Reset retained operation");
        GroupDirections d=new GroupDirections();
        check(d.update(true,false,false,false,true)==-1,"Started with held direction");
        d.update(false,false,false,false,true);
        check(d.update(true,false,false,false,true)==1,"Up did not recall group 1");
        check(d.update(true,false,false,false,true)==-1,"Held/duplicate direction repeated");
        d.update(false,false,false,false,true);
        check(d.update(true,true,false,false,true)==-1,"Diagonal recalled a group");
        check(d.update(false,true,false,false,true)==-1,"Diagonal trailing edge recalled a group");
        d.update(false,false,false,false,true);
        check(d.update(false,true,false,false,true)==2,"Right mapping");
        d.update(false,false,false,false,true);check(d.update(false,false,true,false,true)==3,"Down mapping");
        d.update(false,false,false,false,true);check(d.update(false,false,false,true,true)==4,"Left mapping");
        d.update(false,false,false,false,false);
        check(d.update(true,false,false,false,true)==-1,"Direction leaked out of a wheel");
        System.out.println("30 native commands, wheel ownership, deliberate replacement, drag protection and one-shot D-pad checks passed.");
    }
}
