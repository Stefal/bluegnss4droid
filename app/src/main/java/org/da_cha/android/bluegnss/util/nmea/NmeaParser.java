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

package org.da_cha.android.bluegnss.util.nmea;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationProvider;
import android.text.TextUtils;
import android.text.TextUtils.SimpleStringSplitter;
import android.util.Log;

import org.da_cha.android.bluegnss.GnssSatellite;
import org.da_cha.android.bluegnss.GnssStatus;
import org.da_cha.android.bluegnss.provider.MockLocationProvider;

/**
 * This class is used to parse NMEA sentences an generate the Android Locations when there is a new GPS FIX.
 * It manage also the Mock Location Provider (enable/disable/fix & status notification)
 * and can compute the the checksum of a NMEA sentence.
 *
 * @author Herbert von Broeuschmeul
 */
public class NmeaParser {
    /**
     * Tag used for log messages
     */
    private static final String LOG_TAG = "BlueGNSS";

    private long firstFixTimestamp;
    private NmeaState currentNmeaStatus = new NmeaState();
    private MockLocationProvider mockProvider;
    private final int GPS_NONE = 0;
    private final int GPS_FIXED = 3;
    private final int GPS_NOTIFY = 4;
    private int currentGpsStatus = GPS_NONE;
    private boolean gpsFixNotified = false;

    private GnssStatus gnssStatus;
    private NmeaParserUtil parserUtil;
    private ArrayList<Integer> activeSatellites = new ArrayList<Integer>();

    private SimpleStringSplitter splitter;

    public NmeaParser() {
        this(5f);
    }

    public NmeaParser(float precision) {
        this.gnssStatus = new GnssStatus();
        gnssStatus.setPrecision(precision);
        this.parserUtil = new NmeaParserUtil();
    }

    public void setGpsMockProvider(MockLocationProvider mockProvider) {
        this.mockProvider = mockProvider;
    }

    public GnssStatus getGnssStatus() {
        return this.gnssStatus;
    }

    public long getFirstFixTimestamp() {
        return this.firstFixTimestamp;
    }

  /*
   * As same as GpsBabel developer noted as follows, we should treat
   * every NMEA sentence as optional and pragmatic.
   * cited from nmea.cc in GpsBabel project.
   * ---------------------------------------------------------------------------
   * Zmarties notes:
   *
   * In practice, all fields of the NMEA sentences should be treated as optional -
   * if the data is not available, then the field can be omitted (hence leading
   * to the output of two consecutive commas).
   *
   * An NMEA recording can start anywhere in the stream of data.  It is therefore
   * necessary to discard sentences until sufficient data has been processed to
   * have all the necessary data to construct a waypoint.  In practice, this means
   * discarding data until we have had the first sentence that provides the date.
   * (We could scan forwards in the stream of data to find the first date, and
   * then back apply it to all previous sentences, but that is probably more
   * complexity that is necessary - the lost of one waypoint at the start of the
   * stream can normally be tolerated.)
   *
   * If a sentence is received without a checksum, but previous sentences have
   * had checksums, it is best to discard that sentence.  In practice, the only
   * time I have seen this is when the recording stops suddenly, where the last
   * sentence is truncated - and missing part of the line, including the checksum.
   *-----------------------------------------------------------------------------
   *
   * To determine NMEA sentence status, use currentNmeaStatus object to watch it.
   * every sentence parser calls NmeaState method to record it.
   * currentNmeaStatus can say NMEA sentence is begin , complete or invalid.
   *
   */

