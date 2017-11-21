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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class NetworkHandler extends Service {
    InputStream socketInput;
    ByteArrayOutputStream byteArrayOutputStream;
    String SERVER_IP = "127.0.0.1";
    int SERVER_PORT = 8000;
    Socket socket;

    NetworkListener NL = null;
    NetworkCommunication NC = null;

    Thread t = null;
    Thread r = null;

    String response = "";


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("test", "NetworkHandler Started" + android.os.Process.myTid());

        NL = new NetworkListener();
        t = new Thread(NL);
        t.start();
        //new Thread(NL).start();

        NC = new NetworkCommunication();
        r = new Thread(NC);
        r.start();

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    class NetworkListener implements Runnable {
        @Override
        public void run() {
            Log.d("test", "Network Listener run" + android.os.Process.myTid());

            listener();

            /*
            try {
                Log.d("test", "Network Listener Run");


                socket = new Socket(SERVER_IP, SERVER_PORT);
                socketInput = socket.getInputStream();
                byteArrayOutputStream = new ByteArrayOutputStream(1024);
                listener();

            } catch (IOException e) {
                e.printStackTrace();
            }
*/
        }

        public void listener() {
            int bytesRead;
            byte[] buffer = new byte[1024];
            Log.d("test", "Network Listener listener" + android.os.Process.myTid());

            //r.run();

            //r.NC.handleMessage("Hej");
            /*try {
                while((bytesRead = socketInput.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                    response += byteArrayOutputStream.toString("UTF-8");
                }


                //listener();
            } catch (IOException e) {
                e.printStackTrace();
            }*/
        }
    }

    static class NetworkCommunication implements Runnable {

        @Override
        public void run() {
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


