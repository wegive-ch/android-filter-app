package ch.wegive.androidfilter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.IOException;

public class BlockingVpnService extends VpnService implements Runnable {
    static final String ACTION_START = "ch.wegive.androidfilter.START_BLOCKING_VPN";

    private static final String CHANNEL_ID = "filter_vpn";
    private static final int NOTIFICATION_ID = 42;

    private ParcelFileDescriptor vpnInterface;
    private Thread worker;
    private volatile boolean running;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification());
        startBlockingVpn();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopBlockingVpn();
        super.onDestroy();
    }

    @Override
    public void run() {
        byte[] packet = new byte[32767];
        try (FileInputStream input = new FileInputStream(vpnInterface.getFileDescriptor())) {
            while (running) {
                int ignored = input.read(packet);
                if (ignored < 0) {
                    break;
                }
            }
        } catch (IOException ignored) {
            // Closing the VPN interface interrupts the blocking read during shutdown.
        }
    }

    private synchronized void startBlockingVpn() {
        if (vpnInterface != null) {
            return;
        }

        try {
            Builder builder = new Builder()
                    .setSession("Device Filter")
                    .addAddress("10.111.0.1", 32)
                    .addRoute("0.0.0.0", 0)
                    .addRoute("::", 0);
            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                stopSelf();
                return;
            }
            running = true;
            worker = new Thread(this, "BlockingVpnService");
            worker.start();
        } catch (RuntimeException e) {
            stopSelf();
        }
    }

    private synchronized void stopBlockingVpn() {
        running = false;
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException ignored) {
                // Already closed.
            }
            vpnInterface = null;
        }
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    private Notification notification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Device Filter VPN",
                    NotificationManager.IMPORTANCE_LOW
            );
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(getResources().getIdentifier("icon", "mipmap", getPackageName()))
                .setContentTitle("Device Filter is on")
                .setContentText("Internet traffic is blocked.")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
}
