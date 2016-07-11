package com.android.project.sdcardscanner;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Handler;
import android.support.v4.os.ResultReceiver;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String SHARE_SUBJECT = "SD Card Scanner Update";
    private Button startScanBtn;
    private Button pauseScanBtn;
    private Button resumeScanBtn;
    private Button stopScanBtn;
    private ProgressBar progressBar;
    private LinearLayout infoLayout;
    private TextView loadingTextView;
    private ShareActionProvider mShareActionProvider;

    StringBuilder shareTextBuilder;



    private static final String PAUSE_SCAN_ACTION = "pause_scan_action";
    private static final String RESUME_SCAN_ACTION = "resume_scan_action";
    private static final String STOP_SCAN_ACTION = "stop_scan_action";
    private static final String START_SCAN_ACTION = "start_scan_action";
    private static final String SCAN_RESULT_MODEL = "scan_result_model";
    private static final String PROGRESS_PERCENTAGE = "progress_percentage";

    private static final int A_DIRECTORY_PROCESSED_ACTION = 2;
    private static final int SCAN_PAUSED_ACTION = 3;
    private static final int SCAN_RESUMED_ACTION = 4;
    private static final int SCAN_STOPPED_ACTION = 5;

    private int activeButtonId = 0;

    private ScanResultModel scanResultModel;

    private ResultReceiver resultReceiver;
    private float progressPercentage = 0;
    private boolean isOrientationChange = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int ot = getResources().getConfiguration().orientation;
        switch (ot) {
            case Configuration.ORIENTATION_LANDSCAPE:
                setContentView(R.layout.activity_main_land);
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                setContentView(R.layout.activity_main);
                break;
        }

        setUpViews();

        resultReceiver = new MyResultReceiver(null);

    }

    private void setUpViews() {
        startScanBtn = (Button) findViewById(R.id.scan_button);
        pauseScanBtn = (Button) findViewById(R.id.pause_scan_button);
        resumeScanBtn = (Button) findViewById(R.id.resume_scan_button);
        stopScanBtn = (Button) findViewById(R.id.stop_scan_button);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        infoLayout = (LinearLayout) findViewById(R.id.info_layout);
        loadingTextView = (TextView) findViewById(R.id.loadingTextView);

        setUpOnClickListener(startScanBtn);
        setUpFocusChangeListener(startScanBtn);
        setUpOnClickListener(pauseScanBtn);
        setUpFocusChangeListener(pauseScanBtn);
        setUpOnClickListener(resumeScanBtn);
        setUpFocusChangeListener(resumeScanBtn);
        setUpOnClickListener(stopScanBtn);
        setUpFocusChangeListener(stopScanBtn);
    }



    private void setUpOnClickListener(View view) {
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.requestFocus();
            }
        });
    }

    private void setUpFocusChangeListener(View view) {
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    if (!isOrientationChange) {
                        triggerFocusAction(v.getId());
                    }
                    isOrientationChange = false;
                    v.setBackgroundDrawable(getResources().getDrawable(R.drawable.button_focus_background));
                } else {
                    v.setBackgroundDrawable(getResources().getDrawable(R.drawable.button_background));
                }
            }
        });
    }

    private void triggerFocusAction(int id) {
        activeButtonId = id;
        switch(id)
        {
            case R.id.scan_button:
            {
                Intent intent = new Intent(MainActivity.this, StorageScanService.class);
                intent.setAction(START_SCAN_ACTION);
                intent.putExtra("receiver", resultReceiver);
                startService(intent);
                Toast.makeText(this, "Started", Toast.LENGTH_LONG).show();
                break;
            }
            case R.id.pause_scan_button:
            {
                sendUpdate(PAUSE_SCAN_ACTION);
                Toast.makeText(this, "Paused", Toast.LENGTH_LONG).show();
                break;
            }
            case R.id.resume_scan_button:
            {
                sendUpdate(RESUME_SCAN_ACTION);
                Toast.makeText(this, "Resumed", Toast.LENGTH_LONG).show();
                break;
            }
            case R.id.stop_scan_button:
            {
                sendUpdate(STOP_SCAN_ACTION);
                Toast.makeText(this, "Stopped", Toast.LENGTH_LONG).show();
                break;
            }

        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // TODO Auto-generated method stub
        super.onConfigurationChanged(newConfig);

        int ot = getResources().getConfiguration().orientation;
        switch (ot) {
            case Configuration.ORIENTATION_LANDSCAPE:
                setContentView(R.layout.activity_main_land);
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                setContentView(R.layout.activity_main);
                break;
        }
        isOrientationChange = true;
        setUpViews();
        updateUI();
        // restore the button that was active before the orientation change.
        Button activeButton = (Button) findViewById(activeButtonId);
        if (activeButton != null) {
            activeButton.requestFocus();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //Set up the share action menu item.
        // Inflate menu resource file.
        getMenuInflater().inflate(R.menu.share_menu, menu);
        // Locate MenuItem with ShareActionProvider
        MenuItem item = menu.findItem(R.id.menu_item_share);
        // Fetch and store ShareActionProvider
        mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);
        // Return true to display menu
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle share item click
        switch (item.getItemId()) {
            case R.id.menu_item_share:
                if (progressPercentage >= 100) {
                    shareMessage();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Method to activate the share button.
     * @param shareIntent
     */
    private void setShareIntent(Intent shareIntent) {
        if (mShareActionProvider != null) {
            mShareActionProvider.setShareIntent(shareIntent);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        sendUpdate(STOP_SCAN_ACTION);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sendUpdate(STOP_SCAN_ACTION);
    }

    /**
     * Method to send broadcasts
     * @param action
     */
    private void sendUpdate(String action) {
        Intent intent = new Intent();
        intent.setAction(action);
        sendBroadcast(intent);
    }

    /**
     * Method that updates the UI as scan results are received.
     * method made threadsafe since we are calling method both on orientation change and onReceivedResult.
     */
    private synchronized void updateUI() {
        if (scanResultModel == null) {
            return;
        }
        progressBar.setProgress((int) progressPercentage);
        loadingTextView.setText((int)progressPercentage + "% complete");
        infoLayout.removeAllViews();
        shareTextBuilder = new StringBuilder();
        List<MyFile> files = scanResultModel.getMyFileList();
        long averageSIze = scanResultModel.getAverageFileSize();
        Map<String, Integer> extFreq = scanResultModel.getSortedExtensionFrequencyMap();
        addAverageFileSize(averageSIze, infoLayout);
        addBiggestFilesinfo(files, infoLayout);
        addFrequentExtension(extFreq, infoLayout);
    }

    /**
     * Method to prepare the share intent and activate the share button.
     */
    private void shareMessage() {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, SHARE_SUBJECT);
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareTextBuilder.toString());
        startActivity(Intent.createChooser(shareIntent, "Share via"));
        setShareIntent(shareIntent);
    }

    private String getSizeText(float size) {
        String fileSize;
        double fileSizeNum = (float)(size * 0.000001);
        fileSizeNum =  Math.round(fileSizeNum * 100.0) / 100.0;
        fileSize = fileSizeNum + "MB";
        if (fileSizeNum >= 1000) {
            fileSizeNum *= 0.001f;
            fileSizeNum =  Math.round(fileSizeNum * 100.0) / 100.0;
            fileSize = fileSizeNum + "GB";
        }
        return fileSize;
    }

    /**
     * Method to add the average file size to the info layout.
     * @param avg
     * @param infoLayout
     */
    private void addAverageFileSize(long avg, LinearLayout infoLayout) {
        if (avg == 0) {
            return;
        }
        TextView txt1 = new TextView(this);
        txt1.setText("AVERAGE FILE SIZE");
        txt1.setTypeface(null, Typeface.BOLD_ITALIC);
        shareTextBuilder.append(txt1.getText().toString()).append(System.getProperty("line.separator"));
        Log.d(TAG, txt1.getText().toString());
        infoLayout.addView(txt1);
        TextView txt2 = new TextView(this);
        txt2.setText("Average size: " + getSizeText(avg));
        shareTextBuilder.append(txt2.getText().toString()).append(System.getProperty("line.separator"));

        Log.d(TAG, txt2.getText().toString());
        infoLayout.addView(txt2);
    }

    /**
     * Method to list the 10 biggest Files.
     * @param myFileList
     * @param infoLayout
     */
    private void addBiggestFilesinfo(List<MyFile> myFileList, LinearLayout infoLayout) {
        if (myFileList.size() == 0) {
            return;
        }
        TextView txt1 = new TextView(this);
        txt1.setText("10 FILES WITH THE BIGGEST SIZES");
        txt1.setTypeface(null, Typeface.BOLD_ITALIC);
        shareTextBuilder.append(txt1.getText().toString()).append(System.getProperty("line.separator"));

        Log.d(TAG, txt1.getText().toString());
        infoLayout.addView(txt1);
        Collections.sort(myFileList);
        for (int i = 0; i < 10; i++) {
            if (i < myFileList.size()) {
                TextView txt2 = new TextView(this);
                txt2.setText("name: " + myFileList.get(i).getName() + " ["
                        +  getSizeText(myFileList.get(i).getSize()) + "]");
                shareTextBuilder.append(txt2.getText().toString()).append(System.getProperty("line.separator"));
                Log.d(TAG, txt2.getText().toString());
                infoLayout.addView(txt2);
            } else {
                break;
            }
        }
    }

    /**
     * Method to add the 5 most frequent file extensions.
     * @param extFreq
     * @param infoLayout
     */
    private void addFrequentExtension(Map<String, Integer> extFreq, LinearLayout infoLayout) {
        if (extFreq.size() == 0) {
            return;
        }
        TextView txt1 = new TextView(this);
        txt1.setText("5 MOST FREQUENT EXTENSIONS");
        txt1.setTypeface(null, Typeface.BOLD_ITALIC);
        shareTextBuilder.append(txt1.getText().toString()).append(System.getProperty("line.separator"));

        Log.d(TAG, txt1.getText().toString());
        infoLayout.addView(txt1);
        List<Map.Entry<String, Integer>> list = new LinkedList<>( extFreq.entrySet());
        int i = 0;
        for (Map.Entry<String, Integer> entry : list)
        {
            if (i < extFreq.size() && i < 5) {
                TextView txt2 = new TextView(this);
                txt2.setText("ext name: " + entry.getKey() + " [" + entry.getValue() + " occurrence]");
                shareTextBuilder.append(txt2.getText().toString()).append(System.getProperty("line.separator"));

                Log.d(TAG, txt2.getText().toString());
                infoLayout.addView(txt2);
                i += 1;
            } else {
                break;
            }

        }
    }

    /**
     * Custom ResultReceiver class to communicate with the StorageScanService.
     */
    @SuppressLint("ParcelCreator")
    class MyResultReceiver extends ResultReceiver{

        /**
         * Create a new ResultReceive to receive results.  Your
         * {@link #onReceiveResult} method will be called from the thread running
         * <var>handler</var> if given, or from an arbitrary thread if null.
         *
         * @param handler
         */
        public MyResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(final int resultCode, final Bundle resultData) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (resultCode == A_DIRECTORY_PROCESSED_ACTION) { // a directory has been processed
                        scanResultModel = resultData.getParcelable(SCAN_RESULT_MODEL);
                        progressPercentage = resultData.getFloat(PROGRESS_PERCENTAGE, 0);
                        updateUI();
                    } else if (resultCode == SCAN_STOPPED_ACTION) {
                        stopScanBtn.requestFocus(); // set stop button to active button
                    }
                }
            });
        }
    }

}
