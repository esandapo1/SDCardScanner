package com.android.project.sdcardscanner;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.os.ResultReceiver;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.util.Collection;

public class StorageScanService extends Service {

    private static final int SD_FOUND_ACTION = 1;
    private static final int SCAN_PAUSED_ACTION = 3;
    private static final int SCAN_STOPPED_ACTION = 5;
    private static final int SCAN_RESUMED_ACTION = 4;
    private static final int A_DIRECTORY_PROCESSED_ACTION = 2;
    private static final int SD_NOT_FOUND_ACTION = 0;
    private static final int NOTIFICATION_ID = 1;
    private static final int PROGRESS_COMPLETE_SCORE = 100;

    private static final String PAUSE_SCAN_ACTION = "pause_scan_action";
    private static final String RESUME_SCAN_ACTION = "resume_scan_action";
    private static final String START_SCAN_ACTION = "start_scan_action";
    private static final String STOP_SCAN_ACTION = "stop_scan_action";
    private static final String TAG = "StorageScanService";
    private static final String SCAN_FOR_DIRECTORIES = "scan_for_directories";
    private static final String SCAN_FOR_FILES = "scan_directories_for_files";
    private static final String DIRECTORIES_FOUND_ACTION = "directories_found";
    private static final String NUMBER_OF_DIRECTORIES = "number_of_directories";
    private static final java.lang.String SCAN_SERVICE_THREAD = "scan_service_thread";
    private static final String PROGRESS_PERCENTAGE = "progress_percentage";
    private static final String SCAN_RESULT_MODEL = "scan_result_model";

    private boolean isPaused = false;
    private boolean isScanning = false;

    private String currentScanStep = null;

    private int currentDirectoryIndex = 0;
    private int directoryProcessedCount = 0;

    private Collection<File> directories = null;

    private ScanResultModel scanResultModel = null;

    private HandlerThread mHandlerThread;
    private Handler mHandler;

    private ResultReceiver resultReceiver;
    private boolean stopScanService = false;

    private NotificationCompat.Builder mBuilder;
    private NotificationManager mNotificationManager;


