/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.archess;

import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Frame.TrackingState;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.PlaneHitResult;
import com.google.ar.core.Session;
import com.google.ar.core.examples.java.archess.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.archess.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.archess.rendering.ObjectRenderer.BlendMode;
import com.google.ar.core.examples.java.archess.rendering.PlaneAttachment;
import com.google.ar.core.examples.java.archess.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.archess.rendering.PointCloudRenderer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using
 * the ARCore API. The application will display any detected planes and will allow the user to
 * tap on a plane to place a 3d model of the Android robot.
 */
public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
    Intent networkHandler = null;

    NetworkHandler mService;
    boolean mBound = false;

    Button newGameBtn;
    EditText inputMove;


    // current game state
    String[][] chessBoard = new String[8][8];
    private int[][] test_grid;
    private boolean turn;

    private static final String TAG = MainActivity.class.getSimpleName();

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView mSurfaceView;

    private Config mDefaultConfig;
    private Session mSession;
    private BackgroundRenderer mBackgroundRenderer = new BackgroundRenderer();
    private GestureDetector mGestureDetector;
    private Snackbar mLoadingMessageSnackbar = null;

    private ObjectRenderer mVirtualObject = new ObjectRenderer();
    private PlaneRenderer mPlaneRenderer = new PlaneRenderer();
    private PointCloudRenderer mPointCloud = new PointCloudRenderer();

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] mAnchorMatrix = new float[16];

    // Tap handling and UI.
    private ArrayBlockingQueue<MotionEvent> mQueuedSingleTaps = new ArrayBlockingQueue<>(16);
    private ArrayList<PlaneAttachment> mTouches = new ArrayList<>();

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String board, message;
            String event = intent.getStringExtra("event");
            switch (event) {
                case "observer":
                    // TODO toast?
                    Log.d("test", "Joined game as observer.");
                    break;
                case "waiting":
                    Log.d("test", "Joined game. Waiting for other player for game to start.");
                    break;
                case "board":
                    board = intent.getStringExtra("board");
                    try {
                        JSONObject boardJSON = new JSONObject(board);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Log.d("test", board);
                    break;
                case "moveFailed":
                    message = intent.getStringExtra("message");
                    Log.d("test", message);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        test_grid = new int[8][8];
        for(int x=0;x<8;x++) {
            for (int y = 0; y < 8; y++) {
                test_grid[x][y] = 0;
                chessBoard[x][y] = ".";
            }
        }
        // START BOARD, HARDCODED
        chessBoard[1][0] = "WP";
        chessBoard[1][1] = "WP";
        chessBoard[1][2] = "WP";
        chessBoard[1][3] = "WP";
        chessBoard[1][4] = "WP";
        chessBoard[1][5] = "WP";
        chessBoard[1][6] = "WP";
        chessBoard[1][7] = "WP";

        chessBoard[0][0] = "WR";
        chessBoard[0][1] = "WN";
        chessBoard[0][2] = "WB";
        chessBoard[0][3] = "WQ";
        chessBoard[0][4] = "WK";
        chessBoard[0][5] = "WB";
        chessBoard[0][6] = "WN";
        chessBoard[0][7] = "WR";

        chessBoard[7][0] = "BR";
        chessBoard[7][1] = "BN";
        chessBoard[7][2] = "BB";
        chessBoard[7][3] = "BQ";
        chessBoard[7][4] = "BK";
        chessBoard[7][5] = "BB";
        chessBoard[7][6] = "BN";
        chessBoard[7][7] = "BR";


        chessBoard[6][0] = "BP";
        chessBoard[6][1] = "BP";
        chessBoard[6][2] = "BP";
        chessBoard[6][3] = "BP";
        chessBoard[6][4] = "BP";
        chessBoard[6][5] = "BP";
        chessBoard[6][6] = "BP";
        chessBoard[6][7] = "BP";

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter("Event"));
        createNetworkHandler();
        mSurfaceView = (GLSurfaceView) findViewById(R.id.surfaceview);

        mSession = new Session(/*context=*/this);

        // Create default config, check is supported, create session from that config.
        mDefaultConfig = Config.createDefaultConfig();
        if (!mSession.isSupported(mDefaultConfig)) {
            Toast.makeText(this, "This device does not support AR", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Set up tap listener.
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                onSingleTap(e);
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return mGestureDetector.onTouchEvent(event);
            }
        });

        // Set up renderer.
        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        mSurfaceView.setRenderer(this);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);


        inputMove = (EditText)findViewById(R.id.input_make_turn);

        // setup new game button
        newGameBtn = (Button)findViewById(R.id.btn_new_game);
        newGameBtn.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                newGame();
            }
         });


        inputMove.setOnEditorActionListener(new TextView.OnEditorActionListener(){
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event){
                if(actionId==inputMove.getImeActionId()){
                    return true;
                }
                return false;
            }
        });


    }

    public void newGame(){
        findViewById(R.id.input_make_turn).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_new_game).setVisibility(View.INVISIBLE);
        Log.d("new game","started");
    }

    public void makeMove(){
        String move = inputMove.getText().toString();
        mService.makeMove(move);
    }

    // updates the gameboard, 
    public void updateBoard(String[][] chessBoard){
        this.chessBoard = chessBoard;
    }

    /*
      (int type, int color) = convert__(x, y)
    */
    public int[] getChessPiece(int x, int y){
        int[] answer = new int[2];
        if(chessBoard[x][y] == "."){
            answer[0] = 0;
            answer[1] = 0;
        } else{
            switch(chessBoard[x][y].charAt(0)){
                case 'W':
                    answer[0] = 0;
                    break;
                case 'B':
                    answer[0] = 1;
                    break;
            }
            switch(chessBoard[x][y].charAt(1)){
                case 'P':
                    answer[1] = 1; // PAWN is 1
                    break;
                case 'B':
                    answer[1] = 2; // BISHOP is 2
                    break;
                case 'N':
                    answer[1] = 3; // KNIGHT is 3
                    break;
                case 'R':
                    answer[1] = 4; // ROOK is 4
                    break;
                case 'Q':
                    answer[1] = 5; // QUEEN is 5
                    break;
                case 'K':
                    answer[1] = 6; // KING is 6
                    break;
            }
        }
        return answer;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (CameraPermissionHelper.hasCameraPermission(this)) {
            showLoadingMessage();
            // Note that order matters - see the note in onPause(), the reverse applies here.
            mSession.resume(mDefaultConfig);
            mSurfaceView.onResume();
        } else {
            CameraPermissionHelper.requestCameraPermission(this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Note that the order matters - GLSurfaceView is paused first so that it does not try
        // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
        // still call mSession.update() and get a SessionPausedException.
        mSurfaceView.onPause();
        mSession.pause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this,
                "Camera permission is needed to run this application", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Standard Android full-screen functionality.
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void onSingleTap(MotionEvent e) {
        // Queue tap if there is space. Tap is lost if queue is full.
        //mService.makeMove("e2-e4");
        mQueuedSingleTaps.offer(e);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Create the texture and pass it to ARCore session to be filled during update().
        mBackgroundRenderer.createOnGlThread(/*context=*/this);
        mSession.setCameraTextureName(mBackgroundRenderer.getTextureId());

        // Prepare the other rendering objects.
        try {
            mVirtualObject.createOnGlThread(/*context=*/this, "andy.png");
            mVirtualObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);

        } catch (IOException e) {
            Log.e(TAG, "Failed to read obj file");
        }
        try {
            mPlaneRenderer.createOnGlThread(/*context=*/this, "trigrid.png");
        } catch (IOException e) {
            Log.e(TAG, "Failed to read plane texture");
        }
        mPointCloud.createOnGlThread(/*context=*/this);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        mSession.setDisplayGeometry(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        try {
            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = mSession.update();

            // Handle taps. Handling only one tap per frame, as taps are usually low frequency
            // compared to frame rate.
            MotionEvent tap = mQueuedSingleTaps.poll();
            if (tap != null && frame.getTrackingState() == TrackingState.TRACKING) {
                for (HitResult hit : frame.hitTest(tap)) {
                    // Check if any plane was hit, and if it was hit inside the plane polygon.
                    if (hit instanceof PlaneHitResult && ((PlaneHitResult) hit).isHitInPolygon()) {
                        // Cap the number of objects created. This avoids overloading both the
                        // rendering system and ARCore.
                        if (mTouches.size() >= 16) {
                            mSession.removeAnchors(Arrays.asList(mTouches.get(0).getAnchor()));
                            mTouches.remove(0);
                        }
                        // Adding an Anchor tells ARCore that it should track this position in
                        // space. This anchor will be used in PlaneAttachment to place the 3d model
                        // in the correct position relative both to the world and to the plane.
                        mTouches.add(new PlaneAttachment(
                            ((PlaneHitResult) hit).getPlane(),
                            mSession.addAnchor(hit.getHitPose())));

                        // Hits are sorted by depth. Consider only closest hit on a plane.
                        break;
                    }
                }
            }

            // Draw background.
            mBackgroundRenderer.draw(frame);

            // If not tracking, don't draw 3d objects.
            if (frame.getTrackingState() == TrackingState.NOT_TRACKING) {
                return;
            }

            // Get projection matrix.
            float[] projmtx = new float[16];
            mSession.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewmtx = new float[16];
            frame.getViewMatrix(viewmtx, 0);

            // Compute lighting from average intensity of the image.
            final float lightIntensity = frame.getLightEstimate().getPixelIntensity();

            // Visualize tracked points.
            mPointCloud.update(frame.getPointCloud());
            mPointCloud.draw(frame.getPointCloudPose(), viewmtx, projmtx);

            // Check if we detected at least one plane. If so, hide the loading message.
            if (mLoadingMessageSnackbar != null) {
                for (Plane plane : mSession.getAllPlanes()) {
                    if (plane.getType() == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING &&
                            plane.getTrackingState() == Plane.TrackingState.TRACKING) {
                        hideLoadingMessage();
                        break;
                    }
                }
            }

            // Visualize planes.
            mPlaneRenderer.drawPlanes(mSession.getAllPlanes(), frame.getPose(), projmtx);

            // Visualize anchors created by touch.
            float scaleFactor = 1.0f;
            for (PlaneAttachment planeAttachment : mTouches) {
                if (!planeAttachment.isTracking()) {
                    continue;
                }
                // Get the current combined pose of an Anchor and Plane in world space. The Anchor
                // and Plane poses are updated during calls to session.update() as ARCore refines
                // its estimate of the world.
                planeAttachment.getPose().toMatrix(mAnchorMatrix, 0);

                // Update and draw the model and its shadow
                for(int x=0;x<8;x++) {
                    for(int y=0;y<8;y++) {
                        int[] chessPiece = getChessPiece(x,y);
                        if(chessPiece[1]== 0)
                            continue;

                        float rotation = chessPiece[0] * 180;

                        mVirtualObject.updateModelMatrix(mAnchorMatrix, (x-3.5f) * 0.1f, 0, (y-3.5f) * 0.1f, rotation);
                        float red, green, blue;
                        if(chessPiece[0] == 0){
                            red = 1;
                            green = 0;
                            blue = 0;
                        }else{
                            red = 0;
                            green = 0;
                            blue = 1;
                        }
                        mVirtualObject.draw(viewmtx, projmtx, lightIntensity, chessPiece[1]-1, red, green, blue);// chess pieces mesh index start counting at 0, not 1
                    }
                }
            }

        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }


    private void showLoadingMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLoadingMessageSnackbar = Snackbar.make(
                    MainActivity.this.findViewById(android.R.id.content),
                    "Searching for surfaces...", Snackbar.LENGTH_INDEFINITE);
                mLoadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
                mLoadingMessageSnackbar.show();
            }
        });
    }

    private void hideLoadingMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLoadingMessageSnackbar.dismiss();
                mLoadingMessageSnackbar = null;
            }
        });
    }

    private void createNetworkHandler() {
        if (networkHandler == null) {
            networkHandler = new Intent(this, NetworkHandler.class);
        }
        bindService(networkHandler, mConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            NetworkHandler.LocalBinder binder = (NetworkHandler.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            Log.d("test", "Service Bound. Thread: " + android.os.Process.myTid());

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d("test", "Service Stopped. Thread: " + android.os.Process.myTid());
            mBound = false;
        }
    };

    public void startGame() {

    }
}