package com.google.ar.core.examples.java.archess;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

/**
 * Created by Bosse on 2017-11-30.
 */

public class NetworkHandler extends Service {

    String SERVER_IP = "155.4.193.106";
    int SERVER_PORT = 5000;
    Socket socket = null;
    private final IBinder mBinder = new LocalBinder();


    public class LocalBinder extends Binder {
        NetworkHandler getService() {
            return NetworkHandler.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d("test", "NetworkHandler Bind. Thread: " + android.os.Process.myTid());
        startSocket();
        return mBinder;
    }

    public void startSocket() {
        try{
            socket =  IO.socket("http://" + SERVER_IP + ":" + SERVER_PORT);
            socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    Log.d("test", "EVENT_CONNECT. Thread: " + android.os.Process.myTid());

                }

            }).on("board", new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    Log.d("test", "Board:");

                    JSONObject data = (JSONObject) args[0];
                    Log.d("test", data.toString() + "\n" + android.os.Process.myTid());
                }

            }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    Log.d("test", "EVENT_DISCONNECT. Thread: " + android.os.Process.myTid());
                }

            });
            socket.connect();

        } catch(URISyntaxException e) {
            e.printStackTrace();
        }
        Log.d("test", "Socket done" + android.os.Process.myTid());
    }

    public void sendMessage() {
        Log.d("test", "Sending Message");
        socket.emit("board");
    }

    public void doDisconnect() {
        socket.disconnect();
    }
}
