/*
 * Copyright (C) 2010, 2011, 2012 Herbert von Broeuschmeul
 * Copyright (C) 2010, 2011, 2012 BluetoothGPS4Droid Project
 * 
 * This file is part of BluetoothGPS4Droid.
 *
 * BluetoothGPS4Droid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * BluetoothGPS4Droid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with BluetoothGPS4Droid. If not, see <http://www.gnu.org/licenses/>.
 */

package org.da_cha.android.gps.bluetooth.provider;

import java.util.HashSet;
import java.util.Set;

import org.da_cha.android.gps.bluetooth.provider.R;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.preference.Preference.OnPreferenceClickListener;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

/**
 * A PreferenceActivity Class used to configure, start and stop the NMEA tracker service.
 * 
 * @author Herbert von Broeuschmeul
 *
 */
public class BluetoothGpsActivity extends PreferenceActivity implements OnPreferenceChangeListener, OnSharedPreferenceChangeListener {

	/**
	 * Tag used for log messages
	 */
	private static final String LOG_TAG = "BlueGPS";
	
	private SharedPreferences sharedPref ;
	private BluetoothAdapter bluetoothAdapter = null;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
          onCreatePreferenceActivity();
          Preference pref = findPreferenceActivity(BluetoothGpsProviderService.PREF_ABOUT);
          pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {		
			      @Override
			      public boolean onPreferenceClick(Preference preference) {
				      BluetoothGpsActivity.this.displayAboutDialog();
				      return true;
			      }
          });;
        } else {
          onCreatePreferenceFragment();
        }
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
          findPreference(BluetoothGpsProviderService.PREF_ABOUT)
             .setOnPreferenceClickListener(BluetoothGpsActivity.createClickAboutListener(getActivity()));
        }
   }
   public static OnPreferenceClickListener createClickAboutListener(final Activity activity){
     return new  Preference.OnPreferenceClickListener() {		
			      @Override
			      public boolean onPreferenceClick(Preference preference) {
				      BluetoothGpsActivity.displayAboutDialog();
				      return true;
			      }
          };
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
        ListPreference prefDevices = (ListPreference)findPreferenceActivity(BluetoothGpsProviderService.PREF_BLUETOOTH_DEVICE);
        String deviceAddress = sharedPref.getString(BluetoothGpsProviderService.PREF_BLUETOOTH_DEVICE, null);
        if (BluetoothAdapter.checkBluetoothAddress(deviceAddress)){
        	deviceName = bluetoothAdapter.getRemoteDevice(deviceAddress).getName();
        }
        prefDevices.setSummary(getString(R.string.pref_bluetooth_device_summary, deviceName));
    }   

	private void updateDevicePreferenceList(){
        // update bluetooth device summary
		updateDevicePreferenceSummary();
		// update bluetooth device list
        ListPreference prefDevices = (ListPreference)findPreferenceActivity(BluetoothGpsProviderService.PREF_BLUETOOTH_DEVICE);
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
        prefDevices.setEntryValues(entryValues);
        prefDevices.setEntries(entries);
        Preference pref = (Preference)findPreferenceActivity(BluetoothGpsProviderService.PREF_CONNECTION_RETRIES);
        String maxConnRetries = sharedPref.getString(BluetoothGpsProviderService.PREF_CONNECTION_RETRIES, getString(R.string.defaultConnectionRetries));
        pref.setSummary(getString(R.string.pref_connection_retries_summary,maxConnRetries));
        this.onContentChanged();
    }
    
	@Override
	protected void onDestroy() {
		super.onDestroy();
		sharedPref.unregisterOnSharedPreferenceChangeListener(this);
	}
	
	private void displayAboutDialog(){
        View messageView = getLayoutInflater().inflate(R.layout.about, null, false);
        // we need this to enable html links
        TextView textView = (TextView) messageView.findViewById(R.id.about_license);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        // When linking text, force to always use default color. This works
        // around a pressed color state bug.
        int defaultColor = textView.getTextColors().getDefaultColor();
        textView.setTextColor(defaultColor);
        textView = (TextView) messageView.findViewById(R.id.about_sources);
        textView.setTextColor(defaultColor);
       
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.about_title);
		builder.setIcon(R.drawable.gplv3_icon);
        builder.setView(messageView);
		builder.show();
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (BluetoothGpsProviderService.PREF_BLUETOOTH_DEVICE.equals(key)){
			updateDevicePreferenceSummary();
		} else if (BluetoothGpsProviderService.PREF_SIRF_ENABLE_GLL.equals(key)
				|| BluetoothGpsProviderService.PREF_SIRF_ENABLE_GGA.equals(key)
				|| BluetoothGpsProviderService.PREF_SIRF_ENABLE_RMC.equals(key)
				|| BluetoothGpsProviderService.PREF_SIRF_ENABLE_VTG.equals(key)
				|| BluetoothGpsProviderService.PREF_SIRF_ENABLE_GSA.equals(key)
				|| BluetoothGpsProviderService.PREF_SIRF_ENABLE_GSV.equals(key)
				|| BluetoothGpsProviderService.PREF_SIRF_ENABLE_ZDA.equals(key)
				|| BluetoothGpsProviderService.PREF_SIRF_ENABLE_SBAS.equals(key)
				|| BluetoothGpsProviderService.PREF_SIRF_ENABLE_NMEA.equals(key)
				|| BluetoothGpsProviderService.PREF_SIRF_ENABLE_STATIC_NAVIGATION.equals(key)
		){
			enableSirfFeature(key);
		}
		this.updateDevicePreferenceList();
	}	
	private void enableSirfFeature(String key){
		CheckBoxPreference pref = (CheckBoxPreference)(findPreferenceActivity(key));
		if (pref.isChecked() != sharedPref.getBoolean(key, false)){
			pref.setChecked(sharedPref.getBoolean(key, false));
		} else {
			Intent configIntent = new Intent(BluetoothGpsProviderService.ACTION_CONFIGURE_SIRF_GPS);
			configIntent.putExtra(key, pref.isChecked());
			configIntent.setClass(BluetoothGpsActivity.this, BluetoothGpsProviderService.class);
			startService(configIntent);
		}
	}
}
