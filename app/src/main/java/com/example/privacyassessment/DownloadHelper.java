package com.example.privacyassessment;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Async Task to download file from URL
 *
 * NOTE: applications targeting the Honeycomb SDK or higher MUST NOT attempt to perform a networking operation on its main thread!
 * Otherwise one got special kind of exception, NetworkOnMainThreadException -
 * https://developer.android.com/reference/android/os/NetworkOnMainThreadException.html
 */
public class DownloadHelper extends AsyncTask<URL, Integer, Long> {
    // FIXME: make common const
    private static final String TAG = "OSO"; //MainActivity.class.getSimpleName();

    private Activity m_activity;

    private ProgressDialog m_progressDialog;
    private String m_fileName;
    private String m_folder;
    private boolean m_wasDownloaded;

    // AppCompatActivity activity

    public DownloadHelper(Activity activity)
    {
        m_activity = activity;
    }

    /**
     * Before starting background thread
     * Show Progress Bar Dialog
     */
    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        m_progressDialog = new ProgressDialog(m_activity);
        m_progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        m_progressDialog.setCancelable(false);
        m_progressDialog.show();
    }

    /**
     * Downloading file in background thread
     */
    @Override
    protected Long doInBackground(URL... urls) {
        try {
            int count = urls.length;
            long totalSize = 0;
            for (int i = 0; i < count; i++) {
                //totalSize += Downloader.downloadFile(urls[i]);
                publishProgress((int) ((i / (float) count) * 100));
                // Escape early if cancel() is called
                if (isCancelled()) break;
            }
            return totalSize;


        } catch (Exception e) {
            Log.e("Error: ", e.getMessage());
        }

        return 0L;
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
