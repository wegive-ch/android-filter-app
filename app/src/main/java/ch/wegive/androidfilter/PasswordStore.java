package ch.wegive.androidfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

final class PasswordStore {
    private static final String PREFS = "filter_passwords";
    private static final String KEY_SETUP_DONE = "setup_done";
    private static final String KEY_SALT = "salt";
    private static final String KEY_USER_HASH = "user_hash";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final String KEY_LOCKED_UNTIL = "locked_until";
    private static final long LOCKOUT_MS = 30L * 60L * 1000L;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private PasswordStore() {
    }

    static boolean isSetup(Context context) {
        return prefs(context).getBoolean(KEY_SETUP_DONE, false);
    }

    static void saveInitialPassword(Context context, String password) {
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        String encodedSalt = Base64.encodeToString(salt, Base64.NO_WRAP);
        prefs(context).edit()
                .putBoolean(KEY_SETUP_DONE, true)
                .putString(KEY_SALT, encodedSalt)
                .putString(KEY_USER_HASH, sha512(encodedSalt + ":" + password))
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKED_UNTIL, 0L)
                .apply();
    }

    static boolean isLockedOut(Context context) {
        return lockedUntil(context) > System.currentTimeMillis();
    }

    static long lockedUntil(Context context) {
        return prefs(context).getLong(KEY_LOCKED_UNTIL, 0L);
    }

    static boolean verifyRemovalPassword(Context context, String password) {
        if (isLockedOut(context)) {
            return false;
        }

        boolean userPasswordMatches = false;
        SharedPreferences prefs = prefs(context);
        String salt = prefs.getString(KEY_SALT, "");
        String userHash = prefs.getString(KEY_USER_HASH, "");
        if (!salt.isEmpty() && !userHash.isEmpty()) {
            userPasswordMatches = constantTimeEquals(userHash, sha512(salt + ":" + password));
        }

        boolean adminPasswordMatches = constantTimeEquals(BuildConfig.ADMIN_PASSWORD_SHA512, sha512(password));
        boolean matches = userPasswordMatches || adminPasswordMatches;
        if (matches) {
            clearFailures(context);
        } else {
            recordFailure(context);
        }
        return matches;
    }

    static void clearFailures(Context context) {
        prefs(context).edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKED_UNTIL, 0L)
                .apply();
    }

    static String sha512(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] output = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(output.length * 2);
            for (byte b : output) {
                builder.append(String.format(Locale.US, "%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-512 is unavailable", e);
        }
    }

    private static void recordFailure(Context context) {
        SharedPreferences prefs = prefs(context);
        int failures = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
        SharedPreferences.Editor editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, failures);
        if (failures >= MAX_FAILED_ATTEMPTS) {
            editor.putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + LOCKOUT_MS);
        }
        editor.apply();
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] a = left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right.getBytes(StandardCharsets.UTF_8);
        int diff = a.length ^ b.length;
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            byte av = i < a.length ? a[i] : 0;
            byte bv = i < b.length ? b[i] : 0;
            diff |= av ^ bv;
        }
        return diff == 0;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
