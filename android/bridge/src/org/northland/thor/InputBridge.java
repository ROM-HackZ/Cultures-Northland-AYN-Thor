package org.northland.thor;

import android.os.SystemClock;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.channels.FileLock;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.*;

/** Own-code shell helper. Does not load, modify, or read the game's private files. */
public final class InputBridge {
    static final String GAME = "com.runesoft.cultures";
    static final String DIR = "/data/local/tmp/northland-thor";
    static final Map<String, Integer> COMMANDS = new LinkedHashMap<>();
    static {
        COMMANDS.put("cancel", KeyEvent.KEYCODE_ESCAPE);
        COMMANDS.put("actions", KeyEvent.KEYCODE_SPACE);
        COMMANDS.put("build", KeyEvent.KEYCODE_B);
        COMMANDS.put("road", KeyEvent.KEYCODE_S);
        COMMANDS.put("profession", KeyEvent.KEYCODE_C);
        COMMANDS.put("home", KeyEvent.KEYCODE_W);
        COMMANDS.put("workplace", KeyEvent.KEYCODE_A);
        COMMANDS.put("headquarters", KeyEvent.KEYCODE_H);
        COMMANDS.put("characters", KeyEvent.KEYCODE_F7);
        COMMANDS.put("statistics", KeyEvent.KEYCODE_F6);
        COMMANDS.put("technology", KeyEvent.KEYCODE_F8);
        COMMANDS.put("diplomacy", KeyEvent.KEYCODE_F5);
        COMMANDS.put("save", KeyEvent.KEYCODE_F4);
        COMMANDS.put("load", KeyEvent.KEYCODE_F3);
        COMMANDS.put("options", KeyEvent.KEYCODE_F2);
        COMMANDS.put("help", KeyEvent.KEYCODE_F1);
        COMMANDS.put("pause", KeyEvent.KEYCODE_P);
        COMMANDS.put("speed", KeyEvent.KEYCODE_L);
        COMMANDS.put("zoom", KeyEvent.KEYCODE_Z);
        COMMANDS.put("map", KeyEvent.KEYCODE_F10);
        COMMANDS.put("next-civilian", KeyEvent.KEYCODE_PERIOD);
        COMMANDS.put("next-scout", KeyEvent.KEYCODE_COMMA);
    }
    final Object input;
    final Method inject, setDisplay;
    final Map<Integer, Long> pressed = new HashMap<>();
    final ConcurrentLinkedQueue<Runnable> pending = new ConcurrentLinkedQueue<>();
    final Pad pad = new Pad();
    float x = 960, y = 540;
    volatile int mouseButtons;
    long mouseDown;
    volatile boolean focused, running = true;
    volatile boolean armed;
    Process reader;
    LocalServerSocket server;
    NativeActions nativeActions;
    volatile LocalSocket panel;
    volatile String status = "state 0 0 960 540 -1 -1";
    long lastDiagnostic;
    volatile boolean wheel;
    boolean wheelCancelled;
    boolean selectTriggerDown, selectionNeedsNeutral;
    int choice = -1;
    static final String[] WHEEL = {"actions", "build", "profession", "home", "workplace", "characters", "save", "cancel"};

    void connectPanel() throws Exception {
        String listing = shell("cmd", "package", "list", "packages", "-U", "org.northland.thor");
        Matcher uid = Pattern.compile("package:org\\.northland\\.thor uid:(\\d+)").matcher(listing);
        if (!uid.find()) throw new IOException("Install the Northland Thor companion before starting controller mode");
        final int allowedUid = Integer.parseInt(uid.group(1));
        server = new LocalServerSocket("northland-thor-v1");
        nativeActions=new NativeActions(this);
        Thread accept = new Thread(() -> {
            while (running) {
                try {
                    LocalSocket socket = server.accept();
                    if (socket.getPeerCredentials().getUid() != allowedUid) { socket.close(); continue; }
                    if (panel != null) panel.close();
                    panel = socket;
                    socket.setSoTimeout(3000);
                    servePanel(socket);
                } catch (Exception e) {
                    if (running) System.err.println("Panel disconnected");
                }
            }
        }, "thor-panel");
        accept.setDaemon(true); accept.start();
    }

