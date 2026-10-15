package org.northland.thor;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Non-focusable windows keep the original game's SDL surface active. */
public final class PanelService extends Service {
    final Handler handler = new Handler(Looper.getMainLooper());
    final ConcurrentLinkedQueue<String> commands = new ConcurrentLinkedQueue<>();
    volatile boolean running, connected, active, armed;
    volatile LocalSocket socket;
    volatile int cursorX = 960, cursorY = 540, wheel = -1, choice = -1, replaceProgress;
    long selectionRevision=-1;
    WindowManager topManager, bottomManager;
    CursorView cursor;
    DeckView deck;
    static final String[] WHEEL = {"Actions", "Build", "Profession", "Assign home", "Workplace", "Characters", "Save", "Back"};

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("controller", "Northland controller", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, SetupActivity.class), PendingIntent.FLAG_IMMUTABLE);
        startForeground(1, new Notification.Builder(this, "controller")
            .setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("Northland Thor")
            .setContentText("Controller panel running — tap for controls").setContentIntent(open).build());
        DisplayManager dm = getSystemService(DisplayManager.class);
        Display top = dm.getDisplay(0), bottom = dm.getDisplay(4);
        if (top == null || bottom == null) { stopSelf(); return; }
        Context tc = createDisplayContext(top), bc = createDisplayContext(bottom);
        topManager = (WindowManager)tc.getSystemService(WINDOW_SERVICE);
        bottomManager = (WindowManager)bc.getSystemService(WINDOW_SERVICE);
        cursor = new CursorView(tc); deck = new DeckView(bc);
        WindowManager.LayoutParams tp = params(false);
        // Android's untrusted-overlay threshold is .8. Keep physical touches usable.
        tp.alpha = .75f;
        topManager.addView(cursor, tp); bottomManager.addView(deck, params(true));
        running = true;
        new Thread(this::connect, "northland-panel").start();
        handler.post(new Runnable() { public void run() {
            if (!running) return;
            int visibility = connected && active ? View.VISIBLE : View.INVISIBLE;
            if(wheel>0 || !connected || !active || !armed) {
                deck.confirmGroup=false;deck.pressedGroup=-1;deck.pressedGroupControl=-1;
            }
            cursor.setVisibility(visibility); deck.setVisibility(visibility);
            cursor.invalidate(); deck.invalidate(); handler.postDelayed(this, 33);
        }});
    }

    WindowManager.LayoutParams params(boolean touchable) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(-1, -1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT; p.setTitle("Northland Thor commands");
        return p;
    }

    void connect() {
        while (running) {
            try {
                LocalSocket s = new LocalSocket(); socket = s;
                s.connect(new LocalSocketAddress("northland-thor-v1", LocalSocketAddress.Namespace.ABSTRACT));
                if (s.getPeerCredentials().getUid() != 2000) throw new IOException("Bridge must run as ADB shell");
                s.setSoTimeout(2500);
                connected = true;
                try (BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                    OutputStream out = s.getOutputStream();
                    long heartbeat = 0;
                    String line;
                    while (running && (line = in.readLine()) != null) {
                        if(line.startsWith("actions ")) {
                            ActionMenuSnapshot result=ActionMenuSnapshot.decode(line);
                            handler.post(() -> deck.receiveActions(result));
                            continue;
                        }
                        if(line.equals("notice action-unavailable")) {
                            handler.post(() -> android.widget.Toast.makeText(deck.getContext(),
                                "Couldn't choose that action. Refresh actions or use Y.",android.widget.Toast.LENGTH_LONG).show());
                            continue;
                        }
                        if(line.equals("notice action-busy")) {
                            handler.post(() -> android.widget.Toast.makeText(deck.getContext(),
                                "Release the controls, then try the action again.",android.widget.Toast.LENGTH_LONG).show());
                            continue;
                        }
                        if(line.equals("notice group-busy") || line.equals("notice group-unavailable")) {
                            handler.post(() -> android.widget.Toast.makeText(deck.getContext(),
                                "Couldn't send the group command. Close any game window and release the controls.",
                                android.widget.Toast.LENGTH_LONG).show());
                            continue;
                        }
                        String[] fields = line.split(" ");
                        if (fields.length != 9 || !fields[0].equals("state")) throw new IOException("Bad bridge state");
                        active = fields[1].equals("1"); armed = fields[2].equals("1");
                        cursorX = Integer.parseInt(fields[3]); cursorY = Integer.parseInt(fields[4]);
                        wheel = Integer.parseInt(fields[5]); choice = Integer.parseInt(fields[6]);
                        replaceProgress=Integer.parseInt(fields[7]);
                        long revision=Long.parseLong(fields[8]);
                        if(revision!=selectionRevision) {
                            if(selectionRevision>=0) handler.post(() -> deck.invalidateActions());
                            selectionRevision=revision;
                        }
                        String command;
                        while ((command = commands.poll()) != null) out.write((command + "\n").getBytes("US-ASCII"));
                        if (SystemClock.uptimeMillis() - heartbeat > 1000) {
                            out.write("ping\n".getBytes("US-ASCII")); heartbeat = SystemClock.uptimeMillis();
                        }
                        out.flush();
                    }
                } finally { s.close(); }
            } catch (Exception e) { /* A missing bridge is a visible offline state. */ }
            connected = active = armed = false; wheel = -1;replaceProgress=0;selectionRevision=-1;commands.clear();
            handler.post(() -> { deck.invalidateActions();deck.confirmGroup=false;deck.actionError="Connection lost. Refresh after reconnecting."; });
            try { if (socket != null) socket.close(); } catch (IOException ignored) { }
            SystemClock.sleep(750);
        }
    }

    @Override public void onDestroy() {
        running = false; handler.removeCallbacksAndMessages(null);
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        if (cursor != null) topManager.removeView(cursor);
        if (deck != null) bottomManager.removeView(deck);
        super.onDestroy();
    }

    class CursorView extends View {
        final Paint paint = new Paint(3);
        CursorView(Context context) { super(context); }
        @Override protected void onDraw(Canvas c) {
            if (!connected || !active || !armed) return;
            c.save(); c.scale(getWidth() / 1920f, getHeight() / 1080f);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(6); paint.setColor(Color.BLACK);
            c.drawCircle(cursorX, cursorY, 13, paint);
            paint.setStrokeWidth(3); paint.setColor(0xffffdc86); c.drawCircle(cursorX, cursorY, 13, paint);
            c.drawLine(cursorX - 22, cursorY, cursorX - 8, cursorY, paint);
            c.drawLine(cursorX + 8, cursorY, cursorX + 22, cursorY, paint);
            c.drawLine(cursorX, cursorY - 22, cursorX, cursorY - 8, paint);
            c.drawLine(cursorX, cursorY + 8, cursorX, cursorY + 22, paint);
            if (wheel == 1 || wheel == 2) drawWheel(c);
            c.restore();
        }
        void drawWheel(Canvas c) {
            boolean groups=wheel==2;
            int count=groups?10:8;
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xee131f29);
            c.drawCircle(960, 540, 400, paint);
            for (int i = 0; i < count; i++) {
                double a = i * Math.PI * 2/count - Math.PI / 2;
                float cx = 960 + (float)Math.cos(a) * 285, cy = 540 + (float)Math.sin(a) * 285;
                paint.setColor(i == choice ? 0xffbd8b35 : 0xff304452);
                float half=groups?82:106;
                c.drawRoundRect(cx - half, cy - 37, cx + half, cy + 37, 18, 18, paint);
                paint.setColor(Color.WHITE); paint.setTextSize(27); paint.setTextAlign(Paint.Align.CENTER);
                c.drawText(groups?"Group "+GroupCommand.slotAt(i):WHEEL[i], cx, cy + 10, paint);
            }
            paint.setColor(0xffffdc86); paint.setTextSize(32);
            c.drawText(groups?"GROUPS":"COMMANDS", 960, 490, paint);
            paint.setColor(Color.WHITE); paint.setTextSize(24);
            c.drawText("Right stick to choose", 960, 532, paint);
            c.drawText(groups?"A select   X add":"A confirm   B cancel", 960, 570, paint);
            if(groups) {
                c.drawText("Hold Y to replace   B cancel",960,608,paint);
                paint.setColor(0xffbd8b35);
                c.drawRoundRect(850,634,850+220*replaceProgress/100f,646,5,5,paint);
            }
        }
    }

    class DeckView extends View {
        final Paint p = new Paint(3);
        final String[] tabs = {"VILLAGER", "SETTLEMENT", "SESSION", "GROUPS"};
        final String[][] labels = {
            {"Actions", "Build", "Profession", "Assign home", "Workplace", "Road", "Next civilian", "Next scout", "Back"},
            {"Headquarters", "Characters", "Statistics", "Technology", "Diplomacy", "Map", "Zoom", "Help", "Back"},
            {"Pause", "Speed", "Save", "Load", "Options", "Help", "Map", "Zoom", "Back"}
        };
        final String[][] ids = {
            {"actions", "build", "profession", "home", "workplace", "road", "next-civilian", "next-scout", "cancel"},
            {"headquarters", "characters", "statistics", "technology", "diplomacy", "map", "zoom", "help", "cancel"},
            {"pause", "speed", "save", "load", "options", "help", "map", "zoom", "cancel"}
        };
        int tab, pressedCell = -1;
        ActionMenuSnapshot snapshot;
        long actionRequest,requestStarted,pressedRequest;
        String actionError;
        int pressedAction=-1,actionPage;
        boolean actionsOpen;
        boolean commandGesture;
        int groupIndex,pressedGroup=-1,pressedGroupControl=-1;
        boolean confirmGroup;
        DeckView(Context ctx) { super(ctx); }
        void text(Canvas c, String t, float x, float y, float size, int color) {
            p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextSize(size); p.setTextAlign(Paint.Align.LEFT);
            c.drawText(t, x, y, p);
        }
        void tile(Canvas c, float l, float t, float r, float b, int color) {
            p.setStyle(Paint.Style.FILL); p.setColor(color); c.drawRoundRect(l,t,r,b,16,16,p);
        }
        boolean showingCommands() {
            return tab==0 && actionsOpen;
        }
        void invalidateActions() {
            snapshot=null;actionRequest++;pressedAction=-1;
            confirmGroup=false;pressedGroupControl=-1;
            actionError="Group selection changed. Tap Refresh to read its actions.";
        }
        void refreshActions() {
            actionRequest=Math.max(actionRequest+1,SystemClock.uptimeMillis());
            requestStarted=SystemClock.uptimeMillis();snapshot=null;actionError=null;actionPage=0;
            commands.add("open-actions "+actionRequest);
        }
        void receiveActions(ActionMenuSnapshot result) {
            if(!actionsOpen || result.request!=actionRequest) return;
            snapshot=result;
            if(result.status==ActionMenuSnapshot.Status.BUSY)
                actionError="Release the controls, then tap Refresh.";
            else if(result.status==ActionMenuSnapshot.Status.UNAVAILABLE)
                actionError="Select a character and close other windows, then Refresh.";
        }
        int actionCell(float x,float y) {
            if(snapshot==null || snapshot.status!=ActionMenuSnapshot.Status.READY
                    || x<48 || x>=1192 || y<307 || y>=932) return -1;
            int col=(int)((x-48)/386),row=(int)((y-307)/125);
            if(col>2 || row>4 || (x-48)%386>372 || (y-307)%125>111) return -1;
            int index=actionPage*15+row*3+col;
            return index<snapshot.actions.size()?index:-1;
        }
        void drawActions(Canvas c) {
            if(snapshot!=null && snapshot.status==ActionMenuSnapshot.Status.READY && !snapshot.actions.isEmpty()) {
                int pages=(snapshot.actions.size()+14)/15;
                actionPage=Math.min(actionPage,pages-1);
                for(int cell=0;cell<15;cell++) {
                    int index=actionPage*15+cell;
                    if(index>=snapshot.actions.size()) break;
                    float left=48+(cell%3)*386,top=307+(cell/3)*125;
                    tile(c,left,top,left+372,top+111,index==pressedAction?0xff816431:0xff263b49);
                    text(c,snapshot.actions.get(index).label,left+22,top+65,28,0xfff5eddd);
                }
                if(pages>1) {
                    tile(c,918,967,1192,1050,0xff253743);
                    text(c,"Next page",947,1020,27,0xfff5eddd);
                }
            } else {
                tile(c,48,307,1192,555,0xff263b49);
                String message=actionError;
                if(message==null) message=snapshot!=null?"No labeled commands found. Use the original menu.":
                    SystemClock.uptimeMillis()-requestStarted>5000?"Couldn't read the actions. Tap Refresh to try again.":
                    "Reading the selected character's actions...";
                text(c,message,72,400,27,0xfff5eddd);
                text(c,"The original top-screen menu is always available with Y.",72,460,25,0xff91a9b9);
            }
            tile(c,48,967,345,1050,0xff253743);
            text(c,"Back to Villager",72,1020,27,0xfff5eddd);
            tile(c,365,967,660,1050,0xff253743);
            text(c,"Refresh actions",391,1020,27,0xfff5eddd);
        }
        int groupCell(float x,float y) {
            if(x<48 || x>=1192 || y<307 || y>=597) return -1;
            int col=(int)((x-48)/232),row=(int)((y-307)/145);
            if(col>4 || (x-48)%232>=216 || (y-307)%145>=125) return -1;
            return row*5+col;
        }
        int groupControl(float x,float y) {
            if(y>=690 && y<=790 && x>=48 && x<1192) {
                int col=(int)((x-48)/386);
                return col<3 && (x-48)%386<=372?col:-1;
            }
            if(confirmGroup && y>=820 && y<=910) {
                if(x>=48 && x<=600) return 3;
                if(x>=620 && x<=1192) return 4;
            }
            return -1;
        }
        void drawGroups(Canvas c) {
            boolean controller=wheel==2;
            int selected=controller?choice:groupIndex;
            for(int i=0;i<10;i++) {
                float l=48+(i%5)*232,t=307+(i/5)*145;
                tile(c,l,t,l+216,t+125,i==selected?0xff816431:0xff263b49);
                text(c,"Group "+GroupCommand.slotAt(i),l+24,t+72,31,0xfff5eddd);
            }
            text(c,selected>=0?"Group "+GroupCommand.slotAt(selected):"Choose a group with the right stick",48,650,32,0xffffd787);
            String[] actions=controller?new String[]{"A  Select", "Hold Y  Replace", "X  Add selection"}:
                new String[]{"Select group", "Set from selection", "Add selection"};
            for(int i=0;i<3;i++) {
                float l=48+i*386;
                tile(c,l,690,l+372,790,i==1 && replaceProgress>0?0xff816431:0xff263b49);
                text(c,actions[i],l+22,751,27,0xfff5eddd);
            }
            if(controller) {
                tile(c,48,820,1192,840,0xff253743);
                if(replaceProgress>0) tile(c,48,820,48+1144*replaceProgress/100f,840,0xffbd8b35);
                text(c,"Hold Y to replace with your current selection. B cancels.",48,900,26,0xffffd787);
            } else if(confirmGroup) {
                tile(c,48,820,600,910,0xff253743);tile(c,620,820,1192,910,0xff816431);
                text(c,"Cancel",74,877,29,0xfff5eddd);
                text(c,"Replace group "+GroupCommand.slotAt(groupIndex),645,877,29,0xfff5eddd);
            } else text(c,"Set replaces the group. Add keeps its existing members.",48,856,25,0xff91a9b9);
            text(c,"Units move from their old group when assigned to another.",48,954,24,0xff91a9b9);
            text(c,"D-pad: groups 1-4    Hold RB: all groups    Repeat select: center",48,1024,24,0xffd3e1e9);
        }
        @Override protected void onDraw(Canvas canvas) {
            Canvas c = canvas; c.save(); c.scale(getWidth()/1240f, getHeight()/1080f);
            c.drawColor(0xff111c25);
            text(c, "NORTHLAND", 48, 77, 43, 0xffffd787);
            text(c, "THOR COMMAND DECK", 48, 115, 23, 0xff91a9b9);
            String state = !connected ? "BRIDGE OFFLINE — start from computer" :
                !active ? "Waiting for Northland on the top screen" : !armed ? "Release sticks and buttons to enable controls" :
                showingCommands()?(snapshot!=null && snapshot.status==ActionMenuSnapshot.Status.READY?
                    snapshot.unknown>0?"ACTIONS - "+snapshot.unknown+" unread; use the original menu for those":
                    "ACTIONS - checked when opened; Refresh after changing selection":"ACTIONS"):"CONTROLLER CONNECTED";
            text(c, state, 48, 167, 24, active && armed ? 0xff8dd9b2 : 0xffffc77b);
            for (int i=0; i<4; i++) {
                float left = 48 + i*292;
                tile(c,left,202,left+276,280,(wheel==2?i==3:tab==i)?0xff816431:0xff253743);
                text(c,tabs[i],left+20,252,24,0xfff5eddd);
            }
            if(wheel==2 || tab==3) { drawGroups(c);c.restore();return; }
            if(showingCommands()) { drawActions(c);c.restore();return; }
            for (int i=0; i<9; i++) {
                float left=48+(i%3)*386, top=307+(i/3)*143;
                tile(c,left,top,left+372,top+125,pressedCell==i?0xff665638:0xff263b49);
                text(c,labels[tab][i],left+24,top+73,30,active&&armed?0xfff5eddd:0xff8596a0);
            }
            text(c,"LS  Cursor     RS  Camera     LT  Tap select / confirm",48,810,25,0xffd3e1e9);
            text(c,"Hold LT, then release: order    ·    LT + LS: drag select",48,854,25,0xffd3e1e9);
            text(c,"Hold RT: wheel · RS chooses · A confirms · B cancels",48,908,26,0xffffd787);
            text(c,"B Back · X Order · Y Actions · RB Groups · D-pad Groups 1-4",48,951,24,0xff91a9b9);
            text(c,"Hold LB to add to selection. Top-screen touch is unchanged.",48,1024,22,0xff91a9b9);
            c.restore();
        }
        int cell(float x, float y) {
            int col=(int)((x-48)/386), row=(int)((y-307)/143);
            if(x<48||x>=1192||y<307||y>=718||col<0||col>2||row<0||row>2) return -1;
            if((x-48)%386>372||(y-307)%143>125) return -1;
            return row*3+col;
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX()*1240/getWidth(), y=e.getY()*1080/getHeight();
            if(wheel==2 || !connected || !active || !armed) {
                pressedGroup=-1;pressedGroupControl=-1;confirmGroup=false;return true;
            }
            if(e.getActionMasked()==MotionEvent.ACTION_CANCEL) {
                pressedGroup=-1;pressedGroupControl=-1;confirmGroup=false;
                pressedAction=-1;pressedCell=-1;commandGesture=false;return true;
            }
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {
                if(tab==3 && y>=307) {
                    pressedGroup=groupCell(x,y);pressedGroupControl=groupControl(x,y);return true;
                }
                if(showingCommands() && y>=307) {
                    commandGesture=true;pressedRequest=actionRequest;pressedAction=actionCell(x,y);
                    return true;
                }
                pressedCell=cell(x,y); return true;
            }
            if(e.getActionMasked()==MotionEvent.ACTION_UP) {
                if(tab==3 && y>=307) {
                    int hit=groupCell(x,y),control=groupControl(x,y);
                    if(hit>=0 && hit==pressedGroup) { groupIndex=hit;confirmGroup=false; }
                    else if(control>=0 && control==pressedGroupControl) {
                        if(control==1) confirmGroup=true;
                        else if(control==3) confirmGroup=false;
                        else if(control==0 || control==2 || control==4 && confirmGroup) {
                            GroupCommand.Operation op=control==0?GroupCommand.Operation.SELECT:
                                control==2?GroupCommand.Operation.ADD:GroupCommand.Operation.SET;
                            commands.add(new GroupCommand(op,GroupCommand.slotAt(groupIndex)).encode());confirmGroup=false;
                        }
                    }
                    pressedGroup=-1;pressedGroupControl=-1;performClick();return true;
                }
                if(commandGesture) {
                    if(pressedAction>=0 && connected && active && armed && actionCell(x,y)==pressedAction
                            && snapshot!=null && snapshot.request==pressedRequest && actionRequest==pressedRequest)
                        commands.add("choose-action "+snapshot.request+" "+snapshot.actions.get(pressedAction).id);
                    else if(x>=48 && x<=345 && y>=967) actionsOpen=false;
                    else if(x>=365 && x<=660 && y>=967 && connected && active && armed) refreshActions();
                    else if(x>=918 && y>=967 && snapshot!=null && snapshot.actions.size()>15)
                        actionPage=(actionPage+1)%((snapshot.actions.size()+14)/15);
                    commandGesture=false;pressedAction=-1;performClick();return true;
                }
                int hit=cell(x,y);
                if(y>=202&&y<=280&&x>=48&&x<1192) {
                    tab=Math.min(3,(int)((x-48)/292));actionsOpen=false;confirmGroup=false;
                    pressedAction=-1;commandGesture=false;
                }
                else if(hit>=0&&hit==pressedCell&&connected&&active&&armed) {
                    if(tab==0 && hit==0) { actionsOpen=true;refreshActions(); }
                    else commands.add(ids[tab][hit]);
                }
                pressedCell=-1; performClick(); return true;
            }
            if(e.getActionMasked()==MotionEvent.ACTION_CANCEL) pressedCell=-1;
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
