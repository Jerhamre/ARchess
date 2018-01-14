
/*
 * ARChess Chess Renderer
 *
 *
 */



/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.core.examples.java.archess.rendering;

import com.google.ar.core.examples.java.archess.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import de.javagl.Obj;
import de.javagl.ObjData;
import de.javagl.ObjReader;
import de.javagl.ObjUtils;

/**
 * ChessRenderer is ARChess' standard object renderer for rendering the virtual chess objects.
 *
 * Chess pieces are rendered externally and individually, ChessRenderer holds no chess state.
 * The objects are loaded in as .obj files from the assets folder.
 */
public class ChessRenderer {
    private static final String TAG = ChessRenderer.class.getSimpleName();

    /**
     * Blend mode.
     *
     * @see #setBlendMode(BlendMode)
     */
    public enum BlendMode {
        /** Multiplies the destination color by the source alpha. */
        Shadow,
        /** Normal alpha blending. */
        Grid
    };

    private static final int COORDS_PER_VERTEX = 3;

    // Note: the last component must be zero to avoid applying the translational part of the matrix.
    private static final float[] LIGHT_DIRECTION = new float[] { 0.0f, 1.0f, 0.0f, 0.0f };
    private float[] mViewLightDirection = new float[4];

    /**
     * All the meshes (objects) that can be rendered.
     *
     * meshChessboard is separated into 3 pieces in order to separate the white squares,
     * black squares and the bottom from each other to easily colorize them.
     *
     * meshChessPieces contain all the (6) separate chess pieces.
     */
    private StaticMesh meshChessboard[];    // 3 pieces for the whole chessboard
    private StaticMesh meshChessPieces[];


    /**
     * These are rgb colors that can be altered to change the chessboards colors.
     */
    private float[] color_white;
    private float[] color_black;
    private float[] color_marked;
    private float[] color_error;

    /**
     * The shader program
     */
    private int mProgram;

    /**
     * The renderer uses a blank default texture for all meshes. Extend this to the static meshes
     * or to a separate texture list if desired.
     */
    private int[] mTextures = new int[1];

    // Shader location: model view projection matrix.
    private int mModelViewUniform;
    private int mModelViewProjectionUniform;
    private int mColorUniform;

    // Shader location: object attributes.
    private int mPositionAttribute;
    private int mNormalAttribute;
    private int mTexCoordAttribute;

    // Shader location: texture sampler.
    private int mTextureUniform;

    // Shader location: environment properties.
    private int mLightingParametersUniform;

    // Shader location: material properties.
    private int mMaterialParametersUniform;

    private BlendMode mBlendMode = null;

    // Temporary matrices allocated here to reduce number of allocations for each frame.
    private float[] mModelMatrix = new float[16];
    private float[] mModelViewMatrix = new float[16];
    private float[] mModelViewProjectionMatrix = new float[16];

    // Set some default material properties to use for lighting.
    private float mAmbient = 0.3f;
    private float mDiffuse = 1.0f;
    private float mSpecular = 1.0f;
    private float mSpecularPower = 6.0f;

    public ChessRenderer() {
    }