    // parse NMEA Sentence
    public String parseNmeaSentence(String gpsSentence) throws SecurityException {
        String nmeaSentence = null;
        Pattern xx = Pattern.compile("(\\$([^*$]*)(?:\\*([0-9A-F][0-9A-F]))?)\r\n");
        Matcher m = xx.matcher(gpsSentence);
        if (m.matches()) {
            nmeaSentence = m.group(1);
            String sentence = m.group(2);
            String checkSum = m.group(3);
            Log.v(LOG_TAG, "data: " + System.currentTimeMillis() + " " + sentence + " cheksum: " + checkSum + " control: " + String.format("%02X", computeChecksum(sentence)));
            splitter = new TextUtils.SimpleStringSplitter(',');
            splitter.setString(sentence);
            String command = splitter.next();
            try {
                if (command.equals("GPGGA") ||
                        command.equals("GNGGA")) {
                    long updateTime = currentNmeaStatus.getTimestamp();
                    if (parseGGA()) { // when fixed
                        if (!mockProvider.isMockStatus(LocationProvider.AVAILABLE)) {
                            firstFixTimestamp = updateTime;
                            mockProvider.notifyStatusChanged(LocationProvider.AVAILABLE, null, updateTime);
                            if (!gpsFixNotified) {
                                currentGpsStatus = GPS_FIXED;
                            }
                        }
                        Location fix = gnssStatus.getFixLocation();
                        if (fix.hasAccuracy() && fix.hasAltitude()) {
                            mockProvider.notifyFix(fix);
                        } else {
                            Log.e(LOG_TAG, "Failed to notify Fix becaues the fix does not have accuracy and/or altitude");
                        }
                    } else {
                        if (!mockProvider.isMockStatus(LocationProvider.TEMPORARILY_UNAVAILABLE)) {
                            mockProvider.notifyStatusChanged(LocationProvider.TEMPORARILY_UNAVAILABLE, null, updateTime);
                        }
                    }
                    // FIXME: ad-hoc work around..
                    gnssStatus.clearTrackedSatellites();
                } else if (command.equals("GPVTG") ||
                        command.equals("GNVTG")) {
                    if (currentNmeaStatus.shouldUseVTG()) {
                        parseVTG();
                        Log.d(LOG_TAG, "go to parseVTG");
                        currentGpsStatus = GPS_NOTIFY;

                    }
                } else if (command.equals("GPRMC") || command.equals("GNRMC")) {
                    if (currentNmeaStatus.shouldUseRMC()) {
                        long updateTime = currentNmeaStatus.getTimestamp();
                        Log.d(LOG_TAG, "go to parseRMC");
                        if (parseRMC()) {
                            if (!mockProvider.isMockStatus(LocationProvider.AVAILABLE)) {
                                mockProvider.notifyStatusChanged(LocationProvider.AVAILABLE, null, updateTime);
                                firstFixTimestamp = updateTime;
                                if (!gpsFixNotified) {
                                    currentGpsStatus = GPS_FIXED;
                                }
                            }
                            Location fix = gnssStatus.getFixLocation();
                            if (fix.hasAccuracy() && fix.hasAltitude()) {
                                mockProvider.notifyFix(fix);
                                gpsFixNotified = true;
                            } else {
                                Log.e(LOG_TAG, "Failed to notify Fix because the fix does not have accuracy and/or altitude");
                            }
                        } else {
                            if (!mockProvider.isMockStatus(LocationProvider.TEMPORARILY_UNAVAILABLE)) {
                                mockProvider.notifyStatusChanged(LocationProvider.TEMPORARILY_UNAVAILABLE, null, updateTime);
                            }
                            currentGpsStatus = GPS_NOTIFY;
                        }
                    } else if (!currentNmeaStatus.shouldUseRMC()) {
                        Log.d(LOG_TAG, "go to parseRMCSpeed");
                        parseRMCSpeed();
                        currentGpsStatus = GPS_NOTIFY;

                    }

                } else if (command.equals("GPGSA")) {
                    // GPS active satellites
                    parseGSA("GP");
                } else if (command.equals("GNGSA")) {
                    // gps/glonass active satellites
                    // two GNGSA will be generated.
                    parseGSA("GN");
                } else if (command.equals("QZGSA")) {
                    // QZSS active satellites
                    parseGSA("QZ");
                } else if (command.equals("BDGSA")) {
                    // Beidou satellites
                    parseGSA("BD");
                } else if (command.equals("GBGSA")) {
                    // Beidou satellites
                    parseGSA("GB");
                } else if (command.equals("GAGSA")) {
                    // Galileo satellites
                    parseGSA("GA");
                } else if (command.equals("GPGSV")) {
                    // GPS satellites in View
                    parseGSV("GP");
                } else if (command.equals("GLGSV")) {
                    // Glonass satellites in View
                    parseGSV("GL");
                } else if (command.equals("QZGSV")) {
                    // QZSS satellites in View
                    parseGSV("QZ");
                } else if (command.equals("BDGSV")) {
                    // Beidou satellites in view
                    parseGSV("BD");
                } else if (command.equals("GBGSV")) {
                    // Beidou satellites in view
                    parseGSV("GB");
                } else if (command.equals("GAGSV")) {
                    // Galileo satellites in view
                    parseGSV("GA");
                } else if (command.equals("GPGLL")) {
                    if (currentNmeaStatus.shouldUseGLL()) {
                        // GPS fix
                        parseGLL();
                    }
                } else if (command.equals("GNGLL")) {
                    if (currentNmeaStatus.shouldUseGLL()) {
                        // multi-GNSS fix or glonass/qzss fix
                        parseGLL();
                    }
                } else if (command.equals("GNGNS")) {
                    parseGNS();
                } else if (command.equals("GPGRS")) {
          /* range residuals
           * 1 	UTC of GGA position fix
           * 2 	Residuals
           *    0: used to calculate position in GGA line
           *    1: recomputed after GGA was computed
           * 3–14 	Range residuals in the solution, in meters
           */
                    Log.i(LOG_TAG, "Range residuals message: " + System.currentTimeMillis() + " " + gpsSentence);
                } else if (command.equals("GPLLQ")) {
          /* Leica local position and quality
           *
           * 1 	hhmmss.ss - UTC time of position
           * 2 	ddmmyy - UTC date
           * 3 	xxx.xxx - Grid easting (meters)
           * 4 	M - Meter, fixed text
           * 5 	xxxx.xxxx - Grid northing (meters)
           * 6 	M - Meter, fixed text
           * 7 	x - GPS quality. 0 = not valid. 1 = GPS Nav Fix. 2 = DGPS Fix. 3 = RTK Fix.
           * 8 	x - Number of satellites used in computation
           * 9 	xx.xx - Position quality (meters)
           * 10 	xxxx.xxxx - Height (meters)
           * 11 	M - Meter, fixed text
           */
                    Log.i(LOG_TAG, "Leica local position and quality message: " + System.currentTimeMillis() + " " + gpsSentence);
                } else {
                    Log.d(LOG_TAG, "Unkown nmea data: " + System.currentTimeMillis() + " " + gpsSentence);
            }
            } catch (Exception e) {
                // not propergate to caller.
                Log.d(LOG_TAG, "Caught exception on NmeaParser");
        }
        } else

        {
            // no returns the mismatched data.
            Log.d(LOG_TAG, "Mismatched data: " + System.currentTimeMillis() + " " + gpsSentence);
            return null;
    }
        // gpsSentence == nmeaSentence+"/r/n"
        // should return with "/r/n"
        return gpsSentence;
    }

