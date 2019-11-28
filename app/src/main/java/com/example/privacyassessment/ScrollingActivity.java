package com.example.privacyassessment;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;

import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.widget.NestedScrollView;

import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.Selection;
import android.text.SpannableString;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.StrictMath.max;


public class ScrollingActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String LOG_TAG = "OSO";
    private static final int PERMISSION_REQUEST_STORAGE_RW = 0;

    private View m_layout;
    private MenuItem m_downloadHostsItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);
        m_layout = findViewById(R.id.main_layout);
        // FIXME:
        TextView txtJournal = findViewById(R.id.txtJournal);
        txtJournal.setText("");
        txtJournal.setMovementMethod(new ScrollingMovementMethod());

        Toolbar toolbar = /*(Toolbar)*/ findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = /*(FloatingActionButton)*/ findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        // First of all, check whether we have permissions to read/write from/to "Download" directory
        if (!hasRuntimePermissions(this, m_storagePermissions)) {
            requestRuntimePermissions(m_storagePermissions);
        }
        initDownloadManager();

        // FIXME: begin test
        String testContents;
        testContents =
                "# Title: StevenBlack/hosts\n" +
                        "#\n" +
                        "# ===============================================================\n" +
                        "\n" +
                        "127.0.0.1 localhost\n" +
                        "::1 localhost\n" +
                        "fe80::1%lo0 localhost\n" +
                        "0.0.0.0 0.0.0.0\n" +
                        "\n" +
                        "# http://stevenblack.com\n" +
                        "0.0.0.0 adservice.google.com.vn\n" +
                        "0.0.0.0 jackbootedroom.com  ## phishing\n" +
                        "\n" +
                        "0.0.0.0 ssl-google-analytics.l.google.com\n" +
                        "tinypic.info\n" +
                        "\n";

        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File hostsFile = new File(combinePaths(downloadDir.getPath(), "hosts_1.txt"));
        testContents = readTextFile(hostsFile);

        // FIXME: first of all check whether Internet working!!!
        // FIXME: place this long-running code in da AsyncTask!!!

        List<String> hostsUrls = new ArrayList<>();
        if (parseHostsFile(testContents, hostsUrls)) {

            /*Log.i(LOG_TAG, "HOSTS-file '" + "hosts_1.txt" + "' was parsed successfully");
            Log.i(LOG_TAG, "HOSTS-file domain count: " + hostsUrls.size());
            //Log.i(LOG_TAG, hostsUrls.toString());

            long workingHostCount = 0;
            long blockedHostCount = 0;

            for (String host : hostsUrls) {
                try {
                    InetAddress[] hostIpList = InetAddress.getAllByName(host);
                    assert hostIpList.length > 0;
                    for (InetAddress hostIp : hostIpList) {
                        if (hostIp.isReachable(1000)) { // ms
                            workingHostCount++;
                        } else {
                            blockedHostCount++;
                        }
                    }
                } catch (UnknownHostException exc) {
                    Log.e(LOG_TAG, "IP address of a host '" + host + "' could not be determined");
                } catch (IOException exc) {
                    Log.e(LOG_TAG, "Network error occurs");
                }
            }

            Log.i(LOG_TAG, "WORKING HOSTS: " + workingHostCount);
            Log.i(LOG_TAG, "BLOCKED HOSTS: " + blockedHostCount);
            Log.i(LOG_TAG, "TOTAL HOSTS: " + hostsUrls.size());*/

            new HostsReachabilityChecker().execute(hostsUrls.toArray(new String[0]));
        } else {
            Log.e(LOG_TAG, "PARSE FAILED");
        }
        // FIXME: end test
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        destroyDownloadManager();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);

        m_downloadHostsItem = menu.findItem(R.id.action_download_files);
        assert m_downloadHostsItem != null;
        //m_downloadHostsItem.setEnabled(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void onSettingsMenuItemClicked(MenuItem item) {
        Toast.makeText(this, "Not implemented yet...", Toast.LENGTH_LONG).show();
    }

    public void onDownloadHostsFiles(MenuItem item) {
        Toast.makeText(this, "File was successfully downloaded!", Toast.LENGTH_LONG).show();

        startDownloadHostsFileFromList(getDefaultHostsFileList());
    }

    public void onViewDownloadLog(MenuItem item) {
        //startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));

        // Open the Android Download Manager
        // FIXME: do not work properly on LineageOS 16.0 + Galaxy S7:
        //        just FileManager opened in root directory
        Intent pageView = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        //pageView.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(pageView);
        } catch (ActivityNotFoundException e) {
            Log.e(LOG_TAG, "Cannot find Downloads app", e);
        }
    }

    public void onMergeAndParseHostsFiles(MenuItem item) {
        // Load, merge and parse previously downloaded HOSTS-files
        File downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        assert downloadDirectory != null;
        assert downloadDirectory.exists();
        File[] hostsFiles = downloadDirectory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                Log.e(LOG_TAG, file.getName());
                return (file.getName().startsWith("hosts_") && file.getName().endsWith(".txt"));
            }
        });
        if (hostsFiles != null) {
            Log.e(LOG_TAG, "HOSTS-file count: " + hostsFiles.length);
            showToast("HOSTS-files found: " + hostsFiles.length, Color.GREEN, Color.BLACK);

            for (File hostsFile : hostsFiles) {
                String contents = readTextFile(hostsFile);
                //
            }
        } else {
            showToast("No HOSTS-files found! Please re-download them", Color.RED, Color.BLACK);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Runtime permissions stuff
    // NOTE: runtime permissions introduced only in Android 6.0 (M, or Marshmallow);
    //       earlier versions used just AndroidManifest.xml ones

    String[] m_storagePermissions = {android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public static boolean hasRuntimePermissions(Activity activity, final String... permissions) {
        if (/*android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&*/ activity != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean needRuntimePermissionsRationale(Activity activity, final String... permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission))
                return true;
        }
        return false;
    }

    /**
     * Requests the specified permissions, each from the list {@link android.Manifest.permission}.
     * If an additional rationale should be displayed, the user has to launch the request from
     * a SnackBar that includes additional information.
     */
    public void requestRuntimePermissions(final String... permissions) {

        // Permission has not been granted and must be requested
        if (needRuntimePermissionsRationale(this, permissions)) {
            // Provide an additional rationale to the user if the permission was not granted
            // and the user would benefit from additional context for the use of the permission.
            // Display a SnackBar with cda button to request the missing permission.
            Snackbar.make(m_layout, /*R.string.camera_access_required*/ "(1) We need external storage read/write access to download/update HOSTS-files",
                    Snackbar.LENGTH_INDEFINITE).setAction(/*R.string.ok*/"Ok", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Request the permission
                    ActivityCompat.requestPermissions(ScrollingActivity.this,
                            permissions,
                            PERMISSION_REQUEST_STORAGE_RW);
                }
            }).show();

        } else {
            Snackbar.make(m_layout, /*R.string.camera_unavailable*/ "(2) We need external storage read/write access to download/update HOSTS-files",
                    Snackbar.LENGTH_SHORT).show();
            // Request the permission. The result will be received in onRequestPermissionResult().
            ActivityCompat.requestPermissions(this,
                    permissions, PERMISSION_REQUEST_STORAGE_RW);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_STORAGE_RW) {
            // Request for external storage read/write permissions
            if (grantResults.length == 2
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // Permission has been granted
                Toast.makeText(this, "External storage read/write permissions granted, thanks!", Toast.LENGTH_LONG).show();
            } else {
                // Permission request was denied: show error message, wait while user read it and exit application
                Toast.makeText(this, "External storage read/write permissions were declined, unable to work further!", Toast.LENGTH_LONG).show();

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        exitApplication();
                    }
                }, 3000);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private DownloadManager m_downloadManager;
    private List<Long> m_downloadIds = new ArrayList<>();

    private String downloadStatusMessage(Cursor c) {
        String msg = "";

        switch (c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))) {
            case DownloadManager.STATUS_FAILED:
                msg = "download FAILED";
                break;

            case DownloadManager.STATUS_PAUSED:
                msg = "download paused";
                break;

            case DownloadManager.STATUS_PENDING:
                msg = "download pending";
                break;

            case DownloadManager.STATUS_RUNNING:
                msg = "download in progress";
                break;

            case DownloadManager.STATUS_SUCCESSFUL:
                msg = "download complete";
                break;

            default:
                msg = "<objective in danger!!!>";
                break;
        }

        return msg;
    }

    private String downloadFailReason(Cursor c) {
        String msg = "";
        int reason = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON));
        switch (reason) {
            case DownloadManager.ERROR_CANNOT_RESUME:
                msg = "ERROR_CANNOT_RESUME";
                break;
            case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                msg = "ERROR_DEVICE_NOT_FOUND";
                break;
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                msg = "ERROR_FILE_ALREADY_EXISTS";
                break;
            case DownloadManager.ERROR_FILE_ERROR:
                msg = "ERROR_FILE_ERROR";
                break;
            case DownloadManager.ERROR_HTTP_DATA_ERROR:
                msg = "ERROR_HTTP_DATA_ERROR";
                break;
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                msg = "ERROR_INSUFFICIENT_SPACE";
                break;
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                msg = "ERROR_TOO_MANY_REDIRECTS";
                break;
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                msg = "ERROR_UNHANDLED_HTTP_CODE";
                break;
            case DownloadManager.ERROR_UNKNOWN:
                msg = "ERROR_UNKNOWN";
                break;
        }
        return msg;
    }

    BroadcastReceiver onComplete = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {

            // Get the DMID from the download manager
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            // Remove it from our list
            m_downloadIds.remove(downloadId);

            // if list is empty means all downloads completed

            if (m_downloadIds.isEmpty()) {
                Log.i(LOG_TAG, "All HOSTS-files were successfully downloaded!");
                showToast("All HOSTS-files were successfully downloaded!", Color.GREEN, Color.BLACK);
                m_downloadHostsItem.setEnabled(true);
            } else {
                Log.i(LOG_TAG, "Downloads in progress: " + m_downloadIds.size());

                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                Cursor c = dm.query(new DownloadManager.Query().setFilterById(downloadId));
                assert c != null;
                c.moveToFirst();

                Log.d(getClass().getName(),
                        "COLUMN_ID: "
                                + c.getLong(c.getColumnIndex(DownloadManager.COLUMN_ID)));
                Log.d(getClass().getName(),
                        "COLUMN_BYTES_DOWNLOADED_SO_FAR: "
                                + c.getLong(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));
                Log.d(getClass().getName(),
                        "COLUMN_LAST_MODIFIED_TIMESTAMP: "
                                + c.getLong(c.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)));
                Log.d(getClass().getName(),
                        "COLUMN_LOCAL_URI: "
                                + c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)));
                Log.d(getClass().getName(),
                        "COLUMN_STATUS: "
                                + c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS)));
                Log.d(getClass().getName(),
                        "COLUMN_REASON: "
                                + c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON)));
                //Log.e(LOG_TAG, "STATUS: " + downloadStatusMessage(c));
                //Log.e(LOG_TAG, "REASON: " + downloadFailReason(c));
                c.close();
            }

            /*
            m_downloadHostsItem.setEnabled(true);

            String action = intent.getAction();
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                Log.e(LOG_TAG, "FILE: " + downloadId);

                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                Cursor c = null;
                if (dm != null) {
                    c = dm.query(query);
                }
                if (c != null) {
                    while (c.moveToNext()) {
                        int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
                            // We've finished all downloads
                            showToast("All HOSTS-files were successfully downloaded!", Color.GREEN, Color.BLACK);
                            m_downloadHostsItem.setEnabled(true);
                            m_downloadIds.clear();
                        }
                    }
                }
            }
            */
        }
    };

    BroadcastReceiver onNotificationClick = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            showToast("Now you can download/update HOSTS-files, thanks!", Color.GREEN, Color.BLACK);
            m_downloadHostsItem.setEnabled(true);
        }
    };

    private void initDownloadManager() {
        m_downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        registerReceiver(onNotificationClick, new IntentFilter(DownloadManager.ACTION_NOTIFICATION_CLICKED));
    }

    private void destroyDownloadManager() {
        unregisterReceiver(onComplete);
        unregisterReceiver(onNotificationClick);
    }

    private void startFileDownload(String urlStr, String localFileName) {
        Uri uri = Uri.parse(urlStr);

        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        request.setAllowedOverRoaming(false);
        request.setTitle("Demo");
        request.setDescription("Something useful. No, really.");
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, localFileName);

        long downloadId = m_downloadManager.enqueue(request);
        m_downloadIds.add(downloadId);
        Log.i(LOG_TAG, "Enqueue download of '" + localFileName + "' with DMID = " + downloadId);

        m_downloadHostsItem.setEnabled(false);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private class HostsFileSource {
        public String m_remoteUrl;
        public String m_localFileName;

        public HostsFileSource(String remoteUrl, String localFileName) {
            m_remoteUrl = remoteUrl;
            m_localFileName = localFileName;
        }
    }

    private List<HostsFileSource> getDefaultHostsFileList() {
        // The default lists for Pi-Hole are (edited to remove a list that expired July 2019):
        List<HostsFileSource> result = new ArrayList<>();

        result.add(new HostsFileSource("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", "hosts_1.txt"));
        result.add(new HostsFileSource("https://mirror1.malwaredomains.com/files/justdomains", "hosts_2.txt"));
        // FIXME: return HTTP error 400 (Bad request) on Android 9.0 without android:usesCleartextTraffic="true" in manifest
        result.add(new HostsFileSource("http://sysctl.org/cameleon/hosts", "hosts_3.txt"));
        result.add(new HostsFileSource("https://s3.amazonaws.com/lists.disconnect.me/simple_tracking.txt", "hosts_4.txt"));
        result.add(new HostsFileSource("https://s3.amazonaws.com/lists.disconnect.me/simple_ad.txt", "hosts_5.txt"));
        result.add(new HostsFileSource("https://hosts-file.net/ad_servers.txt", "hosts_6.txt"));

        return result;
    }

    private void startDownloadHostsFileFromList(final List<HostsFileSource> hostsFileSourceList) {
        // 1) check whether we still have permissions to read/write from/to "Download" directory
        //    (user can revoke permissions at any time!)
        if (!hasRuntimePermissions(this, m_storagePermissions)) {
            Log.e(LOG_TAG, "Storage access permissions not granted, aborted!");
            Toast.makeText(getApplicationContext(), "Storage access permissions not granted, aborted!", Toast.LENGTH_LONG).show();
            return;
        }

        // 2) Ensure "Download" directory exists
        File downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadDirectory.exists()) {
            if (!downloadDirectory.mkdirs()) {
                showToast("Unable to create 'Download' directory! Please contact technical support", Color.RED, Color.BLACK);
                return;
            }
        }

        // 3) Check Internet connection and warn about big file download through cellular one
        switch (checkInternetConnection()) {
            case INVALID:
                showToast("No internet connection found! Please connect to Wi-Fi network and try again", Color.RED, Color.BLACK);
                return;
            case WIFI:
                // TODO: Wi-Fi also can have traffic limit, may be ask user in this case too?
                showToast("Wi-Fi network connected", Color.GREEN, Color.BLACK);
                doDownloadHostsFileFromList(hostsFileSourceList);
                break;
            case CELLULAR:
                // Are you sure you want to download %1$s %2$s (%3$s, %4$d files) without WiFi
                new AlertDialog.Builder(this)
                        .setTitle("Warning")
                        .setMessage("Are you sure you want to download several megabytes without Wi-Fi?")
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Do nothing
                            }
                        })
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                doDownloadHostsFileFromList(hostsFileSourceList);
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
                break;
        }
    }

    private void doDownloadHostsFileFromList(List<HostsFileSource> hostsFileSourceList) {
        assert hostsFileSourceList.size() == 6;
        for (HostsFileSource hfs : hostsFileSourceList) {
            // Remove file if already downloaded (HOSTS-files are frequently updated)
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            // Android 8.0+:
            //Path path = Paths.get("foo", "bar", "baz.txt");
            File hostsFile = new File(combinePaths(downloadDir.getPath(), hfs.m_localFileName));
            if (hostsFile.exists()) {
                Log.i(LOG_TAG, "Trying to delete HOSTS-file " + hostsFile.getAbsolutePath() + "...");
                if ((!hostsFile.delete())) throw new AssertionError();
                Log.i(LOG_TAG, "Done!");
            } else {
                Log.w(LOG_TAG, "HOSTS-file '" + hostsFile.getAbsolutePath() + "' was not found");
            }

            // Download each file to default "Downloads" directory (i.e. start download process)
            Log.i(LOG_TAG, "Start downloading '" + hfs.m_localFileName + "'...");
            startFileDownload(hfs.m_remoteUrl, hfs.m_localFileName);
        }
    }

    private boolean parseHostsFile(String fileContents, List<String> urls) {
        String[] fileContentsLines = fileContents.split("\\R+"); // "\\r?\\n"
        for (String line : fileContentsLines) {
            // Cut single-line comments (start with '#')
            int commentIndexBegin = line.indexOf('#');
            if (commentIndexBegin >= 0) {
                line = line.substring(0, commentIndexBegin);
                line = line.trim();
                if (line.isEmpty()) continue;
            }
            // NOTE: spam Logcat output, use with care
            //Log.i(LOG_TAG, "'" + line + "'");

            // Split if needed
            String domainAddress;
            String[] addressPair = line.split("\\s+");
            if (addressPair.length == 2) {
                domainAddress = addressPair[1];
            } else if (addressPair.length == 1) {
                domainAddress = addressPair[0];
            } else {
                Log.e(LOG_TAG, "Hosts parser error: invalid line '" + line + "'");
                return false;
            }

            if (isDomainRemote(domainAddress)) {
                urls.add(domainAddress);
            } else {
                Log.w(LOG_TAG, "Local domain ignored: '" + domainAddress + "'");
            }
        }
        return true;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private class HostsReachabilityChecker extends AsyncTask<String, Integer, Long> {
        private ProgressDialog m_progressDialog;


        public HostsReachabilityChecker() {
        }

        /**
         * Before starting background thread
         * Show Progress Bar Dialog
         */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            m_progressDialog = new ProgressDialog(ScrollingActivity.this);
            m_progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            m_progressDialog.setCancelable(false);
            //m_progressDialog.show();
        }

        /**
         * Downloading file in background thread
         */
        @Override
        protected Long doInBackground(String... hosts) {

            Log.i(LOG_TAG, "HOSTS-file domain count: " + hosts.length);

            long workingHostCount = 0L;
            long blockedHostCount = 0L;
            for (int i = 0; i < hosts.length; i ++) {
                try {
                    InetAddress[] hostIpList = InetAddress.getAllByName(hosts[i]);
                    assert hostIpList.length > 0;

                    //Log.i(LOG_TAG, "Domain '" + hosts[i] + "' IP addresses are " + Arrays.toString(hostIpList));
                    addLineToJournal("Domain '" + hosts[i] + "' IP addresses are " + Arrays.toString(hostIpList));

                    boolean isReachable = false;
                    for (InetAddress hostIp : hostIpList) {
                        if (hostIp.isReachable(1000 /*ms*/)) {
                            isReachable = true;
                            break;
                        }
                    }

                    if (isReachable)
                        workingHostCount++;
                    else
                        blockedHostCount++;
                } catch (UnknownHostException exc) {
                    Log.e(LOG_TAG, "IP address of a host '" + hosts[i] + "' could not be determined");
                } catch (IOException exc) {
                    Log.e(LOG_TAG, "Network error occurs");
                }

                publishProgress((int) ((i / (float) hosts.length) * 100));
                // Escape early if cancel() is called
                if (isCancelled()) break;
            }

            Log.i(LOG_TAG, "WORKING HOSTS: " + workingHostCount);
            Log.i(LOG_TAG, "BLOCKED HOSTS: " + blockedHostCount);
            Log.i(LOG_TAG, "TOTAL HOSTS: 1 = " + hosts.length + ", 2 = " + (workingHostCount + blockedHostCount));
            addLineToJournal("");
            addLineToJournal("WORKING HOSTS: " + workingHostCount);
            addLineToJournal("BLOCKED HOSTS: " + blockedHostCount);
            addLineToJournal("TOTAL HOSTS: 1 = " + hosts.length + ", 2 = " + (workingHostCount + blockedHostCount));

            return blockedHostCount;
        }

        /**
         * Updating progress bar
         */
        protected void onProgressUpdate(Integer... progress) {
            // Set progress percentage
            m_progressDialog.setProgress(progress[0]);
        }


        @Override
        protected void onPostExecute(Long result) {
            // Dismiss the dialog after the file was downloaded
            m_progressDialog.dismiss();

            // Display File path after downloading
            //Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Utils

    private enum NetworkType {INVALID, WIFI, CELLULAR}

    private NetworkType checkInternetConnection() {
        NetworkType result = NetworkType.INVALID;

        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities networkCapabilities = cm.getNetworkCapabilities(activeNetwork);
                // NET_CAPABILITY_VALIDATED - Indicates that connectivity on this network was successfully validated
                // NET_CAPABILITY_INTERNET - Indicates that this network should be able to reach the internet
                if (networkCapabilities != null) {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            result = NetworkType.WIFI;
                        } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            result = NetworkType.CELLULAR;
                        }
                    }
                }
            }
        }
        return result;
    }

    private void showToast(String message, int background, int foreground) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG /*, duration*/);
        View view = toast.getView();

        // Gets the actual oval background of the Toast then sets the colour filter
        view.getBackground().setColorFilter(background, PorterDuff.Mode.SRC_IN);

        // Gets the TextView from the Toast so it can be edited
        TextView text = view.findViewById(android.R.id.message);
        text.setTextColor(foreground);

        toast.show();
    }

    private static String combinePaths(String... paths) {
        File file = new File(paths[0]);

        for (int i = 1; i < paths.length; i++) {
            file = new File(file, paths[i]);
        }

        return file.getPath();
    }

    private static String readTextFile(File file) {
        String result = "";

        StringBuilder text = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));

            String line;
            while ((line = br.readLine()) != null) {
                //Log.i(LOG_TAG, line);
                text.append(line).append("\n");
            }
            br.close();
            result = text.toString();
        } catch (FileNotFoundException exc) {
            Log.e(LOG_TAG, "File '" + file.getAbsolutePath() + "' was not found!");
            exc.printStackTrace();
        } catch (IOException exc) {
            Log.e(LOG_TAG, "Input/output error: '" + exc.getMessage() + "'");
            exc.printStackTrace();
        }

        return result;
    }

    private <T> boolean isArrayContains(T[] array, T value) {
        for (T item : array) {
            if (item.equals(value))
                return true;
        }
        return false;
    }

    private boolean isDomainRemote(String domainAddress) {
        String[] localDomains = {
                "localhost", "localhost.localdomain",
                "local", "broadcasthost", "localhost",
                "ip6-localhost", "ip6-loopback", "ip6-localnet",
                "ip6-mcastprefix", "ip6-allnodes", "ip6-allrouters", "ip6-allhosts",
                "0.0.0.0"};
        return !isArrayContains(localDomains, domainAddress.toLowerCase());
    }

    private void exitApplication() {
        finishAffinity();
        System.exit(0);
    }

    void addLineToJournal(String text) {
        class OneShotTask implements Runnable {
            String m_text;

            OneShotTask(String text) { m_text = text; }

            public void run() {
                // Append a line to TextView
                TextView txtJournal = ScrollingActivity.this.findViewById(R.id.txtJournal);
                txtJournal.append(m_text + "\n");

                // Scroll contents to bottom
                NestedScrollView scrollView = ScrollingActivity.this.findViewById(R.id.scroll_view_1);
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        }
        runOnUiThread(new OneShotTask(text));
    }
}
