package ch.wegive.androidfilter;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.net.Uri;

final class PolicyController {
    static final int REQUEST_ENABLE_ADMIN = 1201;
    static final int REQUEST_ENABLE_VPN = 1202;

    private final Context context;
    private final DevicePolicyManager dpm;
    private final ComponentName admin;

    PolicyController(Context context) {
        this.context = context.getApplicationContext();
        this.dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.admin = new ComponentName(context, WeGiveFilterDeviceAdminReceiver.class);
    }

    boolean isAdminActive() {
        return dpm != null && dpm.isAdminActive(admin);
    }

    boolean isDeviceOwner() {
        return dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());
    }

    void requestAdmin(Activity activity) {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Device Filter needs administrator access to keep the filter active until the password is entered.");
        activity.startActivityForResult(intent, REQUEST_ENABLE_ADMIN);
    }

    void applyFilter(Activity activity) {
        if (dpm == null || !isAdminActive()) {
            requestAdmin(activity);
            return;
        }

        if (isDeviceOwner()) {
            try {
                dpm.setAlwaysOnVpnPackage(admin, context.getPackageName(), true);
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                // The VPN must be approved before some devices accept always-on lockdown.
            }
        }

        ensureBlockingVpn(activity);
    }

    void clearPoliciesForRemoval() {
        if (dpm == null || !isAdminActive()) {
            return;
        }

        if (isDeviceOwner()) {
            try {
                dpm.setAlwaysOnVpnPackage(admin, null, false);
                dpm.clearDeviceOwnerApp(context.getPackageName());
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                // Android may reject owner clearing on some managed builds; removal will then need ADB or EMM tooling.
            }
        }

        context.stopService(new Intent(context, BlockingVpnService.class));

        try {
            dpm.removeActiveAdmin(admin);
        } catch (RuntimeException ignored) {
            // Keep going so Android can show the normal uninstall flow if policy removal already happened.
        }
    }

    void requestUninstall(Activity activity) {
        Uri packageUri = Uri.parse("package:" + context.getPackageName());
        Intent intent = new Intent(Intent.ACTION_DELETE, packageUri);
        activity.startActivity(intent);
    }

    void ensureBlockingVpn(Activity activity) {
        Intent prepareIntent = VpnService.prepare(activity);
        if (prepareIntent != null) {
            activity.startActivityForResult(prepareIntent, REQUEST_ENABLE_VPN);
            return;
        }
        Intent serviceIntent = new Intent(activity, BlockingVpnService.class)
                .setAction(BlockingVpnService.ACTION_START);
        activity.startForegroundService(serviceIntent);
    }
}
