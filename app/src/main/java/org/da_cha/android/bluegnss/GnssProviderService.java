/*
 * Copyright 2014, Hiroshi Miura <miurahr@linux.com>
 * Copyright (C) 2010, 2011, 2012 Herbert von Broeuschmeul
 * Copyright (C) 2010, 2011, 2012 BluetoothGPS4Droid Project
 * 
 * This file is part of BlueGnss4OSM.
 *
 * This is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with it. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * 
 */
package org.da_cha.android.bluegnss;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.GpsStatus.NmeaListener;
import android.location.GpsStatus.Listener;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Binder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.da_cha.android.bluegnss.MainActivity;
import org.da_cha.android.bluegnss.bluetooth.BluetoothGnssManager;
import org.da_cha.android.bluegnss.GnssStatus;
import org.da_cha.android.bluegnss.provider.MockLocationProvider;
import org.da_cha.android.bluegnss.util.nmea.NmeaParser;
import org.da_cha.android.bluegnss.util.sirf.SirfCommander;
import org.da_cha.android.bluegnss.R;

/**
 * A Service used to replace Android internal GPS with a bluetooth GPS and/or write GPS NMEA data in a File.
 * 
 * @author Hiroshi Miura
 * @author Herbert von Broeuschmeul
 *
 */
public class GnssProviderService extends Service implements NmeaListener, Listener {

    public static final String ACTION_START_TRACK_RECORDING = "org.da_cha.android.bluegnss.tracker.intent.action.START_TRACK_RECORDING";
    public static final String ACTION_STOP_TRACK_RECORDING = "org.da_cha.android.bluegnss.tracker.intent.action.STOP_TRACK_RECORDING";
    public static final String ACTION_START_GPS_PROVIDER = "org.da_cha.android.bluegnss.provider.intent.action.START_GPS_PROVIDER";
    public static final String ACTION_STOP_GPS_PROVIDER = "org.da_cha.android.bluegnss.provider.intent.action.STOP_GPS_PROVIDER";
    public static final String ACTION_CONFIGURE_SIRF_GPS = "org.da-cha.android.bluegnss.provider.intent.action.CONFIGURE_SIRF_GPS";
    public static final String PREF_GPS_LOCATION_PROVIDER = "gpsLocationProviderKey";
    public static final String PREF_FORCE_ENABLE_PROVIDER = "forceEnableProvider";
    public static final String PREF_CONNECTION_RETRIES = "connectionRetries";
    public static final String PREF_SIRF_GPS = "sirfGps";
    public static final String PREF_TRACK_FILE_DIR = "trackFileDirectory";
    public static final String PREF_TRACK_FILE_PREFIX = "trackFilePrefix";
    public static final String PREF_BLUETOOTH_DEVICE = "bluetoothDevice";
    public static final String PREF_ABOUT = "about";
    public static final String NOTIFY_UPDATE = "org.da_cha.android.bluegnss.provider.intent.notify.UPDATE";
    public static final String NOTIFY_UPDATE_GPS_STATUS = "org.da_cha.android.bluegnss.provider.intent.notify.UPDATE_GPS_STATUS";
    public static final String NOTIFY_UPDATE_GPS_FIX = "org.da_cha.android.bluegnss.provider.intent.notify.UPDATE_GPS_FIX";
    public static final String NOTIFY_DISCONNECT = "org.da_cha.android.bluegnss.provider.intent.notify.DISCONNECT";


    public static final int MSG_REGISTER_CLIENT   = 1;
    public static final int MSG_UNREGISTER_CLIENT = 2;
    public static final int MSG_DISCONNECTED      = 3;
    public static final int MSG_UPDATED           = 4;
    public static final int MSG_CONNECTED         = 5;


    /**
     * Tag used for log messages
     */
    private static final String LOG_TAG = "BlueGNSS";

    private BluetoothGnssManager gpsManager = null;
    private MockLocationProvider gpsMockProvider = null;
    private PrintWriter writer;
    private File trackFile;
    private boolean preludeWritten = false;
    private Toast toast;
    private static boolean isRunning = false;
    private NmeaParser nmeaParser;

    private static PowerManager.WakeLock wl;
    private static boolean isWakeLocked=false;

    @Override
    public void onCreate() {
        super.onCreate();
        toast = Toast.makeText(getApplicationContext(), "NMEA track recording... on", Toast.LENGTH_SHORT);
        isRunning = true;
        createNewWakeLock();
    }

