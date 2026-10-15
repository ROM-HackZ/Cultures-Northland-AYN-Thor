package org.northland.thor;

/** Coalesce hat/button duplicates; one recall per complete directional gesture. */
public final class GroupDirections {
    private boolean ready;
    public int update(boolean up,boolean right,boolean down,boolean left,boolean enabled) {
        int count=(up?1:0)+(right?1:0)+(down?1:0)+(left?1:0);
        if(!enabled) { ready=false;return -1; }
        if(count==0) { ready=true;return -1; }
        if(!ready) return -1;
        ready=false;
        return count==1?(up?1:right?2:down?3:4):-1;
    }
}
