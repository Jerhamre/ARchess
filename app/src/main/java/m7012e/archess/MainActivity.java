package m7012e.archess;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Network;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    Intent NetworkHandler = null;
/*
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        int receiveCase = intent.getIntExtra("message",1);
        switch (receiveCase) {
            case 1:
                //FAIL
            case 2:

        }
        }
    };
    */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("test", "Started");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("test", "Started" + android.os.Process.myTid());
        NetworkHandler = new Intent(this, NetworkHandler.class);
        startService(NetworkHandler);
    }
}
