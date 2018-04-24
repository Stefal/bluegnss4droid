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
 * This is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with it. If not, see <http://www.gnu.org/licenses/>.
 */

package org.da_cha.android.bluegnss;

import java.util.HashSet;
import java.util.Set;

import org.da_cha.android.bluegnss.GnssProviderService;
import org.da_cha.android.bluegnss.util.sirf.SirfCommander;
import org.da_cha.android.bluegnss.R;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.Build;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;

/**
 * A PreferenceActivity Class used to configure, start and stop the NMEA tracker service.
 * 
 * @author Herbert von Broeuschmeul
 *
 */
public class SettingActivity extends PreferenceActivity implements OnPreferenceChangeListener, OnSharedPreferenceChangeListener {

  /**
   * Tag used for log messages
   */
  private static final String LOG_TAG = "BlueGNSS";
  
  private SharedPreferences sharedPref ;
  private BluetoothAdapter bluetoothAdapter = null;
  
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplication());
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //FIXME: use deprecated functions still now.
        //if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
          onCreatePreferenceActivity();
        //} else {
        //  onCreatePreferenceFragment();
        //}
   }

   @SuppressWarnings("deprecation")
   private void onCreatePreferenceActivity() {
     addPreferencesFromResource(R.xml.pref);
   }

   @SuppressWarnings("deprecation")
   private Preference findPreferenceActivity(String key) {
     return findPreference(key);
   }

   @TargetApi(Build.VERSION_CODES.HONEYCOMB)
   private void onCreatePreferenceFragment() {
     getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new BluetoothGpsPreferenceFragment())
                .commit();
   }

   @TargetApi(Build.VERSION_CODES.HONEYCOMB)
   public static class BluetoothGpsPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
          super.onCreate(savedInstanceState);
          addPreferencesFromResource(R.xml.pref);
        }
   }
 
    /* (non-Javadoc)
   * @see android.app.Activity#onResume()
   */
  @Override
  protected void onResume() {
    this.updateDevicePreferenceList();
    super.onResume();
  }

  private void updateDevicePreferenceSummary(){
        // update bluetooth device summary
    String deviceName = "";
        ListPreference prefDevices = (ListPreference)findPreferenceActivity(GnssProviderService.PREF_BLUETOOTH_DEVICE);
        String deviceAddress = sharedPref.getString(GnssProviderService.PREF_BLUETOOTH_DEVICE, null);
        if (BluetoothAdapter.checkBluetoothAddress(deviceAddress)){
          deviceName = bluetoothAdapter.getRemoteDevice(deviceAddress).getName();
        }
        if (prefDevices != null){
            deviceName = getString(R.string.pref_bluetooth_device_summary, deviceName);
            if (deviceName != null && deviceName != ""){
                prefDevices.setSummary(deviceName);
            } else {
                Log.e(LOG_TAG, "deviceName is null or empty");
            }
        } else {
            Log.e(LOG_TAG, "refDevices is null!");
        }
    }   

  private void updateDevicePreferenceList(){
        // update bluetooth device summary
        updateDevicePreferenceSummary();
        // update bluetooth device list
        Set<BluetoothDevice> pairedDevices = new HashSet<BluetoothDevice>();
        if (bluetoothAdapter != null){
          pairedDevices = bluetoothAdapter.getBondedDevices();  
        }
        String[] entryValues = new String[pairedDevices.size()];
        String[] entries = new String[pairedDevices.size()];
        int i = 0;
          // Loop through paired devices
        for (BluetoothDevice device : pairedDevices) {
          // Add the name and address to the ListPreference enties and entyValues
          Log.v(LOG_TAG, "device: "+device.getName() + " -- " + device.getAddress());
          entryValues[i] = device.getAddress();
            entries[i] = device.getName();
            i++;
        }
        ListPreference prefDevices = (ListPreference)findPreferenceActivity(GnssProviderService.PREF_BLUETOOTH_DEVICE);
        if (prefDevices != null){
            prefDevices.setEntryValues(entryValues);
            prefDevices.setEntries(entries);
        } else {
            Log.e(LOG_TAG, "prefDevice is null!");
        }
        // update connection retry number
        Preference pref = (Preference)findPreferenceActivity(GnssProviderService.PREF_CONNECTION_RETRIES);
        String maxConnRetries = sharedPref.getString(GnssProviderService.PREF_CONNECTION_RETRIES, getString(R.string.defaultConnectionRetries));
        if (pref != null){
            pref.setSummary(getString(R.string.pref_connection_retries_summary,maxConnRetries));
        } else {
            Log.e(LOG_TAG, "preference state is wrong!");
        }
        this.onContentChanged();
    }
    
  @Override
  protected void onDestroy() {
    super.onDestroy();
    sharedPref.unregisterOnSharedPreferenceChangeListener(this);
  }
  
  @Override
  public boolean onPreferenceChange(Preference preference, Object newValue) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (GnssProviderService.PREF_BLUETOOTH_DEVICE.equals(key)){
      updateDevicePreferenceSummary();
    } else if (SirfCommander.PREF_SIRF_ENABLE_GLL.equals(key)
        || SirfCommander.PREF_SIRF_ENABLE_GGA.equals(key)
        || SirfCommander.PREF_SIRF_ENABLE_RMC.equals(key)
        || SirfCommander.PREF_SIRF_ENABLE_VTG.equals(key)
        || SirfCommander.PREF_SIRF_ENABLE_GSA.equals(key)
        || SirfCommander.PREF_SIRF_ENABLE_GSV.equals(key)
        || SirfCommander.PREF_SIRF_ENABLE_ZDA.equals(key)
        || SirfCommander.PREF_SIRF_ENABLE_SBAS.equals(key)
        || SirfCommander.PREF_SIRF_ENABLE_NMEA.equals(key)
        || SirfCommander.PREF_SIRF_ENABLE_STATIC_NAVIGATION.equals(key)){
        enableSirfFeature(key);
    }
    this.updateDevicePreferenceList();
  } 
  private void enableSirfFeature(String key){
    CheckBoxPreference pref = (CheckBoxPreference)(findPreferenceActivity(key));
    if (pref != null){
        if (pref.isChecked() != sharedPref.getBoolean(key, false)){
          pref.setChecked(sharedPref.getBoolean(key, false));
        } else {
          Intent configIntent = new Intent(GnssProviderService.ACTION_CONFIGURE_SIRF_GPS);
          configIntent.putExtra(key, pref.isChecked());
          configIntent.setClass(SettingActivity.this, GnssProviderService.class);
          startService(configIntent);
        }
      }
  }
}
// vim: tabstop=4 expandtab shiftwidth=4 softtabstop=4