    @SuppressWarnings("deprecation")
    private void createNewWakeLock(){
        PowerManager pm = (PowerManager)getApplicationContext().getSystemService(Context.POWER_SERVICE);
        // SCREEN_DIM_WAKE_LOCK is deprecated but no better way to specify from service.
        wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, LOG_TAG);
    }

    /* (non-Javadoc)
     * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String deviceAddress = sharedPreferences.getString(PREF_BLUETOOTH_DEVICE, null);
        int maxConRetries = Integer.parseInt(sharedPreferences.getString(PREF_CONNECTION_RETRIES, this.getString(R.string.defaultConnectionRetries)));
        Log.d(LOG_TAG, "prefs device addr: "+deviceAddress);
        String action;
        if (intent != null){
            action = intent.getAction();
            if (action == null){
                return Service.START_STICKY;
            }
        } else {
            return Service.START_STICKY;
        }
        if (ACTION_START_GPS_PROVIDER.equals(action)) {
            if (gpsManager == null){
                if (BluetoothAdapter.checkBluetoothAddress(deviceAddress)){
                /*
                 * Instanciate btgps manager, mock provider and nmea parser
                 */
                    gpsManager = new BluetoothGnssManager(this, deviceAddress, maxConRetries);
                    gpsMockProvider = new MockLocationProvider(this);
                    /*
                     * Use the result of
                     * 
                     * http://edu-observatory.org/gps/gps_accuracy.html
                     *
                     *  Table 2   Standard error model - L1 C/A (no SA)
                     *
                     *                                 One-sigma error, m
                     *
                     *  Error source            Bias    Random  Total   DGPS
                     *  ------------------------------------------------------------
                     *  Ephemeris data          2.1     0.0     2.1 0.0
                     *  Satellite clock         2.0     0.7     2.1     0.0
                     *  Ionosphere          4.0     0.5     4.0     0.4
                     *  Troposphere             0.5     0.5     0.7     0.2
                     *  Multipath           1.0     1.0     1.4     1.4
                     *  Receiver measurement        0.5     0.2     0.5     0.5
                     *  ------------------------------------------------------------
                     *  User equivalent range 
                     *    error (UERE), rms*        5.1     1.4     5.3     1.6
                     *  Filtered UERE, rms      5.1     0.4     5.1     1.5
                     *  ------------------------------------------------------------
                     *
                     *  Vertical one-sigma errors--VDOP= 2.5           12.8     3.9
                     *  Horizontal one-sigma errors--HDOP= 2.0         10.2     3.1
                     *
                     * -----------------------------------------------------------------
                     *  I adopt 5.1
                     */
                    nmeaParser = new NmeaParser(5.1f);
                    // register
                    gpsManager.setGpsMockProvider(gpsMockProvider);
                    gpsManager.setNMEAParser(nmeaParser);
                    nmeaParser.setGpsMockProvider(gpsMockProvider);

                    // now ready to enable it.
                    boolean enabled = gpsManager.enable();
