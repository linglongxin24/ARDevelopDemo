/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.vuforia.samples.VuforiaSamples.app.VuMark;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.vuforia.CameraCalibration;
import com.vuforia.CameraDevice;
import com.vuforia.Device;
import com.vuforia.Image;
import com.vuforia.InstanceId;
import com.vuforia.Matrix44F;
import com.vuforia.PIXEL_FORMAT;
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.Vec2F;
import com.vuforia.Vec3F;
import com.vuforia.VuMarkTarget;
import com.vuforia.VuMarkTargetResult;
import com.vuforia.VuMarkTemplate;
import com.vuforia.Vuforia;
import com.vuforia.samples.SampleApplication.SampleAppRenderer;
import com.vuforia.samples.SampleApplication.SampleAppRendererControl;
import com.vuforia.samples.SampleApplication.SampleApplicationSession;
import com.vuforia.samples.SampleApplication.utils.CubeShaders;
import com.vuforia.samples.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.samples.SampleApplication.utils.SampleUtils;
import com.vuforia.samples.SampleApplication.utils.Texture;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

// The renderer class for the ImageTargets sample.
public class VuMarkRenderer implements GLSurfaceView.Renderer, SampleAppRendererControl
{
    private static final String LOGTAG = "VuMarkRenderer";

    private SampleApplicationSession vuforiaAppSession;
    private SampleAppRenderer mSampleAppRenderer;
    private VuMark mActivity;

    private Vector<Texture> mTextures;
    private int shaderProgramID;
    private int vertexHandle;
    private int textureCoordHandle;
    private int mvpMatrixHandle;
    private int texSampler2DHandle;
    private int calphaHandle;

    private Renderer mRenderer;

    private double t0;

    private final Plane mPlaneObj;

    private boolean mIsActive = false;

    // ratio to apply so that the augmentation surrounds the vumark
    private static final float VUMARK_SCALE = 1.02f;
    private String currentVumarkIdOnCard;

    public VuMarkRenderer(VuMark activity,
                          SampleApplicationSession session)
    {
        mActivity = activity;
        vuforiaAppSession = session;
        t0 = -1.0;
        mPlaneObj = new Plane();

        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
        mSampleAppRenderer = new SampleAppRenderer(this, mActivity, Device.MODE.MODE_AR, false, .01f, 100f);
    }


    // Called to draw the current frame.
    @Override
    public void onDrawFrame(GL10 gl)
    {
        if (!mIsActive)
            return;

        // Call our function to render content from SampleAppRenderer class
        mSampleAppRenderer.render();
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


    // Called when the surface changed size.
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");

        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height);

        // RenderingPrimitives to be updated when some rendering change is done
        mSampleAppRenderer.onConfigurationChanged(mIsActive);