    public int getGpsStatusChange() {
        currentNmeaStatus.notified();
        if (currentGpsStatus == GPS_NOTIFY) {
            currentGpsStatus = GPS_NONE;
            //if (currentNmeaStatus.canNotify()){
            return GpsStatus.GPS_EVENT_SATELLITE_STATUS;
            //} else if (currentNmeaStatus.canFixNotify()){
        } else if (currentGpsStatus == GPS_FIXED && !gpsFixNotified) {
            gpsFixNotified = true;
            return GpsStatus.GPS_EVENT_FIRST_FIX;
        }
        return 0;
    }

    public Iterator<Entry<Integer, GnssSatellite>> getSatellitesIter() {
        return gnssStatus.getSatellitesIter();
    }

    /*
     * @return true if fixed.
     */
    private boolean parseGGA() {
    /* $GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47
      
      Where:
           GGA          Global Positioning System Fix Data
           123519       Fix taken at 12:35:19 UTC
           4807.038,N   Latitude 48 deg 07.038' N
           01131.000,E  Longitude 11 deg 31.000' E
           1            Fix quality: 0 = invalid
                                     1 = GPS fix (SPS)
                                     2 = DGPS fix
                                     3 = PPS fix
                                     4 = Real Time Kinematic
                                     5 = Float RTK
                                     6 = estimated (dead reckoning) (2.3 feature)
                                     7 = Manual input mode
                                     8 = Simulation mode
           08           Number of satellites being tracked
           0.9          Horizontal dilution of position
           545.4,M      Altitude, Meters, above mean sea level
           46.9,M       Height of geoid (mean sea level) above WGS84
                            ellipsoid
           (empty field) time in seconds since last DGPS update
           (empty field) DGPS station ID number
           *47          the checksum data, always begins with *
     */
        // UTC time of fix HHmmss.S
        String time = splitter.next();
        // latitude ddmm.M
        String lat = splitter.next();
        // direction (N/S)
        String latDir = splitter.next();
        // longitude dddmm.M
        String lon = splitter.next();
        // direction (E/W)
        String lonDir = splitter.next();
    /* fix quality: 
      0= invalid
      1 = GPS fix (SPS)
      2 = DGPS fix
      3 = PPS fix
      4 = Real Time Kinematic
      5 = Float RTK
      6 = estimated (dead reckoning) (2.3 feature)
      7 = Manual input mode
      8 = Simulation mode
     */
        String quality = splitter.next();
        // Number of satellites being tracked
        String nbSat = splitter.next();
        // Horizontal dilution of position (float)
        String hdop = splitter.next();
        // Altitude, Meters, above mean sea level
        String alt = splitter.next();
        String altUnit = splitter.next();
        // Height of geoid (mean sea level) above WGS84 ellipsoid
        String geoAlt = splitter.next();
        String geoUnit = splitter.next();
        // time in seconds since last DGPS update
        // DGPS station ID number
        //
        // Update GNSS object status
        if (quality != null && !quality.equals("") && !quality.equals("0")) {
            long timestamp = parserUtil.parseNmeaTime(time);
            currentNmeaStatus.recvGGA(true, timestamp);
            gnssStatus.setFixTimestamp(timestamp);
            gnssStatus.setQuality(parserUtil.parseNmeaInt(quality));
            if (lat != null && !lat.equals("")) {
                gnssStatus.setLatitude(parserUtil.parseNmeaLatitude(lat, latDir));
            }
            if (lon != null && !lon.equals("")) {
                gnssStatus.setLongitude(parserUtil.parseNmeaLongitude(lon, lonDir));
            }
            if (hdop != null && !hdop.equals("")) {
                gnssStatus.setHDOP(parserUtil.parseNmeaFloat(hdop));
            }
            if (alt != null && !alt.equals("")) {
                gnssStatus.setAltitude(parserUtil.parseNmeaAlt(alt, altUnit));
            }
            if (nbSat != null && !nbSat.equals("")) {
                gnssStatus.setNbSat(parserUtil.parseNmeaInt(nbSat));
            }
            if (geoAlt != null & !geoAlt.equals("")) {
                gnssStatus.setHeight(parserUtil.parseNmeaFloat(geoAlt));
            }
            return true;
        } else if (quality.equals("0")) {
            long timestamp = parserUtil.parseNmeaTime(time);
            gnssStatus.setTimestamp(timestamp);
            return false;
        } else {
            Log.e(LOG_TAG, "Unknown status of GGA quality");
            return false;
        }
    }

