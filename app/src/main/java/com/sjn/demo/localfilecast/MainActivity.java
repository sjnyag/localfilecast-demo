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
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

import static fi.iki.elonen.NanoHTTPD.Response.Status.NOT_FOUND;
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main, menu);
        try {
            CastButtonFactory.setUpMediaRouteButton(getApplicationContext(),
                    menu, R.id.media_route_menu_item);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item != null && item.getItemId() == R.id.play) {
            List<MediaMetadataCompat> mediaList = fetchMedia();
            if (mediaList == null || mediaList.isEmpty()) {
                return true;
            }
            Collections.shuffle(mediaList);
            if (mHttpServer == null || !mHttpServer.isAlive()) {
                startSever();
            }
            String url = "http://" + getWifiAddress() + ":" + mHttpServer.mPort;
            mHttpServer.setMedia(mediaList.get(0));
            castMedia(url, mediaList.get(0));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class HttpServer extends NanoHTTPD {
        final int mPort;
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
            if (mMedia == null) {
                return new Response(NOT_FOUND, MIME_PLAINTEXT, "No music");
            }
            if (session.getUri().contains("image")) {
                return serveImage();
            } else {
                return serveMusic();
            }
        }

        private Response serveMusic() {
            InputStream stream = null;
            try {
                stream = new FileInputStream(
                        mMedia.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return new Response(OK, "audio/mp3", stream);
        }

        private Response serveImage() {
            InputStream stream = null;
            try {
                stream = getContentResolver().openInputStream(Uri.parse(
                        mMedia.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return new Response(OK, "image/jpeg", stream);
        }
    }

    private void startSever() {
        try {
            int port = findOpenPort();
            mHttpServer = new HttpServer(port);
            mHttpServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getWifiAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        return ((ipAddress) & 0xFF) + "." +
                ((ipAddress >> 8) & 0xFF) + "." +
                ((ipAddress >> 16) & 0xFF) + "." +
                ((ipAddress >> 24) & 0xFF);
    }

    private int findOpenPort() {
        ServerSocket socket;
        try {
            socket = new ServerSocket(0);
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("There is no open port.");
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }

    private List<MediaMetadataCompat> fetchMedia() {
        List<MediaMetadataCompat> mediaList = new ArrayList<>();
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
            do {
                mediaList.add(buildMediaMetadataCompat(mediaCursor));
            } while (mediaCursor.moveToNext());
            mediaCursor.close();
        }
        return mediaList;
    }

    private MediaMetadataCompat buildMediaMetadataCompat(Cursor cursor) {
        return new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, cursor.getString(0))
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, cursor.getString(1))
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, cursor.getString(2))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, cursor.getString(3))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, cursor.getString(4))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                        getAlbumArtUri(cursor.getLong(5)).toString())
                .build();
    }

    private Uri getAlbumArtUri(long albumId) {
        Uri albumArtUri = Uri.parse("content://media/external/audio/albumart");
        return ContentUris.withAppendedId(albumArtUri, albumId);
    }

    void castMedia(String url, MediaMetadataCompat track) {
        CastSession castSession = CastContext.getSharedInstance(getApplicationContext()).getSessionManager()
                .getCurrentCastSession();
        if (castSession != null) {
            MediaInfo media = toCastMediaMetadata(url, track);
            RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
            if (remoteMediaClient != null) {
                remoteMediaClient.load(media);
            }
        }
    }

    private MediaInfo toCastMediaMetadata(String url, MediaMetadataCompat track) {
        MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE,
                track.getDescription().getTitle() == null ? "" :
                        track.getDescription().getTitle().toString());
        mediaMetadata.putString(MediaMetadata.KEY_SUBTITLE,
                track.getDescription().getSubtitle() == null ? "" :
                        track.getDescription().getSubtitle().toString());
        mediaMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST,
                track.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
        mediaMetadata.putString(MediaMetadata.KEY_ALBUM_TITLE,
                track.getString(MediaMetadataCompat.METADATA_KEY_ALBUM));
        WebImage image = new WebImage(
                new Uri.Builder().encodedPath(url + "/image").build());
        // First image is used by the receiver for showing the audio album art.
        mediaMetadata.addImage(image);
        // Second image is used by Cast Companion Library on the full screen activity that is shown
        // when the cast dialog is clicked.
        mediaMetadata.addImage(image);

        return new MediaInfo.Builder(url)
                .setContentType("audio/mpeg")
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(mediaMetadata)
                .build();
    }

}