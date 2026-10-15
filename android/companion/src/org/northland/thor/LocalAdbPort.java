package org.northland.thor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Read adbd's current local TLS port; ROMs may restrict the property. */
final class LocalAdbPort {
    static int parse(String value) {
        String port=value.trim();
        if(!port.matches("[0-9]{1,5}")) return 0;
        int number=Integer.parseInt(port);
        return number>0 && number<65536?number:0;
    }
    static int current() {
        Process query=null;
        try {
            query=new ProcessBuilder("/system/bin/getprop","service.adb.tls.port").start();
            if(!query.waitFor(2,TimeUnit.SECONDS) || query.exitValue()!=0) return 0;
            try(InputStream input=query.getInputStream()) {
                byte[] bytes=new byte[32];int count=input.read(bytes);
                return count<0?0:parse(new String(bytes,0,count,StandardCharsets.US_ASCII));
            }
        } catch(Exception ignored) { return 0; }
        finally { if(query!=null) query.destroy(); }
    }
}
