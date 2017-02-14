package com.sjn.demo.localfilecast;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class MainActivity extends AppCompatActivity {
    private HttpServer mHttpServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        String ip = getWifiAddress();
        int port = findOpenPort(ip, 8080);
        ((TextView) findViewById(R.id.ip_address)).setText("http://" + ip + ":" + port);
        try {
            mHttpServer = new HttpServer(port);
            mHttpServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mHttpServer != null) {
            mHttpServer.stop();
        }
    }

    private class HttpServer extends NanoHTTPD {
        HttpServer(int port) throws IOException {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String msg = "<html><body><h1>Hello server</h1>\n";
            Map<String, String> parms = session.getParms();
            if (parms.get("username") == null) {
                msg += "<form action='?' method='get'>\n  <p>Your name: <input type='text' name='username'></p>\n" + "</form>\n";
            } else {
                msg += "<p>Hello, " + parms.get("username") + "!</p>";
            }
            return new Response(msg + "</body></html>\n");
        }
    }

    private String getWifiAddress() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        return ((ipAddress) & 0xFF) + "." +
                ((ipAddress >> 8) & 0xFF) + "." +
                ((ipAddress >> 16) & 0xFF) + "." +
                ((ipAddress >> 24) & 0xFF);
    }

    private int findOpenPort(String ip, int startPort) {
        final int timeout = 200;
        for (int port = startPort; port <= 65535; port++) {
            if (isPortAvailable(ip, port, timeout)) {
                return port;
            }
        }
        throw new RuntimeException("There is no open port.");
    }

    private boolean isPortAvailable(String ip, int port, int timeout) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(ip, port), timeout);
            socket.close();
            return false;
        } catch (Exception e) {
            return true;
        }
    }
}