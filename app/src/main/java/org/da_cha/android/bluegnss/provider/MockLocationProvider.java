/*
 * Copyright 2014, Hiroshi Miura <miurahr@linux.com>
 * Copyright 2010, 2011, 2012 Herbert von Broeuschmeul
 * Copyright 2010, 2011, 2012 BluetoothGPS4Droid Project
 * 
 * This file is part of BlueGnss4OSm.
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

package org.da_cha.android.bluegnss.provider;


import android.app.Service;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;

/**
 * This class is used to provide Mock GPS provider for clients.
 *
 */
public class MockLocationProvider {

  /**
   * Tag used for log messages
   */
  private static final String LOG_TAG = "BlueGNSS";

  private Service callingService;
  private Context appContext;
  private LocationManager lm;
  private boolean mockGpsAutoEnabled = false;
  private boolean mockGpsEnabled = false;
  private int mockStatus = LocationProvider.OUT_OF_SERVICE;
 
  public MockLocationProvider(Service callingService) {
    this.callingService = callingService;
    this.appContext = callingService.getApplicationContext();
    this.lm = (LocationManager)callingService.getSystemService(Context.LOCATION_SERVICE);
  }

  /**
   * Enables the Mock GPS Location Provider used for the bluetooth GPS.
   */
  public void enableMockLocationProvider(boolean force){
    try {
      LocationProvider prov;
      if (! mockGpsEnabled){
        prov = lm.getProvider(LocationManager.GPS_PROVIDER);
        if (prov != null){
          Log.v(LOG_TAG, "Mock provider(1): "+prov.getName()+" "+prov.getPowerRequirement()+" "+prov.getAccuracy()+" "+lm.isProviderEnabled(LocationManager.GPS_PROVIDER));
          try {
            lm.removeTestProvider(LocationManager.GPS_PROVIDER);
          } catch (IllegalArgumentException e){
            Log.d(LOG_TAG, "unable to remove current provider.");
          }
        }
        prov = lm.getProvider(LocationManager.GPS_PROVIDER);
        lm.addTestProvider(LocationManager.GPS_PROVIDER, false, true,false, false, true, true, true, Criteria.POWER_MEDIUM, Criteria.ACCURACY_FINE);
        if ( force || (prov == null)){
            Log.d(LOG_TAG, "enabling Mock provider.");
            lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
            mockGpsAutoEnabled = true;
          }
          mockGpsEnabled = true;
        } else {
          Log.d(LOG_TAG, "Mock provider already enabled.");
        }
        prov = lm.getProvider(LocationManager.GPS_PROVIDER);
        if (prov != null){
          Log.e(LOG_TAG, "Mock provider(2): "+prov.getName()+" "+prov.getPowerRequirement()+" "+prov.getAccuracy()+" "+lm.isProviderEnabled(LocationManager.GPS_PROVIDER));
        }
    } catch (SecurityException e){
      Log.e(LOG_TAG, "Error while enabling Mock Mocations Provider", e);
      disableMockLocationProvider();
    }
  }

  public void disableMockLocationProvider(){
    try {
      LocationProvider prov;
      if (mockGpsEnabled){
        prov = lm.getProvider(LocationManager.GPS_PROVIDER);
        mockGpsEnabled = false;
        if ( mockGpsAutoEnabled )  { 
          Log.d(LOG_TAG, "disabling Mock provider.");
          lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false);
        }
        lm.clearTestProviderEnabled(LocationManager.GPS_PROVIDER);
        lm.clearTestProviderStatus(LocationManager.GPS_PROVIDER);
        lm.removeTestProvider(LocationManager.GPS_PROVIDER);
        Log.d(LOG_TAG, "removed mock GPS");
      } else {
        Log.d(LOG_TAG, "Mock provider already disabled.");
      }
    } catch (SecurityException e){
      Log.e(LOG_TAG, "Error while enabling Mock Mocations Provider", e);
    } finally {
      mockGpsEnabled = false;
      mockGpsAutoEnabled = false;
      mockStatus = LocationProvider.OUT_OF_SERVICE;
    }
  }

  /**
   * @return the mockGpsEnabled
   */
  public boolean isMockGpsEnabled() {
    return mockGpsEnabled;
  }

  public boolean isMockStatus(int status) {
    return (this.mockStatus == status);
  }

  public void setMockLocationProviderOutOfService(){
    notifyStatusChanged(LocationProvider.OUT_OF_SERVICE, null, System.currentTimeMillis());
  }
  public void setMockLocationProviderAvailable(){
    notifyStatusChanged(LocationProvider.AVAILABLE, null, System.currentTimeMillis());
  }

  public void notifyFix(Location fix) throws SecurityException {
    if (fix != null){
      Log.v(LOG_TAG, "New Fix: "+System.currentTimeMillis()+" "+fix);
      if (lm != null && mockGpsEnabled){
        lm.setTestProviderLocation(LocationManager.GPS_PROVIDER, fix);
        Log.v(LOG_TAG, "New Fix notified to Location Manager.");
      }
    }
  }

  public void notifyStatusChanged(int status, Bundle extras, long updateTime){
    if (this.mockStatus != status){
      Log.v(LOG_TAG, "New mockStatus: "+System.currentTimeMillis()+" "+status);
      if (lm != null && mockGpsEnabled){
        lm.setTestProviderStatus(LocationManager.GPS_PROVIDER, status, extras, updateTime);
        Log.d(LOG_TAG, "New mockStatus notified to Location Manager: "+status);
      }
      this.mockStatus = status;
    }
  }

}
// vim: tabstop=4 expandtab shiftwidth=4 softtabstop=4
