package com.example.privacyassessment;

import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
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

import android.os.PowerManager;
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
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScrollingActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    // NOTE: List of Pair<String,String> cannot be passed to vararg function,
    //       so we need to describe it as separate structure
    private static class RemoteFileDescription {
        String m_remoteUrl;
        String m_localFileName;

        RemoteFileDescription(String remoteUrl, String localFileName) {
            m_remoteUrl = remoteUrl;
            m_localFileName = localFileName;
        }
    }

    private static class RemoteFileDownloadResult {
        boolean isValid;
        String path;
        long length;
        String errorMessage;
    }

    private interface OnDownloadFinishedListener {
        void onSuccess();
        void onFailure(String message);
    }

    private interface IReportable {
        void addMessage(String message);
    }

    private static final String LOG_TAG = "OSO";

    //private View m_layout;
    private MenuItem m_downloadHostsItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_scrolling);
        //m_layout = findViewById(R.id.main_layout);

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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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
        showToast("Not implemented yet...", Color.GREEN, Color.BLACK);
    }

    public void onQuitMenuItemClicked(MenuItem item) {
        quitApplication();
    }

    public void onDownloadHostsFiles(MenuItem item) {
        showToast("Starting download...", Color.GREEN, Color.BLACK);

        startDownloadHostsFileFromList(getDefaultHostsFileList());
    }

    public void onViewDownloadLog(MenuItem item) {
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

    /**
     * Load, merge and parse previously downloaded HOSTS-files
     * @param item Menu item object
     */
    public void onMergeAndParseHostsFiles(MenuItem item) {

        // Get private storage object
        File privateStorageDir = this.getFilesDir();
        if (privateStorageDir == null || !privateStorageDir.exists()) {
            Log.e(LOG_TAG, "Hosts parser: unable to obtain Private Internal Storage object");
            return;
        }
        Log.i(LOG_TAG, String.format("Hosts parser: Private Internal Storage path is '%s'",
                privateStorageDir.getAbsolutePath()));

        // List only HOSTS-files
        File[] hostsFiles = privateStorageDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                Log.e(LOG_TAG, file.getName());
                return (file.getName().startsWith("hosts_") && file.getName().endsWith(".txt"));
            }
        });
        if (hostsFiles == null || hostsFiles.length == 0) {
            Log.e(LOG_TAG, "No HOSTS-files found! Please re-download them");
            showToast("No HOSTS-files found! Please re-download them", Color.RED, Color.BLACK);
            return;
        }

        Log.e(LOG_TAG, "HOSTS-file found: " + hostsFiles.length);
        showToast("HOSTS-files found: " + hostsFiles.length, Color.GREEN, Color.BLACK);

        Log.v(LOG_TAG, "Starting HOSTS-files merging into big one...");
        StringBuilder finalHostsFile = new StringBuilder();
        for (File hostsFile : hostsFiles) {
            Log.i(LOG_TAG, String.format("Merge HOSTS-file '%s' with length %d Kb",
                    hostsFile.getAbsolutePath(),
                    (int)((float)hostsFile.length()/1024)));

            String contents = readTextFile(hostsFile);
            finalHostsFile.append(contents).append("\n\n\n### OSO MERGED ###\n\n\n");
        }

        if (finalHostsFile.length() == 0) {
            Log.e(LOG_TAG, "Merged HOSTS-file is empty!");
            showToast("Merged HOSTS-file is empty!", Color.RED, Color.BLACK);
            return;
        }
        Log.v(LOG_TAG, "HOSTS-files were successfully merged");

        Log.v(LOG_TAG, "Starting parse of merged HOSTS-file...");
        List<String> hostsUrls = new ArrayList<>();
        if (parseHostsFile(finalHostsFile.toString(), hostsUrls)) {
            Log.i(LOG_TAG, String.format("Merged HOSTS-file was parsed successfully: %d records found", hostsUrls.size()));

            HostsAvailabilityChecker task = new HostsAvailabilityChecker(ScrollingActivity.this, m_reportable);
            task.execute(hostsUrls.toArray(new String[0]));
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private List<RemoteFileDescription> getDefaultHostsFileList() {
        // The default lists for Pi-Hole are (edited to remove a list that expired July 2019):
        List<RemoteFileDescription> result = new ArrayList<>();

        result.add(new RemoteFileDescription("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", "hosts_1.txt"));
        result.add(new RemoteFileDescription("https://mirror1.malwaredomains.com/files/justdomains", "hosts_2.txt"));
        // FIXME: return HTTP error 400 (Bad request) on Android 9.0 without android:usesCleartextTraffic="true" in manifest
        result.add(new RemoteFileDescription("http://sysctl.org/cameleon/hosts", "hosts_3.txt"));
        result.add(new RemoteFileDescription("https://s3.amazonaws.com/lists.disconnect.me/simple_tracking.txt", "hosts_4.txt"));
        result.add(new RemoteFileDescription("https://s3.amazonaws.com/lists.disconnect.me/simple_ad.txt", "hosts_5.txt"));
        result.add(new RemoteFileDescription("https://hosts-file.net/ad_servers.txt", "hosts_6.txt"));

        return result;
    }

    private void startDownloadHostsFileFromList(final List<RemoteFileDescription> hostsFileSourceList) {

        // Check Internet connection and warn about big file download through cellular one
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

    private void doDownloadHostsFileFromList(List<RemoteFileDescription> hostsFileSourceList) {
        m_downloadHostsItem.setEnabled(false);

        DownloadHelperTask task = new DownloadHelperTask( ScrollingActivity.this, m_onDownloadFinishedListener);
        task.execute(hostsFileSourceList.toArray(new RemoteFileDescription[0]));
    }

    protected OnDownloadFinishedListener m_onDownloadFinishedListener = new OnDownloadFinishedListener() {
        public void onSuccess() {
            AlertDialog.Builder builder = new AlertDialog.Builder(ScrollingActivity.this);
            builder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            builder.setMessage("Success");
            builder.show();
        }

        public void onFailure(String message) {
            AlertDialog.Builder builder = new AlertDialog.Builder(ScrollingActivity.this);
            builder.setPositiveButton("Okay", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            builder.setMessage("Failure. Message: " + message);
            builder.show();
        }
    };

    protected IReportable m_reportable = new IReportable() {
        @Override
        public void addMessage(String message) {
            class OneShotTask implements Runnable {
                private String m_text;

                private OneShotTask(String text) { m_text = text; }

                public void run() {
                    // Append a line to TextView
                    TextView txtJournal = ScrollingActivity.this.findViewById(R.id.txtJournal);
                    txtJournal.append(m_text + "\n");

                    // Scroll contents to bottom
                    NestedScrollView scrollView = ScrollingActivity.this.findViewById(R.id.scroll_view_1);
                    scrollView.fullScroll(View.FOCUS_DOWN);
                }
            }

            runOnUiThread(new OneShotTask(message));
        }
    };

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

    /**
     * Helper class for async download of several files to application private storage.
     * Returns number of files that were actually downloaded
     *
     * NOTE:
     * Usually subclasses of AsyncTask are declared inside the activity class.
     * That way you can easily modify the UI thread from here
     */
    private static class DownloadHelperTask extends AsyncTask<RemoteFileDescription, Integer, RemoteFileDownloadResult[]> {

        private PowerManager.WakeLock m_wakeLock;
        private ProgressDialog m_progressDialog;
        private WeakReference<Context> m_contextRef;
        private OnDownloadFinishedListener m_responder;
        private int m_remoteFileCount;

        DownloadHelperTask(Context context, OnDownloadFinishedListener responder) {
            m_contextRef = new WeakReference<>(context);
            m_responder = responder;

            Log.i(LOG_TAG, "Downloader: init");
        }

        @Override
        protected RemoteFileDownloadResult[] doInBackground(RemoteFileDescription... urlStrings) {
            m_remoteFileCount = urlStrings.length;

            Log.i(LOG_TAG, String.format("Downloader: starting downloading of %d URLs...", urlStrings.length));

            List<RemoteFileDownloadResult> result = new ArrayList<>();

            // First we need to check remote file availability at all,
            // and query sizes to display download percentage later
            List<RemoteFileDescription> availableUrlStrings = new ArrayList<>();
            int totalFileLength = 0;
            for (RemoteFileDescription urlString : urlStrings) {
                RemoteFileInfo rfi = getRemoteFileInfo(urlString.m_remoteUrl);
                if (rfi.isReachable) {
                    availableUrlStrings.add(urlString);

                    if (rfi.fileLength > 0) {
                        totalFileLength += rfi.fileLength;
                        Log.i(LOG_TAG, String.format("Downloader: remote file '%s' length is %d bytes",
                                urlString.m_remoteUrl, rfi.fileLength));
                    } else {
                        // FIXME: hack! just to show progress bar, assume our file have 2 Mb size;
                        //        HOSTS-file rarely exceed that size
                        Log.e(LOG_TAG,  String.format("Downloader: remote file '%s' length is not known",
                                urlString.m_remoteUrl));
                        totalFileLength += 2_000_000;
                    }
                } else {
                    Log.e(LOG_TAG, String.format("Downloader: remote file '%s' is not available",
                            urlString.m_remoteUrl));
                }
            }

            if (availableUrlStrings.size() != urlStrings.length) {
                Log.w(LOG_TAG, String.format("Downloader: only %d URLs are available now from %d specified",
                        availableUrlStrings.size(), urlStrings.length));
            } else {
                Log.i(LOG_TAG, "Downloader: all URLs are available");
            }

            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;

            for (int i = 0; i < availableUrlStrings.size(); i++) {
                try {
                    RemoteFileDescription remoteFileDescription = availableUrlStrings.get(i);
                    RemoteFileDownloadResult tempResult = new RemoteFileDownloadResult();

                    URL url = new URL(remoteFileDescription.m_remoteUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.connect();

                    // We expect HTTP 200 OK, so we don't mistakenly save error report instead of the file
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        throw new IOException("Server returned HTTP" +
                                " " + connection.getResponseCode() + " " +
                                connection.getResponseMessage());
                    }

                    // Download the file
                    input = connection.getInputStream();
                    // NOTE: uncomment to save file to External Storage (need appropriate permissions)
                    //output = new FileOutputStream("/sdcard/file_name.extension");
                    Context context = m_contextRef.get();
                    if (context == null) {
                        throw new IOException("Context is not available: unable to create file in internal storage");
                    }
                    output = context.openFileOutput(remoteFileDescription.m_localFileName, Context.MODE_PRIVATE);
                    Log.i(LOG_TAG, String.format("Downloader: empty file '%s' was created in Internal Storage",
                            remoteFileDescription.m_localFileName));

                    byte[] data = new byte[4096];
                    long actualFileLength = 0;
                    int bytesRead;
                    while ((bytesRead = input.read(data)) != -1) {
                        // Allow canceling download with back button
                        if (isCancelled()) {
                            input.close();
                            return null;
                        }
                        actualFileLength += bytesRead;

                        // Copy chunk of data to output file
                        output.write(data, 0, bytesRead);

                        // Publishing the progress
                        publishProgress((int) ((actualFileLength / (float) totalFileLength) * 100));
                    }
                    output.close();
                    input.close();

                    Log.i(LOG_TAG, String.format("Downloader: downloaded file '%s' of %d bytes long",
                            remoteFileDescription.m_localFileName, actualFileLength));

                    tempResult.path = remoteFileDescription.m_localFileName;
                    tempResult.length = actualFileLength;
                    tempResult.errorMessage = "";
                    tempResult.isValid = true;

                    result.add(tempResult);
                } catch (IOException e) {
                    Log.e(LOG_TAG, e.toString());
                    return null;
                } finally {
                    try {
                        if (output != null)
                            output.close();
                        if (input != null)
                            input.close();
                    } catch (IOException ignored) {
                    }

                    if (connection != null)
                        connection.disconnect();
                }
            }

            return result.toArray(new RemoteFileDownloadResult[0]);
        }

        /**
         * Before starting background thread
         * Show Progress Bar Dialog
         */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            // Take CPU lock to prevent CPU from going off if the user presses the power button during download
            Context context = m_contextRef.get();
            if (context == null) {
                Log.e(LOG_TAG, "Context is not available: unable to create download progress dialog");
                return;
            }
            PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            if (pm == null) {
                Log.e(LOG_TAG, "PowerManager is not available: unable to create download progress dialog");
                return;
            }
            m_wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
            m_wakeLock.acquire(600_000); // 10 minutes == 600 000 milliseconds

            m_progressDialog = new ProgressDialog(context);
            m_progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            m_progressDialog.setCancelable(false);
            m_progressDialog.setIndeterminate(false);
            m_progressDialog.setMax(100);
            m_progressDialog.show();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);

            m_progressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(RemoteFileDownloadResult[] result) {
            m_wakeLock.release();

            // Dismiss the dialog after the file was downloaded
            if (m_progressDialog.isShowing())
                m_progressDialog.dismiss();

            if (result.length == m_remoteFileCount) {
                Log.i(LOG_TAG, "Downloader: successfully downloaded " + result.length + " files");
                m_responder.onSuccess();
            } else {
                Log.e(LOG_TAG, "Downloader: no files were downloaded");
                m_responder.onFailure("Error");
            }
        }

        private class RemoteFileInfo
        {
            boolean isReachable = false;
            int fileLength = -1;
        }

        private RemoteFileInfo getRemoteFileInfo(String urlString) {
            RemoteFileInfo rfi = new RemoteFileInfo();

            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // We expect HTTP 200 OK, so we don't mistakenly save error report instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.e(LOG_TAG, "Server returned HTTP" +
                            " " + connection.getResponseCode() + " " +
                            connection.getResponseMessage());
                    rfi.isReachable = false;
                    rfi.fileLength = -1;
                    return rfi;
                }

                // NOTE: might be -1 if server did not report the length
                rfi.fileLength = connection.getContentLength();
                rfi.isReachable = true;
            } catch (IOException exc) {
                Log.e(LOG_TAG, "Unable to get remote file length");
                exc.printStackTrace();
            } finally {
                if (connection != null)
                    connection.disconnect();
            }
            return rfi;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class HostsAvailabilityChecker extends AsyncTask<String, Integer, Long> {

        private PowerManager.WakeLock m_wakeLock;
        private ProgressDialog m_progressDialog;
        private WeakReference<Context> m_contextRef;
        private IReportable m_reportable;


        HostsAvailabilityChecker(@NonNull Context context, @NonNull IReportable reportable) {
            m_contextRef = new WeakReference<>(context);
            m_reportable = reportable;

            Log.i(LOG_TAG, "Checker: init");
        }


        /**
         * Before starting background thread
         * Show Progress Bar Dialog
         */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            // Take CPU lock to prevent CPU from going off if the user presses the power button during download
            Context context = m_contextRef.get();
            if (context == null) {
                Log.e(LOG_TAG, "Context is not available: unable to create download progress dialog");
                return;
            }
            PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            if (pm == null) {
                Log.e(LOG_TAG, "PowerManager is not available: unable to create download progress dialog");
                return;
            }
            m_wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
            m_wakeLock.acquire(600_000); // 10 minutes == 600 000 milliseconds

            m_progressDialog = new ProgressDialog(context);
            m_progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            m_progressDialog.setCancelable(false);
            m_progressDialog.setIndeterminate(false);
            m_progressDialog.setMax(100);
            m_progressDialog.show();
        }

        /**
         * Checking whether hosts are available in background thread
         */
        @Override
        protected Long doInBackground(String... hosts) {

            Log.i(LOG_TAG, "HOSTS-file domain count: " + hosts.length);

            long obsoleteHostCount = 0L;
            long actualHostCount = 0L;
            long workingHostCount = 0L;
            long blockedHostCount = 0L;
            for (int i = 0; i < hosts.length; i ++) {
                try {
                    InetAddress[] hostIpList = InetAddress.getAllByName(hosts[i]);
                    if (hostIpList.length == 0)
                        throw new UnknownHostException(hosts[i]);

                    actualHostCount++;
                    //Log.i(LOG_TAG, "Domain '" + hosts[i] + "' IP addresses are " + Arrays.toString(hostIpList));
                    m_reportable.addMessage("Domain '" + hosts[i] + "' IP addresses are " + Arrays.toString(hostIpList));

                    boolean isReachable = false;
                    for (InetAddress hostIp : hostIpList) {
                        if (hostIp.isReachable(1000)) { // milliseconds, i.e. 1 second
                            isReachable = true;
                            break;
                        }
                    }

                    if (isReachable)
                        workingHostCount++;
                    else
                        blockedHostCount++;
                } catch (UnknownHostException exc) {
                    //Log.e(LOG_TAG, "IP address of a host '" + hosts[i] + "' could not be determined");
                    m_reportable.addMessage("IP address of a host '" + hosts[i] + "' could not be determined");
                    obsoleteHostCount++;
                } catch (IOException exc) {
                    Log.e(LOG_TAG, "Network error occurs");
                    m_reportable.addMessage("Network error occurs");
                }

                publishProgress((int) ((i / (float) hosts.length) * 100));
                // Escape early if cancel() is called
                if (isCancelled()) break;
            }

            Log.i(LOG_TAG, "");
            Log.i(LOG_TAG, "OBSOLETE HOSTS: " + obsoleteHostCount);
            Log.i(LOG_TAG, "ACTUAL HOSTS: " + actualHostCount);
            Log.i(LOG_TAG, "WORKING HOSTS: " + workingHostCount);
            Log.i(LOG_TAG, "BLOCKED HOSTS: " + blockedHostCount);
            Log.i(LOG_TAG, "TOTAL HOSTS: 1 = " + hosts.length + ", 2 = " + (workingHostCount + blockedHostCount));
            m_reportable.addMessage("");
            m_reportable.addMessage("OBSOLETE HOSTS: " + obsoleteHostCount);
            m_reportable.addMessage("ACTUAL HOSTS: " + actualHostCount);
            m_reportable.addMessage("WORKING HOSTS: " + workingHostCount);
            m_reportable.addMessage("BLOCKED HOSTS: " + blockedHostCount);
            m_reportable.addMessage("TOTAL HOSTS: 1 = " + hosts.length + ", 2 = " + (workingHostCount + blockedHostCount));

            return blockedHostCount;
        }

        /**
         * Updating progress bar
         */
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);

            // Set progress percentage
            m_progressDialog.setProgress(progress[0]);
        }


        @Override
        protected void onPostExecute(Long result) {
            m_wakeLock.release();

            // Dismiss the dialog after the file was downloaded
            if (m_progressDialog.isShowing())
                m_progressDialog.dismiss();

            if (result == 0) {
                Log.i(LOG_TAG, "Checker: ok");
                //m_responder.onSuccess();
            } else {
                Log.e(LOG_TAG, "Checker: fail");
                //m_responder.onFailure("Error");
            }
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

    private void quitApplication() {
        finishAffinity();
        System.exit(0);
    }
}