    /*
     * @return boolean: true when fixed
     */
    private boolean parseRMC() {
    /* $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A

       Where:
         RMC          Recommended Minimum sentence C
         123519       Fix taken at 12:35:19 UTC
         A            Status A=active or V=Void.
         4807.038,N   Latitude 48 deg 07.038' N
         01131.000,E  Longitude 11 deg 31.000' E
         022.4        Speed over the ground in knots
         084.4        Track angle in degrees True
         230394       Date - 23rd of March 1994
         003.1,W      Magnetic Variation
         *6A          The checksum data, always begins with *
    */
        // UTC time of fix HHmmss.S
        String time = splitter.next();
        // fix status (A/V)
        String status = splitter.next();
        // latitude ddmm.M
        String lat = splitter.next();
        // direction (N/S)
        String latDir = splitter.next();
        // longitude dddmm.M
        String lon = splitter.next();
        // direction (E/W)
        String lonDir = splitter.next();
        // Speed over the ground in knots
        String speed = splitter.next();
        // Track angle in degrees True
        String bearing = splitter.next();
        // UTC date of fix DDMMYY
        String date = splitter.next();
        // Magnetic Variation ddd.D
        String magn = splitter.next();
        // Magnetic variation direction (E/W)
        String magnDir = splitter.next();
        // for NMEA 0183 version 3.00 active the Mode indicator field is added
        // Mode indicator, (A=autonomous, D=differential, E=Estimated, N=not valid, S=Simulator )
        gnssStatus.setMode(status);
        long timestamp = parserUtil.parseNmeaTime(time);
        if (status != null && !status.equals("") && status.equals("A")) {
            gnssStatus.setFixTimestamp(timestamp);
            currentNmeaStatus.recvRMC(true, timestamp);
            Log.d(LOG_TAG, "recRMC = True");
            if (lat != null && !lat.equals("")) {
                gnssStatus.setLatitude(parserUtil.parseNmeaLatitude(lat, latDir));
            }
            if (lon != null && !lon.equals("")) {
                gnssStatus.setLongitude(parserUtil.parseNmeaLongitude(lon, lonDir));
            }
            if (speed != null && !speed.equals("")) {
                gnssStatus.setSpeed(parserUtil.parseNmeaSpeed(speed, "N"));
            }
            if (bearing != null && !bearing.equals("")) {
                gnssStatus.setBearing(parserUtil.parseNmeaFloat(bearing));
            }
            return true;
        } else if (status.equals("V")) {
            currentNmeaStatus.recvRMC(false, timestamp);
            Log.d(LOG_TAG, "recvRMC = False");
            return false;
        }
        return false;
    }

