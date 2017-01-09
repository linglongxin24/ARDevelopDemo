/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.samples.VuforiaSamples.app.TextRecognition;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.vuforia.Device;
import com.vuforia.Matrix44F;
import com.vuforia.Obb2D;
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.TrackableResult;
import com.vuforia.Vec2F;
import com.vuforia.Vuforia;
import com.vuforia.Word;
import com.vuforia.WordResult;
import com.vuforia.samples.SampleApplication.SampleAppRenderer;
import com.vuforia.samples.SampleApplication.SampleAppRendererControl;
import com.vuforia.samples.SampleApplication.SampleApplicationSession;
import com.vuforia.samples.SampleApplication.utils.LineShaders;
import com.vuforia.samples.SampleApplication.utils.SampleUtils;


// The renderer class for the ImageTargets sample. 
public class TextRecoRenderer implements GLSurfaceView.Renderer, SampleAppRendererControl
{
    private static final String LOGTAG = "TextRecoRenderer";
    
    private SampleApplicationSession vuforiaAppSession;
    private SampleAppRenderer mSampleAppRenderer;

    private static final int MAX_NB_WORDS = 132;
    private static final float TEXTBOX_PADDING = 0.0f;
    
    private static final float ROIVertices[] = { -0.5f, -0.5f, 0.0f, 0.5f,
            -0.5f, 0.0f, 0.5f, 0.5f, 0.0f, -0.5f, 0.5f, 0.0f };
    
    private static final int NUM_QUAD_OBJECT_INDICES = 8;
    private static final short ROIIndices[] = { 0, 1, 1, 2, 2, 3, 3, 0 };
    
    private static final float quadVertices[] = { -0.5f, -0.5f, 0.0f, 0.5f,
            -0.5f, 0.0f, 0.5f, 0.5f, 0.0f, -0.5f, 0.5f, 0.0f, };
    
    private static final short quadIndices[] = { 0, 1, 1, 2, 2, 3, 3, 0 };
    
    private ByteBuffer mROIVerts = null;
    private ByteBuffer mROIIndices = null;

    private boolean mIsActive = false;
    
    // Reference to main activity *
    public TextReco mActivity;
    
    private int shaderProgramID;
    private int vertexHandle;
    private int mvpMatrixHandle;
    private Renderer mRenderer;
    private int lineOpacityHandle;
    private int lineColorHandle;
    
    private List<WordDesc> mWords = new ArrayList<>();
    public float ROICenterX;
    public float ROICenterY;
    public float ROIWidth;
    public float ROIHeight;
    private int viewportPosition_x;
    private int viewportPosition_y;
    private int viewportSize_x;
    private int viewportSize_y;
    private ByteBuffer mQuadVerts;
    private ByteBuffer mQuadIndices;
    
    
    public TextRecoRenderer(TextReco activity, SampleApplicationSession session)
    {
        mActivity = activity;
        vuforiaAppSession = session;

        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
        mSampleAppRenderer = new SampleAppRenderer(this, mActivity, Device.MODE.MODE_AR, false, 10f, 5000f);
    }


    // Called when the surface is created or recreated.
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");

        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        vuforiaAppSession.onSurfaceCreated();

