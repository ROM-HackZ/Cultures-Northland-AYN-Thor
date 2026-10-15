package org.northland.thor;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class ShellReplyCheck {
    static byte[] frame(int id,byte[] data) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(id);
        for(int i=0;i<4;i++) bytes.write(data.length>>(8*i));
        bytes.write(data);return bytes.toByteArray();
    }
    static byte[] join(byte[]... chunks) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        for(byte[] chunk:chunks) bytes.write(chunk);
        return bytes.toByteArray();
    }
    static byte[] text(int id,String value) throws Exception { return frame(id,value.getBytes(StandardCharsets.UTF_8)); }
    static void reject(byte[] bytes) throws Exception {
        try { ShellReply.read(new ByteArrayInputStream(bytes)); }
        catch(IOException expected) { return; }
        throw new AssertionError("Malformed shell reply accepted");
    }
    public static void main(String[] args) throws Exception {
        byte[] bytes=join(text(1,"20"),text(2,"warning\n"),text(1,"00\n"),frame(3,new byte[]{0}));
        // ADB may split headers/payloads anywhere and leave the transport open
        // after the exit frame. Reading for stream EOF was a startup failure.
        InputStream fragmented=new ByteArrayInputStream(bytes) {
            public synchronized int read(byte[] target,int offset,int length) {
                if(available()==0) throw new AssertionError("Read beyond shell exit frame");
                return super.read(target,offset,Math.min(length,1));
            }
        };
        ShellReply reply=ShellReply.read(fragmented);
        if(!reply.stdout.equals("2000\n") || !reply.stderr.equals("warning\n") || reply.exitCode!=0)
            throw new AssertionError("Shell output or exit status lost");
        ShellReply failed=ShellReply.read(new ByteArrayInputStream(join(text(2,"denied"),frame(3,new byte[]{126}))));
        if(failed.exitCode!=126 || !failed.stderr.equals("denied")) throw new AssertionError("Failure status lost");
        reject(text(1,"NORTHLAND_STARTED\n")); // Output without exit is not success.
        reject(new byte[]{1,5,0,0,0,65});
        reject(new byte[]{1,-1,-1,-1,-1});
        reject(frame(4,new byte[0]));
        reject(frame(3,new byte[0]));
        reject(join(frame(1,new byte[16384]),text(2,"x"),frame(3,new byte[]{0})));
        System.out.println("Shell-v2 framing, fragmentation, exit and size checks passed");
    }
}
