package sc.socketdemo.client;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

import sc.websock.common.Constants;

public class ServerHandle implements Constants {

    private WebSocketServer server;
    private WebSocket client;


    public ServerHandle() {
        new Thread() {
            @Override
            public void run() {
                super.run();
//                InetSocketAddress address = new InetSocketAddress("127.0.0.1", PORT);
                InetSocketAddress address = new InetSocketAddress(ADDRESS, PORT);
                server = new WebSocketServer(address) {
                    @Override
                    public void onOpen(WebSocket conn, ClientHandshake handshake) {
                        Log.d(TAG_CLIENT, "ServerHandle ===> onOpen() ");
                        client = conn;
                    }

                    @Override
                    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                        Log.d(TAG_CLIENT, "ServerHandle ===> onClose() , reason = " + reason);
                    }

                    @Override
                    public void onMessage(WebSocket conn, String message) {
                        Log.d(TAG_CLIENT, "ServerHandle ===> onMessage() , message = " + message);
                    }

                    @Override
                    public void onError(WebSocket conn, Exception ex) {
                        Log.e(TAG_CLIENT, "ServerHandle ===> onError() , ex = " + ex.getMessage());
                    }

                    @Override
                    public void onStart() {
                        Log.d(TAG_CLIENT, "ServerHandle ===> onStart() ");
                    }
                };
                server.start();
            }
        }.start();
    }

    public void sendData(byte[] data) {
        Log.d(TAG_CLIENT, "ServerHandle ===> sendData() , data.length = " + (data != null ? data.length : 0) + " , client = " + client);
        if (client == null) {
            return;
        }
        client.send(data);
    }

    public void destroy() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                Log.e(TAG_CLIENT, "ServerHandle ===> destroy() , e1 = " + e.toString());
            }
        }
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                Log.e(TAG_CLIENT, "ServerHandle ===> destroy() , e2 = " + e.toString());
            }
        }
    }
}
