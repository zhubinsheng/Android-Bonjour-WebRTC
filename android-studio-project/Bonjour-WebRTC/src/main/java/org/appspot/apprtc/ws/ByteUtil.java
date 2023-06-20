package org.appspot.apprtc.ws;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

public class ByteUtil {
    public static String byteBufferToString(ByteBuffer buffer)
    {
        CharBuffer charBuffer = null;
        try {
            Charset charset = Charset.forName("UTF-8");
            CharsetDecoder decoder = charset.newDecoder();
            charBuffer = decoder.decode(buffer);
            buffer.flip();
            return charBuffer.toString();
         } catch (Exception ex)
            {
                ex.printStackTrace();
                 return null;
            }
    }
}