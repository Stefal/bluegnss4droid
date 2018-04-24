/*
 * Copyright 2014 Hiroshi Miura <miurahr@linux.com>
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

package org.da_cha.android.bluegnss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Iterator;

import android.os.Bundle;
import android.os.SystemClock;
import android.location.Location;
import android.location.LocationManager;

import org.da_cha.android.bluegnss.GnssSatellite;

public class GnssStatus {

  private long timestamp;
	private long fixTimestamp;
  private long startTimestamp;
  private long firstFixTimestamp=0;
  private float PDOP;
  private float HDOP;
  private float VDOP;
  private float precision;
  private double latitude;
  private double longitude;
  private double altitude;
  private double height;
  private float speed;
  private float angle;
  private int nbsat;
  private int numSatellites;
  /* 1: nofix, 2: 2D fix, 3: 3D fix */
  private int fixMode; 
  /* 
       Fix quality: 0 = invalid
                    1 = GPS fix (SPS)
                    2 = DGPS fix
                    3 = PPS fix
                    4 = Real Time Kinematic
                    5 = double RTK
                    6 = estimated (dead reckoning) (2.3 feature)
                    7 = Manual input mode
                    8 = Simulation mode
   */
  private int quality;
  // for NMEA 0183 version 3.00 active the Mode indicator field is added
  // Mode indicator, (A=autonomous, D=differential, E=Estimated, N=not valid, S=Simulator )
  private String mode;


  /******************************************************************************
   * 
   * GnssSatellite list in view
   * satellites PRN list in fix
   *
   */
  private HashMap<Integer, GnssSatellite> gnssSatellitesList = new HashMap<Integer, GnssSatellite>();

  // satallite list
  // accessor
  public void addSatellite(GnssSatellite sat){
    gnssSatellitesList.put(sat.getRpn(), sat);
  }
  // clear list at all
  public void clearSatellitesList(){
    gnssSatellitesList.clear();
  }
  // clear list except for ones in activeList
  public void clearSatellitesList(ArrayList<Integer> activeList){
    ArrayList<Integer> currentRpnList = new ArrayList<Integer>();
    for (Integer rpn: gnssSatellitesList.keySet()){
      currentRpnList.add(rpn);
    }
    for (Integer i : currentRpnList){
      if (!activeList.contains(i)) {
        removeSatellite(i);
      }
    }
  }

  // remove sat data, return cleared satellite
  public GnssSatellite removeSatellite(int rpn){
    return gnssSatellitesList.remove(rpn);
  }
  // get sat data with index
  public GnssSatellite getSatellite(int rpn){
    return gnssSatellitesList.get(rpn);
  }
  // get iterator
  public Iterator<Entry<Integer, GnssSatellite>> getSatellitesIter(){
    return gnssSatellitesList.entrySet().iterator();
  }
  // get list of satellites
  public ArrayList<GnssSatellite> getSatellitesList(){
    return new ArrayList<GnssSatellite>(gnssSatellitesList.values());
  }


  /***************************************************************************
   *
   * tracked satellites PRN list
   *
   */
  private ArrayList<Integer> satellitesPrnListInFix = new ArrayList<Integer>();

  public void setTrackedSatellites(ArrayList<Integer> rpnList){
      satellitesPrnListInFix.clear();
      for (Integer rpn : rpnList){
        satellitesPrnListInFix.add(rpn);
      }
  }

  public void addTrackedSatellites(int rpn){
      satellitesPrnListInFix.add(rpn);
  }

  public ArrayList<Integer> getTrackedSatellites() {
      return satellitesPrnListInFix;
  }

  public void clearTrackedSatellites(){
      this.satellitesPrnListInFix.clear();
  }

  /*****************************************************************************
   *
   * time stamps handler
   *
   */
  public void clearTTFF(){
    this.firstFixTimestamp=0;
  }
  public long getTTFF(){
    if (this.firstFixTimestamp == 0 || this.startTimestamp == 0 ||
        this.firstFixTimestamp < this.startTimestamp) {
      // invalid status
      return 0;
    } else {
      return this.firstFixTimestamp - this.startTimestamp;
    }
  }
  public long getFixTimestamp(){
    return this.fixTimestamp;
  }
  public void setFixTimestamp(long timestamp){
    this.timestamp = timestamp;
    this.fixTimestamp = timestamp;
    if (this.firstFixTimestamp == 0){
      this.firstFixTimestamp = timestamp;
    }
  }
  public void setTimestamp(long timestamp){
    if (this.startTimestamp ==0 ){
      this.startTimestamp = timestamp;
    }
    if (this.timestamp != timestamp){
      // new result will be notified.
      this.numSatellites = 0;
    }
    this.timestamp = timestamp;
  }
  public long getTimestamp(){
    return this.timestamp;
  }

  /***************************************************************************
   *
   * clear all
   */
  public void clear(){
    clearTTFF();
    clearSatellitesList();
    timestamp = 0;
    startTimestamp = 0;
	  fixTimestamp = 0;
    precision = 10f;
    PDOP = 0f;
    HDOP = 0f;
    VDOP = 0f;
    latitude  = 0d;
    longitude = 0d;
    altitude  = 0d;
    height    = 0d;
    speed     = 0f;
    angle     = 0f;
    nbsat = 0;
    numSatellites = 0;
    fixMode = 0;
    quality = 0;
    mode = "N";
  }

  /**********************************************************************
   * 
   * @return Location fix
   */
  public Location getFixLocation(){
    Location fix = new Location(LocationManager.GPS_PROVIDER);

    if (android.os.Build.VERSION.SDK_INT >= 17)
        fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

    fix.setLatitude(this.latitude);
    fix.setLongitude(this.longitude);
    fix.setAccuracy(this.HDOP*this.precision);
    fix.setTime(this.fixTimestamp);
    fix.setAltitude(this.altitude);
    Bundle extras = new Bundle();
    extras.putInt("satellites", this.nbsat);
    fix.setExtras(extras);
    fix.setBearing(angle);
    fix.setSpeed(speed);

    return fix;
  }

  /***********************************************************************
   *
   * accessors
   */

  // Lat/Lon/Alt
  public double getLatitude(){
    return this.latitude;
  }
  public void setLatitude(double lat){
    this.latitude = lat;
  }
  public double getLongitude(){
    return this.longitude;
  }
  public void setLongitude(double lon){
    this.longitude = lon;
  }
  public double getAltitude(){
    return this.altitude;
  }
  public void setAltitude(double alt){ this.altitude = alt;
  }
  // DOP/precision
  public float getPrecision(){
    return this.precision;
  }
  public void setPrecision(float p){
    this.precision = p;
  }
  public double getPDOP(){
    return this.PDOP;
  }
  public void setPDOP(float dop){
    this.PDOP = dop;
  }
  public double getHDOP(){
    return this.HDOP;
  }
  public void setHDOP(float dop){
    this.HDOP = dop;
  }
  public double getVDOP(){
    return this.VDOP;
  }
  public void setVDOP(float dop){
    this.VDOP = dop;
  }
  // others
  public double getHeight(){
    return this.height;
  }
  public void setHeight(double height){
    this.height = height;
  }
  public float getSpeed(){
    return this.speed;
  }
  public void setSpeed(float speed){
    this.speed = speed;
  }
  public double getBearing(){
    return this.angle;
  }
  public void setBearing(float angle){
    this.angle = angle;
  }
  public int getQuality(){
    return this.quality;
  }
  public void setQuality(int q){
    this.quality = q;
  }
  public int getNbSat(){
    return this.nbsat;
  }
  public void setNbSat(int sat){
    this.nbsat = sat;
  }
  public String getMode(){
    return this.mode;
  }
  public void setMode(String mode){
    this.mode = mode;
  }
  public int getNumSatellites(){
    return this.numSatellites;
  }
  public void setNumSatellites(int num){
    this.numSatellites = num;
  }
  public void addNumSatellites(int num){
    this.numSatellites = this.numSatellites + num;
  }
  public int getFixMode(){
    return this.fixMode;
  }
  public void setFixMode(int mode){
    this.fixMode = mode;
  }

}
// vim: tabstop=4 expandtab shiftwidth=4 softtabstop=4
