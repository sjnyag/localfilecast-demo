package com.sjn.demo.localfilecast;

import android.Manifest;
import android.database.Cursor;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import fi.iki.elonen.NanoHTTPD;

import static fi.iki.elonen.NanoHTTPD.Response.Status.OK;

public class MainActivity extends AppCompatActivity {
    private HttpServer mHttpServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();
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
            FileInputStream stream = null;
            String mediaPath = fetchMedia();
            if (mediaPath == null) {
                return new Response("No music");
            }
            try {
                stream = new FileInputStream(mediaPath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return new Response(OK, "audio/mp3", stream);
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
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

    private String fetchMedia() {
        String mediaPath = null;
        Cursor mediaCursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media.DATA},
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, null);
        if (mediaCursor != null && mediaCursor.moveToFirst()) {
            mediaPath = mediaCursor.getString(0);
            mediaCursor.close();
        }
        return mediaPath;
    }
}