    void servePanel(LocalSocket socket) throws Exception {
        final ConcurrentLinkedQueue<String> panelMessages=new ConcurrentLinkedQueue<>();
        Thread write = new Thread(() -> {
            try {
                OutputStream out = socket.getOutputStream();
                while (running && panel == socket) {
                    String notice;
                    while((notice=panelMessages.poll())!=null) out.write((notice+"\n").getBytes("US-ASCII"));
                    out.write((status + "\n").getBytes("US-ASCII")); out.flush();
                    SystemClock.sleep(33);
                }
            } catch (IOException e) {
                try { socket.close(); } catch (IOException ignored) { }
            }
        }, "thor-panel-state");
        write.setDaemon(true); write.start();
        try {
            InputStream in = socket.getInputStream();
            StringBuilder line = new StringBuilder();
            int c;
            long lastCommand = 0;
            while (running && (c = in.read()) != -1) {
                if (c == '\n') {
                    String command = line.toString(); line.setLength(0);
                    long now = SystemClock.uptimeMillis();
                    if (command.equals("ping")) continue;
                    if (now - lastCommand < 80 || pending.size() > 20) continue;
                    lastCommand = now;
                    if (COMMANDS.containsKey(command)) pending.add(() -> {
                        if (focused && armed) try {
                            tapKey(COMMANDS.get(command));
                        }
                        catch (Exception e) { throw new RuntimeException(e); }
                    });
                    else if(command.startsWith("open-actions ")) {
                        final long request=Long.parseLong(command.substring(13));
                        if(request<=0) throw new IOException("Invalid action request");
                        pending.add(() -> {
                            ActionMenuSnapshot result;
                            try {
                                synchronized(pad) {
                                    if(panel!=socket) return;
                                    if(!focused || !armed || wheel || mouseButtons!=0 || !pressed.isEmpty() || !pad.neutral()) {
                                        nativeActions.invalidate();
                                        result=ActionMenuSnapshot.failure(request,ActionMenuSnapshot.Status.BUSY);
                                    } else result=nativeActions.refresh(request);
                                }
                            } catch(Exception e) {
                                nativeActions.invalidate();
                                result=ActionMenuSnapshot.failure(request,ActionMenuSnapshot.Status.UNAVAILABLE);
                                System.err.println("Action menu unavailable: "+e.getClass().getSimpleName());
                            }
                            panelMessages.add(result.encode());
                        });
                    }
                    else if (command.startsWith("choose-action ")) {
                        String[] fields=command.split(" ");
                        if(fields.length!=3) throw new IOException("Invalid action command");
                        final long request=Long.parseLong(fields[1]);
                        final VillagerAction action=VillagerAction.fromId(fields[2]);
                        if(request<=0) throw new IOException("Invalid action request");
                        pending.add(() -> {
                            try {
                                synchronized(pad) {
                                    if(panel!=socket || !focused || !armed || wheel || mouseButtons!=0 || !pressed.isEmpty() || !pad.neutral()) {
                                        panelMessages.add("notice action-busy");return;
                                    }
                                    int[] point=nativeActions.choose(request,action);
                                    if(point==null) { panelMessages.add("notice action-unavailable");return; }
                                    if(action.shortcut!=null) {
                                        if(panel==socket && gameFocused()) tapKey(COMMANDS.get(action.shortcut));
                                        return;
                                    }
                                    x=point[0];y=point[1];mouse(MotionEvent.ACTION_HOVER_MOVE);
                                    SystemClock.sleep(45);
                                    if(panel!=socket || !gameFocused()) return;
                                    button(1,true);
                                    try { SystemClock.sleep(70); } finally { button(1,false); }
                                }
                            } catch(Exception e) {
                                panelMessages.add("notice action-unavailable");
                                System.err.println("Villager action unavailable: "+e.getClass().getSimpleName());
                            }
                        });
                    }
                    else if (command.equals("stop")) { running = false; break; }
                } else {
                    if (c < 32 || c > 126 || line.length() >= 96) throw new IOException("Invalid panel command");
                    line.append((char)c);
                }
            }
        } finally {
            if (panel == socket) panel = null;
            socket.close();
        }
    }

    InputBridge() throws Exception {
        Class<?> cls = Class.forName("android.hardware.input.InputManager");
        input = cls.getMethod("getInstance").invoke(null);
        inject = cls.getMethod("injectInputEvent", InputEvent.class, int.class);
        setDisplay = InputEvent.class.getMethod("setDisplayId", int.class);
    }

    static String shell(String... args) throws Exception {
        Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        try (InputStream in = p.getInputStream()) {
            int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
        }
        if (p.waitFor() != 0) throw new IOException("Device command failed: " + args[0]);
        return out.toString("UTF-8");
    }

    static boolean gameFocused() throws Exception {
        String dump = shell("dumpsys", "input");
        int start = dump.indexOf("FocusedWindows:");
        int end = dump.indexOf("FocusRequests:", start);
        if (start < 0 || end < start) return false;
        return Pattern.compile("displayId=0, name='[^'\\n]* " + Pattern.quote(GAME) + "/")
                .matcher(dump.substring(start, end)).find();
    }

