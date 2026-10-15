package org.northland.thor;

import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.IBinder;
import java.io.*;
import java.lang.reflect.*;
import java.util.regex.*;

/** Captures only Northland-owned surfaces on the Thor's top display. */
final class GameScreen {
    private final Object args;
    private final Method capture;

    GameScreen() throws Exception {
        String packages = InputBridge.shell("cmd", "package", "list", "packages", "-U", InputBridge.GAME);
        Matcher uid = Pattern.compile("package:com\\.runesoft\\.cultures uid:(\\d+)").matcher(packages);
        if (!uid.find()) throw new IOException("Northland package UID unavailable");
        Class<?> surface = Class.forName("android.view.SurfaceControl");
        IBinder token = (IBinder)surface.getMethod("getPhysicalDisplayToken", long.class)
            .invoke(null, 4630946441858561667L);
        Class<?> builder = Class.forName("android.view.SurfaceControl$DisplayCaptureArgs$Builder");
        Object b = builder.getConstructor(IBinder.class).newInstance(token);
        Method setUid = builder.getMethod("setUid", long.class);
        setUid.setAccessible(true);
        setUid.invoke(b, Long.parseLong(uid.group(1)));
        args = builder.getMethod("build").invoke(b);
        capture = surface.getMethod("captureDisplay", Class.forName("android.view.SurfaceControl$DisplayCaptureArgs"));
    }

    Bitmap take() throws Exception {
        Object result = capture.invoke(null, args);
        if (result == null) throw new IOException("Game screen capture unavailable");
        HardwareBuffer buffer = (HardwareBuffer)result.getClass().getMethod("getHardwareBuffer").invoke(result);
        Bitmap hardware = null;
        try {
            hardware = (Bitmap)result.getClass().getMethod("asBitmap").invoke(result);
            if (hardware == null) throw new IOException("Game screen has no bitmap");
            Bitmap bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false);
            if (bitmap.getWidth() != 1920 || bitmap.getHeight() != 1080) {
                bitmap.recycle(); throw new IOException("Unexpected top display size");
            }
            return bitmap;
        } finally {
            if (hardware != null) hardware.recycle();
            if (buffer != null) buffer.close();
        }
    }

    public static void main(String[] ignored) throws Exception {
        if (!InputBridge.gameFocused()) throw new IOException("Northland must be focused");
        long start = android.os.SystemClock.uptimeMillis();
        Bitmap bitmap = new GameScreen().take();
        try {
            int[] pixels = new int[1920 * 1080];
            bitmap.getPixels(pixels, 0, 1920, 0, 0, 1920, 1080);
            MenuGeometry.Box menu = new MenuGeometry(1920, 1080).find(pixels);
            try (OutputStream out = new FileOutputStream(InputBridge.DIR + "/screen-probe.png")) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            System.out.println("Game-only capture: " + (android.os.SystemClock.uptimeMillis() - start)
                + " ms; menu=" + menu);
        } finally { bitmap.recycle(); }
    }
}