//                  Bundle extras = intent.getExtras();

                    if (enabled) {
                        wlacquire();
                        boolean force = sharedPreferences.getBoolean(PREF_FORCE_ENABLE_PROVIDER, false);
                        gpsMockProvider.enableMockLocationProvider(force);
                        gpsManager.addGpsStatusListener(this);
                        Intent myIntent = new Intent(this, MainActivity.class);
                        PendingIntent myPendingIntent = PendingIntent.getActivity(this, 0, myIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                        Context appContext = getApplicationContext();
                        Notification notification = new Notification.Builder(appContext)
                            .setSmallIcon(R.drawable.ic_stat_notify)
                            .setContentText(this.getString(R.string.foreground_gps_provider_started_notification))
                            .setWhen(System.currentTimeMillis())
                            .setContentTitle(this.getString(R.string.foreground_service_started_notification_title))
                            .setContentIntent(myPendingIntent)
                            .build();
                        startForeground(R.string.foreground_gps_provider_started_notification, notification);
                        if (sharedPreferences.getBoolean(PREF_SIRF_GPS, false)){
                            SirfCommander sirfCommander = new SirfCommander(gpsManager, this);
                            sirfCommander.enableSirfConfig(sharedPreferences);
                        }                   
                        toast.setText(this.getString(R.string.msg_gps_provider_started));
                        toast.show();   
                    } else {
                        sendGpsDisconnected();
                        stopSelf();
                    }
                } else {
                    sendGpsDisconnected();
                    stopSelf();
                }
            } else {
                toast.setText(this.getString(R.string.msg_gps_provider_already_started));
                toast.show();
            }
        } else if (ACTION_START_TRACK_RECORDING.equals(action)){
            if (trackFile == null){
                if (gpsManager != null){
                    beginTrack();
                    gpsManager.addNmeaListener(this);
                    toast.setText(this.getString(R.string.msg_nmea_recording_started));
                    toast.show();
                } else {
                    endTrack();
                }
            } else {
                toast.setText(this.getString(R.string.msg_nmea_recording_already_started));
                toast.show();
            }
        } else if (ACTION_STOP_TRACK_RECORDING.equals(action)){
            if (gpsManager != null){
                gpsManager.removeNmeaListener(this);
                endTrack();
                toast.setText(this.getString(R.string.msg_nmea_recording_stopped));
                toast.show();
            }
        } else if (ACTION_STOP_GPS_PROVIDER.equals(action)){
            wlrelease();
            sendGpsDisconnected();
            gpsManager.removeGpsStatusListener(this);
            stopSelf();
        } else if (ACTION_CONFIGURE_SIRF_GPS.equals(action)){
            if (gpsManager != null){
                Bundle extras = intent.getExtras();
                SirfCommander sirfCommander = new SirfCommander(gpsManager, this);
                sirfCommander.enableSirfConfig(extras);
            }
        }
        return Service.START_STICKY;
    }

    private void wlrelease() {
        if (isWakeLocked){
            wl.release();
            isWakeLocked = false;
        }
    }
    private void wlacquire() {
        if (!isWakeLocked){
            wl.acquire();
            isWakeLocked = true;
        }
    }

    @Override
    public void onDestroy() {
        BluetoothGnssManager manager = gpsManager;
        gpsManager  = null;
        if (manager != null){
            if (manager.getDisableReason() != 0){
                toast.setText(getString(R.string.msg_gps_provider_stopped_by_problem, getString(manager.getDisableReason())));
                toast.show();
            } else {
                toast.setText(R.string.msg_gps_provider_stopped);
                toast.show();
            }
            manager.removeNmeaListener(this);
            gpsMockProvider.disableMockLocationProvider();
            manager.disable();
        }
        endTrack();
        isRunning = false;
        super.onDestroy();
    }

    private void beginTrack(){
        SimpleDateFormat fmt = new SimpleDateFormat("_yyyy-MM-dd_HH-mm-ss'.nmea'");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String trackDirName = sharedPreferences.getString(PREF_TRACK_FILE_DIR, this.getString(R.string.defaultTrackFileDirectory));
        String trackFilePrefix = sharedPreferences.getString(PREF_TRACK_FILE_PREFIX, this.getString(R.string.defaultTrackFilePrefix));
        trackFile = new File(trackDirName,trackFilePrefix+fmt.format(new Date()));
        Log.d(LOG_TAG, "Writing the prelude of the NMEA file: "+trackFile.getAbsolutePath());
        File trackDir = trackFile.getParentFile();
        try {
            if ((! trackDir.mkdirs()) && (! trackDir.isDirectory())){
                Log.e(LOG_TAG, "Error while creating parent dir of NMEA file: "+trackDir.getAbsolutePath());
            }
            writer = new PrintWriter(new BufferedWriter(new FileWriter(trackFile)));
            preludeWritten = true;
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error while writing the prelude of the NMEA file: "+trackFile.getAbsolutePath(), e);
            // there was an error while writing the prelude of the NMEA file, stopping the service...
            stopSelf();
        }
    }
    private void endTrack(){
        if (trackFile != null && writer != null){
            Log.d(LOG_TAG, "Ending the NMEA file: "+trackFile.getAbsolutePath());
            preludeWritten = false;
            writer.close();
            trackFile = null;
        }
    }
    private void addNMEAString(String data){
        if (! preludeWritten){
            beginTrack();
        }
        Log.v(LOG_TAG, "Adding data in the NMEA file: "+ data);
        if (trackFile != null && writer != null){
            writer.print(data);
        }
    }
    public static boolean isRunning()
    {
        return isRunning;
    }


    public GnssStatus getGnssStatus(){
        if (nmeaParser == null){
            return null;
        }
        return nmeaParser.getGnssStatus();
    }

    public class GnssProviderServiceBinder extends Binder {
            GnssProviderService getService() {
                return GnssProviderService.this;
            }
    }

      private final IBinder mBinder = new GnssProviderServiceBinder();

    /* (non-Javadoc)
     * @see android.app.Service#onBind(android.content.Intent)
     */
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(LOG_TAG, "trying access IBinder");
        return mBinder;
    }

    private void sendGpsDisconnected() {
        Intent broadcastIntent = new Intent();
        broadcastIntent.putExtra(
            "notification", NOTIFY_DISCONNECT);
        broadcastIntent.setAction(NOTIFY_UPDATE);
        getBaseContext().sendBroadcast(broadcastIntent);
    }
    private void sendGpsUpdate() {
        if (nmeaParser != null){
            GnssStatus gnssStatus = nmeaParser.getGnssStatus();
            long timestamp = gnssStatus.getTimestamp();
            Intent broadcastIntent = new Intent();
            broadcastIntent.putExtra("notification", NOTIFY_UPDATE_GPS_STATUS);
            broadcastIntent.putExtra("timestamp", timestamp);
            broadcastIntent.setAction(NOTIFY_UPDATE);
            getBaseContext().sendBroadcast(broadcastIntent);
        }
    }

    @Override
    public void onGpsStatusChanged(int event){
        sendGpsUpdate();
    }

    @Override
    public void onNmeaReceived(long timestamp, String data) {
        addNMEAString(data);
    }
}
// vim: tabstop=4 expandtab shiftwidth=4 softtabstop=4
