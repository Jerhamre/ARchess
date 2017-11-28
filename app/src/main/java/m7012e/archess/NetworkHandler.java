package m7012e.archess;


import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URISyntaxException;
//import java.net.Socket;

public class NetworkHandler extends Service {
    String SERVER_IP = "155.4.193.106";
    int SERVER_PORT = 5000;

    NetworkListener NL = null;
    NetworkCommunication NC = null;


    String response = "";


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("test", "NetworkHandler Started" + android.os.Process.myTid());

        /*try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            //socketInput = socket.getInputStream();
            //byteArrayOutputStream = new ByteArrayOutputStream(1024);
*/
            NL = new NetworkListener(null, "Gradle");
            new Thread(NL).start();


            SystemClock.sleep(5000);

            //NL.sendMessage("board");

            //new Thread(new NetworkCommunication("Hej")).start();

        /*} catch (IOException e) {
            e.printStackTrace();
        }*/

        //new Thread(NL).start();

        /*
        NC = new NetworkCommunication();
        r = new Thread(NC);
        r.start();
*/

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    class NetworkListener implements Runnable {
        Socket socket = null;
        InputStream socketInput = null;
        String msg = "";
        ByteArrayOutputStream byteArrayOutputStream = null;
        String response = "";

        public NetworkListener(Socket socket, String msg) {
            Log.d("test", "Network Listener" + android.os.Process.myTid() + " Args: " + msg);

            this.socket = socket;
            this.msg = msg;
            //try {
                //this.socketInput = socket.getInputStream();
            /*} catch (IOException e) {
                e.printStackTrace();
            }*/
        }

        @Override
        public void run() {
            Log.d("test", "Network Listener run" + android.os.Process.myTid() + " Args: " + msg);

                try {
                    this.socket =  IO.socket("http://" + SERVER_IP + ":" + SERVER_PORT);
                    this.socket.on("board", onNewMessage);
                    this.socket.connect();
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
                Log.d("test", "Socket done" + android.os.Process.myTid());

                //this.socketInput = this.socket.getInputStream();
                //this.byteArrayOutputStream = new ByteArrayOutputStream(1024);

            sendMessage("move");
            SystemClock.sleep(3000);
            listener();
        }

        public void listener() {
            int bytesRead;
            byte[] buffer = new byte[1024];


            Log.d("test", "Network Listener listener" + android.os.Process.myTid() + " Args: " + msg);


            /*try {
                while((bytesRead = this.socketInput.read(buffer)) != -1) {
                    this.byteArrayOutputStream.write(buffer, 0, bytesRead);
                    this.response += byteArrayOutputStream.toString("UTF-8");
                }
                Log.d("test", response);

                //new Thread(new NetworkCommunication("Test")).start();


                listener();
            } catch (IOException e) {
                e.printStackTrace();
            }
            */
        }

        private Emitter.Listener onNewMessage = new Emitter.Listener() {
            @Override
            public void call(Object... args) {

                JSONObject data = (JSONObject) args[0];
                Log.d("test", data.toString() + "\n" + android.os.Process.myTid());

                /*String username;
                String message;
                try {
                    username = data.getString("username");
                    message = data.getString("message");
                } catch (JSONException e) {
                    return;
                }*/

            }
        };


        public void handleMessage(JSONObject msg) {
            Log.d("test", msg.toString());
        }

        public void sendMessage(String msg) {

            Log.d("test", "Message: " + msg);
            JSONObject chessMove = new JSONObject();
            try {
                chessMove.put("move", "b2-b4");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            socket.emit("move", chessMove);
            //BufferedWriter outToServer = null;

            /*
            try {
                outToServer = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                outToServer.write(msg);
                outToServer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            */
            //outToServer.flush();

            Log.d("test", msg + android.os.Process.myTid());

        }
    }

    static class NetworkCommunication implements Runnable {

        public NetworkCommunication(String test)
        {
            Log.d("test", "Communication " + android.os.Process.myTid() + " Args: " + test);
        }
        @Override
        public void run() {
            sendMessage("board");
            Log.d("test", "Communication " + android.os.Process.myTid());

        }

        public void sendMessage(String msg) {

            Log.d("test", msg + android.os.Process.myTid());

        }

        public void handleMessage(String msg) {
            Log.d("test", msg + " " + android.os.Process.myTid());
            //Log.d("test", msg);

        }
    }
}