    private boolean parseRMCSpeed() {
    /* $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A

       Where:
         RMC          Recommended Minimum sentence C
         123519       Fix taken at 12:35:19 UTC
         A            Status A=active or V=Void.
         4807.038,N   Latitude 48 deg 07.038' N
         01131.000,E  Longitude 11 deg 31.000' E
         022.4        Speed over the ground in knots
         084.4        Track angle in degrees True
         230394       Date - 23rd of March 1994
         003.1,W      Magnetic Variation
         *6A          The checksum data, always begins with *

      This function parse only speed and bearing as lat/lon are already
      computed in parseGGA()
    */
        // UTC time of fix HHmmss.S
        String time = splitter.next();
        // fix status (A/V)
        String status = splitter.next();
        // latitude ddmm.M
        String lat = splitter.next();
        // direction (N/S)
        String latDir = splitter.next();
        // longitude dddmm.M
        String lon = splitter.next();
        // direction (E/W)
        String lonDir = splitter.next();
        // Speed over the ground in knots
        String speed = splitter.next();
        // Track angle in degrees True
        String bearing = splitter.next();
        // UTC date of fix DDMMYY
        String date = splitter.next();
        // Magnetic Variation ddd.D
        String magn = splitter.next();
        // Magnetic variation direction (E/W)
        String magnDir = splitter.next();
        // for NMEA 0183 version 3.00 active the Mode indicator field is added
        // Mode indicator, (A=autonomous, D=differential, E=Estimated, N=not valid, S=Simulator )
        gnssStatus.setMode(status);
        //long timestamp = parserUtil.parseNmeaTime(time);
        if (status != null && !status.equals("") && status.equals("A")) {
            //gnssStatus.setFixTimestamp(timestamp);
            //currentNmeaStatus.recvRMC(true, timestamp);
            //if (lat != null && !lat.equals("")) {
            //    gnssStatus.setLatitude(parserUtil.parseNmeaLatitude(lat, latDir));
            //}
            //if (lon != null && !lon.equals("")) {
            //    gnssStatus.setLongitude(parserUtil.parseNmeaLongitude(lon, lonDir));
            //}
            if (speed != null && !speed.equals("")) {
                gnssStatus.setSpeed(parserUtil.parseNmeaSpeed(speed, "N"));
            }
            if (bearing != null && !bearing.equals("")) {
                gnssStatus.setBearing(parserUtil.parseNmeaFloat(bearing));
            }
            return true;
        }
        return false;
    }

