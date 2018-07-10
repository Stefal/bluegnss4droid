/*
 * Copyright 2014, Hiroshi Miura <miurahr@linux.com>
 * Copyright 2014, BlueGnss4OSM Project * Copyright (C) 2010, 2011, 2012 BluetoothGPS4Droid Project
 *
 * This file is part of BlueGnss4OSM.
 *
 * This is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with it. If not, see <http://www.gnu.org/licenses/>.
 */

package org.da_cha.android.bluegnss;

import org.da_cha.android.bluegnss.GnssProviderService;
import org.da_cha.android.bluegnss.GnssStatus;
import org.da_cha.android.bluegnss.R;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.util.Log;


public class MainFragment extends Fragment {

    private final static String LOG_TAG = "BlueGNSS";

    private SharedPreferences sharedPref;
    private boolean conn_state = false;
    private boolean logging_state = false;

    private GnssProviderService mService = null;
    boolean mIsBound;
    boolean mIsRegistered;
    private final GnssUpdateReceiver mGnssUpdateReceiver = new GnssUpdateReceiver();

    private View myView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        myView = inflater.inflate(R.layout.main_fragment, container, false);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplication());

       return myView;
    }

    /*
     * Service communications
     */
    private void CheckIfServiceIsRunning() {
        if (GnssProviderService.isRunning()) {
            doBindService();
            doRegisterReceiver();
        } else {
            mIsRegistered = false;
            mIsBound = false;
        }
    }
    private void doRegisterReceiver(){
        IntentFilter filter = new IntentFilter(GnssProviderService.NOTIFY_UPDATE);
        getActivity().registerReceiver(mGnssUpdateReceiver, filter);
        mIsRegistered = true;
    }
    private void doUnregisterReceiver(){
        if (mIsRegistered) {
            getActivity().unregisterReceiver(mGnssUpdateReceiver);
            mIsRegistered = false;
        }
    }
    private void doBindService() {
        Context appContext = getActivity().getApplicationContext();
        getActivity().bindService(new Intent(appContext, GnssProviderService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }
    private void doUnbindService() {
        if (mIsBound) {
            getActivity().unbindService(mConnection);
            mIsBound = false;
        }
    }
    private void restoreMe(Bundle state) {
        if (state!=null) {
            conn_state = Boolean.valueOf(state.getString("connStatus"));
            logging_state = Boolean.valueOf(state.getString("LoggingStatus"));
        } else {
            Log.d(LOG_TAG, "restoreMe: bundle state is null.");
        }
    }

    /* 
     * Fragment life cycle handler
     */
     @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("connStatus", Boolean.toString(conn_state));
        outState.putString("loggingStatus", Boolean.toString(logging_state));
    }
    @Override
    public void onActivityCreated(Bundle outState) {
        Button btnStartStop = (Button)myView.findViewById(R.id.btn_start_stop);
        btnStartStop.setOnClickListener(mStartStop);

        Button btnStartLogging = (Button)myView.findViewById(R.id.btn_start_logging);
        btnStartLogging.setOnClickListener(mStartLogging);

        if (logging_state) {
            btnStartLogging.setText(R.string.main_logging_stop);
        } else {
            btnStartLogging.setText(R.string.main_logging_start);
        }
        if (conn_state) {
            btnStartLogging.setEnabled(true);
            btnStartStop.setText(R.string.main_stop);
        } else {
            btnStartLogging.setEnabled(false);
            btnStartStop.setText(R.string.main_start);
        }
       super.onActivityCreated(outState);
    }
    @Override
    public void onViewStateRestored(Bundle savedState){
        super.onViewStateRestored(savedState);
        restoreMe(savedState);
        setBluetoothDeviceName(myView);
    }
    @Override
    public void onResume() {
        super.onResume();
        CheckIfServiceIsRunning();
        setBluetoothDeviceName(myView);
        updateButtonLabels();
    }
    @Override
    public void onPause() {
        doUnregisterReceiver();
        doUnbindService();
        super.onPause();
    }

    // action listeners
    //
    private OnClickListener mStartStop = new OnClickListener() {
        public void onClick(View v) {
            if (conn_state) {
                Log.d(LOG_TAG, "mStartStop: stop service");
                stopProviderService();
            } else if (checkBluetoothDevice()) {
                Log.d(LOG_TAG, "mStartStop: start service");
                startProviderService();
            } else {
                // do nothing
                Log.d(LOG_TAG, "mStartStop: wrong state.");
            }
        }
    };

    private OnClickListener mStartLogging = new OnClickListener() {
        public void onClick(View v) {
            if (logging_state) {
                commandService(GnssProviderService.ACTION_STOP_TRACK_RECORDING);
                Log.d(LOG_TAG, "mStartLogging: stop service");
                Button btnStartLogging = (Button)v.findViewById(R.id.btn_start_logging);
                btnStartLogging.setText(R.string.main_logging_start);
                logging_state = false;
            } else {
                commandService(GnssProviderService.ACTION_START_TRACK_RECORDING);
                Log.d(LOG_TAG, "mStartLogging: start service");
                Button btnStartLogging = (Button)v.findViewById(R.id.btn_start_logging);
                btnStartLogging.setText(R.string.main_logging_stop);
                logging_state = true;
            }
        }
    };

    private void setBluetoothDeviceName(View v) {
       Log.v(LOG_TAG, "entered setBluetoothDeviceName()");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        String deviceAddress = sharedPref.getString(GnssProviderService.PREF_BLUETOOTH_DEVICE, null);
        if (deviceAddress == null){
            Log.d(LOG_TAG, "setBluetoothDeviceName(): deviceAddress is null.");
        }
        TextView txtDeviceName = (TextView)v.findViewById(R.id.main_bluetooth_device_name);
        if (bluetoothAdapter != null) {
            if (BluetoothAdapter.checkBluetoothAddress(deviceAddress)){
                txtDeviceName.setText(bluetoothAdapter.getRemoteDevice(deviceAddress).getName());
            }
        }
    }
    private boolean checkBluetoothDevice() {
        String deviceAddress = sharedPref.getString(GnssProviderService.PREF_BLUETOOTH_DEVICE, null);
        if (deviceAddress == null){
            Log.d(LOG_TAG, "device Address is null.");
        }
        return (deviceAddress == null)?false:true;
    }

    private void commandService(String action){
        Context appContext = getActivity().getApplicationContext();
        Intent i = new Intent(action);
        i.setClass(appContext, GnssProviderService.class);
        getActivity().startService(i);
    }

    private void updateButtonLabels(){
        if (conn_state){
            // button -> "Stop"
            Button btnStartStop = (Button)myView.findViewById(R.id.btn_start_stop);
            btnStartStop.setText(R.string.main_stop);
             // Logging button enabled
            Button btnStartLogging = (Button)myView.findViewById(R.id.btn_start_logging);
            btnStartLogging.setEnabled(true);
            if (logging_state) {
                btnStartLogging.setText(R.string.main_logging_stop);
            } else {
                btnStartLogging.setText(R.string.main_logging_start);
            }
        } else {
             // button -> "Start"
            Button btnStartStop = (Button)myView.findViewById(R.id.btn_start_stop);
            btnStartStop.setText(R.string.main_start);
        }
    }
 
   /*
     * Start/Stop service and logging button.
     */
    private void stopProviderService() {
        doUnbindService();
        doUnregisterReceiver();
        // FIXME: send intent for status fragment to unbind service.
        //
        // stop service
        commandService(GnssProviderService.ACTION_STOP_GPS_PROVIDER);
        // button -> "Start"
        Button btnStartStop = (Button)myView.findViewById(R.id.btn_start_stop);
        btnStartStop.setText(R.string.main_start);
        // Logging button disabled
        Button btnStartLogging = (Button)myView.findViewById(R.id.btn_start_logging);
        btnStartLogging.setEnabled(false);
        conn_state = false;
    }
    private void startProviderService() {
        // start service
        commandService(GnssProviderService.ACTION_START_GPS_PROVIDER);
        // wait 1000ms.
        try{
            Thread.sleep(2000);
        }catch(InterruptedException e){}
        doBindService();
        doRegisterReceiver();
        conn_state = true;
        // button -> "Stop"
        Button btnStartStop = (Button)myView.findViewById(R.id.btn_start_stop);
        btnStartStop.setText(R.string.main_stop);
        // Logging button enabled
        Button btnStartLogging = (Button)myView.findViewById(R.id.btn_start_logging);
        btnStartLogging.setEnabled(true);
        if (GnssProviderService.isRunning()) {
            Log.d(LOG_TAG, "Cannot detect Service running");
        }
    }

    /*
     * For bind to ProviderService
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = (GnssProviderService)((GnssProviderService.GnssProviderServiceBinder)service).getService();
        }
        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }
    };
    /*
     * for reciever
     */
     private class GnssUpdateReceiver extends BroadcastReceiver {
        private GnssStatus status;
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            String message = bundle.getString("notification");
            if (GnssProviderService.NOTIFY_UPDATE_GPS_STATUS.equals(message)){
               status = mService.getGnssStatus();
               long fix = status.getFixTimestamp();
               if (fix != 0) {
                   update_view(bundle);
               } else {
                   update_view_nonfix(bundle);
               }
            } else if (GnssProviderService.NOTIFY_DISCONNECT.equals(message)){
               stopProviderService();
            } else {
               Log.e(LOG_TAG, "Unknown message: "+message);
            }
        }
        private void update_view_nonfix(Bundle bundle){
            // Update date/time on View
            long timestamp = bundle.getLong("timestamp");
            Time sat_time = new Time();
            sat_time.set(timestamp);
            TextView tv = (TextView) myView.findViewById(R.id.main_date_time);
            tv.setText(sat_time.format("%Y-%m-%d %H-%M-%S"));
            int numSat = status.getNumSatellites();
            tv = (TextView) myView.findViewById(R.id.main_num_satellites);
            tv.setText("0/"+Integer.toString(numSat));
        }
        private void update_view(Bundle bundle){
            // Update all information on main screen
            // date/time
            long timestamp = status.getTimestamp();
            Time sat_time = new Time();
            sat_time.set(timestamp);
            TextView tv = (TextView) myView.findViewById(R.id.main_date_time);
            tv.setText(sat_time.format("%Y-%m-%d %H-%M-%S"));
            double latitude = status.getLatitude();
            tv = (TextView) myView.findViewById(R.id.main_lat);
            tv.setText(lonlat_format(latitude));
            double longitude = status.getLongitude();
            tv = (TextView) myView.findViewById(R.id.main_lon);
            tv.setText(lonlat_format(longitude));
            float speed = (float) (status.getSpeed()*3.6);
            tv = (TextView) myView.findViewById(R.id.main_speed);
            tv.setText(len_format(speed));
            double hdop = status.getHDOP();
            tv = (TextView) myView.findViewById(R.id.main_hdop);
            tv.setText(len_format(hdop));
            int numNbSat = status.getNbSat();
            int numSat = status.getNumSatellites();
            tv = (TextView) myView.findViewById(R.id.main_num_satellites);
            tv.setText(Integer.toString(numNbSat)+"/"+Integer.toString(numSat));
            // method tv.setText cancelled because of java.util.ConcurrentModificationException in getCSV
            //ArrayList<Integer> satList = status.getTrackedSatellites();
            //tv = (TextView) myView.findViewById(R.id.main_used_satellites);
            //tv.setText(getCSV(satList));
       }
        private String lonlat_format(Double lonlat){
            NumberFormat format = NumberFormat.getInstance();
            format.setMaximumFractionDigits(6);
            return format.format(lonlat);
        }
        private String len_format(Double len){
            NumberFormat format = NumberFormat.getInstance();
            format.setMaximumFractionDigits(2);
            return format.format(len);
        }
        private String len_format(Float len){
            NumberFormat format = NumberFormat.getInstance();
            format.setMaximumFractionDigits(2);
            return format.format(len);
        }
    }

    public static String getCSV(ArrayList<Integer> integers){
        String ret = "";
        Iterator<Integer> iterator = integers.iterator();
        for (int i = 0; i < integers.size(); i++)
        {
            if (i == 0) {
                ret = Integer.toString(iterator.next().intValue());
            } else {
                ret = ret +","+ Integer.toString(iterator.next().intValue());
            }
        }
        return ret;
    }
} 
// vim: tabstop=4 expandtab shiftwidth=4 softtabstop=4
