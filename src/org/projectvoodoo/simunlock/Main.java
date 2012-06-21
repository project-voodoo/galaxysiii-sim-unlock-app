
package org.projectvoodoo.simunlock;

import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

public class Main extends Activity implements OnClickListener {

    private static final String TAG = "Voodoo SIM Unlock";

    private TextView simLockStatusTv;
    private Button unlockButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        // Check Root availability
        boolean root = Utils.canGetRootPermission();
        boolean compatible = Utils.isCompatibleDevice();

        if (root && compatible)
            setContentView(R.layout.main);
        else {
            setContentView(R.layout.unrecoverable_error);
            setProgressBarIndeterminateVisibility(false);

            TextView incompatibleTitleTv = (TextView) findViewById(R.id.incompatible_title);
            TextView incompatibleDescTv = (TextView) findViewById(R.id.incompatible_desc);

            TextView noRootTitleTv = (TextView) findViewById(R.id.no_root_title);
            TextView noRootDescTv = (TextView) findViewById(R.id.no_root_desc);

            if (!compatible) {
                incompatibleTitleTv.setVisibility(View.VISIBLE);
                incompatibleDescTv.setVisibility(View.VISIBLE);
                incompatibleTitleTv.setText(
                        getString(R.string.incompatible_device_title) + " " + Build.MODEL);
            } else {
                incompatibleTitleTv.setVisibility(View.GONE);
                incompatibleDescTv.setVisibility(View.GONE);
            }

            if (!root) {
                noRootTitleTv.setVisibility(View.VISIBLE);
                noRootDescTv.setVisibility(View.VISIBLE);
            } else {
                noRootTitleTv.setVisibility(View.GONE);
                noRootDescTv.setVisibility(View.GONE);
            }
            return;
        }

        setProgressBarIndeterminateVisibility(false);

        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        String deviceId = tm.getDeviceId();

        TextView imeiTv = (TextView) findViewById(R.id.text_imei);
        simLockStatusTv = (TextView) findViewById(R.id.text_sim_lock_status);
        ((Button) findViewById(R.id.button_query_current_status)).setOnClickListener(this);
        unlockButton = (Button) findViewById(R.id.button_unlock);
        unlockButton.setEnabled(false);
        unlockButton.setOnClickListener(this);

        if (deviceId == null) {
            Log.e(TAG, "Strange. we're unable to read phone's IMEI. Stop here");
            imeiTv.setText(R.string.imei_error);
            return;
        }
        imeiTv.setText(deviceId);

        new IsSimLockedTask().execute();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_query_current_status:
                String ussdCode = "*" + Uri.encode("#7465625#");
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel: " + ussdCode)));
                break;

            case R.id.button_unlock:
                unlockButton.setEnabled(false);
                new RunUnlockTask().execute();
                break;

            default:
                break;
        }
    }

    private class IsSimLockedTask extends AsyncTask<Void, Void, Integer> {

        @Override
        protected Integer doInBackground(Void... params) {

            try {
                return UnlockTools.readNvDataBin();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return -1;
        }

        @Override
        protected void onPostExecute(Integer result) {

            switch (result) {
                case 0:
                    simLockStatusTv.setText(R.string.locking_status_inactive);
                    simLockStatusTv.setTextColor(Color.GREEN);
                    unlockButton.setEnabled(false);
                    break;

                case 1:
                    simLockStatusTv.setText(R.string.locking_status_active);
                    simLockStatusTv.setTextColor(Color.YELLOW);
                    unlockButton.setEnabled(true);
                    break;

                default:
                    simLockStatusTv.setText(R.string.locking_status_error);
                    simLockStatusTv.setTextColor(Color.RED);
                    unlockButton.setEnabled(false);
            }
        }
    }

    private class RunUnlockTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                UnlockTools.backupNvDataBin();
                UnlockTools.modifyNvDataBinCopy();
                UnlockTools.killRild();
                Thread.sleep(2000);
                UnlockTools.writeNewNvDataBinMd5();
                UnlockTools.copyModifiedNvDataBin();
                UnlockTools.killRild();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            new IsSimLockedTask().execute();
            setProgressBarIndeterminateVisibility(false);
        }
    }

}