    private void parseGSA(String system) {
        // system can be GP GN GL QZ
    /*  $GPGSA,A,3,04,05,,09,12,,,24,,,,,2.5,1.3,2.1*39

      Where:
           GSA      Satellite status
           A        Auto selection of 2D or 3D fix (M = manual) 
           3        3D fix - values include: 1 = no fix
                                             2 = 2D fix
                                             3 = 3D fix
           04,05... PRNs of satellites used for fix (space for 12) 
           2.5      PDOP (Position dilution of precision) 
           1.3      Horizontal dilution of precision (HDOP) 
           2.1      Vertical dilution of precision (VDOP)
           *39      the checksum data, always begins with *
     */
        // mode : A Auto selection of 2D or 3D fix / M = manual
        String mode = splitter.next();
        gnssStatus.setMode(mode);
        // fix type  : 1 - no fix / 2 - 2D / 3 - 3D
        String fixType = splitter.next();

        if (system.equals("GP")) {
            gnssStatus.clearTrackedSatellites();
        }

        if (!fixType.equals("1")) {
            String prn;

            for (int i = 0; i < 12; i++) {
                prn = splitter.next();
                if (prn != null && !prn.equals("")) {
                    gnssStatus.addTrackedSatellites(Integer.parseInt(prn));
                }
            }


            // Position dilution of precision (float)
            String pdop = splitter.next();
            // Horizontal dilution of precision (float)
            String hdop = splitter.next();
            // Vertical dilution of precision (float)
            String vdop = splitter.next();
            gnssStatus.setPDOP(Float.parseFloat(pdop));
            gnssStatus.setHDOP(Float.parseFloat(hdop));
            gnssStatus.setVDOP(Float.parseFloat(vdop));
        }
        currentNmeaStatus.recvGSA();
    }

