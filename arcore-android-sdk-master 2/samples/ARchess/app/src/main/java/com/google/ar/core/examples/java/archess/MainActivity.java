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
import com.google.ar.core.examples.java.archess.rendering.ChessRenderer;
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
import android.opengl.GLU;
import android.opengl.Matrix;
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

import org.json.JSONArray;
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
    Button joinRoomBtn;
    TextView playerColour;
    TextView currentTurn;
    EditText inputUsername;
    EditText inputRoom;
    String inputMove2 = "";
    String[] y_pos  = {"a","b","c","d","e","f","g","h"};

    boolean hasPlacedBoard = false; // flag to keep track of state
    int[] choosenPiece; // int[2] (x, y) on chessboard

    // current game state
    String[][] chessBoard = new String[8][8];
    private int[][] test_grid;
    private boolean turn;
    private boolean firstMove = true;
    private boolean playerWhite;
    private int[] shortest;

    private static final String TAG = MainActivity.class.getSimpleName();

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView mSurfaceView;

    private Config mDefaultConfig;
    private Session mSession;
    private BackgroundRenderer mBackgroundRenderer = new BackgroundRenderer();
    private GestureDetector mGestureDetector;
    private Snackbar mLoadingMessageSnackbar = null;

    private ChessRenderer mChessRenderer = new ChessRenderer();
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
                    playerColour.setVisibility(View.VISIBLE);
                    if (intent.getStringExtra("player").equals("W")) {
                        playerColour.setText("Playing as white");
                        playerWhite = true;
                    } else {
                        playerColour.setText("Playing as black");
                        playerWhite = false;
                    }
                    currentTurn.setVisibility(View.VISIBLE);
                    currentTurn.setText("Waiting for opponent");

                    Log.d("test", "Joined game. Waiting for other player for game to start.");
                    break;
                case "startGame":
                    playerColour.setVisibility(View.VISIBLE);
                    if (intent.getStringExtra("player").equals("W")) {
                        playerColour.setText("Playing as white");
                        playerWhite = true;
                    } else {
                        playerColour.setText("Playing as black");
                        playerWhite = false;
                    }
                    currentTurn.setVisibility(View.VISIBLE);
                    currentTurn.setText("");
                    break;
                case "board":
                    board = intent.getStringExtra("board");
                    Log.d("test", board);
                    try {
                        JSONObject boardJSON = new JSONObject(board);


                        if (playerColour.getVisibility() == View.INVISIBLE) {
                            playerColour.setVisibility(View.VISIBLE);
                        }
                        if (currentTurn.getVisibility() == View.INVISIBLE) {
                            currentTurn.setVisibility(View.VISIBLE);
                        }
                        if (boardJSON.getString("player").equals("W")) {
                            currentTurn.setText("Whites move");
                        } else {
                            currentTurn.setText("Blacks move");
                        }

                        JSONArray column;
                        JSONArray jsonArrayBoard = boardJSON.getJSONArray("board");
                        for (int i = 0; i<8; i++) {
                            column = jsonArrayBoard.getJSONArray(i);
                            for (int j = 0; j<8; j++) {
                                chessBoard[i][j] = column.getString(j);
                            }
                            //Log.d("test", "New Board: " + tempBoard);
                        }
                        //chessBoard[0][0] = "";
                        //updateBoard(tempBoard);
                        //Log.d("test", chessBoard[0].toString());
                        if (boardJSON.getString("checkmate").equals("true")) {
                            Log.d("test", "Cheackmate");
                            currentTurn.setVisibility(View.INVISIBLE);
                            if (boardJSON.getString("player").equals("W")) {
                                if (playerWhite) {
                                    Log.d("test", "white white");
                                    playerColour.setText("You lost");
                                } else {
                                    Log.d("test", "white black");
                                    playerColour.setText("You won");
                                }

                            } else {
                                if (playerWhite) {
                                    Log.d("test", "black white");
                                    playerColour.setText("You won");
                                } else {
                                    Log.d("test", "white black");
                                    playerColour.setText("You lost");
                                }
                            }
                            mService.disconnect();
                            unbindService(mConnection);
                            findViewById(R.id.input_username).setVisibility(View.VISIBLE);
                            findViewById(R.id.input_room).setVisibility(View.VISIBLE);
                            findViewById(R.id.submit).setVisibility(View.VISIBLE);
                            //newGameBtn.setVisibility(View.VISIBLE);
                        }

                        if (boardJSON.getString("check").equals("true")) {
                            Log.d("test", "Check");
                            if (boardJSON.getString("player").equals("W")) {
                                Toast toast = Toast.makeText(getApplicationContext(), "Black is in check", Toast.LENGTH_SHORT);
                            } else {
                                Toast toast = Toast.makeText(getApplicationContext(), "White is in check", Toast.LENGTH_SHORT);

                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Log.d("test", board);



                    break;
                case "moveFailed":
                    message = intent.getStringExtra("message");
                    Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
                    toast.show();
            }
        }
    };

    // Helper function
    private static float distance_to_ray(float[] point, float[] lineStart, float[] lineEnd){
        float[] PointThing = new float[3];
        float[] TotalThing = new float[3];
        PointThing[0] = lineStart[0] - point[0];
        PointThing[1] = lineStart[1] - point[1];
        PointThing[2] = lineStart[2] - point[2];

        TotalThing[0] = (PointThing[1]*lineEnd[2] - PointThing[2]*lineEnd[1]);
        TotalThing[1] = -(PointThing[0]*lineEnd[2] - PointThing[2]*lineEnd[0]);
        TotalThing[2] = (PointThing[0]*lineEnd[1] - PointThing[1]*lineEnd[0]);

        float distance = (float) (Math.sqrt(TotalThing[0]*TotalThing[0] + TotalThing[1]*TotalThing[1] + TotalThing[2]*TotalThing[2]) /
                Math.sqrt(lineEnd[0] * lineEnd[0] + lineEnd[1] * lineEnd[1] + lineEnd[2] * lineEnd[2] ));


        return distance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        test_grid = new int[8][8];
        for(int x=0;x<8;x++) {
            for (int y = 0; y < 8; y++) {
                test_grid[x][y] = 0;
                chessBoard[x][y] = ".";
            }
        }

        hasPlacedBoard = false;
        choosenPiece = new int[]{-1, -1};

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
        //createNetworkHandler();
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


        inputUsername = (EditText)findViewById(R.id.input_username);
        inputRoom = (EditText)findViewById(R.id.input_room);

        // Used to set room and username
        /*newGameBtn = (Button)findViewById(R.id.btn_new_game);
        newGameBtn.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                newGame();
            }
         });
*/
        joinRoomBtn = (Button)findViewById(R.id.submit);
        joinRoomBtn.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                startGame(inputUsername.getText().toString(), inputRoom.getText().toString());
                findViewById(R.id.input_username).setVisibility(View.INVISIBLE);
                findViewById(R.id.input_room).setVisibility(View.INVISIBLE);
                findViewById(R.id.submit).setVisibility(View.INVISIBLE);
                //newGameBtn.setVisibility(View.INVISIBLE);
            }
        });
        playerColour = (TextView)findViewById(R.id.txt_player_colour);
        playerColour.setVisibility(View.INVISIBLE);
        currentTurn = (TextView)findViewById(R.id.txt_current_turn);
        currentTurn.setVisibility(View.INVISIBLE);
        /* Legacy code
        inputMove.setOnEditorActionListener(new TextView.OnEditorActionListener(){
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event){
                Log.d("test", String.valueOf(actionId));

                if(actionId==KeyEvent.KEYCODE_ENDCALL){
                    Log.d("test", "Event Move");
                    makeMove();
                    return true;
                }
                Log.d("test", "Edit return False");
                return false;
            }
        });
        */

    }

    public void setMove(int xPos, int yPos){
        int y = xPos;
        int x = yPos;
        Log.d("test", String.valueOf(firstMove));
        Log.d("test", "inputMove" + inputMove2);
        if (firstMove) {
            String selectedPiece = chessBoard[y][x];
            if(selectedPiece != "."){
                if (chessBoard[y][x].charAt(1) != 'P') {
                    inputMove2 += String.valueOf(chessBoard[y][x].charAt(1));
                }
            }

            inputMove2 += y_pos[y];
            inputMove2 += Integer.toString(x+1);
            Log.d("inputMove2", "move" + inputMove2);
            firstMove = false;
        } else {
            inputMove2 += "-";
            inputMove2 += y_pos[y];
            inputMove2 += Integer.toString(x+1);
            Log.d("inputMove2", "move" + inputMove2);
            makeMove();
            inputMove2 = "";
            shortest[0] = -1;
            shortest[1] = -1;
            firstMove = true;
        }
    }

    public void newGame(){
        findViewById(R.id.input_username).setVisibility(View.VISIBLE);
        findViewById(R.id.input_room).setVisibility(View.VISIBLE);
        findViewById(R.id.submit).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_new_game).setVisibility(View.INVISIBLE);
        Log.d("new game","started");
    }

    public void makeMove(){
        String move = inputMove2;
        Log.d("test", "Move: " + move);
        mService.makeMove(move);
    }

    // updates the gameboard, 
    public void updateBoard(String[][] chessBoard){
        Log.d("test", chessBoard.toString());
        this.chessBoard = chessBoard;
    }

    /*
      (int type, int color) = convert__(x, y)
    */
    public int[] getChessPiece(int x, int y){
        int[] answer = new int[2];
        if(chessBoard[x][y].equals(".")){
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
        mQueuedSingleTaps.offer(e);
        Log.d("onTap", choosenPiece.toString());
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Create the texture and pass it to ARCore session to be filled during update().
        mBackgroundRenderer.createOnGlThread(/*context=*/this);
        mSession.setCameraTextureName(mBackgroundRenderer.getTextureId());

        // Prepare the other rendering objects.
        try {
            mChessRenderer.createOnGlThread(/*context=*/this, "default_texture.png");
            mChessRenderer.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);

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

            if(hasPlacedBoard == false)
            {
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

                            hasPlacedBoard = true;

                            // Hits are sorted by depth. Consider only closest hit on a plane.
                            break;
                        }
                    }
                }
            }else{
                MotionEvent tap = mQueuedSingleTaps.poll();
                if(tap != null )
                {
                    // Get projection matrix.
                    float[] projmtx = new float[16];
                    mSession.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

                    // Get camera matrix and draw.
                    float[] viewmtx = new float[16];
                    frame.getViewMatrix(viewmtx, 0);

                    int[] viewport = new int[4];
                    float[] modelview = new float[16];
                    float[] projection = new float[16];
                    float winx, winy, winz;
                    float[] newcoords = new float[4]; // x, y, z, w

                    GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport,0);

                    Matrix.multiplyMM(modelview,0,
                            viewmtx,0,
                            mAnchorMatrix,0);

                    projection = projmtx;
                    int setx = (int)tap.getX();
                    int sety = (int)tap.getY();

                    winx = (float)setx;
                    winy = (float)viewport[3] - sety;
                    winz = 0;

                    GLU.gluUnProject(winx, winy, winz, modelview, 0, projection, 0,   viewport, 0, newcoords, 0);

                    float [] start = new float[3];
                    start[0] = newcoords[0] / newcoords[3];
                    start[1] = newcoords[1] / newcoords[3];
                    start[2] = newcoords[2] / newcoords[3];

                    winz = 1;
                    GLU.gluUnProject(winx, winy, winz, modelview, 0, projection, 0,   viewport, 0, newcoords, 0);

                    float [] end = new float[3];
                    end[0] = newcoords[0] / newcoords[3];
                    end[1] = newcoords[1] / newcoords[3];
                    end[2] = newcoords[2] / newcoords[3];

                    shortest = new int[2]; // chessboard coordinates

                    float distance = Float.MAX_VALUE;

                    for (int x = -1; x < 9; x++) {
                        for (int y = -1; y < 9; y++) {

                            float[] temp_point = mChessRenderer.get_piece_3d_offset(x,y);
                            float temp_distance = distance_to_ray(temp_point, start, end);
                            if(temp_distance <= distance){
                                distance = temp_distance;
                                shortest[0] = x;
                                shortest[1] = y;
                            }

                        }
                    }

                    if(playerWhite){
                        shortest[0] = 7-shortest[0];
                        shortest[1] = 7-shortest[1];
                    }


                    choosenPiece = shortest;
                    Log.d("test",chessBoard[choosenPiece[0]][choosenPiece[1]]);
                    Log.d("test", "running setMove");
                    setMove(choosenPiece[0],choosenPiece[1]);
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
            if(hasPlacedBoard == false)
            {
                mPointCloud.draw(frame.getPointCloudPose(), viewmtx, projmtx);
            }


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
            if(hasPlacedBoard == false)
            {
                mPlaneRenderer.drawPlanes(mSession.getAllPlanes(), frame.getPose(), projmtx);
            }

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
                        float[] piece_world_offset = mChessRenderer.get_piece_3d_offset(x, y);

                        int color_id = (chessPiece[0] == 0) ? 0 : 1;
                        if(choosenPiece[0] == x && choosenPiece[1] == y)
                            color_id = 2;
                        if (playerWhite) {
                            mChessRenderer.updateModelMatrix(mAnchorMatrix, piece_world_offset, rotation, 180);
                        } else {
                            mChessRenderer.updateModelMatrix(mAnchorMatrix, piece_world_offset, rotation,0);
                        }
                        mChessRenderer.draw_piece(viewmtx, projmtx, lightIntensity, chessPiece[1]-1, color_id);// chess pieces mesh index start counting at 0, not 1
                    }
                }
                if (playerWhite) {
                    mChessRenderer.updateModelMatrix(mAnchorMatrix,new float[]{0,0,0},90, 180);
                } else {
                    mChessRenderer.updateModelMatrix(mAnchorMatrix,new float[]{0,0,0},90, 0);
                }

                mChessRenderer.draw_chessboard(viewmtx, projmtx, lightIntensity);
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
            networkHandler.putExtra("username", "bosse");
            networkHandler.putExtra("room", "testRoom");
        }
        bindService(networkHandler, mConnection, Context.BIND_AUTO_CREATE);

    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            NetworkHandler.LocalBinder binder = (NetworkHandler.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            Log.d("test", "Service Bound. Thread: " + android.os.Process.myTid());

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d("test", "Service Stopped. Thread: " + android.os.Process.myTid());
            networkHandler = null;
            mBound = false;
        }
    };

    private void startGame(String username, String room) {
        if (networkHandler == null) {
            networkHandler = new Intent(this, NetworkHandler.class);
            networkHandler.putExtra("username", username);
            networkHandler.putExtra("room", room);
        }
        bindService(networkHandler, mConnection, Context.BIND_AUTO_CREATE);
    }
}
