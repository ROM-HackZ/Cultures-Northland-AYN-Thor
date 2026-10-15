package org.northland.thor;

/** One cardinal direction, with a short delay before repeat. Diagonals stop it. */
public final class DirectionRepeat {
    private int held=-1;
    private long next;
    public void reset() { held=-1;next=0; }
    public int update(boolean up,boolean right,boolean down,boolean left,boolean enabled,long now) {
        int count=(up?1:0)+(right?1:0)+(down?1:0)+(left?1:0);
        if(!enabled || count!=1) { reset();return -1; }
        int direction=up?0:right?1:down?2:3;
        if(direction!=held) { held=direction;next=now+380;return direction; }
        if(now<next) return -1;
        next=now+180;return direction;
    }
}
