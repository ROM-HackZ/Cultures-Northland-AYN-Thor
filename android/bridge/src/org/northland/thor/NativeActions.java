package org.northland.thor;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.util.*;

/** Reads native menu contents only for explicit refreshes and action requests. */
final class NativeActions {
    final InputBridge bridge;
    final MenuGeometry geometry=new MenuGeometry(1920,1080);
    final int[] pixels=new int[1920*1080];
    GameScreen screen;
    ActionMenuSnapshot shown;

    NativeActions(InputBridge bridge) { this.bridge=bridge; }

    boolean ready() { return bridge.focused && bridge.armed && bridge.panel!=null && !bridge.wheel; }

    boolean readMenu() throws Exception {
        if(screen==null) screen=new GameScreen();
        Bitmap full=screen.take();
        try {
            full.getPixels(pixels,0,1920,0,0,1920,1080);
            if(geometry.find(pixels)==null) return false;
            Set<String> labels=new HashSet<>();
            for(MenuGeometry.Box box:geometry.commands) {
                String label=ActionNames.identify(pixels,1920,box);
                if(label!=null) labels.add(label);
            }
            return labels.size()>=2;
        } finally { full.recycle(); }
    }

    boolean openMenu(boolean rebuild) throws Exception {
        android.net.LocalSocket owner=bridge.panel;
        if(!ready() || !InputBridge.gameFocused()) return false;
        boolean visible=readMenu();
        if(rebuild || !visible) {
            if(geometry.modalWindow!=null) return false;
            // Native touch and our controller cursor can differ. Anchor the requested
            // menu to the controller cursor, inside the area where the entire ring fits.
            bridge.x=Math.max(300,Math.min(1620,bridge.x));
            bridge.y=Math.max(300,Math.min(740,bridge.y));
            bridge.mouse(MotionEvent.ACTION_HOVER_MOVE);
            SystemClock.sleep(45);
            if(!ready() || bridge.panel!=owner || !InputBridge.gameFocused()) return false;
            bridge.tapKey(KeyEvent.KEYCODE_SPACE);
            long deadline=SystemClock.uptimeMillis()+700;
            do {
                SystemClock.sleep(100);
                if(!ready() || bridge.panel!=owner || !InputBridge.gameFocused()) return false;
                visible=readMenu();
            } while(!visible && SystemClock.uptimeMillis()<deadline);
        }
        return visible && ready() && bridge.panel==owner;
    }

    ActionMenuSnapshot refresh(long request) throws Exception {
        shown=null;
        if(!openMenu(true)) return ActionMenuSnapshot.failure(request,ActionMenuSnapshot.Status.UNAVAILABLE);
        Set<VillagerAction> detected=EnumSet.noneOf(VillagerAction.class);
        Set<VillagerAction> ambiguous=EnumSet.noneOf(VillagerAction.class);
        int unknown=0;
        for(MenuGeometry.Box box:geometry.commands) {
            String label=ActionNames.identify(pixels,1920,box);
            if(label==null) { unknown++;continue; }
            VillagerAction action=VillagerAction.fromLabel(label);
            if(!detected.add(action)) { ambiguous.add(action);unknown++; }
        }
        detected.removeAll(ambiguous);unknown+=ambiguous.size();
        shown=new ActionMenuSnapshot(request,ActionMenuSnapshot.Status.READY,new ArrayList<>(detected),unknown);
        return shown;
    }

    void invalidate() { shown=null; }

    int[] choose(long request,VillagerAction action) throws Exception {
        if(shown==null || shown.request!=request || !shown.actions.contains(action) || !openMenu(false)) return null;
        MenuGeometry.Box match=null;
        for(MenuGeometry.Box box:geometry.commands) {
            if(!action.label.equals(ActionNames.identify(pixels,1920,box))) continue;
            if(match!=null) return null;
            match=box;
        }
        return match==null?null:new int[]{match.x(),match.y()};
    }
}