    public StorageScanService() {

    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        showScanNotification();
        resultReceiver = intent.getParcelableExtra("receiver");
        final String action = intent.getAction();
        if (START_SCAN_ACTION.equals(action)) {
            if (!isScanning) { // prevent call to startScan if scan is in progress.
                startScan();
            }
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        currentScanStep = SCAN_FOR_DIRECTORIES;
        scanResultModel = new ScanResultModel();
        IntentFilter filter = new IntentFilter();
        filter.addAction(PAUSE_SCAN_ACTION);
        filter.addAction(RESUME_SCAN_ACTION);
        filter.addAction(STOP_SCAN_ACTION);
        registerReceiver(receiver, filter);
        mHandlerThread = new HandlerThread(SCAN_SERVICE_THREAD);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(receiver);
    }

    /**
     * Handle action to start sd card scanning.
     */
    private void startScan() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Staring file scanner.....");
                isScanning = true; //prevent new scan execution in onStartCommand when scan is in progress.
                try {
                    while (currentScanStep.equals(SCAN_FOR_DIRECTORIES)) {
                        final String rawExternalStorage = System.getenv("EXTERNAL_STORAGE");
                        if (!TextUtils.isEmpty(rawExternalStorage)) {
                            Log.d(TAG, "Found SD Card.....");
                            scanForDirs();
                        } else {
                            sendUpdate(SD_NOT_FOUND_ACTION);
                            stopScanner("Did not find any SD Card.....");
                            break;
                        }
                    }
                    while (currentScanStep.equals(SCAN_FOR_FILES)) {
                        if (directories == null || directories.size() == 0) {
                            stopScanner("No directories found...");
                            break;
                        } else if (isPaused) {
                            Log.d(TAG, "Scanning paused.....");
                            break;
                        } else if (isScanDone()) {
                            stopScanner("All files have been scanned...");
                            break;
                        } else if (stopScanService) {
                            stopScanner(null);
                            break;
                        }
                        scanForFilesInDir();
                    }
                } catch (Exception e) {
                    Log.e(TAG,"File error:",e);
                }
            }
        });
    }

    /**
     * Show scan status in the notification bar.
     */
    private void showScanNotification() {
        mBuilder = new NotificationCompat.Builder(this);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setContentTitle("SD Card Scanner")
                .setContentText("Scan in progress")
                .setSmallIcon(R.mipmap.ic_launcher);;
        mBuilder.setProgress(PROGRESS_COMPLETE_SCORE, 0, false);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    /**
     * Stop the scan service.
     * @param msg
     */
    private void stopScanner(String msg){
        if(!TextUtils.isEmpty(msg)){
            Log.d(TAG, msg);
        }
        Log.d(TAG, "Scan Service stopped.....");
        resultReceiver.send(SCAN_STOPPED_ACTION, null);
        mHandlerThread = null;
        mHandler = null;
        stopSelf();
    }

    /**
     * Scan external storage for Directories.
     * @throws Exception
     */
    private void scanForDirs() throws Exception {
        Collection<File> dirs;
        if (!isPaused) {
            Log.d(TAG, "Scanning for directories.....");
            dirs = FileUtils.listFilesAndDirs(new File(System.getenv("EXTERNAL_STORAGE")),
                    new NotFileFilter(TrueFileFilter.INSTANCE), DirectoryFileFilter.DIRECTORY);
            directories = dirs;
            Log.d(TAG, dirs.size() + " directories found.....");
            Intent intent = new Intent();
            intent.setAction(DIRECTORIES_FOUND_ACTION);
            intent.putExtra(NUMBER_OF_DIRECTORIES, dirs.size());
            sendBroadcast(intent);
            currentScanStep = SCAN_FOR_FILES;
        } else {
            Log.d(TAG, "Scanning paused.....");
            sendUpdate(SCAN_PAUSED_ACTION);
        }
    }

    /**
     * Scan known directories for files.
     * @throws Exception
     */
    private void scanForFilesInDir() throws Exception {
        while (currentDirectoryIndex < directories.size()) {
            try {
                Thread.sleep(200);
            } catch(InterruptedException e) {
                Log.e(TAG, "Error... " + e);
            }
            if (isPaused || stopScanService) {
                break;
            }

            File dir = (File) directories.toArray()[currentDirectoryIndex];
            Log.d(TAG, "Scanning for files inside " + dir.getName() + " directory");
            Collection<File> files = FileUtils.listFiles(dir, null, false);
            Log.d(TAG, files.size() + " files found.....");
            scanResultModel.addFilesAndCompute(files);
            directoryProcessedCount += 1;
            sendCurrentProgress(scanResultModel);
            currentDirectoryIndex++;
        }
    }

    /**
     * Send the result of the files retrieved from a directory and update the notification progress status.
     * @param scanResultModel
     */
    private void sendCurrentProgress(ScanResultModel scanResultModel) {
        float completed = (float) directoryProcessedCount / directories.size() * 100;
        Log.d(TAG, completed + "% complete...");
        Bundle bundle = new Bundle();
        bundle.putParcelable(SCAN_RESULT_MODEL, scanResultModel);
        bundle.putFloat(PROGRESS_PERCENTAGE, completed);
        resultReceiver.send(A_DIRECTORY_PROCESSED_ACTION, bundle);
        if (completed >= PROGRESS_COMPLETE_SCORE) {
            mBuilder.setContentText("Scan completed")
                    // Removes the progress bar
                    .setProgress(0,0,false);
        } else {
            mBuilder.setContentText((int)completed + "% complete...");
            mBuilder.setProgress(PROGRESS_COMPLETE_SCORE, (int)completed, false);
        }

        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private boolean isScanDone() {
        Log.d(TAG, "current index is " + currentDirectoryIndex);
        return currentDirectoryIndex >= directories.size();
    }

    /**
     * Send update to the caller.
     * @param action
     */
    private void sendUpdate(int action) {
        resultReceiver.send(action, null);
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (PAUSE_SCAN_ACTION.equals(action)) {
                isPaused = true;
            } else if (RESUME_SCAN_ACTION.equals(action)) {
                if (!isPaused) { // don't do anything is scan is on going.
                    return;
                }
                isPaused = false;
                startScan();
            } else if (STOP_SCAN_ACTION.equals(action)) {
                stopScanService = true;
            }
        }
    };
}
