package org.northland.thor;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.Arrays;

public final class UiNavigationCheck {
    public static void main(String[] args) throws Exception {
        DirectionRepeat repeat=new DirectionRepeat();
        if(repeat.update(false,false,true,false,true,100)!=2 || repeat.update(false,false,true,false,true,200)!=-1
                || repeat.update(false,false,true,false,true,480)!=2
                || repeat.update(true,true,false,false,true,700)!=-1) throw new AssertionError("Directional repeat");
        if(repeat.update(false,false,true,false,false,800)!=-1
                || repeat.update(false,false,true,false,true,801)!=2
                || repeat.update(false,false,true,false,true,1180)!=-1
                || repeat.update(false,false,true,false,true,1181)!=2
                || repeat.update(false,false,true,false,true,1360)!=-1
                || repeat.update(false,false,true,false,true,1361)!=2)
            throw new AssertionError("Repeat reset or cadence");
        syntheticFrames();
        for(String arg:args) {
            int split=arg.indexOf('=');String profile=split<0?"":arg.substring(0,split);
            String path=split<0?arg:arg.substring(split+1);
            BufferedImage image=ImageIO.read(new File(path));int w=image.getWidth(),h=image.getHeight();
            UiGeometry ui=new UiGeometry(w,h);ui.find(image.getRGB(0,0,w,h,null,0,w));
            System.out.println(path+" modal="+ui.modal+" targets="+ui.targets.size());
            if(ui.targets.isEmpty()) throw new AssertionError("No native UI targets");
            if(ui.modal!=null) for(MenuGeometry.Box box:ui.targets)
                if(box.left<ui.modal.left || box.right>ui.modal.right || box.top<ui.modal.top || box.bottom>ui.modal.bottom)
                    throw new AssertionError("Target escaped modal");
            if(profile.equals("toolbar")) {
                if(ui.modal!=null || ui.targets.size()!=10) throw new AssertionError("Toolbar count");
                MenuGeometry.Box wrench=at(ui,26,466);
                MenuGeometry.Box down=ui.next(wrench.x(),wrench.y(),2);
                if(down==null || Math.abs(down.y()-515)>4) throw new AssertionError("Toolbar down");
                if(ui.next(26,84,0)!=null) throw new AssertionError("Toolbar wrapped above top");
            } else if(profile.equals("profession")) {
                at(ui,1143,327);at(ui,942,381);at(ui,942,438);at(ui,942,495);at(ui,942,666);
                MenuGeometry.Box row=ui.next(942,381,2);
                if(row==null || Math.abs(row.y()-438)>4) throw new AssertionError("Skipped profession row");
            } else if(profile.equals("general")) {
                at(ui,1297,249);at(ui,960,528);at(ui,960,672);
            } else if(profile.equals("save")) {
                for(int y:new int[]{414,471,528,585,642,699}) {
                    MenuGeometry.Box row=at(ui,942,y);
                    if(row.height()>60) throw new AssertionError("Merged save rows");
                }
            } else if(!profile.isEmpty()) throw new IllegalArgumentException("Unknown profile: "+profile);
            if(ui.modal!=null && ui.next(26,466,1)==null) throw new AssertionError("Cannot enter popup");
        }
        System.out.println("UI navigation geometry and repeat checks passed");
    }
    static MenuGeometry.Box at(UiGeometry ui,int x,int y) {
        for(MenuGeometry.Box box:ui.targets) if(Math.abs(box.x()-x)<=5 && Math.abs(box.y()-y)<=5) return box;
        throw new AssertionError("Missing target near "+x+","+y);
    }
    static void syntheticFrames() {
        UiGeometry ui=new UiGeometry(1920,1080);int[] pixels=new int[1920*1080];
        Arrays.fill(pixels,0x809080);ui.find(pixels);
        if(!ui.targets.isEmpty() || ui.next(960,540,0)!=null) throw new AssertionError("Blank screen target");
        for(int[] rect:new int[][]{{400,100,700,188},{400,300,700,348},{400,357,700,405}})
            for(int y=rect[1];y<rect[3];y++) for(int x=rect[0];x<rect[2];x++)
                if(y<rect[1]+3 || y>=rect[3]-3 || x<rect[0]+3 || x>=rect[2]-3) pixels[y*1920+x]=0x201008;
        ui.find(pixels);
        if(ui.targets.size()!=3) throw new AssertionError("Framed targets: "+ui.targets);
        at(ui,550,144);at(ui,550,324);at(ui,550,381);
        MenuGeometry.Box row=ui.next(550,324,2);
        if(row==null || Math.abs(row.y()-381)>3) throw new AssertionError("Row navigation");
        ui.modal=new MenuGeometry.Box(390,90,710,410);
        if(ui.next(1000,800,0)==null) throw new AssertionError("Popup entry");
    }
}