        // Initializes rendering
        initRendering();
    }


    public void setActive(boolean active)
    {
        mIsActive = active;

        if(mIsActive)
            mSampleAppRenderer.configureVideoBackground();
    }


    // Function for initializing the renderer.
    private void initRendering()
    {
        mRenderer = Renderer.getInstance();

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
                : 1.0f);

        for (Texture t : mTextures)
        {
            GLES20.glGenTextures(1, t.mTextureID, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE, t.mData);
        }

        shaderProgramID = SampleUtils.createProgramFromShaderSrc(
                CubeShaders.CUBE_MESH_VERTEX_SHADER,
                CubeShaders.CUBE_MESH_FRAGMENT_SHADER);

        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexPosition");
        textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,
            "vertexTexCoord");
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "modelViewProjectionMatrix");
        texSampler2DHandle = GLES20.glGetUniformLocation(shaderProgramID,
            "texSampler2D");
        calphaHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "calpha");

        // Hide the Loading Dialog
        mActivity.loadingDialogHandler
            .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);

    }

    private float blinkVumark(boolean reset)
    {
        if (reset || t0 < 0.0f)
        {
            t0 = System.currentTimeMillis();
        }
        if (reset) 
        {
            return 0.0f;
        }
        double time = System.currentTimeMillis();
        double delta = (time-t0);

        if (delta > 1000.0f)
        {
            return 1.0f;
        }

        if ((delta < 300.0f) || ((delta > 500.0f) && (delta < 800.0f)))
        {
            return 1.0f;
        }

        return 0.0f;
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

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        boolean gotVuMark = false;

        String markerType = "";
        String markerValue = "";
        Bitmap markerBitmap = null;
        int indexVuMarkToDisplay = -1;

        if (state.getNumTrackableResults() > 1)
        {
            float minimumDistance = Float.MAX_VALUE;
            CameraCalibration cameraCalibration = CameraDevice.getInstance().getCameraCalibration();
            Vec2F screenSize = cameraCalibration.getSize();
            Vec2F screenCenter = new Vec2F(screenSize.getData()[0] / 2.0f, screenSize.getData()[1] / 2.0f);

            for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++)
            {
                TrackableResult result = state.getTrackableResult(tIdx);
                if (result.isOfType(VuMarkTargetResult.getClassType()))
                {
                    Vec3F point = new Vec3F(0, 0, 0);
                    Vec2F projection = Tool.projectPoint(cameraCalibration, result.getPose(), point);

                    float distance = distanceSquared(projection, screenCenter);
                    if (distance < minimumDistance)
                    {
                        minimumDistance = distance;
                        indexVuMarkToDisplay = tIdx;
                    }
                }
            }

        }

        // Did we find any trackables this frame?
        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++)
        {
            TrackableResult result = state.getTrackableResult(tIdx);
            Trackable trackable = result.getTrackable();
            Matrix44F modelViewMatrix_Vuforia = Tool
                .convertPose2GLMatrix(result.getPose());
            float[] modelViewMatrix = modelViewMatrix_Vuforia.getData();

            if (result.isOfType(VuMarkTargetResult.getClassType()))
            {
                VuMarkTargetResult vmtResult = (VuMarkTargetResult) result;
                VuMarkTarget vmTgt = (VuMarkTarget) vmtResult.getTrackable();
                
                VuMarkTemplate vmTmp = (VuMarkTemplate) vmTgt.getTemplate();
                // user data contains the the SVG layer corresponding to the contour
                // of the VuMark template
                // look at the iOS sample app to see how this data can be used to dynamically
                // render an OpenGL object on top of the contour.
                String userData = vmTmp.getVuMarkUserData();


                InstanceId instanceId = vmTgt.getInstanceId();
                boolean isMainVuMark = ((indexVuMarkToDisplay < 0) ||
                                        (indexVuMarkToDisplay == tIdx));
                gotVuMark = true;

                if (isMainVuMark) {
                    markerValue = instanceIdToValue(instanceId);
                    markerType = instanceIdToType(instanceId);
                    Image instanceImage = vmTgt.getInstanceImage();
                    markerBitmap = getBitMapFromImage(instanceImage);

                    if (! markerValue.equalsIgnoreCase(currentVumarkIdOnCard))
                    {
                        mActivity.hideCard();
                        blinkVumark(true);
                    }
                }
                int textureIndex = 0;

                // deal with the modelview and projection matrices
                float[] modelViewProjection = new float[16];
				
                // Add a translation to recenter the augmentation
                // on the VuMark center, w.r.t. the origin
                Vec2F origin = vmTmp.getOrigin();
                float translX = -origin.getData()[0];
                float translY = -origin.getData()[1];
                Matrix.translateM(modelViewMatrix, 0, translX, translY, 0);

                // Scales the plane relative to the target
                float vumarkWidth = vmTgt.getSize().getData()[0];
                float vumarkHeight = vmTgt.getSize().getData()[1];
                Matrix.scaleM(modelViewMatrix, 0, vumarkWidth * VUMARK_SCALE,
                        vumarkHeight * VUMARK_SCALE, 1.0f);

                Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelViewMatrix, 0);

                // activate the shader program and bind the vertex/normal/tex coords
                GLES20.glUseProgram(shaderProgramID);

                GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                        false, 0, mPlaneObj.getVertices());
                GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                        GLES20.GL_FLOAT, false, 0, mPlaneObj.getTexCoords());

                GLES20.glEnableVertexAttribArray(vertexHandle);
                GLES20.glEnableVertexAttribArray(textureCoordHandle);

                // activate texture 0, bind it, and pass to shader
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                        mTextures.get(textureIndex).mTextureID[0]);
                GLES20.glUniform1i(texSampler2DHandle, 0);
                GLES20.glUniform1f(calphaHandle, isMainVuMark ? blinkVumark(false) : 1.0f);

                // pass the model view matrix to the shader
                GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                        modelViewProjection, 0);

                // finally draw the plane
                GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                        mPlaneObj.getNumObjectIndex(), GLES20.GL_UNSIGNED_SHORT,
                        mPlaneObj.getIndices());

                // disable the enabled arrays
                GLES20.glDisableVertexAttribArray(vertexHandle);
                GLES20.glDisableVertexAttribArray(textureCoordHandle);
                SampleUtils.checkGLError("Render Frame");
            }

        }

        if(gotVuMark)
        {
            // If we have a detection, let's make sure
            // the card is visible
            mActivity.showCard(markerType, markerValue, markerBitmap);
            currentVumarkIdOnCard = markerValue;
        }
        else
        {
            // We reset the state of the animation so that
            // it triggers next time a vumark is detected
            blinkVumark(true);
            // We also reset the value of the current value of the vumark on card
            // so that we hide and show the mumark if we redetect the same vumark instance
            currentVumarkIdOnCard = null;
        }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);

        mRenderer.end();
    }

    private float distanceSquared(Vec2F p1, Vec2F p2)
    {
        return (float) (Math.pow(p1.getData()[0] - p2.getData()[0], 2.0) +
                        Math.pow(p1.getData()[1] - p2.getData()[1], 2.0));
    }

    public void setTextures(Vector<Texture> textures)
    {
        mTextures = textures;
    }

    private String instanceIdToType(InstanceId instanceId)
    {
        switch(instanceId.getDataType())
        {
            case InstanceId.ID_DATA_TYPE.STRING:
                return "String";
            
            case InstanceId.ID_DATA_TYPE.BYTES:
                return "Bytes";
            
            case InstanceId.ID_DATA_TYPE.NUMERIC:
                return "Numeric";
        }

        return "Unknown";
    }

    static final String hexTable = "0123456789abcdef";

    private String instanceIdToValue(InstanceId instanceId)
    {
        ByteBuffer instanceIdBuffer = instanceId.getBuffer();
        byte[] instanceIdBytes = new byte[instanceIdBuffer.remaining()];
        instanceIdBuffer.get(instanceIdBytes);

        String instanceIdStr = "";
        switch(instanceId.getDataType())
        {
            case InstanceId.ID_DATA_TYPE.STRING:
                instanceIdStr = new String(instanceIdBytes, Charset.forName("US-ASCII"));
                break;

            case InstanceId.ID_DATA_TYPE.BYTES:
                for (int i = instanceIdBytes.length - 1; i >= 0; i--)
                {
                    byte byteValue = instanceIdBytes[i];
                    instanceIdStr += hexTable.charAt((byteValue & 0xf0) >> 4);
                    instanceIdStr += hexTable.charAt(byteValue & 0x0f);
                }
                break;

            case InstanceId.ID_DATA_TYPE.NUMERIC:
                BigInteger instanceIdNumeric = instanceId.getNumericValue();
                Long instanceIdLong = instanceIdNumeric.longValue();
                instanceIdStr = Long.toString(instanceIdLong);
                break;

            default:
                return "Unknown";
        }

        return instanceIdStr;
    }

    private Bitmap getBitMapFromImage(Image image)
    {
        // we handle only RGB888 in this example
        if (image.getFormat() != PIXEL_FORMAT.RGBA8888) {
            // the default 'stock' image will be used for the instance image
            return null;
        }
        ByteBuffer pixels = image.getPixels();
        byte[] imgData = new byte[pixels.remaining()];
        pixels.get(imgData, 0, imgData.length);
        int width = image.getWidth();
        int height = image.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        int[] colors = new int[width * height];
        int r,g,b,a;
        for (int ci = 0; ci < colors.length; ci++)
        {
            r = (0xFF & imgData[4*ci]);
            g = (0xFF & imgData[4*ci+1]);
            b = (0xFF & imgData[4*ci+2]);
            a = (0xFF & imgData[4*ci+3]);
            colors[ci] = Color.argb(a, r, g, b);
        }

        bitmap.setPixels(colors, 0, width, 0, 0, width, height);
        return bitmap;
    }
}
