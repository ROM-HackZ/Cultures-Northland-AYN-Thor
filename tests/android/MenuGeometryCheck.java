package org.northland.thor;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/** Checks supplied private captures without checking game images into the project. */
public final class MenuGeometryCheck {
    public static void main(String[] args) throws Exception {
        if(args.length==0 || args.length%2!=0) throw new IllegalArgumentException("COUNT[:dialog|clear] IMAGE [...]; 0 means no native menu");
        for(int i=0;i<args.length;i+=2) {
            String[] expectation=args[i].split(":");
            int expected=Integer.parseInt(expectation[0]);
            BufferedImage image=ImageIO.read(new File(args[i+1]));
            int w=image.getWidth(),h=image.getHeight();
            int[] pixels=image.getRGB(0,0,w,h,null,0,w);
            MenuGeometry geometry=new MenuGeometry(w,h);
            MenuGeometry.Box bounds=geometry.find(pixels);
            int count=geometry.commands.size();
            System.out.println(args[i+1]+": buttons="+count+", menu="+bounds+", selectionMarker="+geometry.selectionMarker(pixels)+", modal="+geometry.modalWindow);
            for(MenuGeometry.Box button:geometry.commands) System.out.println("  "+button);
            if(count!=expected) throw new AssertionError("Expected "+expected+" commands; found "+count);
            if((bounds==null)!=(expected==0)) throw new AssertionError("Menu visibility mismatch");
            if(expectation.length>1 && (geometry.modalWindow!=null)!=expectation[1].equals("dialog"))
                throw new AssertionError("Expected modal state "+expectation[1]);
        }
    }
}
