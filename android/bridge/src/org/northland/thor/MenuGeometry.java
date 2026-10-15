package org.northland.thor;

import java.util.*;

/** Finds the visible menu's bounds from UI geometry. No unit/action identities or artwork. */
final class MenuGeometry {
    static final class Box {
        final int left, top, right, bottom;
        Box(int l, int t, int r, int b) { left=l; top=t; right=r; bottom=b; }
        int width() { return right-left; }
        int height() { return bottom-top; }
        int x() { return (left+right)/2; }
        int y() { return (top+bottom)/2; }
        @Override public String toString() { return left+","+top+","+right+","+bottom; }
    }
    private final int width, height;
    private final byte[] mask;
    private final int[] stack;
    List<Box> commands=Collections.emptyList();
    Box modalWindow;

    MenuGeometry(int w, int h) { width=w; height=h; mask=new byte[w*h]; stack=new int[w*h]; }

    Box find(int[] pixels) {
        commands=Collections.emptyList();
        modalWindow=null;
        List<Box> buttons = new ArrayList<>();
        for (int threshold : new int[]{50,70,90}) {
            for (int i=0; i<mask.length; i++) {
                int c=pixels[i], r=(c>>16)&255, g=(c>>8)&255, b=c&255;
                mask[i]=(byte)(r<threshold && g<threshold && b<threshold && r+g+b<threshold*23/10 ? 1 : 0);
            }
            for (int seed=0; seed<mask.length; seed++) {
                if (mask[seed]==0) continue;
                int size=0, count=0, l=seed%width, r=l, t=seed/width, b=t;
                mask[seed]=0; stack[size++]=seed;
                while (size>0) {
                    int p=stack[--size], x=p%width, y=p/width;
                    count++; l=Math.min(l,x); r=Math.max(r,x); t=Math.min(t,y); b=Math.max(b,y);
                    if (x>0 && mask[p-1]!=0) { mask[p-1]=0; stack[size++]=p-1; }
                    if (x<width-1 && mask[p+1]!=0) { mask[p+1]=0; stack[size++]=p+1; }
                    if (y>0 && mask[p-width]!=0) { mask[p-width]=0; stack[size++]=p-width; }
                    if (y<height-1 && mask[p+width]!=0) { mask[p+width]=0; stack[size++]=p+width; }
                }
                int w=r-l+1, h=b-t+1;
                if(w>260 && h>140 && l>70 && l<1300 && r<1770 && t>30 && b<1050
                        && rectangularFrame(pixels,l,t,r,b) && closeButton(pixels,r,t))
                    modalWindow=new Box(l,t,r+1,b+1);
                if (w<38 || w>65 || h<38 || h>65 || Math.abs(w-h)>9 || count<60) continue;
                Box candidate=new Box(l,t,r+1,b+1);
                if(!buttonFace(pixels,candidate.x(),candidate.y())) continue;
                boolean duplicate=false;
                for (Box button : buttons)
                    if (Math.abs(button.x()-candidate.x())<16 && Math.abs(button.y()-candidate.y())<16) { duplicate=true; break; }
                if (!duplicate) buttons.add(candidate);
            }
        }
        boolean[] seen=new boolean[buttons.size()];
        Box best=null;
        int bestCount=0;
        List<Box> bestButtons=null;
        for (int i=0; i<buttons.size(); i++) {
            if (seen[i]) continue;
            List<Box> group=new ArrayList<>();
            group.add(buttons.get(i)); seen[i]=true;
            int l=width,t=height,r=0,b=0;
            for (int j=0; j<group.size(); j++) {
                Box a=group.get(j);
                l=Math.min(l,a.left);t=Math.min(t,a.top);r=Math.max(r,a.right);b=Math.max(b,a.bottom);
                for (int k=0;k<buttons.size();k++) if (!seen[k]) {
                    Box c=buttons.get(k);
                    int dx=a.x()-c.x(),dy=a.y()-c.y();
                    if (dx*dx+dy*dy<220*220) { seen[k]=true;group.add(c); }
                }
            }
            if (group.size()<3 || r-l<100 || b-t<80 || r-l>650 || b-t>650) continue;
            int outside=0, cx=(l+r)/2,cy=(t+b)/2;
            for (Box a:group) if (Math.abs(a.x()-cx)>(r-l)/4 || Math.abs(a.y()-cy)>(b-t)/4) outside++;
            if (outside*10<group.size()*6) continue;
            if (group.size()>bestCount) {
                bestCount=group.size();
                best=new Box(Math.max(0,l-35),Math.max(0,t-60),Math.min(width,r+35),Math.min(height,b+35));
                bestButtons=new ArrayList<>();
                for(Box a:group) {
                    double nx=(a.x()-cx)*2.0/(r-l),ny=(a.y()-cy)*2.0/(b-t);
                    if(nx*nx+ny*ny>.55) bestButtons.add(a);
                }
            }
        }
        if(bestButtons!=null) {
            fillGaps(bestButtons,pixels);
            bestButtons.sort(Comparator.comparingInt(Box::y));
            for(int i=0;i<bestButtons.size();) {
                int end=i+1;
                while(end<bestButtons.size() && bestButtons.get(end).y()-bestButtons.get(i).y()<16) end++;
                bestButtons.subList(i,end).sort(Comparator.comparingInt(Box::x));i=end;
            }
            if(bestButtons.size()<3) best=null;
            else commands=bestButtons;
        }
        return best;
    }

