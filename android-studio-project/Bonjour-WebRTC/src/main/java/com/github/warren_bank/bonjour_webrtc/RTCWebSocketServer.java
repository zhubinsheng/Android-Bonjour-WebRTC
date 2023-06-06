package com.github.warren_bank.bonjour_webrtc;

import android.util.Log;

import org.appspot.apprtc.TCPChannelClient;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

public class RTCWebSocketServer extends WebSocketServer {
    private final String TAG = this.getClass().toString();

    private final TCPChannelClient.TCPChannelEvents eventListener;

    private final ExecutorService executor;
    private WebSocket mConn;
    public RTCWebSocketServer(ExecutorService executor, TCPChannelClient.TCPChannelEvents eventListener, InetSocketAddress host){
        super(host);
        this.eventListener = eventListener;
        this.executor = executor;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Log.d(TAG, "onOpen()一个客户端连接成功："+conn.getRemoteSocketAddress());
        execute(() -> {
            Log.v(TAG, "Run onTCPConnected");
            eventListener.onTCPConnected(true);
        });
        mConn = conn;
    }
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.d(TAG, "onClose()服务器关闭");
        execute(() -> {
            Log.v(TAG, "Run onTCPConnected");
            eventListener.onTCPClose();
        });
    }
    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.d(TAG, "onMessage()网页端来的消息->"+message);
        execute(() -> {
            eventListener.onTCPMessage(message);
        });
    }
    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        String msg = byteBufferToString(message);
        Log.d(TAG, "onMessage()接收到ByteBuffer的数据->"+msg);
        execute(() -> {
            eventListener.onTCPMessage(msg);
        });
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.e(TAG, "->onError()出现异常："+ex);
        execute(() -> eventListener.onTCPError(ex.getMessage()));
    }
    @Override
    public void onStart() {
        Log.d(TAG, "onStart()，WebSocket服务端启动成功");
    }

    private static String byteBufferToString(ByteBuffer buffer) {
        CharBuffer charBuffer;
        try {
            Charset charset = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                charset = StandardCharsets.UTF_8;
            }
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

    public void send(String message) {
        if (mConn != null){
            mConn.send(message.getBytes());
        }
    }

    private void execute(Runnable command) {
        if ((executor == null) || executor.isShutdown() || executor.isTerminated())
            return;

        executor.execute(command);
    }
}