    void send(InputEvent event) throws Exception {
        setDisplay.invoke(event, 0);
        if (!Boolean.TRUE.equals(inject.invoke(input, event, 1)))
            throw new IOException("Android rejected input injection");
    }

    void key(int code, boolean down) throws Exception {
        if (down == pressed.containsKey(code)) return;
        long now = SystemClock.uptimeMillis();
        long since = down ? now : pressed.get(code);
        send(new KeyEvent(since, now, down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD));
        if (down) pressed.put(code, since); else pressed.remove(code);
    }

    void tapKey(int code) throws Exception {
        key(code, true);
        // The game polls some key state per frame; a zero-duration pulse can be missed.
        SystemClock.sleep(45);
        key(code, false);
    }

    void mouse(int action) throws Exception {
        long now = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
        pp.id = 0; pp.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
        pc.x = x; pc.y = y; pc.pressure = mouseButtons == 0 ? 0 : 1; pc.size = 1;
        MotionEvent event = MotionEvent.obtain(mouseDown == 0 ? now : mouseDown, now,
                action, 1, new MotionEvent.PointerProperties[]{pp},
                new MotionEvent.PointerCoords[]{pc}, 0, mouseButtons,
                1, 1, -1, 0, InputDevice.SOURCE_MOUSE, 0);
        try { send(event); } finally { event.recycle(); }
    }

    void button(int which, boolean down) throws Exception {
        if (((mouseButtons & which) != 0) == down) return;
        // Keep one pointer gesture owner; don't interrupt selection with X.
        if (down && mouseButtons != 0) return;
        if (down) {
            if (mouseButtons == 0) mouseDown = SystemClock.uptimeMillis();
            mouseButtons |= which;
            mouse(MotionEvent.ACTION_DOWN);
        } else {
            // SDL compares the new mask with its previous mask to find the release.
            mouseButtons &= ~which;
            mouse(MotionEvent.ACTION_UP);
            if (mouseButtons == 0) mouseDown = 0;
        }
    }

    void release() throws Exception {
        for (int code : new ArrayList<>(pressed.keySet())) key(code, false);
        if ((mouseButtons & 1) != 0) button(1, false);
        if ((mouseButtons & 2) != 0) button(2, false);
    }

    static final class Pad {
        final int[] axis = new int[64];
        final boolean[] buttons = new boolean[768];
        boolean neutral() {
            for (int i : new int[]{0, 1, 2, 5}) if (Math.abs(axis[i]) > 7000) return false;
            for (int i = 0x130; i <= 0x13f; i++) if (buttons[i]) return false;
            return axis[16] == 0 && axis[17] == 0 && axis[9] < 8000 && axis[10] < 8000;
        }
    }

    static String inputDevice(String name) throws Exception {
        File root = new File("/sys/class/input");
        File[] entries = root.listFiles();
        if (entries != null) for (File entry : entries) {
            if (!entry.getName().matches("event[0-9]+")) continue;
            try (BufferedReader in = new BufferedReader(new FileReader(new File(entry, "device/name")))) {
                if (name.equals(in.readLine())) return "/dev/input/" + entry.getName();
            }
        }
        throw new IOException(name + " not found; no other device will be captured");
    }

    void readPad() throws Exception {
        String device = inputDevice("Odin Controller");
        String initial = shell("getevent", "-lp", device);
        String[] axes = {"ABS_X", "ABS_Y", "ABS_Z", "ABS_RZ", "ABS_GAS", "ABS_BRAKE", "ABS_HAT0X", "ABS_HAT0Y"};
        int[] codes = {0, 1, 2, 5, 9, 10, 16, 17};
        for (int i = 0; i < axes.length; i++) {
            Matcher value = Pattern.compile(axes[i] + "\\s*: value (-?\\d+)").matcher(initial);
            if (!value.find()) throw new IOException("Missing Thor axis: " + axes[i]);
            pad.axis[codes[i]] = Integer.parseInt(value.group(1));
        }
        System.out.println("Reading " + device + " (Odin Controller); no exclusive grab");
        reader = new ProcessBuilder("getevent", "-t", device).redirectErrorStream(true).start();
        Thread thread = new Thread(() -> {
            Pattern event = Pattern.compile(".*?([0-9a-f]{4}) ([0-9a-f]{4}) ([0-9a-f]{8})\\s*$");
            try (BufferedReader in = new BufferedReader(new InputStreamReader(reader.getInputStream()))) {
                String line;
                while (running && (line = in.readLine()) != null) {
                    Matcher m = event.matcher(line);
                    if (!m.matches()) continue;
                    int type = Integer.parseInt(m.group(1), 16);
                    int code = Integer.parseInt(m.group(2), 16);
                    int value = (int) Long.parseLong(m.group(3), 16);
                    if (type == 3 && code < pad.axis.length) {
                        synchronized (pad) { pad.axis[code] = value; }
                    } else if (type == 1 && code < pad.buttons.length) {
                        synchronized (pad) { pad.buttons[code] = value != 0; }
                        if (value != 2) pending.add(() -> hardwareButton(code, value != 0));
                    } else if (type == 0 && code == 3) {
                        throw new IOException("Controller event overflow; restart with controls released");
                    }
                }
                if (running) throw new IOException("Controller disconnected");
            } catch (Exception e) {
                System.err.println(e.getMessage()); running = false;
            }
        }, "thor-pad");
        thread.setDaemon(true); thread.start();
    }