    private void fillGaps(List<Box> boxes,int[] pixels) {
        List<Box> original=new ArrayList<>(boxes);
        for(Box a:original) for(Box b:original) {
            int dx=b.x()-a.x(),dy=b.y()-a.y();
            boolean row=Math.abs(dy)<16 && dx>100 && dx<215;
            boolean col=Math.abs(dx)<16 && dy>100 && dy<215;
            if(!row && !col) continue;
            int distance=row?dx:dy, steps=Math.round(distance/67f);
            if(steps<2 || Math.abs(distance/steps-67)>10) continue;
            for(int i=1;i<steps;i++) {
                int cx=a.x()+dx*i/steps,cy=a.y()+dy*i/steps;
                addFramedButton(boxes,pixels,cx,cy);
            }
        }
        int left=width,right=0,top=height,bottom=0;
        for(Box a:original) {
            left=Math.min(left,a.x());right=Math.max(right,a.x());
            top=Math.min(top,a.y());bottom=Math.max(bottom,a.y());
        }
        // A dark piece of scenery can join an end button's frame. Complete the regular
        // bottom row only inside the already observed horizontal extent of the menu.
        for(Box a:original) {
            int row=0;
            for(Box b:original) {
                if(Math.abs(a.y()-b.y())<16) row++;
            }
            if(row>=3) for(int dx:new int[]{-67,67}) if(a.x()+dx>=left-8 && a.x()+dx<=right+8)
                    addFramedButton(boxes,pixels,a.x()+dx,a.y());
        }
        for(Box a:original) {
            if(a.x()>left+35 && a.x()<right-35) continue;
            int col=0;
            for(Box b:original) if(Math.abs(a.x()-b.x())<16) col++;
            if(col>=2) for(int dy:new int[]{-67,67}) if(a.y()+dy>=top-8 && a.y()+dy<=bottom+8)
                    addFramedButton(boxes,pixels,a.x(),a.y()+dy);
        }
    }

    private void addFramedButton(List<Box> boxes,int[] pixels,int cx,int cy) {
        for(Box c:boxes) if(Math.abs(c.x()-cx)<30 && Math.abs(c.y()-cy)<30) return;
        if(cx<26 || cy<26 || cx>=width-26 || cy>=height-26) return;
        int dark=0,gold=0;
        for(int y=cy-20;y<cy+20;y++) for(int x=cx-20;x<cx+20;x++) {
            int c=pixels[y*width+x],r=(c>>16)&255,g=(c>>8)&255,bv=c&255;
            if(r<85 && g<85 && bv<85) dark++;
            if(r>90 && g>55 && bv*5<g*4 && r*10>g*9) gold++;
        }
        if(dark>300 && gold>100 && buttonFace(pixels,cx,cy)) boxes.add(new Box(cx-24,cy-24,cx+24,cy+24));
    }

