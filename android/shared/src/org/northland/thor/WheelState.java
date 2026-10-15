package org.northland.thor;

/** Input ownership and deliberate replacement, independent of Android injection. */
public final class WheelState {
    public enum Mode { NONE, COMMANDS, GROUPS }
    public Mode mode=Mode.NONE;
    public int choice=-1;
    public boolean capturesInput;
    private boolean latched,replaceReleased=true;
    private int replaceChoice=-1;
    private long replaceStarted=-1;
    public void reset() {
        mode=Mode.NONE;choice=-1;capturesInput=false;latched=false;
        resetReplacement();replaceReleased=true;
    }
    private void resetReplacement() { replaceStarted=-1;replaceChoice=-1; }
    public void update(boolean rt,boolean rb,boolean selectionHeld,float x,float y) {
        if(!rt && !rb) { reset();return; }
        if(mode!=Mode.NONE && (rt && rb || mode==Mode.COMMANDS && !rt || mode==Mode.GROUPS && !rb)) finish();
        if(mode==Mode.NONE && !latched) {
            if(selectionHeld) { latched=true;return; }
            if(rt && rb) { latched=true;capturesInput=true;return; }
            mode=rb?Mode.GROUPS:Mode.COMMANDS;capturesInput=true;
        }
        if(mode==Mode.NONE) return;
        int count=mode==Mode.GROUPS?10:8;
        int next=Math.hypot(x,y)<.35?-1:
            ((int)Math.floor((Math.atan2(x,-y)+Math.PI/count+Math.PI*2)/(Math.PI*2/count)))%count;
        if(next!=choice) {
            resetReplacement();replaceReleased=false;
        }
        choice=next;
    }
    public void finish() { mode=Mode.NONE;choice=-1;latched=true;capturesInput=true;resetReplacement(); }
    public int progress(long now) {
        return replaceStarted<0?0:(int)Math.max(0,Math.min(100,(now-replaceStarted)*100/700));
    }
    public boolean replace(boolean held,long now) {
        if(!held) { resetReplacement();replaceReleased=true;return false; }
        if(mode!=Mode.GROUPS || choice<0 || !replaceReleased) return false;
        if(replaceStarted<0) { replaceStarted=now;replaceChoice=choice; }
        if(replaceChoice!=choice) { resetReplacement();replaceReleased=false;return false; }
        return progress(now)>=100;
    }
}
