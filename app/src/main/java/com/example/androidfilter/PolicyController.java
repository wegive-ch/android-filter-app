package com.example.androidfilter;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.net.Uri;
import android.os.UserManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PolicyController {
    static final int REQUEST_ENABLE_ADMIN = 1201;
    static final int REQUEST_ENABLE_VPN = 1202;

    private static final String[] RESTRICTED_PACKAGES = {
            "com.android.chrome",
            "com.google.android.apps.chrome",
            "com.google.android.googlequicksearchbox",
            "com.google.android.gm",
            "com.google.android.youtube",
            "com.android.vending",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.android.settings"
    };

    private final Context context;
    private final DevicePolicyManager dpm;
    private final ComponentName admin;

    PolicyController(Context context) {
        this.context = context.getApplicationContext();
        this.dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.admin = new ComponentName(context, FilterDeviceAdminReceiver.class);
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
                dpm.setLockTaskPackages(admin, new String[]{context.getPackageName()});
            } catch (RuntimeException ignored) {
                // Continue with the remaining policies if the device image rejects lock-task policy.
            }
            try {
                dpm.setAlwaysOnVpnPackage(admin, context.getPackageName(), true);
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                // The VPN must be approved before some devices accept always-on lockdown.
            }
            addUserRestrictions();
            suspendRestrictedPackages(true);
        }

        ensureBlockingVpn(activity);

        try {
            activity.startLockTask();
        } catch (RuntimeException ignored) {
            // Some devices require device-owner provisioning before lock-task mode can start.
        }
    }

    void clearPoliciesForRemoval() {
        if (dpm == null || !isAdminActive()) {
            return;
        }

        if (isDeviceOwner()) {
            try {
                dpm.setLockTaskPackages(admin, new String[]{});
                clearUserRestrictions();
                suspendRestrictedPackages(false);
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

    private void addUserRestrictions() {
        for (String restriction : managedRestrictions()) {
            try {
                dpm.addUserRestriction(admin, restriction);
            } catch (RuntimeException ignored) {
                // OEM builds can reject individual restrictions.
            }
        }
    }

    private void clearUserRestrictions() {
        for (String restriction : managedRestrictions()) {
            try {
                dpm.clearUserRestriction(admin, restriction);
            } catch (RuntimeException ignored) {
                // Continue clearing whatever the device accepts.
            }
        }
    }

    private List<String> managedRestrictions() {
        List<String> restrictions = new ArrayList<>(Arrays.asList(
                UserManager.DISALLOW_ADD_USER,
                UserManager.DISALLOW_APPS_CONTROL,
                UserManager.DISALLOW_CONFIG_BLUETOOTH,
                UserManager.DISALLOW_CONFIG_CREDENTIALS,
                UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
                UserManager.DISALLOW_CONFIG_TETHERING,
                UserManager.DISALLOW_CONFIG_VPN,
                UserManager.DISALLOW_CONFIG_WIFI,
                UserManager.DISALLOW_DEBUGGING_FEATURES,
                UserManager.DISALLOW_FACTORY_RESET,
                UserManager.DISALLOW_INSTALL_APPS,
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserManager.DISALLOW_MODIFY_ACCOUNTS,
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_SHARE_LOCATION,
                UserManager.DISALLOW_UNINSTALL_APPS
        ));
        return restrictions;
    }

    private void suspendRestrictedPackages(boolean suspended) {
        PackageManager packageManager = context.getPackageManager();
        Set<String> packages = new HashSet<>(Arrays.asList(RESTRICTED_PACKAGES));
        packages.remove(context.getPackageName());

        List<String> installed = new ArrayList<>();
        for (String packageName : packages) {
            try {
                packageManager.getPackageInfo(packageName, 0);
                installed.add(packageName);
            } catch (PackageManager.NameNotFoundException ignored) {
                // Not installed on this device.
            }
        }

        if (!installed.isEmpty()) {
            try {
                dpm.setPackagesSuspended(admin, installed.toArray(new String[0]), suspended);
            } catch (RuntimeException ignored) {
                // Package suspension support varies by package and device policy mode.
            }
        }
    }

    Intent deviceOwnerSettingsIntent() {
        return new Intent(Settings.ACTION_SETTINGS);
    }
}