    /**
     *
     * @param context Context for asset loading
     * @param asset_name Name of asset in assets folder
     * @return StaticMesh handle for asset usable by draw(#) function.
     * @throws IOException
     */
    private StaticMesh load_model(Context context, String asset_name) throws IOException {

        StaticMesh temp;

        // Read the obj file.
        InputStream objInputStream = context.getAssets().open(asset_name);
        Obj obj = ObjReader.read(objInputStream);

        // Prepare the Obj so that its structure is suitable for
        // rendering with OpenGL:
        // 1. Triangulate it
        // 2. Make sure that texture coordinates are not ambiguous
        // 3. Make sure that normals are not ambiguous
        // 4. Convert it to single-indexed data
        obj = ObjUtils.convertToRenderable(obj);

        // OpenGL does not use Java arrays. ByteBuffers are used instead to provide data in a format
        // that OpenGL understands.

        // Obtain the data from the OBJ, as direct buffers:
        IntBuffer wideIndices = ObjData.getFaceVertexIndices(obj, 3);
        FloatBuffer vertices = ObjData.getVertices(obj);
        FloatBuffer texCoords = ObjData.getTexCoords(obj, 2);
        FloatBuffer normals = ObjData.getNormals(obj);

        // Convert int indices to shorts for GL ES 2.0 compatibility
        ShortBuffer indices = ByteBuffer.allocateDirect(2 * wideIndices.limit())
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        while (wideIndices.hasRemaining()) {
            indices.put((short) wideIndices.get());
        }
        indices.rewind();

        // ----

        temp = new StaticMesh();

        int[] buffers = new int[2];
        GLES20.glGenBuffers(2, buffers, 0);
        temp.mVertexBufferId = buffers[0];
        temp.mIndexBufferId = buffers[1];

        // Load vertex buffer
        temp.mVerticesBaseAddress = 0;
        temp.mTexCoordsBaseAddress = temp.mVerticesBaseAddress + 4 * vertices.limit();
        temp.mNormalsBaseAddress = temp.mTexCoordsBaseAddress + 4 * texCoords.limit();
        final int totalBytes = temp.mNormalsBaseAddress + 4 * normals.limit();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, temp.mVertexBufferId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, totalBytes, null, GLES20.GL_STATIC_DRAW);
        GLES20.glBufferSubData(
                GLES20.GL_ARRAY_BUFFER, temp.mVerticesBaseAddress, 4 * vertices.limit(), vertices);
        GLES20.glBufferSubData(
                GLES20.GL_ARRAY_BUFFER, temp.mTexCoordsBaseAddress, 4 * texCoords.limit(), texCoords);
        GLES20.glBufferSubData(
                GLES20.GL_ARRAY_BUFFER, temp.mNormalsBaseAddress, 4 * normals.limit(), normals);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // Load index buffer
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, temp.mIndexBufferId);
        temp.mIndexCount = indices.limit();
        GLES20.glBufferData(
                GLES20.GL_ELEMENT_ARRAY_BUFFER, 2 * temp.mIndexCount, indices, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "OBJ buffer load");

        final int vertexShader = ShaderUtil.loadGLShader(TAG, context,
                GLES20.GL_VERTEX_SHADER, R.raw.object_vertex);
        final int fragmentShader = ShaderUtil.loadGLShader(TAG, context,
                GLES20.GL_FRAGMENT_SHADER, R.raw.object_fragment);

        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragmentShader);
        GLES20.glLinkProgram(mProgram);
        GLES20.glUseProgram(mProgram);

        ShaderUtil.checkGLError(TAG, "Program creation");

        return temp;
    }

    /**
     * Creates and initializes OpenGL resources needed for rendering the model.
     *
     * @param context Context for loading the shader and below-named model and texture assets.
     * @param diffuseTextureAssetName  Name of the PNG file containing the diffuse texture map.
     */
    public void createOnGlThread(Context context,
                                 String diffuseTextureAssetName) throws IOException {
        // Read the texture.
        Bitmap textureBitmap = BitmapFactory.decodeStream(
                context.getAssets().open(diffuseTextureAssetName));

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glGenTextures(mTextures.length, mTextures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures[0]);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        textureBitmap.recycle();

        ShaderUtil.checkGLError(TAG, "Texture loading");

        // Load static meshes.
        meshChessboard = new StaticMesh[3];

        meshChessboard[0] = load_model(context, "chessboard_part_white.obj");
        meshChessboard[1] = load_model(context, "chessboard_part_black.obj");
        meshChessboard[2] = load_model(context, "chessboard_part_hull.obj");

        meshChessPieces = new StaticMesh[6];

        meshChessPieces[0] = load_model(context, "pawn.obj");
        meshChessPieces[1] = load_model(context, "bishop.obj");
        meshChessPieces[2] = load_model(context, "knight.obj");
        meshChessPieces[3] = load_model(context, "rook.obj");
        meshChessPieces[4] = load_model(context, "queen.obj");
        meshChessPieces[5] = load_model(context, "king.obj");


        // Set chessboard colors.
        color_white = new float[] {0.9f, 0.7f, 0.4f};
        color_black = new float[] {0.9f, 0.4f, 0.4f};
        color_marked = new float[] {1, 1, 0};
        color_error = new float[] {1, 0, 1};

        // Set up shader uniforms and attributes.
        mColorUniform = GLES20.glGetUniformLocation(mProgram, "color");

        mModelViewUniform = GLES20.glGetUniformLocation(mProgram, "u_ModelView");
        mModelViewProjectionUniform =
                GLES20.glGetUniformLocation(mProgram, "u_ModelViewProjection");

        mPositionAttribute = GLES20.glGetAttribLocation(mProgram, "a_Position");
        mNormalAttribute = GLES20.glGetAttribLocation(mProgram, "a_Normal");
        mTexCoordAttribute = GLES20.glGetAttribLocation(mProgram, "a_TexCoord");

        mTextureUniform = GLES20.glGetUniformLocation(mProgram, "u_Texture");

        mLightingParametersUniform = GLES20.glGetUniformLocation(mProgram, "u_LightingParameters");
        mMaterialParametersUniform = GLES20.glGetUniformLocation(mProgram, "u_MaterialParameters");

        ShaderUtil.checkGLError(TAG, "Program parameters");

        Matrix.setIdentityM(mModelMatrix, 0);
    }

    /**
     * Selects the blending mode for rendering.
     *
     * @param blendMode The blending mode.  Null indicates no blending (opaque rendering).
     */
    public void setBlendMode(BlendMode blendMode) {
        mBlendMode = blendMode;
    }

    /**
     * Updates the object model matrix and applies scaling.
     *
     * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
     * @param translation translation offset
     * @param rotation rotation after translation (rename)
     * @param rotation2 rotation before translation (rename)
     * @see android.opengl.Matrix
     */
    public void updateModelMatrix(float[] modelMatrix, float[] translation, float rotation, float rotation2) {


        float[] translationMatrix = new float[16];
        Matrix.setIdentityM(translationMatrix, 0);


        Matrix.rotateM(translationMatrix, 0, rotation2, 0, 1, 0);
        Matrix.translateM(translationMatrix, 0, translation[0], translation[1], translation[2]);
        Matrix.rotateM(translationMatrix, 0, rotation, 0, 1, 0);


        Matrix.multiplyMM(mModelMatrix, 0, modelMatrix, 0, translationMatrix, 0);
    }

    /**
     * Sets the surface characteristics of the rendered model.
     *
     * @param ambient  Intensity of non-directional surface illumination.
     * @param diffuse  Diffuse (matte) surface reflectivity.
     * @param specular  Specular (shiny) surface reflectivity.
     * @param specularPower  Surface shininess.  Larger values result in a smaller, sharper
     *     specular highlight.
     */
    public void setMaterialProperties(
            float ambient, float diffuse, float specular, float specularPower) {
        mAmbient = ambient;
        mDiffuse = diffuse;
        mSpecular = specular;
        mSpecularPower = specularPower;
    }

    /**
     *
     * @param mesh mesh data to render
     * @param cameraView camera view matrix
     * @param cameraPerspective camera perspective matrix
     * @param lightIntensity light intensity
     * @param red color red intensity
     * @param green color green intensity
     * @param blue color blue intensity
     */
    private void draw(StaticMesh mesh, float[] cameraView, float[] cameraPerspective, float lightIntensity, float red, float green, float blue)
    {

        StaticMesh current_mesh = mesh;

        ShaderUtil.checkGLError(TAG, "Before draw");

        // Build the ModelView and ModelViewProjection matrices
        // for calculating object position and light.
        Matrix.multiplyMM(mModelViewMatrix, 0, cameraView, 0, mModelMatrix, 0);
        Matrix.multiplyMM(mModelViewProjectionMatrix, 0, cameraPerspective, 0, mModelViewMatrix, 0);

        GLES20.glUseProgram(mProgram);

        // Set the lighting environment properties.
        Matrix.multiplyMV(mViewLightDirection, 0, mModelViewMatrix, 0, LIGHT_DIRECTION, 0);
        normalizeVec3(mViewLightDirection);
        GLES20.glUniform4f(mLightingParametersUniform,
                mViewLightDirection[0], mViewLightDirection[1], mViewLightDirection[2], lightIntensity);

        // Set the object material properties.
        GLES20.glUniform4f(mMaterialParametersUniform, mAmbient, mDiffuse, mSpecular,
                mSpecularPower);

        // Attach the object texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures[0]);
        GLES20.glUniform1i(mTextureUniform, 0);

        // Set the vertex attributes.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, current_mesh.mVertexBufferId);

        GLES20.glVertexAttribPointer(
                mPositionAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, current_mesh.mVerticesBaseAddress);
        GLES20.glVertexAttribPointer(
                mNormalAttribute, 3, GLES20.GL_FLOAT, false, 0, current_mesh.mNormalsBaseAddress);
        GLES20.glVertexAttribPointer(
                mTexCoordAttribute, 2, GLES20.GL_FLOAT, false, 0, current_mesh.mTexCoordsBaseAddress);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(
                mModelViewUniform, 1, false, mModelViewMatrix, 0);
        GLES20.glUniformMatrix4fv(
                mModelViewProjectionUniform, 1, false, mModelViewProjectionMatrix, 0);

        GLES20.glUniform3f(mColorUniform, red, green, blue);

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(mPositionAttribute);
        GLES20.glEnableVertexAttribArray(mNormalAttribute);
        GLES20.glEnableVertexAttribArray(mTexCoordAttribute);

        if (mBlendMode != null) {
            GLES20.glDepthMask(false);
            GLES20.glEnable(GLES20.GL_BLEND);
            switch (mBlendMode) {
                case Shadow:
                    // Multiplicative blending function for Shadow.
                    GLES20.glBlendFunc(GLES20.GL_ZERO, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                    break;
                case Grid:
                    // Grid, additive blending function.
                    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                    break;
            }
        }

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, current_mesh.mIndexBufferId);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, current_mesh.mIndexCount, GLES20.GL_UNSIGNED_SHORT, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        if (mBlendMode != null) {
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glDepthMask(true);
        }

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(mPositionAttribute);
        GLES20.glDisableVertexAttribArray(mNormalAttribute);
        GLES20.glDisableVertexAttribArray(mTexCoordAttribute);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        ShaderUtil.checkGLError(TAG, "After draw");
    }


    /**
     * render the base chess board.
     *
     * @param cameraView
     * @param cameraPerspective
     * @param lightIntensity
     */
    public void draw_chessboard(float[] cameraView, float[] cameraPerspective, float lightIntensity)
    {
        float[] color;
        color = color_white;
        draw(meshChessboard[0], cameraView, cameraPerspective, lightIntensity, color[0], color[1], color[2]);

        color = color_black;
        draw(meshChessboard[1], cameraView, cameraPerspective, lightIntensity, color[0], color[1], color[2]);

        color = color_white;
        draw(meshChessboard[2], cameraView, cameraPerspective, lightIntensity, color[0], color[1], color[2]);
    }

    /**
     * render chess piece
     *
     * @param cameraView
     * @param cameraPerspective
     * @param lightIntensity
     * @param chess_piece_type matching index from meshChessPieces[] todo: enums
     * @param color_id range is [0, 2] todo: enums
     */
    public void draw_piece(float[] cameraView, float[] cameraPerspective, float lightIntensity, int chess_piece_type, int color_id) {

        if(chess_piece_type < 0 || chess_piece_type >= 6)
            return; // bad input
        float[] color;
        switch(color_id)
        {
            case 0:
                color = color_white;
                break;
            case 1:
                color = color_black;
                break;
            case 2:
                color = color_marked;
                break;
            default:
                color = color_error;
                break;
        }

        draw(meshChessPieces[chess_piece_type],cameraView, cameraPerspective, lightIntensity, color[0], color[1], color[2]);

    }


    /**
     *
     * @param x chess piece x coordinate on the board
     * @param y chess piece y coordinate on the board
     * @return float[3] (x, y, z) offset to chessboard origin in world coordinates
     */
    public float[] get_piece_3d_offset(int x, int y)
    {
        float[] location = {
                (x-3.5f) * 0.08f,
                0,
                (y-3.5f) * 0.08f,
        };
        return location;
    }

    public static void normalizeVec3(float[] v) {
        float reciprocalLength = 1.0f / (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        v[0] *= reciprocalLength;
        v[1] *= reciprocalLength;
        v[2] *= reciprocalLength;
    }
}