    private void parseGSV(String system) {
    /*   $GPGSV,2,1,08,01,40,083,46,02,17,308,41,12,07,344,39,14,22,228,45*75
        Where:
            GSV          Satellites in view
            2            Number of sentences for full data
            1            sentence 1 of 2
            08           Number of satellites in view
            01           Satellite PRN number
            40           Elevation, degrees
            083          Azimuth, degrees
            46           SNR - higher is better
                         for up to 4 satellites per sentence
            *75          the checksum data, always begins with *


        Sometimes got $GPGSV,1,1,00*75 when just started
        or inside building.

     */
        String totalGsvSentence = splitter.next();
        String currentGsvSentence = splitter.next();
        String satellitesInView = splitter.next();

        Integer numCurrentGsvSentence = Integer.parseInt(currentGsvSentence);
        Integer numTotalGsvSentence = Integer.parseInt(totalGsvSentence);
        Integer numSatellitesInView = Integer.parseInt(satellitesInView);

        if (numCurrentGsvSentence == 1) { // count num of satellites in view
            if ("GP".equals(system)) { // first sentence and GP
                activeSatellites.clear();
                gnssStatus.setNumSatellites(numSatellitesInView);
            } else {
                gnssStatus.addNumSatellites(numSatellitesInView);
            }
        }

        if (numSatellitesInView != 0) {
            int numRecord = 4;
            if (numCurrentGsvSentence == numTotalGsvSentence) { // last sentence
                numRecord = numSatellitesInView % 4;
                if (numRecord == 0) {
                    numRecord = 4;
                }
            }
            for (int i = 0; i < numRecord; i++) {
                //
                // Heulistics for recognize satellite system
                //
                // GPGSA sometimes report Galilleo, QZSS, SBS others with
                // PRN > 32
                // known example
                //
                //-------------------------------------------------------------------
                // SBS numbering with GPGSV
                //
                //   PRN 122, 134, 135, 138‚ are American WAAS satellites
                //   PRN 120, 124, 126, 131 are European EGNOS satellites
                //   PRN 127, 128 are GAGAN satellites
                //   PRN 129, 137 are Japanese‚ MSAS satellites
                //   PRN 140, 125, 141 are SDCM satellites
                //
                //   NMEA ID 42, 50 for MSAS by JP
                //
                //   PRN  -  NMEA ID
                //    120 = 33
                //    121 = 34
                //    122 = 35
                //    123 = 36
                //    135 = 48
                //    136 = 49
                //    137 = 50
                //    138 = 51
                //    157 = 70
                //    158 = 71
                //   NMEA ID is same as PRN -87 for SBS
                //
                // -----------------------------------------------------------------
                //   PRN 193..197 for QZSS ID
                //   PRN 183 for QZSS L1-SAIF
                //   PRN 201..210 for BEIDOU
                //
                // some GNSS report QZ*** and PRN=1 for QZSS.
                // other report  GP*** and RPN=193.
                //
                // Galileo has Satellite ID 1..36 with GA specifier
                //
                // SBS numbering with GLGSV
                //   PRN 33-64 are reserved for SBS
                //
                // SBS numbering with GAGSV
                //   PRN 37-64 are reserved for SBS
                //
                //------------------------------------------------------------------
                // IMES id are 173-182
                String prn = splitter.next();
                String decidedSystem;
                if (prn != null && !prn.equals("")) {
                    String elevation = splitter.next();
                    String azimuth = splitter.next();
                    String snr = splitter.next();

                    int nprn = Integer.parseInt(prn);
                    if (system.equals("QZ")) {
                        decidedSystem = "QZ";
                    } else if (system.equals("GL") && nprn < 64) {
                        // Maybe GLONASS SBS satellite
                        // treat as GLONASS (FIXME)
                        decidedSystem = "GL";
                    } else if (system.equals("GP") && nprn == 193) {
                        // Some receiver report QZSS with GP prefix
                        decidedSystem = "QZ";
                    } else if (system.equals("GP") && 200 < nprn && nprn < 211) {
                        decidedSystem = "BD"; // maybe beidou/compass
                    } else if (system.equals("GP") && 32 < nprn && nprn < 72) {
                        decidedSystem = "SB";
                        nprn = nprn + 87;
                    } else if (system.equals("GP") && 119 < nprn && nprn < 139) { // 120..138
                        decidedSystem = "SB";
                    } else if (system.equals("BD") && nprn < 16) {
                        decidedSystem = "BD";
                        nprn = nprn + 200;
                    } else {
                        decidedSystem = system;
                    }
                    GnssSatellite sat = new GnssSatellite(decidedSystem, nprn);
                    sat.setStatus(parserUtil.parseNmeaFloat(elevation),
                            parserUtil.parseNmeaFloat(azimuth),
                            parserUtil.parseNmeaFloat(snr));
                    gnssStatus.addSatellite(sat);
                    activeSatellites.add(nprn);
                } else {
                    break;
                }
            }
            if (numCurrentGsvSentence == numTotalGsvSentence) { // last sentence
                gnssStatus.clearSatellitesList(activeSatellites);
            }
        }
        currentNmeaStatus.recvGSV();
    }

    private void parseVTG() {
    /*  $GPVTG,054.7,T,034.4,M,005.5,N,010.2,K*48
      
      where:
              VTG          Track made good and ground speed
              054.7,T      True track made good (degrees)
              034.4,M      Magnetic track made good
              005.5,N      Ground speed, knots
              010.2,K      Ground speed, Kilometers per hour
              *48          Checksum
     */
        // Track angle in degrees True
        String bearing = splitter.next();
        // T
        splitter.next();
        // Magnetic track made good
        String magn = splitter.next();
        // M
        splitter.next();
        // Speed over the ground in knots
        String speedKnots = splitter.next();
        // N
        splitter.next();
        // Speed over the ground in Kilometers per hour
        String speedKm = splitter.next();
        // K
        splitter.next();
        // for NMEA 0183 version 3.00 active the Mode indicator field is added
        // Mode indicator, (A=autonomous, D=differential, E=Estimated, N=not valid, S=Simulator )

        if (speedKm != null && !speedKm.equals("")) {
            gnssStatus.setSpeed(parserUtil.parseNmeaSpeed(speedKm, "K"));
        }
        if (bearing != null && !bearing.equals("")) {
            gnssStatus.setBearing(parserUtil.parseNmeaFloat(bearing));
        }

        // notify VTG received
        currentNmeaStatus.recvVTG();
    }

