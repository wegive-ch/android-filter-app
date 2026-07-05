package com.example.androidfilter;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class FilterDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, "Device Filter admin enabled.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Open Device Filter and enter the uninstall password before removing this administrator.";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Toast.makeText(context, "Device Filter admin disabled.", Toast.LENGTH_SHORT).show();
    }
}
