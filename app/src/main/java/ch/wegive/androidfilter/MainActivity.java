package ch.wegive.androidfilter;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    private PolicyController policyController;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        policyController = new PolicyController(this);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (PasswordStore.isSetup(this)) {
            policyController.applyFilter(this);
        }
    }

    private void render() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(32));
        setContentView(root);

        addIcon();
        if (PasswordStore.isSetup(this)) {
            renderActiveState();
        } else {
            renderSetupState();
        }
    }

    private void renderSetupState() {
        TextView title = addText("Welcome to the app. Please set your password.", 22, true);
        title.setGravity(Gravity.CENTER);

        EditText passwordInput = passwordInput("Password");
        Button saveButton = button("Set password");
        saveButton.setOnClickListener(view -> {
            String password = passwordInput.getText().toString();
            if (password.length() < 6) {
                toast("Use at least 6 characters.");
                return;
            }
            PasswordStore.saveInitialPassword(this, password);
            policyController.applyFilter(this);
            root.removeAllViews();
            addIcon();
            addText("Succeeded. The filter is on.", 22, true).setGravity(Gravity.CENTER);
        });

        root.addView(passwordInput);
        root.addView(saveButton);
    }

    private void renderActiveState() {
        TextView title = addText("The filter is on.", 24, true);
        title.setGravity(Gravity.CENTER);

        if (!policyController.isAdminActive()) {
            Button enableAdmin = button("Enable device admin");
            enableAdmin.setOnClickListener(view -> policyController.requestAdmin(this));
            root.addView(enableAdmin);
        }

        if (!policyController.isDeviceOwner()) {
            addText("Device owner mode is not active. Some blocking features require ADB provisioning.", 14, false)
                    .setGravity(Gravity.CENTER);
        }

        Button uninstallButton = button("Uninstall app");
        uninstallButton.setOnClickListener(view -> showUninstallDialog());
        root.addView(uninstallButton);
    }

    private void showUninstallDialog() {
        if (PasswordStore.isLockedOut(this)) {
            String until = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(PasswordStore.lockedUntil(this)));
            toast("Uninstall attempts are locked until " + until + ".");
            return;
        }

        EditText input = passwordInput("Password");
        new AlertDialog.Builder(this)
                .setTitle("Enter password")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Uninstall", (dialog, which) -> {
                    if (PasswordStore.verifyRemovalPassword(this, input.getText().toString())) {
                        policyController.clearPoliciesForRemoval();
                        policyController.requestUninstall(this);
                    } else if (PasswordStore.isLockedOut(this)) {
                        toast("Incorrect password. Uninstall is locked for 10 minutes.");
                    } else {
                        toast("Incorrect password.");
                    }
                })
                .show();
    }

    private void addIcon() {
        ImageView icon = new ImageView(this);
        icon.setImageResource(getResources().getIdentifier("icon", "mipmap", getPackageName()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(96), dp(96));
        params.setMargins(0, 0, 0, dp(24));
        root.addView(icon, params);
    }

    private TextView addText(String text, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(0xff111827);
        if (bold) {
            view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(18));
        root.addView(view, params);
        return view;
    }

    private EditText passwordInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return input;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(14), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