    private boolean buttonFace(int[] pixels,int cx,int cy) {
        long red=0,green=0,blue=0;
        for(int y=cy-12;y<cy+12;y++) for(int x=cx-12;x<cx+12;x++) {
            int c=pixels[y*width+x];red+=(c>>16)&255;green+=(c>>8)&255;blue+=c&255;
        }
        return red*100>green*103 && red*10>blue*13;
    }

    private boolean rectangularFrame(int[] pixels,int l,int t,int r,int b) {
        int horizontal=0,vertical=0;
        for(int x=l+5;x<r-5;x+=3) {
            boolean top=false,bottom=false;
            for(int d=0;d<5;d++) {
                top|=dark(pixels[(t+d)*width+x]);bottom|=dark(pixels[(b-d)*width+x]);
            }
            if(top && bottom) horizontal++;
        }
        for(int y=t+5;y<b-5;y+=3) {
            boolean left=false,right=false;
            for(int d=0;d<5;d++) {
                left|=dark(pixels[y*width+l+d]);right|=dark(pixels[y*width+r-d]);
            }
            if(left && right) vertical++;
        }
        return horizontal>(r-l-10)*.20 && vertical>(b-t-10)*.20;
    }
    private static boolean dark(int c) {
        return ((c>>16)&255)<100 && ((c>>8)&255)<100 && (c&255)<100;
    }

    private boolean closeButton(int[] pixels,int right,int top) {
        // Native dialogs have a pale X in the top-right title button. Dark scenery has no title chrome.
        for(int cy=top+10;cy<=top+35;cy++) for(int cx=right-50;cx<=right-20;cx++) {
            if(!pale(pixels[cy*width+cx])) continue;
            int diagonals=0;
            for(int d:new int[]{-6,-3,3,6}) {
                if(paleNear(pixels,cx+d,cy+d)) diagonals++;
                if(paleNear(pixels,cx+d,cy-d)) diagonals++;
            }
            if(diagonals<8) continue;
            if(!paleNear(pixels,cx-7,cy) && !paleNear(pixels,cx+7,cy)
                    && !paleNear(pixels,cx,cy-7) && !paleNear(pixels,cx,cy+7)) return true;
        }
        return false;
    }
    private boolean paleNear(int[] pixels,int x,int y) {
        for(int dy=-1;dy<=1;dy++) for(int dx=-1;dx<=1;dx++)
            if(pale(pixels[(y+dy)*width+x+dx])) return true;
        return false;
    }
    private static boolean pale(int c) {
        int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
        return r>=170 && g>=160 && b>=140 && Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<70;
    }

    boolean selectionMarker(int[] pixels) {
        for(int i=0;i<mask.length;i++) {
            int c=pixels[i],r=(c>>16)&255,g=(c>>8)&255,b=c&255;
            mask[i]=(byte)(b>100 && b*2>r*3 && b*4>g*5 ? 1:0);
        }
        for(int seed=0;seed<mask.length;seed++) {
            if(mask[seed]==0) continue;
            int size=0,count=0,l=seed%width,r=l,t=seed/width,b=t;
            mask[seed]=0;stack[size++]=seed;
            while(size>0) {
                int p=stack[--size],x=p%width,y=p/width;count++;
                l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);
                if(x>0 && mask[p-1]!=0){mask[p-1]=0;stack[size++]=p-1;}
                if(x<width-1 && mask[p+1]!=0){mask[p+1]=0;stack[size++]=p+1;}
                if(y>0 && mask[p-width]!=0){mask[p-width]=0;stack[size++]=p-width;}
                if(y<height-1 && mask[p+width]!=0){mask[p+width]=0;stack[size++]=p+width;}
            }
            int w=r-l+1,h=b-t+1;
            if(w<16 || w>65 || h<w*.65 || h>w*1.1 || count<w*h*.4) continue;
            int middle=0,tip=0;
            for(int x=l;x<=r;x++) {
                int c=pixels[(t+h/2)*width+x];if(blue(c)) middle++;
                c=pixels[(b-1)*width+x];if(blue(c)) tip++;
            }
            if(middle>w*.65 && tip<w*.35 && blue(pixels[(t+h/4)*width+l+w/4])
                    && blue(pixels[(t+h/4)*width+l+w*3/4])) return true;
        }
        return false;
    }
    private static boolean blue(int c) {
        int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
        return b>100 && b*2>r*3 && b*4>g*5;
    }
}
