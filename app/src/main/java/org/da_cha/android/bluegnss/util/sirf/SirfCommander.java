/*
 * Copyright 2014, Hiroshi Miura <miurahr@linux.com>
 * Copyright 2014, BlueGnss4OSM Project
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
package org.da_cha.android.bluegnss.util.sirf;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import org.da_cha.android.bluegnss.bluetooth.BluetoothGnssManager;
import org.da_cha.android.bluegnss.R;

public class SirfCommander {

    public static final String PREF_SIRF_ENABLE_GGA = "enableGGA";
    public static final String PREF_SIRF_ENABLE_RMC = "enableRMC";
    public static final String PREF_SIRF_ENABLE_GLL = "enableGLL";
    public static final String PREF_SIRF_ENABLE_VTG = "enableVTG";
    public static final String PREF_SIRF_ENABLE_GSA = "enableGSA";
    public static final String PREF_SIRF_ENABLE_GSV = "enableGSV";
    public static final String PREF_SIRF_ENABLE_ZDA = "enableZDA";
    public static final String PREF_SIRF_ENABLE_SBAS = "enableSBAS";
    public static final String PREF_SIRF_ENABLE_NMEA = "enableNMEA";
    public static final String PREF_SIRF_ENABLE_STATIC_NAVIGATION = "enableStaticNavigation";

    private BluetoothGnssManager gpsManager = null;
    private Context appContext;

    public SirfCommander(BluetoothGnssManager gpsManager, Context appContext){
        this.gpsManager = gpsManager;
        this.appContext = appContext;
    }

    public void enableSirfConfig(Bundle extras){
        if (extras.containsKey(PREF_SIRF_ENABLE_GGA)){
            enableNmeaGGA(extras.getBoolean(PREF_SIRF_ENABLE_GGA, true));
        }
        if (extras.containsKey(PREF_SIRF_ENABLE_RMC)){
            enableNmeaRMC(extras.getBoolean(PREF_SIRF_ENABLE_RMC, true));
        }
        if (extras.containsKey(PREF_SIRF_ENABLE_GLL)){
            enableNmeaGLL(extras.getBoolean(PREF_SIRF_ENABLE_GLL, false));
        }
        if (extras.containsKey(PREF_SIRF_ENABLE_VTG)){
            enableNmeaVTG(extras.getBoolean(PREF_SIRF_ENABLE_VTG, false));
        }
        if (extras.containsKey(PREF_SIRF_ENABLE_GSA)){
            enableNmeaGSA(extras.getBoolean(PREF_SIRF_ENABLE_GSA, false));
        }
        if (extras.containsKey(PREF_SIRF_ENABLE_GSV)){
            enableNmeaGSV(extras.getBoolean(PREF_SIRF_ENABLE_GSV, false));
        }
        if (extras.containsKey(PREF_SIRF_ENABLE_ZDA)){
            enableNmeaZDA(extras.getBoolean(PREF_SIRF_ENABLE_ZDA, false));
        }
        if (extras.containsKey(PREF_SIRF_ENABLE_STATIC_NAVIGATION)){
            enableStaticNavigation(extras.getBoolean(PREF_SIRF_ENABLE_STATIC_NAVIGATION, false));
        } else if (extras.containsKey(PREF_SIRF_ENABLE_NMEA)){
            enableNMEA(extras.getBoolean(PREF_SIRF_ENABLE_NMEA, true));
        }
        if (extras.containsKey(PREF_SIRF_ENABLE_SBAS)){
            enableSBAS(extras.getBoolean(PREF_SIRF_ENABLE_SBAS, true));
        }
    }

    public void enableSirfConfig(SharedPreferences extras){
        if (extras.contains(PREF_SIRF_ENABLE_GLL)){
            enableNmeaGLL(extras.getBoolean(PREF_SIRF_ENABLE_GLL, false));
        }
        if (extras.contains(PREF_SIRF_ENABLE_VTG)){
            enableNmeaVTG(extras.getBoolean(PREF_SIRF_ENABLE_VTG, false));
        }
        if (extras.contains(PREF_SIRF_ENABLE_GSA)){
            enableNmeaGSA(extras.getBoolean(PREF_SIRF_ENABLE_GSA, false));
        }
        if (extras.contains(PREF_SIRF_ENABLE_GSV)){
            enableNmeaGSV(extras.getBoolean(PREF_SIRF_ENABLE_GSV, false));
        }
        if (extras.contains(PREF_SIRF_ENABLE_ZDA)){
            enableNmeaZDA(extras.getBoolean(PREF_SIRF_ENABLE_ZDA, false));
        }
        if (extras.contains(PREF_SIRF_ENABLE_STATIC_NAVIGATION)){
            enableStaticNavigation(extras.getBoolean(PREF_SIRF_ENABLE_STATIC_NAVIGATION, false));
        } else if (extras.contains(PREF_SIRF_ENABLE_NMEA)){
            enableNMEA(extras.getBoolean(PREF_SIRF_ENABLE_NMEA, true));
        }
        if (extras.contains(PREF_SIRF_ENABLE_SBAS)){
            enableSBAS(extras.getBoolean(PREF_SIRF_ENABLE_SBAS, true));
        }
        gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_gga_on));
        gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_rmc_on));
        if (extras.contains(PREF_SIRF_ENABLE_GGA)){
            enableNmeaGGA(extras.getBoolean(PREF_SIRF_ENABLE_GGA, true));
        }
        if (extras.contains(PREF_SIRF_ENABLE_RMC)){
            enableNmeaRMC(extras.getBoolean(PREF_SIRF_ENABLE_RMC, true));
        }
    }

    private void enableNmeaGGA(boolean enable){
        if (gpsManager != null){
            if (enable){
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_gga_on));
            } else {
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_gga_off));
            }
        }
    }

    private void enableNmeaRMC(boolean enable){
        if (gpsManager != null){
            if (enable){
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_rmc_on));
            } else {
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_rmc_off));
            }
        }
    }

    private void enableNmeaGLL(boolean enable){
        if (gpsManager != null){
            if (enable){
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_gll_on));
            } else {
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_gll_off));
            }
        }
    }

    private void enableNmeaVTG(boolean enable){
        if (gpsManager != null){
            if (enable){
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_vtg_on));
            } else {
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_vtg_off));
            }
        }
    }

    private void enableNmeaGSA(boolean enable){
        if (gpsManager != null){
            if (enable){
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_gsa_on));
            } else {
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_gsa_off));
            }
        }
    }

    private void enableNmeaGSV(boolean enable){
        if (gpsManager != null){
            if (enable){
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_gsv_on));
            } else {
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_gsv_off));
            }
        }
    }

    private void enableNmeaZDA(boolean enable){
        if (gpsManager != null){
            if (enable){
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_zda_on));
            } else {
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_zda_off));
            }
        }
    }

    private void enableSBAS(boolean enable){
        if (gpsManager != null){
            if (enable){
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_sbas_on));
            } else {
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_sbas_off));
            }
        }
    }

    private void enableNMEA(boolean enable){
        if (gpsManager != null){
            if (enable){
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);
                int gll = (sharedPreferences.getBoolean(PREF_SIRF_ENABLE_GLL, false)) ? 1 : 0 ;
                int vtg = (sharedPreferences.getBoolean(PREF_SIRF_ENABLE_VTG, false)) ? 1 : 0 ;
                int gsa = (sharedPreferences.getBoolean(PREF_SIRF_ENABLE_GSA, false)) ? 5 : 0 ;
                int gsv = (sharedPreferences.getBoolean(PREF_SIRF_ENABLE_GSV, false)) ? 5 : 0 ;
                int zda = (sharedPreferences.getBoolean(PREF_SIRF_ENABLE_ZDA, false)) ? 1 : 0 ;
                int mss = 0;
                int epe = 0;
                int gga = 1;
                int rmc = 1;
                String command = appContext.getString(R.string.sirf_bin_to_nmea_38400_alt, gga, gll, gsa, gsv, rmc, vtg, mss, epe, zda);
                gpsManager.sendSirfCommand(command);
            } else {
                gpsManager.sendNmeaCommand(appContext.getString(R.string.sirf_nmea_to_binary));
            }
        }
    }

    private void enableStaticNavigation(boolean enable){
        if (gpsManager != null){
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);
            boolean isInNmeaMode = sharedPreferences.getBoolean(PREF_SIRF_ENABLE_NMEA, true);
            if (isInNmeaMode){
                enableNMEA(false);
            }
            if (enable){
                gpsManager.sendSirfCommand(appContext.getString(R.string.sirf_bin_static_nav_on));
            } else {
                gpsManager.sendSirfCommand(appContext.getString(R.string.sirf_bin_static_nav_off));
            }
            if (isInNmeaMode){
                enableNMEA(true);
            }
        }
    }

}
// vim: tabstop=4 expandtab shiftwidth=4 softtabstop=4