    void hardwareButton(int code, boolean down) {
        if (!focused || !armed) return;
        try {
            if (wheel) {
                if (code == 0x130 && down && choice >= 0 && !wheelCancelled) {
                    tapKey(COMMANDS.get(WHEEL[choice])); wheelCancelled = true;
                }
                if (code == 0x131 && down) wheelCancelled = true;
                return;
            }
            switch (code) {
                case 0x130: // A remains an alternative to LT; either can hold selection.
                    if (!selectionNeedsNeutral) button(1,down || selectTriggerDown);
                    break;
                case 0x133: button(2, down); break; // X / BTN_NORTH
                case 0x131:
                    key(KeyEvent.KEYCODE_ESCAPE, down);break; // B
                case 0x134: key(KeyEvent.KEYCODE_SPACE, down); break; // Y
                case 0x136: key(KeyEvent.KEYCODE_SHIFT_LEFT, down); break; // LB
                case 0x137:
                    key(KeyEvent.KEYCODE_PERIOD, down);
                    break; // RB
                case 0x13a: key(KeyEvent.KEYCODE_F10, down); break; // Select
                case 0x13b: key(KeyEvent.KEYCODE_P, down); break; // Start
                case 0x13e: key(KeyEvent.KEYCODE_Z, down); break; // R3, verified vendor .kl
                default: break;
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static float stick(int value) {
        float n = value / 32767f;
        float m = Math.max(0, (Math.abs(n) - .18f) / .82f);
        return Math.copySign(m * m, n);
    }

    void frame(float dt) throws Exception {
        synchronized (pad) {
            if (!focused || panel == null) {
                armed = false; wheel = false; selectTriggerDown = false;
                selectionNeedsNeutral = true; release(); return;
            }
            if (!armed) {
                armed = pad.neutral();
                if (armed) selectionNeedsNeutral = false;
                return;
            }
            boolean rt = pad.axis[9] > 12000 || pad.buttons[0x139];
            if (rt && !wheel) {
                release(); wheelCancelled = false; selectTriggerDown = false;
                selectionNeedsNeutral = true;
            }
            if (!rt) wheelCancelled = false;
            wheel = rt;
            if (wheel) {
                float wx = pad.axis[2] / 32767f, wy = pad.axis[5] / 32767f;
                choice = Math.hypot(wx, wy) < .35 ? -1 :
                    ((int)Math.floor((Math.atan2(wx, -wy) + Math.PI / 8 + Math.PI * 2) / (Math.PI / 4))) % 8;
                return;
            }
            key(KeyEvent.KEYCODE_SHIFT_LEFT, pad.buttons[0x136]);
            // Hysteresis avoids repeated clicks around the analog trigger threshold.
            boolean lt = pad.axis[10] > (selectTriggerDown ? 8000 : 12000) || pad.buttons[0x138];
            if (selectionNeedsNeutral && pad.axis[10] < 8000 && !pad.buttons[0x138] && !pad.buttons[0x130])
                selectionNeedsNeutral = false;
            selectTriggerDown = lt && !selectionNeedsNeutral;
            button(1,!selectionNeedsNeutral && (selectTriggerDown || pad.buttons[0x130]));
            key(KeyEvent.KEYCODE_H, pad.axis[17] < 0 || pad.buttons[0x220]);
            key(KeyEvent.KEYCODE_F7, pad.axis[17] > 0 || pad.buttons[0x221]);
            key(KeyEvent.KEYCODE_DPAD_LEFT, pad.axis[2] < -10000);
            key(KeyEvent.KEYCODE_DPAD_RIGHT, pad.axis[2] > 10000);
            key(KeyEvent.KEYCODE_DPAD_UP, pad.axis[5] < -10000);
            key(KeyEvent.KEYCODE_DPAD_DOWN, pad.axis[5] > 10000);
            float dx = stick(pad.axis[0]), dy = stick(pad.axis[1]);
            if (dx != 0 || dy != 0) {
                x = Math.max(1, Math.min(1918, x + dx * 1050 * dt));
                y = Math.max(1, Math.min(1078, y + dy * 1050 * dt));
                mouse(mouseButtons == 0 ? MotionEvent.ACTION_HOVER_MOVE : MotionEvent.ACTION_MOVE);
            }
        }
    }

    void runController() throws Exception {
        String display = shell("dumpsys", "input");
        if (!Pattern.compile("Display: 0\\s+logicalSize=1920x1080").matcher(display).find())
            throw new IOException("This Thor prototype requires a 1920x1080 top display");
        File stop = new File(DIR, "stop");
        if (stop.exists() && !stop.delete()) throw new IOException("Cannot clear previous stop request");
        Thread watcher = new Thread(() -> {
            while (running) {
                try { focused = gameFocused(); } catch (Exception e) { focused = false; }
                SystemClock.sleep(350);
            }
        }, "thor-focus");
        watcher.setDaemon(true); watcher.start();
        long last = SystemClock.uptimeMillis();
        try {
            readPad();
            connectPanel();
            System.out.println("Controller bridge ready; only Northland display 0 receives input");
            while (running && !stop.exists()) {
                long now = SystemClock.uptimeMillis();
                frame(Math.min(.05f, (now - last) / 1000f));
                Runnable job;
                while ((job = pending.poll()) != null) job.run();
                status = "state " + (focused ? 1 : 0) + " " + (armed ? 1 : 0) + " " + Math.round(x) + " " + Math.round(y)
                    + " " + (wheel && !wheelCancelled ? 1 : -1) + " " + choice;
                if (now - lastDiagnostic >= 200) {
                    File temp = new File(DIR, "state.tmp");
                    try (Writer out = new OutputStreamWriter(new FileOutputStream(temp), "UTF-8")) {
                        out.write("{\"focused\":" + focused + ",\"armed\":" + armed + ",\"x\":" + Math.round(x)
                            + ",\"y\":" + Math.round(y) + ",\"wheel\":" + (wheel && !wheelCancelled)
                            + ",\"choice\":" + choice + ",\"mouse\":" + mouseButtons + ",\"keys\":" + pressed.keySet() + "}");
                    }
                    if (!temp.renameTo(new File(DIR, "state.json"))) throw new IOException("Cannot publish bridge state");
                    lastDiagnostic = now;
                }
                last = now;
                SystemClock.sleep(8);
            }
        } finally {
            running = false;
            try { release(); } finally {
                if (reader != null) reader.destroy();
                if (panel != null) panel.close();
                if (server != null) server.close();
                new File(DIR, "state.json").delete();
            }
            System.out.println("Bridge stopped; injected buttons released");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("controller | key NAME | click X Y left|right | move X Y");
        try (RandomAccessFile lockFile = new RandomAccessFile(DIR + "/lock", "rw");
             FileLock lock = lockFile.getChannel().tryLock()) {
            if (lock == null) throw new IOException("Northland bridge already running");
            InputBridge bridge = new InputBridge();
            if (args[0].equals("controller")) { bridge.runController(); return; }
            if (!gameFocused()) throw new IOException("Northland is not focused on display 0");
            try {
                if (args[0].equals("key") && args.length == 2 && COMMANDS.containsKey(args[1])) {
                    bridge.tapKey(COMMANDS.get(args[1]));
                } else if ((args[0].equals("click") || args[0].equals("move")) && args.length >= 3) {
                    bridge.x = Float.parseFloat(args[1]); bridge.y = Float.parseFloat(args[2]);
                    if (!(bridge.x >= 0 && bridge.x < 1920 && bridge.y >= 0 && bridge.y < 1080))
                        throw new IllegalArgumentException("Point outside display");
                    bridge.mouse(MotionEvent.ACTION_HOVER_MOVE);
                    SystemClock.sleep(100);
                    if (args[0].equals("click")) {
                        if (args.length != 4 || !(args[3].equals("left") || args[3].equals("right")))
                            throw new IllegalArgumentException("Expected left or right");
                        int button = args[3].equals("left") ? 1 : 2;
                        bridge.button(button, true); SystemClock.sleep(90); bridge.button(button, false);
                    }
                } else throw new IllegalArgumentException("Unknown probe command");
                System.out.println("Input delivered to Northland: " + args[0]);
            } finally { bridge.release(); }
        }
    }
}
