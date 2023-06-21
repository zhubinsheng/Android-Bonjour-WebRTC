package org.appspot.apprtc.ws;

import android.util.Log;

import org.appspot.apprtc.DirectRTCClient;
import org.appspot.apprtc.TCPChannelClient;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.webrtc.ThreadUtils;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;

public class MyWebSocketServer extends WebSocketServer {

    private final ExecutorService executor;

    private final TCPChannelClient.TCPChannelEvents eventListener;

    private void execute(Runnable command) {
        if ((executor == null) || executor.isShutdown() || executor.isTerminated())
            return;

        executor.execute(command);
    }

    public MyWebSocketServer(InetSocketAddress host, ExecutorService executor, TCPChannelClient.TCPChannelEvents tcpChannelEvents){
        super(host);
        this.executor = executor;
        this.eventListener = tcpChannelEvents;

    }
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Log.d("websocket", "onOpen()一个客户端连接成功："+conn.getRemoteSocketAddress());

        execute(new Runnable() {
            @Override
            public void run() {
                eventListener.onTCPConnected(true);
            }
        });

    }
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.d("websocket", "onClose()服务器关闭");
    }
    @Override
    public void onMessage(WebSocket conn, String message) {
        // 这里在网页测试端发过来的是文本数据 测试网页 http://www.websocket-test.com/
        // 需要保证android 和 加载网页的设备(我这边是电脑) 在同一个网段内，连同一个wifi即可
        Log.d("websocket", "onMessage()网页端来的消息->"+message);

        execute(new Runnable() {
            @Override
            public void run() {
                eventListener.onTCPMessage(message);
            }
        });
    }
    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        // 接收到的是Byte数据，需要转成文本数据，根据你的客户端要求
        // 看看是string还是byte，工具类在下面贴出
        Log.d("websocket", "onMessage()接收到ByteBuffer的数据->" + ByteUtil.byteBufferToString(message));

        execute(new Runnable() {
            @Override
            public void run() {
                eventListener.onTCPMessage(ByteUtil.byteBufferToString(message));
            }
        });
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        // 异常  经常调试的话会有缓存，导致下一次调试启动时，端口未关闭,多等待一会儿
        // 可以在这里回调处理，关闭连接，开个线程重新连接调用startMyWebsocketServer()
        Log.e("websocket", "->onError()出现异常："+ex);
    }
    @Override
    public void onStart() {
            Log.d("websocket", "onStart()，WebSocket服务端启动成功");
        }
}
 