    private void parseGLL() {
    /*  $GPGLL,4916.45,N,12311.12,W,225444,A,*1D
      Where:
			       GLL          Geographic position, Latitude and Longi
			                     4916.46,N    Latitude 49 deg. 16.45 min. North
			                     12311.12,W   Longitude 123 deg. 11.12 min. West
			                     225444       Fix taken at 22:54:44 UTC
			                     A            Data Active or V (void)
			                     *iD          checksum data
	  */
        // latitude ddmm.M
        //String lat = splitter.next();
        splitter.next();

        // direction (N/S)
        //String latDir = splitter.next();
        splitter.next();

        // longitude dddmm.M
        //String lon = splitter.next();
        splitter.next();

        // direction (E/W)
        //String lonDir = splitter.next();
        splitter.next();

        // UTC time of fix HHmmss.S
        String time = splitter.next();

        // fix status (A/V)
        //String status = splitter.next();

        // for NMEA 0183 version 3.00 active the Mode indicator field is
        // Mode indicator, (A=autonomous, D=differential, E=Estimated, N=
        currentNmeaStatus.recvGLL(parserUtil.parseNmeaTime(time));
    }

    public void parseGNS() {
    /*
     * GNS: GNSS fix data
     *
     * GNSS capable receivers may output this message with the GN talker ID
     * GNSS capable receivers also output this message with the GP and/or GL talker ID
     * when using more than one constellation for the position fix
     * An example of the GNS message output from a GNSS capable receiver is:
     * $GNGNS,014035.00,4332.69262,S,17235.48549,E,RR,13,0.9,25.63,11.24,,*70<CR><LF>
     * $GPGNS,014035.00,,,,,,8,,,,1.0,23*76<CR><LF>
     * $GLGNS,014035.00,,,,,,5,,,,1.0,23*67<CR><LF>
     *
     * Field 	Meaning
     * 1 	UTC of position fix
     * 2 	Latitude
     * 3 	Direction of latitude:
     *                N: North
     *                S: South
     * 4 	Longitude
     * 5 	Direction of longitude:
     *                E: East
     *                W: West
     * 6 	Mode indicator:
     *  Variable character field with one character for each supported constellation.
     *  First character is for GPS
     *  Second character is for GLONASS
     *  Subsequent characters will be added for new constellation
     *  
     *     N = No fix. not used in position fix, or fix not valid
     *     A = Autonomous
     *     D = Differential
     *     P = Precise(no SA, P code)
     *     R = RTK
     *     F = Float RTK
     *     E = Estimated
     *     M = Manual Input Mode
     *     S = Simulator Mode
     * 7 	Number of SVs in use, range 00–99
     * 8 	HDOP calculated using all the satellites (GPS, GLONASS, etc)
     * 9 	Orthometric height in meters
     * 10	Geoidal separation in meters
     *    “-” = mean-sea-level surface below ellipsoid.
     * 11 	Age of differential data
     *      - Null if talker ID is GN, 
     *        additional GNS messages follow with GP 
     *        and/or GL Age of differential data
     * 12 	Reference station ID1, range 0000-4095
     *      - Null if talker ID is GN,
     *        additional GNS messages follow with GP
     *        and/or GL Reference station ID
     * 13 	The checksum data, always begins with *
     */
        String time = splitter.next();
        //String latitude = splitter.next();
        //String latDir = splitter.next();
        //String longitude = splitter.next();
        //String lonDir = splitter.next();
        //String mode   = splitter.next();
        //String numNbSat = splitter.next();
        //String hdop   = splitter.next();
        //String height = splitter.next();
        //String geoid  = splitter.next();
        currentNmeaStatus.recvGNS(parserUtil.parseNmeaTime(time));
    }

    public byte computeChecksum(String s) {
        byte checksum = 0;
        for (char c : s.toCharArray()) {
            checksum ^= (byte) c;
        }
        return checksum;
    }

}