        mSampleAppRenderer.onSurfaceCreated();
    }


    // Called to draw the current frame.
    @Override
    public void onDrawFrame(GL10 gl)
    {
        List<WordDesc> words= null;
        if (!mIsActive)
        {
            mWords.clear();
            mActivity.updateWordListUI(mWords);
            return;
        }

        // Call our function to render content from SampleAppRenderer class
        mSampleAppRenderer.render();

        synchronized (mWords)
        {
            words = new ArrayList<>(mWords);
        }

        Collections.sort(words);

        // update UI - we copy the list to avoid concurrent modifications
        mActivity.updateWordListUI(new ArrayList<>(words));
    }


    // Called when the surface changed size.
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        Log.d(LOGTAG, "onSurfaceChanged");

        mActivity.configureVideoBackgroundROI();

        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height);

        // RenderingPrimitives to be updated when some rendering change is done
        mSampleAppRenderer.onConfigurationChanged(mIsActive);

        // Call function to initialize rendering:
        initRendering();
    }


    public void setActive(boolean active)
    {
        mIsActive = active;

        if(mIsActive)
            mSampleAppRenderer.configureVideoBackground();
    }


    public void updateConfiguration()
    {
        mSampleAppRenderer.onConfigurationChanged(mIsActive);
    }


    boolean modelLoaded = false;
    // Function for initializing the renderer.
    private void initRendering()
    {
        if(!modelLoaded) {
            // init the vert/inde buffers
            mROIVerts = ByteBuffer.allocateDirect(4 * ROIVertices.length);
            mROIVerts.order(ByteOrder.LITTLE_ENDIAN);
            updateROIVertByteBuffer();

            mROIIndices = ByteBuffer.allocateDirect(2 * ROIIndices.length);
            mROIIndices.order(ByteOrder.LITTLE_ENDIAN);
            for (short s : ROIIndices)
                mROIIndices.putShort(s);
            mROIIndices.rewind();

            mQuadVerts = ByteBuffer.allocateDirect(4 * quadVertices.length);
            mQuadVerts.order(ByteOrder.LITTLE_ENDIAN);
            for (float f : quadVertices)
                mQuadVerts.putFloat(f);
            mQuadVerts.rewind();

            mQuadIndices = ByteBuffer.allocateDirect(2 * quadIndices.length);
            mQuadIndices.order(ByteOrder.LITTLE_ENDIAN);
            for (short s : quadIndices)
                mQuadIndices.putShort(s);
            mQuadIndices.rewind();

            mRenderer = Renderer.getInstance();
            modelLoaded = true;
        }

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
            : 1.0f);
        
        shaderProgramID = SampleUtils.createProgramFromShaderSrc(
            LineShaders.LINE_VERTEX_SHADER, LineShaders.LINE_FRAGMENT_SHADER);
        
        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
            "vertexPosition");
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
            "modelViewProjectionMatrix");
        
        lineOpacityHandle = GLES20.glGetUniformLocation(shaderProgramID,
            "opacity");
        lineColorHandle = GLES20.glGetUniformLocation(shaderProgramID, "color");
        
    }
    
    
    private void updateROIVertByteBuffer()
    {
        mROIVerts.rewind();
        for (float f : ROIVertices)
            mROIVerts.putFloat(f);
        mROIVerts.rewind();
    }


    // The render function called from SampleAppRendering by using RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling it's lifecycle.
    // State should not be cached outside this method.
    public void renderFrame(State state, float[] projectionMatrix)
    {
        // Renders video background replacing Renderer.DrawVideoBackground()
        mSampleAppRenderer.renderVideoBackground();
        
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        // enable blending to support transparency
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,
            GLES20.GL_ONE_MINUS_CONSTANT_ALPHA);
        
        // clear words list
        mWords.clear();
        
        // did we find any trackables this frame?
        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++)
        {
            // get the trackable
            TrackableResult result = state.getTrackableResult(tIdx);
            
            Vec2F wordBoxSize = null;
            
            if (result.isOfType(WordResult.getClassType()))
            {
                WordResult wordResult = (WordResult) result;
                Word word = (Word) wordResult.getTrackable();
                Obb2D obb = wordResult.getObb();
                wordBoxSize = word.getSize();
                
                String wordU = word.getStringU();
                if (wordU != null)
                {
                    // in portrait, the obb coordinate is based on
                    // a 0,0 position being in the upper right corner
                    // with :
                    // X growing from top to bottom and
                    // Y growing from right to left
                    //
                    // we convert those coordinates to be more natural
                    // with our application:
                    // - 0,0 is the upper left corner
                    // - X grows from left to right
                    // - Y grows from top to bottom
                    float wordx = -obb.getCenter().getData()[1];
                    float wordy = obb.getCenter().getData()[0];
                    
                    if (mWords.size() < MAX_NB_WORDS)
                    {
                        mWords.add(new WordDesc(wordU,
                            (int) (wordx - wordBoxSize.getData()[0] / 2),
                            (int) (wordy - wordBoxSize.getData()[1] / 2),
                            (int) (wordx + wordBoxSize.getData()[0] / 2),
                            (int) (wordy + wordBoxSize.getData()[1] / 2)));
                    }
                    
                }
            } else
            {
                Log.d(LOGTAG, "Unexpected Detection : " + result.getType());
                continue;
            }
            
            Matrix44F mvMat44f = Tool.convertPose2GLMatrix(result.getPose());
            float[] mvMat = mvMat44f.getData();
            float[] mvpMat = new float[16];
            Matrix.translateM(mvMat, 0, 0, 0, 0);
            Matrix.scaleM(mvMat, 0, wordBoxSize.getData()[0] - TEXTBOX_PADDING,
                wordBoxSize.getData()[1] - TEXTBOX_PADDING, 1.0f);
            Matrix.multiplyMM(mvpMat, 0, projectionMatrix, 0, mvMat, 0);
            
            GLES20.glUseProgram(shaderProgramID);
            GLES20.glLineWidth(3.0f);
            GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                false, 0, mQuadVerts);
            GLES20.glEnableVertexAttribArray(vertexHandle);
            GLES20.glUniform1f(lineOpacityHandle, 1.0f);
            GLES20.glUniform3f(lineColorHandle, 1.0f, 0.447f, 0.0f);
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMat, 0);
            GLES20.glDrawElements(GLES20.GL_LINES, NUM_QUAD_OBJECT_INDICES,
                GLES20.GL_UNSIGNED_SHORT, mQuadIndices);
            GLES20.glDisableVertexAttribArray(vertexHandle);
            GLES20.glLineWidth(1.0f);
            GLES20.glUseProgram(0);
        }
        
        // Draw the region of interest
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        
        drawRegionOfInterest(ROICenterX, ROICenterY, ROIWidth, ROIHeight);
        
        GLES20.glDisable(GLES20.GL_BLEND);
        
        mRenderer.end();
    }
    
    
    public void setROI(float center_x, float center_y, float width, float height)
    {
        ROICenterX = center_x;
        ROICenterY = center_y;
        ROIWidth = width;
        ROIHeight = height;
    }
    
    
    static String fromShortArray(short[] str)
    {
        StringBuilder result = new StringBuilder();
        for (short c : str)
            result.appendCodePoint(c);
        return result.toString();
    }
    
    
    public void setViewport(int vpX, int vpY, int vpSizeX, int vpSizeY)
    {
        viewportPosition_x = vpX;
        viewportPosition_y = vpY;
        viewportSize_x = vpSizeX;
        viewportSize_y = vpSizeY;
    }
    
    
    private void drawRegionOfInterest(float center_x, float center_y,
        float width, float height)
    {
        // assumption is that center_x, center_y, width and height are given
        // here in screen coordinates (screen pixels)
        float[] orthProj = new float[16];
        setOrthoMatrix(0.0f, (float) viewportSize_x, (float) viewportSize_y,
            0.0f, -1.0f, 1.0f, orthProj);
        
        // compute coordinates
        float minX = center_x - width / 2;
        float maxX = center_x + width / 2;
        float minY = center_y - height / 2;
        float maxY = center_y + height / 2;
        
        // Update vertex coordinates of ROI rectangle
        ROIVertices[0] = minX - viewportPosition_x;
        ROIVertices[1] = minY - viewportPosition_y;
        ROIVertices[2] = 0;
        
        ROIVertices[3] = maxX - viewportPosition_x;
        ROIVertices[4] = minY - viewportPosition_y;
        ROIVertices[5] = 0;
        
        ROIVertices[6] = maxX - viewportPosition_x;
        ROIVertices[7] = maxY - viewportPosition_y;
        ROIVertices[8] = 0;
        
        ROIVertices[9] = minX - viewportPosition_x;
        ROIVertices[10] = maxY - viewportPosition_y;
        ROIVertices[11] = 0;
        
        updateROIVertByteBuffer();
        
        GLES20.glUseProgram(shaderProgramID);
        GLES20.glLineWidth(3.0f);
        
        GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT, false,
            0, mROIVerts);
        GLES20.glEnableVertexAttribArray(vertexHandle);
        
        GLES20.glUniform1f(lineOpacityHandle, 1.0f); // 0.35f);
        GLES20.glUniform3f(lineColorHandle, 0.0f, 1.0f, 0.0f);// R,G,B
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, orthProj, 0);
        
        // Then, we issue the render call
        GLES20.glDrawElements(GLES20.GL_LINES, NUM_QUAD_OBJECT_INDICES,
            GLES20.GL_UNSIGNED_SHORT, mROIIndices);
        
        // Disable the vertex array handle
        GLES20.glDisableVertexAttribArray(vertexHandle);
        
        // Restore default line width
        GLES20.glLineWidth(1.0f);
        
        // Unbind shader program
        GLES20.glUseProgram(0);
    }
    
    class WordDesc implements Comparable<WordDesc>
    {
        public WordDesc(String text, int aX, int aY, int bX, int bY)
        {
            this.text = text;
            this.Ax = aX;
            this.Ay = aY;
            this.Bx = bX;
            this.By = bY;
        }
        
        String text;
        int Ax, Ay, Bx, By;
        
        
        @Override
        public int compareTo(WordDesc w2)
        {
            WordDesc w1 = this;
            int ret;
            
            // We split the screen into 100 bins so that words on a line 
            // are roughly kept together.
            int bins = viewportSize_y/100;
            if(bins == 0)
            {
                // Not expected, but should make sure we don't divide by 0.
                bins = 1;
            }

            // We want to order words starting from the top left to the bottom right.
            // We therefore use the top-middle point of the word obb and bin the word 
            // to an address that is consistently comparable to other word locations.
            int w1mx = w1.Ax/bins;
            int w1my = ((w1.By + w1.Ay)/2)/bins;

            int w2mx = w2.Ax/bins;
            int w2my = ((w2.By + w2.Ay)/2)/bins;

            ret = new Integer(w1my*viewportSize_x + w1mx).compareTo((w2my*viewportSize_x + w2mx));

            return ret;
        }

        @Override
        public String toString()
        {
            return text + " [" + Ax + ", " + Ay + ", " + Bx + ", " + By + "]";
        }
    }
    
    
    private void setOrthoMatrix(float nLeft, float nRight, float nBottom,
        float nTop, float nNear, float nFar, float[] _ROIOrthoProjMatrix)
    {
        for (int i = 0; i < 16; i++)
            _ROIOrthoProjMatrix[i] = 0.0f;
        
        _ROIOrthoProjMatrix[0] = 2.0f / (nRight - nLeft);
        _ROIOrthoProjMatrix[5] = 2.0f / (nTop - nBottom);
        _ROIOrthoProjMatrix[10] = 2.0f / (nNear - nFar);
        _ROIOrthoProjMatrix[12] = -(nRight + nLeft) / (nRight - nLeft);
        _ROIOrthoProjMatrix[13] = -(nTop + nBottom) / (nTop - nBottom);
        _ROIOrthoProjMatrix[14] = (nFar + nNear) / (nFar - nNear);
        _ROIOrthoProjMatrix[15] = 1.0f;
        
    }
    
}
