package com.zygisk_enc.fingerprintreset;

import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.lang.reflect.Field;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "FpDimReset: ";
    private static CancellationSignal mCancellationSignal = null;
    private static long lastWakeTime = 0;
    private static int lastPolicy = -1;
    private static Handler mHandler = null;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("android")) return;

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.server.display.DisplayPowerController",
                lpparam.classLoader,
                "updatePowerState",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object request = getDisplayPowerRequest(param.thisObject);
                            if (request == null) return;

                            int policy = XposedHelpers.getIntField(request, "policy");

                            if (policy != lastPolicy) {
                                if (policy == 2) { // DIM
                                    Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                                    startGhostAuth(context);
                                } else {
                                    stopGhostAuth();
                                }
                                lastPolicy = policy;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            );

            XposedHelpers.findAndHookMethod(
                "com.android.server.biometrics.sensors.AuthenticationClient",
                lpparam.classLoader,
                "onAcquired",
                int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        int acquiredInfo = (int) param.args[0];
                        if (acquiredInfo == 0 && mCancellationSignal != null) {
                            wakeUp(param.thisObject);
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Critical Hook Error: " + t);
        }
    }

    private Object getDisplayPowerRequest(Object dpc) {
        String[] fields = {"mDisplayPowerRequest", "mPendingRequest", "mAppliedRequest"};
        for (String fieldName : fields) {
            try {
                return XposedHelpers.getObjectField(dpc, fieldName);
            } catch (Throwable ignored) {}
        }

        try {
            for (Field f : dpc.getClass().getDeclaredFields()) {
                if (f.getType().getName().contains("DisplayPowerRequest")) {
                    f.setAccessible(true);
                    return f.get(dpc);
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private synchronized Handler getHandler() {
        if (mHandler == null) {
            try {
                mHandler = new Handler(Looper.getMainLooper());
            } catch (Throwable ignored) {}
        }
        return mHandler;
    }

    private void startGhostAuth(final Context context) {
        Handler handler = getHandler();
        if (handler == null) return;

        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    FingerprintManager fm = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
                    if (fm == null || !fm.isHardwareDetected() || !fm.hasEnrolledFingerprints()) {
                        return;
                    }

                    stopGhostAuth();
                    
                    mCancellationSignal = new CancellationSignal();
                    fm.authenticate(null, mCancellationSignal, 0, new FingerprintManager.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            mCancellationSignal = null;
                        }
                    }, getHandler());

                    // Reduced timeout to 3 seconds as requested
                    getHandler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            stopGhostAuth();
                        }
                    }, 3000);

                } catch (Throwable ignored) {}
            }
        });
    }

    private void stopGhostAuth() {
        if (mCancellationSignal != null) {
            try {
                mCancellationSignal.cancel();
            } catch (Throwable ignored) {}
            mCancellationSignal = null;
        }
    }

    private void wakeUp(Object client) {
        try {
            long now = SystemClock.uptimeMillis();
            if (now - lastWakeTime < 1000) return;
            lastWakeTime = now;

            Context context = (Context) XposedHelpers.getObjectField(client, "mContext");
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                XposedHelpers.callMethod(pm, "userActivity", now, 2, 0);
                stopGhostAuth();
            }
        } catch (Throwable ignored) {}
    }
}
