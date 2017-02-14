package com.sjn.demo.localfilecast;

import android.Manifest;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v7.app.AppCompatActivity;

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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mHttpServer != null && mHttpServer.isAlive()) {
            mHttpServer.stop();
        }
    }

    private class HttpServer extends NanoHTTPD {
        int mPort;
        MediaMetadataCompat mMedia;

        HttpServer(int port) throws IOException {
            super(port);
            mPort = port;
        }

        void setMedia(MediaMetadataCompat media) {
            mMedia = media;
        }

        @Override
        public Response serve(IHTTPSession session) {
            FileInputStream stream = null;
            if (mMedia == null) {
                return new Response("No music");
            }
            try {
                stream = new FileInputStream(mMedia.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return new Response(OK, "audio/mp3", stream);
        }
    }

    private void startServer() {
        String ip = getWifiAddress();
        if (mHttpServer == null || !mHttpServer.isAlive()) {
            try {
                mHttpServer = new HttpServer(findOpenPort(ip, 8080));
                mHttpServer.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        MediaMetadataCompat media = fetchMedia();
        mHttpServer.setMedia(media);
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

    private MediaMetadataCompat fetchMedia() {
        MediaMetadataCompat media = null;
        Cursor mediaCursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.ALBUM_ID},
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, null);
        if (mediaCursor != null && mediaCursor.moveToFirst()) {
            media = buildMediaMetadataCompat(mediaCursor);
            mediaCursor.close();
        }
        return media;
    }

    private MediaMetadataCompat buildMediaMetadataCompat(Cursor cursor) {
        return new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, cursor.getString(0))
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, cursor.getString(1))
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, cursor.getString(2))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, cursor.getString(3))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, cursor.getString(4))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), cursor.getLong(5)).toString())
                .build();
    }
}