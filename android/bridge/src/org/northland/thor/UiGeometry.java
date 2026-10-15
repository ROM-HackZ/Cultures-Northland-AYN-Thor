package org.northland.thor;

import java.util.*;

/** Visible native UI frames, without text, artwork templates or game-state claims. */
final class UiGeometry {
    final int width,height;
    final MenuGeometry menus;
    final boolean[] border;
    final List<MenuGeometry.Box> targets=new ArrayList<>();
    MenuGeometry.Box modal;
    UiGeometry(int width,int height) {
        this.width=width;this.height=height;menus=new MenuGeometry(width,height);border=new boolean[width*height];
    }
    static boolean frame(int color) {
        int r=color>>16&255,g=color>>8&255,b=color&255;
        return r>=18 && r<=42 && g<=21 && b<=21 && r>=g+10;
    }
    void find(int[] pixels) {
        targets.clear();menus.find(pixels);modal=menus.modalWindow;
        for(int i=0;i<border.length;i++) border[i]=frame(pixels[i]);
        Map<Long,List<int[]>> runs=new HashMap<>();
        int left=modal==null?65:modal.left+2,right=modal==null?width-1:modal.right-2;
        int top=modal==null?40:modal.top+2,bottom=modal==null?height-10:modal.bottom-2;
        for(int y=top;y<bottom;y++) {
            for(int x=left;x<right;) {
                if(!border[y*width+x]) { x++;continue; }
                int l=x;
                while(x<right && border[y*width+x]) x++;
                if(x-l<28 || x-l>900) continue;
                int[] line={l,x,y};
                for(int dl=-1;dl<=1;dl++) for(int dr=-1;dr<=1;dr++) {
                    List<int[]> prior=runs.get(key(l/4+dl,x/4+dr));
                    if(prior==null) continue;
                    // Prefer the nearest complete bottom border, rather than spanning rows.
                    for(int j=prior.size()-1;j>=0;j--) {
                        int[] a=prior.get(j);int h=y-a[2];
                        if(h>110) break;
                        if(h<27 || Math.abs(a[0]-l)>3 || Math.abs(a[1]-x)>3) continue;
                        if(!vertical(l,a[2],y) || !vertical(x-1,a[2],y) || divided(l,x,a[2],y)) continue;
                        MenuGeometry.Box box=new MenuGeometry.Box(l,a[2],x,y+1);
                        if(modal!=null && box.top<modal.top+48 && box.width()>100) continue;
                        if(modal==null && box.x()>1500 && box.width()>95) continue;
                        add(box);break;
                    }
                }
                runs.computeIfAbsent(key(l/4,x/4),k->new ArrayList<>()).add(line);
            }
        }
        if(modal!=null) {
            // The existing window detector requires a visible native X title button.
            add(new MenuGeometry.Box(modal.right-76,modal.top+4,modal.right-6,modal.top+46));
        } else {
            toolbar(pixels);
            for(MenuGeometry.Box box:menus.commands) add(box);
        }
        targets.sort(Comparator.comparingInt(MenuGeometry.Box::y).thenComparingInt(MenuGeometry.Box::x));
    }
    static long key(int l,int r) { return ((long)l<<32)|(r&0xffffffffL); }
    boolean vertical(int x,int t,int b) {
        int hits=0;
        for(int y=t;y<=b;y++) {
            boolean hit=false;
            for(int dx=-2;dx<=2;dx++) if(x+dx>=0 && x+dx<width && border[y*width+x+dx]) hit=true;
            if(hit) hits++;
        }
        return hits*100>=(b-t+1)*86;
    }
    boolean divided(int l,int r,int t,int b) {
        // A border between two list rows must not become one tall target.
        for(int y=t+24;y<b-24;y++) {
            int hits=0;
            for(int x=l+4;x<r-4;x++) if(border[y*width+x]) hits++;
            if(hits*100>(r-l-8)*90) return true;
        }
        return false;
    }
    void add(MenuGeometry.Box box) {
        if(box.left<0 || box.top<0 || box.right>width || box.bottom>height) return;
        for(int i=0;i<targets.size();i++) {
            MenuGeometry.Box a=targets.get(i);
            int overlap=Math.max(0,Math.min(a.right,box.right)-Math.max(a.left,box.left))*
                Math.max(0,Math.min(a.bottom,box.bottom)-Math.max(a.top,box.top));
            if(overlap>.55*Math.min(a.width()*a.height(),box.width()*box.height())
                    && Math.abs(a.x()-box.x())<15 && Math.abs(a.y()-box.y())<20) {
                if(box.height()<a.height()) targets.set(i,box);
                return;
            }
        }
        targets.add(box);
    }
    void toolbar(int[] pixels) {
        List<int[]> bands=new ArrayList<>();int start=-1,last=-1;
        for(int y=50;y<680;y++) {
            int gold=0;
            for(int x=7;x<43;x++) {
                int c=pixels[y*width+x],r=c>>16&255,g=c>>8&255,b=c&255;
                if(r>90 && g>55 && g*10>b*13 && r*100>g*105) gold++;
            }
            if(gold>9) {
                if(start<0) start=y;
                else if(y-last>12) { bands.add(new int[]{start,last});start=y; }
                last=y;
            }
        }
        if(start>=0) bands.add(new int[]{start,last});
        float scale=1;
        for(int[] band:bands) if(band[1]-band[0]>20 && band[0]<100) { scale=(band[0]+band[1])/170f;break; }
        List<MenuGeometry.Box> buttons=new ArrayList<>();
        for(int expected:new int[]{85,131,199,245,285,331,382,467,515,579}) {
            int best=-1;float distance=18;
            for(int[] band:bands) {
                if(band[1]-band[0]<10) continue;
                int cy=(band[0]+band[1])/2;
                float d=Math.abs(cy-expected*scale);
                if(d<distance) { best=cy;distance=d; }
            }
            if(best>=0) buttons.add(new MenuGeometry.Box(5,best-20,47,best+20));
        }
        // A full native toolbar profile avoids treating isolated scenery as a button.
        if(buttons.size()>=7) for(MenuGeometry.Box box:buttons) add(box);
    }
    MenuGeometry.Box next(int x,int y,int direction) {
        MenuGeometry.Box best=null;double score=Double.POSITIVE_INFINITY;
        boolean inside=modal==null || x>=modal.left && x<modal.right && y>=modal.top && y<modal.bottom;
        for(MenuGeometry.Box box:targets) {
            if(x>=box.left && x<box.right && y>=box.top && y<box.bottom) continue;
            int dx=box.x()-x,dy=box.y()-y;
            int forward=direction==0?-dy:direction==1?dx:direction==2?dy:-dx;
            if(inside && forward<8) continue;
            double value;
            if(!inside) value=Math.hypot(dx,dy);
            else {
                int gap=direction%2==0?Math.max(0,Math.max(box.left-x,x-box.right)):
                    Math.max(0,Math.max(box.top-y,y-box.bottom));
                int lateral=direction%2==0?Math.abs(dx):Math.abs(dy);
                value=forward+gap*3+lateral*.1;
            }
            if(value<score) { best=box;score=value; }
        }
        return best;
    }
}
