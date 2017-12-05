package com.google.ar.core.examples.java.archess;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
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

    String SERVER_IP = "130.240.155.95";
    int SERVER_PORT = 5000;
    Socket socket = null;
    private final IBinder mBinder = new LocalBinder();
    public String username = "Bosse";


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
                    // Emit username to server
                    JSONObject user = new JSONObject();
                    try {
                        user.put("user", username);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    socket.emit("join", user);
                }

            }).on("board", new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    JSONObject data = (JSONObject) args[0];
                    sendMessage("board", data.toString());
                }

            }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    Log.d("test", "EVENT_DISCONNECT. Thread: " + android.os.Process.myTid());
                }

            }).on("joined", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    JSONObject data = (JSONObject) args[0];
                    try {
                        if (data.getString("you").equals("observer")) {
                            sendMessage("observer", "");
                        } else {
                            if (data.getString("started").equals("false")) {
                                sendMessage("waiting", "");
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            }).on("moveFailed", new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    JSONObject data = (JSONObject) args[0];
                    try {
                        String message = data.getString("result");
                        sendMessage("moveFailed", message);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            }).on("promote", new Emitter.Listener() {

                @Override
                public void call(Object... args) {

                }

            });
            socket.connect();

        } catch(URISyntaxException e) {
            e.printStackTrace();
        }
        Log.d("test", "Socket done" + android.os.Process.myTid());
    }

    public void makeMove(String move) {
        Log.d("test", "Sending Message");

        JSONObject chessMove = new JSONObject();
        try {
            chessMove.put("move", move);
            chessMove.put("user", username);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        socket.emit("move", chessMove);
        //socket.emit("board");
    }

    public void sendMessage(String event) {

    }
    public void sendMessage (String event, String data) {
        Intent intent = new Intent("Event");
        intent.putExtra("event", event);
        if (event == "board") {
            intent.putExtra("board", data);
        }


        if (event == "moveFailed") {
            intent.putExtra("message", data);
        }


        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void startGame () {

    }

    public void doDisconnect() {
        socket.disconnect();
    }
}
