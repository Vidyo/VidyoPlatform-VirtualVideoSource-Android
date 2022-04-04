package com.vidyo.vidyoconnector.share;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.vidyo.vidyoconnector.R;
import com.vidyo.vidyoconnector.utils.Logger;

public class ShareService extends Service {

    class ShareServiceBinder extends Binder {

        void provideManager(ShareManager shareManager) {
            ShareService.this.shareManager = shareManager;
        }

        void showNotification() {
            ShareService.this.startAsForeground();
        }
    }

    public static void startShareService(Activity activity, ServiceConnection connection) {
        Intent startServiceIntent = new Intent(activity, ShareService.class);

        ContextCompat.startForegroundService(activity, startServiceIntent);

        activity.bindService(startServiceIntent, connection, BIND_AUTO_CREATE);
    }

    public static void releaseShareService(Activity activity) {
        Intent serviceIntent = new Intent(activity, ShareService.class);
        serviceIntent.setAction(SHARE_RELEASE_ACTION);
        activity.startService(serviceIntent);
    }

    public static final String SHARE_RELEASE_ACTION = "share.release.action";

    public static final String SHARE_CHANNEL_ID = "SHARE_CHANNEL_ID";

    private static final int SHARE_NOTIFICATION_ID = 0x143;

    private final IBinder shareServiceBinder = new ShareServiceBinder();
    private ShareManager shareManager;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();

        if (action != null) {
            Logger.i("Share service action: " + action);

            if (SHARE_RELEASE_ACTION.equals(action)) {
                stopShareService();
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (shareManager != null) {
            shareManager.tryUpdateShareOrientation();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return shareServiceBinder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Logger.i("Task was removed from stack. Clean all.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.i("Share service destroyed.");
    }

    private void startAsForeground() {
        Notification notification = createNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SHARE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(SHARE_NOTIFICATION_ID, notification);
        }
    }

    private void stopShareService() {
        stopForeground(true);
        stopSelf();
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(getApplicationContext(), SHARE_CHANNEL_ID)
                .setPriority(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? NotificationManager.IMPORTANCE_MAX : Notification.PRIORITY_MAX)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText("Share in progress...")
                .setSmallIcon(R.drawable.ic_share_notification_icon)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(SHARE_CHANNEL_ID, "Share notification channel", NotificationManager.IMPORTANCE_DEFAULT);
            serviceChannel.setSound(null, null);
            serviceChannel.enableVibration(false);
            serviceChannel.enableLights(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }
}