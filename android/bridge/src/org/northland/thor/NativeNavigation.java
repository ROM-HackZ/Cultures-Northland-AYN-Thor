package org.northland.thor;

import android.graphics.Bitmap;
import android.view.MotionEvent;

/** Capture only on a directional navigation request. Never presses a button. */
final class NativeNavigation {
    final InputBridge bridge;
    final UiGeometry geometry=new UiGeometry(1920,1080);
    final int[] pixels=new int[1920*1080];
    GameScreen screen;
    MenuGeometry.Box target;
    int requests;
    long elapsed;
    NativeNavigation(InputBridge bridge) { this.bridge=bridge; }
    void clear() { target=null; }
    void move(int direction) throws Exception {
        clear();
        long started=android.os.SystemClock.uptimeMillis();
        android.net.LocalSocket owner=bridge.panel;
        if(owner==null || !bridge.focused || !bridge.armed || bridge.mouseButtons!=0 || !InputBridge.gameFocused()) return;
        if(screen==null) screen=new GameScreen();
        Bitmap bitmap=screen.take();
        try { bitmap.getPixels(pixels,0,1920,0,0,1920,1080); } finally { bitmap.recycle(); }
        geometry.find(pixels);
        requests++;elapsed=android.os.SystemClock.uptimeMillis()-started;
        MenuGeometry.Box next=geometry.next(Math.round(bridge.x),Math.round(bridge.y),direction);
        if(next==null || bridge.panel!=owner || !bridge.focused || !InputBridge.gameFocused()) return;
        bridge.x=next.x();bridge.y=next.y();target=next;
        bridge.mouse(MotionEvent.ACTION_HOVER_MOVE);
    }
}
