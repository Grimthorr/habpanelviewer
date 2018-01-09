package de.vier_bier.habpanelviewer.command;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import de.vier_bier.habpanelviewer.AdminReceiver;
import de.vier_bier.habpanelviewer.R;

/**
 * Handler for SCREEN_ON, KEEP_SCREEN_ON and ALLOW_SCREEN_OFF commands.
 */
public class ScreenHandler implements CommandHandler {
    private final DevicePolicyManager mDPM;
    private final PowerManager.WakeLock screenOnLock;
    private final Activity mActivity;

    public ScreenHandler(PowerManager pwrManager, Activity activity) {
        mActivity = activity;
        mDPM = (DevicePolicyManager) mActivity.getSystemService(Context.DEVICE_POLICY_SERVICE);

        screenOnLock = pwrManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "HabpanelViewer");

        setKeepScreenOn(false);
    }

    @Override
    public boolean handleCommand(String cmd) {
        if ("SCREEN_ON".equals(cmd)) {
            screenOn();
        } else if ("ALLOW_SCREEN_OFF".equals(cmd)) {
            setKeepScreenOn(false);
        } else if ("KEEP_SCREEN_ON".equals(cmd)) {
            screenOn();
            setKeepScreenOn(true);
        } else if ("LOCK_SCREEN".equals(cmd) && mDPM.isAdminActive(AdminReceiver.COMP)) {
            screenLock();
        } else {
            return false;
        }

        return true;
    }

    public void setKeepScreenOn(final boolean on) {
        Log.v("ScreenController", "setKeepScreenOn: on=" + on);

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.findViewById(R.id.activity_main_webview).setKeepScreenOn(on);
            }
        });
    }

    private void screenLock() {
        mDPM.lockNow();
    }

    private synchronized void screenOn() {
        screenOnLock.acquire(500);
    }
}
