package org.northland.thor;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;

/** Private reference captures exercise the same recognition used by the installed helper. */
public final class ActionNamesCheck {
    public static void main(String[] args) throws Exception {
        int expected=15,start=0;
        if(args.length>=2 && args[0].equals("--count")) { expected=Integer.parseInt(args[1]);start=2; }
        for(int arg=start;arg<args.length;arg++) {
            String path=args[arg];
            BufferedImage image=ImageIO.read(new File(path));
            int w=image.getWidth(),h=image.getHeight();
            int[] pixels=image.getRGB(0,0,w,h,null,0,w);
            MenuGeometry geometry=new MenuGeometry(w,h);geometry.find(pixels);
            Set<String> labels=new HashSet<>();
            for(MenuGeometry.Box box:geometry.commands) {
                String label=ActionNames.identify(pixels,w,box);
                System.out.println(path+" "+box+": "+label);
                if(label==null || !labels.add(label)) throw new AssertionError("Unknown or duplicate action");
            }
            if(labels.size()!=expected) throw new AssertionError("Expected "+expected+" checked actions; found "+labels.size());
        }
    }
}
