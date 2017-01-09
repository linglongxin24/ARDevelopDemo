/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.


Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.samples.VuforiaSamples.app.TextRecognition;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.vuforia.CameraDevice;
import com.vuforia.RectangleInt;
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.TextTracker;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.VideoBackgroundConfig;
import com.vuforia.VideoMode;
import com.vuforia.Vuforia;
import com.vuforia.WordList;
import com.vuforia.samples.SampleApplication.SampleApplicationControl;
import com.vuforia.samples.SampleApplication.SampleApplicationException;
import com.vuforia.samples.SampleApplication.SampleApplicationSession;
import com.vuforia.samples.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.samples.SampleApplication.utils.SampleApplicationGLView;
import com.vuforia.samples.SampleApplication.utils.SampleUtils;
import com.vuforia.samples.VuforiaSamples.R;
import com.vuforia.samples.VuforiaSamples.app.TextRecognition.TextRecoRenderer.WordDesc;
import com.vuforia.samples.VuforiaSamples.ui.SampleAppMenu.SampleAppMenu;
import com.vuforia.samples.VuforiaSamples.ui.SampleAppMenu.SampleAppMenuGroup;
import com.vuforia.samples.VuforiaSamples.ui.SampleAppMenu.SampleAppMenuInterface;


public class TextReco extends Activity implements SampleApplicationControl,
    SampleAppMenuInterface
{
    private static final String LOGTAG = "TextReco";
    
    SampleApplicationSession vuforiaAppSession;
    
    private final static int COLOR_OPAQUE = Color.argb(178, 0, 0, 0);
    private final static int WORDLIST_MARGIN = 10;
    
    // Our OpenGL view:
    private SampleApplicationGLView mGlView;
    
    // Our renderer:
    private TextRecoRenderer mRenderer;
    
    private SampleAppMenu mSampleAppMenu;
    
    private ArrayList<View> mSettingsAdditionalViews;
    
    private RelativeLayout mUILayout;
    
    private LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(
        this);
    private boolean mIsTablet = false;
    
    private boolean mIsVuforiaStarted = false;
    
    private GestureDetector mGestureDetector;
    
    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;
    
    // Called when the activity first starts or the user navigates back to an
    // activity.
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);
        
        vuforiaAppSession = new SampleApplicationSession(this);
        
        startLoadingAnimation();
        
        vuforiaAppSession
            .initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        mGestureDetector = new GestureDetector(this, new GestureListener());
    }
    
    // Process Single Tap event to trigger autofocus
    private class GestureListener extends
        GestureDetector.SimpleOnGestureListener
    {
        // Used to set autofocus one second after a manual focus is triggered
        private final Handler autofocusHandler = new Handler();
        
        
        @Override
        public boolean onDown(MotionEvent e)
        {
            return true;
        }
        
        
        @Override
        public boolean onSingleTapUp(MotionEvent e)
        {
            // Generates a Handler to trigger autofocus
            // after 1 second
            autofocusHandler.postDelayed(new Runnable()
            {
                public void run()
                {
                    boolean result = CameraDevice.getInstance().setFocusMode(
                        CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);
                    
                    if (!result)
                        Log.e("SingleTapUp", "Unable to trigger focus");
                }
            }, 1000L);
            
            return true;
        }
    }
    
    
    // Called when the activity will start interacting with the user.
    @Override
    protected void onResume()
    {
        Log.d(LOGTAG, "onResume");
        super.onResume();
        
        try
        {
            vuforiaAppSession.resumeAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }
        
        if (mIsVuforiaStarted)
            postStartCamera();
        
        // Resume the GL view:
        if (mGlView != null)
        {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
        
    }
    
    
    // Callback for configuration changes the activity handles itself
    @Override
    public void onConfigurationChanged(Configuration config)
    {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);
        
        vuforiaAppSession.onConfigurationChanged();

        mRenderer.updateConfiguration();

        if(mIsVuforiaStarted)
            configureVideoBackgroundROI();
    }
    
    
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        // Process the Gestures
        if (mSampleAppMenu != null && mSampleAppMenu.processEvent(event))
            return true;
        
        return mGestureDetector.onTouchEvent(event);
    }
    
    
    // Called when the system is about to start resuming a previous activity.
    @Override
    protected void onPause()
    {
        Log.d(LOGTAG, "onPause");
        super.onPause();
        
        if (mGlView != null)
        {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }
        
        try
        {
            vuforiaAppSession.pauseAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }
        
        stopCamera();
    }
    
    
    // The final call you receive before your activity is destroyed.
    @Override
    protected void onDestroy()
    {
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();
        
        try
        {
            vuforiaAppSession.stopAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }
        
        System.gc();
    }
    
    
    private void startLoadingAnimation()
    {
        LayoutInflater inflater = LayoutInflater.from(this);
        mUILayout = (RelativeLayout) inflater.inflate(
            R.layout.camera_overlay_textreco, null, false);
        
        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);
        
        // Gets a reference to the loading dialog
        loadingDialogHandler.mLoadingDialogContainer = mUILayout
            .findViewById(R.id.loading_indicator);
        
        // Shows the loading indicator at start
        loadingDialogHandler
            .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);
        
        // Adds the inflated layout to the view
        addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT));
        
    }
    
    
    // Initializes AR application components.
    private void initApplicationAR()
    {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();
        
        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);
        
        mRenderer = new TextRecoRenderer(this, vuforiaAppSession);
        mGlView.setRenderer(mRenderer);

        showLoupe(false);
        
    }
    
    
    private void postStartCamera()
    {
        // Sets the layout background to transparent
        mUILayout.setBackgroundColor(Color.TRANSPARENT);
        
        // start the image tracker now that the camera is started
        Tracker t = TrackerManager.getInstance().getTracker(
            TextTracker.getClassType());
        if (t != null)
            t.start();
        
        configureVideoBackgroundROI();
    }
    
    
    void configureVideoBackgroundROI()
    {
        VideoMode vm = CameraDevice.getInstance().getVideoMode(
            CameraDevice.MODE.MODE_DEFAULT);
        VideoBackgroundConfig config = Renderer.getInstance()
            .getVideoBackgroundConfig();
        
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        
        {
            // calc ROI
            // width of margin is :
            // 5% of the width of the screen for a phone
            // 20% of the width of the screen for a tablet
            int marginWidth = mIsTablet ? (screenWidth * 20) / 100
                : (screenWidth * 5) / 100;
            
            // loupe height is :
            // 15% of the screen height for a phone
            // 10% of the screen height for a tablet
            int loupeHeight = mIsTablet ? (screenHeight * 10) / 100
                : (screenHeight * 15) / 100;
            
            // lupue width takes the width of the screen minus 2 margins
            int loupeWidth = screenWidth - (2 * marginWidth);
            
            // definition of the region of interest
            mRenderer.setROI(screenWidth / 2, marginWidth + (loupeHeight / 2),
                loupeWidth, loupeHeight);
        }
        
        // Get the camera rotation
        int cameraDirection;
        switch (CameraDevice.getInstance().getCameraDirection()) {
            case CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_BACK:
                cameraDirection = Camera.CameraInfo.CAMERA_FACING_BACK;
                break;
            case CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_FRONT:
                cameraDirection = Camera.CameraInfo.CAMERA_FACING_FRONT;
                break;
            default:
                cameraDirection = Camera.CameraInfo.CAMERA_FACING_BACK;
        }
        int cameraRotation = 0;
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == cameraDirection) {
                cameraRotation = cameraInfo.orientation;
                break;
            }
        }

        // Get the display rotation
        Display display = ((WindowManager)this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int displayRotation = display.getRotation();

        // convert into camera coords
        int[] loupeCenterX = { 0 };
        int[] loupeCenterY = { 0 };
        int[] loupeWidth = { 0 };
        int[] loupeHeight = { 0 };
        SampleUtils.screenCoordToCameraCoord((int) mRenderer.ROICenterX,
            (int) mRenderer.ROICenterY, (int) mRenderer.ROIWidth,
            (int) mRenderer.ROIHeight, screenWidth, screenHeight,
            vm.getWidth(), vm.getHeight(), loupeCenterX, loupeCenterY,
            loupeWidth, loupeHeight, displayRotation, cameraRotation);

        // Compute the angle by which the camera image should be rotated clockwise so that it is
        // shown correctly on the display given its current orientation.
        int correctedRotation = ((((displayRotation*90)-cameraRotation)+360)%360)/90;

        int upDirection;
        switch (correctedRotation){
            case 0:
                upDirection = TextTracker.UP_DIRECTION.REGIONOFINTEREST_UP_IS_0_HRS;
                break;
            case 1:
                upDirection = TextTracker.UP_DIRECTION.REGIONOFINTEREST_UP_IS_3_HRS;
                break;
            case 2:
                upDirection = TextTracker.UP_DIRECTION.REGIONOFINTEREST_UP_IS_6_HRS;
                break;
            case 3:
                upDirection = TextTracker.UP_DIRECTION.REGIONOFINTEREST_UP_IS_9_HRS;
                break;
            default:
                upDirection = TextTracker.UP_DIRECTION.REGIONOFINTEREST_UP_IS_9_HRS;
        }

        RectangleInt detROI = new RectangleInt(loupeCenterX[0]
            - (loupeWidth[0] / 2), loupeCenterY[0] - (loupeHeight[0] / 2),
            loupeCenterX[0] + (loupeWidth[0] / 2), loupeCenterY[0]
                + (loupeHeight[0] / 2));
        
        TextTracker tt = (TextTracker) TrackerManager.getInstance().getTracker(
            TextTracker.getClassType());

        if (tt != null)
            tt.setRegionOfInterest(detROI, detROI,
                    upDirection);
        
        mRenderer.setViewport(0, 0, metrics.widthPixels, metrics.heightPixels);
    }
    
    
    private void stopCamera()
    {
        doStopTrackers();
        
        CameraDevice.getInstance().stop();
        CameraDevice.getInstance().deinit();
    }
    
    
    void updateWordListUI(final List<WordDesc> words)
    {
        runOnUiThread(new Runnable()
        {
            
            public void run()
            {
                RelativeLayout wordListLayout = (RelativeLayout) mUILayout
                    .findViewById(R.id.wordList);
                wordListLayout.removeAllViews();
                
                if (words.size() > 0)
                {
                    LayoutParams params = wordListLayout.getLayoutParams();
                    // Changes the height and width to the specified *pixels*
                    int maxTextHeight = params.height - (2 * WORDLIST_MARGIN);
                    
                    int[] textInfo = fontSizeForTextHeight(maxTextHeight,
                        words.size(), params.width, 32, 12);
                    
                    int count = -1;
                    int nbWords = textInfo[2]; // number of words we can display
                    TextView previousView = null;
                    TextView tv;
                    for (WordDesc word : words)
                    {
                        count++;
                        if (count == nbWords)
                        {
                            break;
                        }
                        tv = new TextView(TextReco.this);
                        tv.setText(word.text);
                        RelativeLayout.LayoutParams txtParams = new RelativeLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.WRAP_CONTENT);
                        
                        if (previousView != null)
                            txtParams.addRule(RelativeLayout.BELOW,
                                previousView.getId());
                        
                        txtParams.setMargins(0, (count == 0) ? WORDLIST_MARGIN
                            : 0, 0, (count == (nbWords - 1)) ? WORDLIST_MARGIN
                            : 0);
                        tv.setLayoutParams(txtParams);
                        tv.setGravity(Gravity.CENTER_VERTICAL
                            | Gravity.CENTER_HORIZONTAL);
                        tv.setTextSize(textInfo[0]);
                        tv.setTextColor(Color.WHITE);
                        tv.setHeight(textInfo[1]);
                        tv.setId(count + 100);
                        
                        wordListLayout.addView(tv);
                        previousView = tv;
                    }
                }
            }
        });
    }
    
    
    private void showLoupe(boolean isActive)
    {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        
        // width of margin is :
        // 5% of the width of the screen for a phone
        // 20% of the width of the screen for a tablet
        int marginWidth = mIsTablet ? (width * 20) / 100 : (width * 5) / 100;
        
        // loupe height is :
        // 33% of the screen height for a phone
        // 20% of the screen height for a tablet
        int loupeHeight = mIsTablet ? (height * 10) / 100 : (height * 15) / 100;
        
        // lupue width takes the width of the screen minus 2 margins
        int loupeWidth = width - (2 * marginWidth);
        
        int wordListHeight = height - (loupeHeight + marginWidth);
        
        // definition of the region of interest
        mRenderer.setROI(width / 2, marginWidth + (loupeHeight / 2),
            loupeWidth, loupeHeight);
        
        // Gets a reference to the loading dialog
        View loadingIndicator = mUILayout.findViewById(R.id.loading_indicator);
        
        RelativeLayout loupeLayout = (RelativeLayout) mUILayout
            .findViewById(R.id.loupeLayout);
        
        ImageView topMargin = (ImageView) mUILayout
            .findViewById(R.id.topMargin);
        
        ImageView leftMargin = (ImageView) mUILayout
            .findViewById(R.id.leftMargin);
        
        ImageView rightMargin = (ImageView) mUILayout
            .findViewById(R.id.rightMargin);
        
        ImageView loupeArea = (ImageView) mUILayout.findViewById(R.id.loupe);
        
        RelativeLayout wordListLayout = (RelativeLayout) mUILayout
            .findViewById(R.id.wordList);
        
        wordListLayout.setBackgroundColor(COLOR_OPAQUE);
        
        if (isActive)
        {
            topMargin.getLayoutParams().height = marginWidth;
            topMargin.getLayoutParams().width = width;
            
            leftMargin.getLayoutParams().width = marginWidth;
            leftMargin.getLayoutParams().height = loupeHeight;
            
            rightMargin.getLayoutParams().width = marginWidth;
            rightMargin.getLayoutParams().height = loupeHeight;
            
            RelativeLayout.LayoutParams params;
            
            params = (RelativeLayout.LayoutParams) loupeLayout
                .getLayoutParams();
            params.height = loupeHeight;
            loupeLayout.setLayoutParams(params);
            
            loupeArea.getLayoutParams().width = loupeWidth;
            loupeArea.getLayoutParams().height = loupeHeight;
            loupeArea.setVisibility(View.VISIBLE);
            
            params = (RelativeLayout.LayoutParams) wordListLayout
                .getLayoutParams();
            params.height = wordListHeight;
            params.width = width;
            wordListLayout.setLayoutParams(params);
            
            loadingIndicator.setVisibility(View.GONE);
            loupeArea.setVisibility(View.VISIBLE);
            topMargin.setVisibility(View.VISIBLE);
            loupeLayout.setVisibility(View.VISIBLE);
            wordListLayout.setVisibility(View.VISIBLE);
            
        } else
        {
            loadingIndicator.setVisibility(View.VISIBLE);
            loupeArea.setVisibility(View.GONE);
            topMargin.setVisibility(View.GONE);
            loupeLayout.setVisibility(View.GONE);
            wordListLayout.setVisibility(View.GONE);
        }
        
    }
    
    
    // the funtions returns 3 values in an array of ints
    // [0] : the text size
    // [1] : the text component height
    // [2] : the number of words we can display
    private int[] fontSizeForTextHeight(int totalTextHeight, int nbWords,
        int textWidth, int textSizeMax, int textSizeMin)
    {
        
        int[] result = new int[3];
        String text = "Agj";
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT));
        tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        // tv.setTextSize(30);
        // tv.setHeight(textHeight);
        int textSize = 0;
        int layoutHeight = 0;
        
        final float densityMultiplier = getResources().getDisplayMetrics().density;
        
        for (textSize = textSizeMax; textSize >= textSizeMin; textSize -= 2)
        {
            // Get the font size setting
            float fontScale = Settings.System.getFloat(getContentResolver(),
                Settings.System.FONT_SCALE, 1.0f);
            // Text view line spacing multiplier
            float spacingMult = 1.0f * fontScale;
            // Text view additional line spacing
            float spacingAdd = 0.0f;
            TextPaint paint = new TextPaint(tv.getPaint());
            paint.setTextSize(textSize * densityMultiplier);
            // Measure using a static layout
            StaticLayout layout = new StaticLayout(text, paint, textWidth,
                Alignment.ALIGN_NORMAL, spacingMult, spacingAdd, true);
            layoutHeight = layout.getHeight();
            if ((layoutHeight * nbWords) < totalTextHeight)
            {
                result[0] = textSize;
                result[1] = layoutHeight;
                result[2] = nbWords;
                return result;
            }
        }
        
        // we won't be able to display all the fonts
        result[0] = textSize;
        result[1] = layoutHeight;
        result[2] = totalTextHeight / layoutHeight;
        return result;
    }
    
    
    @Override
    public void onInitARDone(SampleApplicationException exception)
    {
        
        if (exception == null)
        {
            initApplicationAR();
            
            // Hint to the virtual machine that it would be a good time to
            // run the garbage collector:
            //
            // NOTE: This is only a hint. There is no guarantee that the
            // garbage collector will actually be run.
            System.gc();

            mRenderer.setActive(true);

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
            
            // Hides the Loading Dialog
            loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
            showLoupe(true);
            
            // Sets the UILayout to be drawn in front of the camera
            mUILayout.bringToFront();
            
            try
            {
                vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT);
            } catch (SampleApplicationException e)
            {
                Log.e(LOGTAG, e.getString());
            }
            
            mIsVuforiaStarted = true;
            
            postStartCamera();
            
            setSampleAppMenuAdditionalViews();
            mSampleAppMenu = new SampleAppMenu(this, this, "Text Reco",
                mGlView, mUILayout, mSettingsAdditionalViews);
            setSampleAppMenuSettings();
            
        } else
        {
            Log.e(LOGTAG, exception.getString());
            showInitializationErrorMessage(exception.getString());
        }
    }
    
    
    // Shows initialization error messages as System dialogs
    public void showInitializationErrorMessage(String message)
    {
        final String errorMessage = message;
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                if (mErrorDialog != null)
                {
                    mErrorDialog.dismiss();
                }
                
                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                    TextReco.this);
                builder
                    .setMessage(errorMessage)
                    .setTitle(getString(R.string.INIT_ERROR))
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton(getString(R.string.button_OK),
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int id)
                            {
                                finish();
                            }
                        });
                
                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }
    
    
    // Functions to load and destroy tracking data.
    @Override
    public boolean doLoadTrackersData()
    {
        TrackerManager tm = TrackerManager.getInstance();
        TextTracker tt = (TextTracker) tm
            .getTracker(TextTracker.getClassType());
        WordList wl = tt.getWordList();
        
        return wl.loadWordList("TextReco/Vuforia-English-word.vwl",
            STORAGE_TYPE.STORAGE_APPRESOURCE);
    }
    
    
    @Override
    public boolean doUnloadTrackersData()
    {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;
        TrackerManager tm = TrackerManager.getInstance();
        TextTracker tt = (TextTracker) tm
            .getTracker(TextTracker.getClassType());
        
        if(tt != null)
        {
            WordList wl = tt.getWordList();
            wl.unloadAllLists();
        }
        
        return result;
    }
    
    
    @Override
    public void onVuforiaUpdate(State state)
    {
    }
    
    
    @Override
    public boolean doInitTrackers()
    {
        TrackerManager tManager = TrackerManager.getInstance();
        Tracker tracker;
        
        // Indicate if the trackers were initialized correctly
        boolean result = true;
        
        tracker = tManager.initTracker(TextTracker.getClassType());
        if (tracker == null)
        {
            Log.e(
                LOGTAG,
                "Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        } else
        {
            Log.i(LOGTAG, "Tracker successfully initialized");
        }
        
        return result;
    }
    
    
    @Override
    public boolean doStartTrackers()
    {
        // Indicate if the trackers were started correctly
        boolean result = true;
        
        Tracker textTracker = TrackerManager.getInstance().getTracker(
            TextTracker.getClassType());
        if (textTracker != null)
            textTracker.start();
        
        return result;
    }
    
    
    @Override
    public boolean doStopTrackers()
    {
        // Indicate if the trackers were stopped correctly
        boolean result = true;
        
        Tracker textTracker = TrackerManager.getInstance().getTracker(
            TextTracker.getClassType());
        if (textTracker != null)
            textTracker.stop();
        
        return result;
    }
    
    
    @Override
    public boolean doDeinitTrackers()
    {
        // Indicate if the trackers were deinitialized correctly
        boolean result = true;
        Log.e(LOGTAG, "UnloadTrackersData");
        
        TrackerManager tManager = TrackerManager.getInstance();
        tManager.deinitTracker(TextTracker.getClassType());
        
        return result;
    }
    
    final public static int CMD_BACK = -1;
    
    
    // This method sets the additional views to be moved along with the GLView
    private void setSampleAppMenuAdditionalViews()
    {
        mSettingsAdditionalViews = new ArrayList<View>();
        mSettingsAdditionalViews.add(mUILayout.findViewById(R.id.topMargin));
        mSettingsAdditionalViews.add(mUILayout.findViewById(R.id.loupeLayout));
        mSettingsAdditionalViews.add(mUILayout.findViewById(R.id.wordList));
    }
    
    
    // This method sets the menu's settings
    private void setSampleAppMenuSettings()
    {
        SampleAppMenuGroup group;
        
        group = mSampleAppMenu.addGroup("", false);
        group.addTextItem(getString(R.string.menu_back), -1);
        
        mSampleAppMenu.attachMenu();
    }
    
    
    @Override
    public boolean menuProcess(int command)
    {
        boolean result = true;
        
        switch (command)
        {
            case CMD_BACK:
                finish();
                break;
            
        }
        
        return result;
    }
    
}
