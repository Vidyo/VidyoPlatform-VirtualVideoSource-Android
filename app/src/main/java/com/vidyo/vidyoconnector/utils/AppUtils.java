package com.vidyo.vidyoconnector.utils;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.FileProvider;

import com.vidyo.vidyoconnector.BuildConfig;

import java.io.File;
import java.util.List;

public class AppUtils {

    private static final String LOGS_FOLDER = "VidyoConnectorLogs";
    private static final String LOG_FILE = "VidyoConnectorLog.log";

    /**
     * Log file is create individually for every session
     *
     * @param context {@link Context}
     * @return log file path
     */
    public static String configLogFile(Context context) {
        File cacheDir = context.getCacheDir();
        File logDir = new File(cacheDir, LOGS_FOLDER);
        deleteRecursive(logDir);

        File logFile = new File(logDir, LOG_FILE);
        logFile.mkdirs();

        String[] logFiles = logDir.list();
        if (logFiles != null)
            for (String file : logFiles) Logger.i(AppUtils.class, "Cached log file: " + file);

        return logFile.getAbsolutePath();
    }

    /**
     * Expose log file URI for sharing.
     *
     * @param context {@link Context}
     * @return log file uri.
     */
    private static Uri logFileUri(Context context) {
        File cacheDir = context.getCacheDir();
        File logDir = new File(cacheDir, LOGS_FOLDER);
        File logFile = new File(logDir, LOG_FILE);

        if (!logFile.exists()) return null;

        return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".file.provider", logFile);
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }

    /**
     * Send email with log file
     */
    public static void sendLogs(Context context) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Vidyo Connector Sample Logs");
        intent.putExtra(Intent.EXTRA_TEXT, "Logs attached..." + additionalInfo());

        intent.putExtra(Intent.EXTRA_STREAM, logFileUri(context));

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(Intent.createChooser(intent, "Choose sender..."));
        } catch (Exception sendReportEx) {
            sendReportEx.printStackTrace();
        }
    }

    private static String additionalInfo() {
        return "\n\nModel: " + Build.MODEL +
                "\n" + "Manufactured: " + Build.MANUFACTURER +
                "\n" + "Brand: " + Build.BRAND +
                "\n" + "Android OS version: " + Build.VERSION.RELEASE +
                "\n" + "Hardware : " + Build.HARDWARE +
                "\n" + "SDK Version : " + Build.VERSION.SDK_INT;
    }

    public static boolean isLandscape(Resources resources) {
        return resources.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static <T> void dump(List<T> list) {
        for (T t : list) Logger.i("Item: %s", t.toString());
    }
}