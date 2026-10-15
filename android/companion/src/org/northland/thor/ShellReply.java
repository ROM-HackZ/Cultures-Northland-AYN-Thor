package org.northland.thor;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** ADB shell-v2 frames: separate output and an explicit process exit status. */
final class ShellReply {
    final String stdout,stderr;
    final int exitCode;
    ShellReply(String stdout,String stderr,int exitCode) {
        this.stdout=stdout;this.stderr=stderr;this.exitCode=exitCode;
    }
    static ShellReply read(InputStream input) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(),err=new ByteArrayOutputStream();
        while(true) {
            byte[] header=exact(input,5);
            int id=header[0]&255;
            long length=0;
            for(int i=0;i<4;i++) length|=(long)(header[i+1]&255)<<(8*i);
            if(id==3) {
                if(length!=1) throw new IOException("Invalid shell exit frame");
                int status=exact(input,1)[0]&255;
                return new ShellReply(new String(out.toByteArray(),StandardCharsets.UTF_8),
                    new String(err.toByteArray(),StandardCharsets.UTF_8),status);
            }
            if((id!=1 && id!=2) || length>16384-out.size()-err.size())
                throw new IOException("Unexpected shell response");
            byte[] data=exact(input,(int)length);
            (id==1?out:err).write(data,0,data.length);
        }
    }
    private static byte[] exact(InputStream input,int length) throws IOException {
        byte[] bytes=new byte[length];int offset=0;
        while(offset<length) {
            int count=input.read(bytes,offset,length-offset);
            if(count<0) throw new EOFException("Shell closed before its exit status");
            if(count==0) throw new IOException("Empty shell read");
            offset+=count;
        }
        return bytes;
    }
}
