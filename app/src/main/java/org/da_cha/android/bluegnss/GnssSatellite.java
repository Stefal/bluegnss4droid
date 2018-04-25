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

public class GnssSatellite {
  public float azimuth;
  public float elevation;
  public float snr; 
  private int rpn;

  private enum SatelliteSystem {GPS, GLONASS, GALILEO, QZSS, IRNASS, BEIDOU, SBS, UNKNOWN}
  private SatelliteSystem system;

  /*
   * Satellite identification
   *
   * System   System ID   Satellite ID        Signal ID      Signal/Channel
   * GPS       1 (GP)        1-99                0            All Signals
   *                         1-32 reserve GPS    1            L1 C/A
   *                         33-64 reserve SBAS  2            L1 P(Y)
   *                         66-99 undefined     3            L1 M
   *                                             4            L2 P(Y)
   *                                             5            L2C-M
   *                                             6            L5-I
   *                                             7            L5-Q
   *                                             9-F          Reserved
   *
   * GLONASS  2 (GL)         1-99                0            All Signals
   *                         1-32 undefined      1            G1 C/A
   *                         32-64 for SBAS      2            G1 P
   *                         65-99 for GLONASS   3            G2 C/A
   *                                             4            GLONASS (M) G2 P
   *                                             5-F          Reserved
   *
   * GALILEO  3 (GA)         1-99                0            All signals
   *                         1-36 for Galileo    1            E5a
   *                         37-64 for SBAS      2            E5b
   *                         65-99 undefined     3            E5 a+b
   *                                             4            E6-A
   *                                             5            E6-BC
   *                                             6            L1-A
   *                                             7            L1-BC
   *                                             8-F          Reserved
   *
   * BEIDOU  unknown(BD)
   * 
   * QZSS     ? (QZ)         193                 unknown      unknown
   *
   */

  public GnssSatellite(String systemid, int rpn){
    this.rpn = rpn;
    setSystem(systemid);
  }

  private void setSystem(String systemid){
    if (systemid.equals("GP")){
      this.system = SatelliteSystem.GPS;
    } else if (systemid.equals("GL")){
      this.system = SatelliteSystem.GLONASS;
    } else if (systemid.equals("QZ")){
      this.system = SatelliteSystem.QZSS;
    } else if (systemid.equals("GA")){
      this.system = SatelliteSystem.GALILEO;
    } else if (systemid.equals("SB")){
      this.system = SatelliteSystem.SBS;
    } else if (systemid.equals("BD")){
      this.system = SatelliteSystem.BEIDOU;
    } else if (systemid.equals("GB")){
      this.system = SatelliteSystem.BEIDOU;
    } else {
      // unknown satellite
      this.system = SatelliteSystem.UNKNOWN;
    }
  }

  public int getRpn(){
    return this.rpn;
  }
  public boolean isRpn(int n){
    return (n == this.rpn);
  }

  // object that has same RPN are equals for satellite.
  @Override
  public int hashCode(){
    return this.rpn + this.system.ordinal() * 1000;
  }

  @Override
  public boolean equals(Object o){
    if (o == this){
      return true;
    }
    if (! (o instanceof GnssSatellite)) {
      return false;
    }
    GnssSatellite sat = (GnssSatellite)o;
    return ((sat.getRpn() == this.rpn) && sat.getSystemPrefix().equals(this.getSystemPrefix()));
  }

  public void setStatus(float elevation, float azimuth, float snr){
    if (elevation < 0) {
      this.elevation = 0;
    } else if (elevation > 90) {
      this.elevation = 90;
    } else {
      this.elevation = elevation;
    }
    if (azimuth < 0) {
      this.azimuth = 0;
    } else if (azimuth > 360) { // ignore
      this.azimuth = 0;
    } else {
      this.azimuth = azimuth;
    }
    if (snr < 0) {
      this.snr = 0;
    } else if (snr > 99){
      this.snr= 99;
    } else {
      this.snr = snr;
    }
  }

  public String getSystemPrefix(){
    return getSystemPrefix(2);
  }
  public String getSystemPrefix(int len){
    if (len == 2){
        switch(this.system){
          case GPS:
            return "GP";
          case GLONASS:
            return "GL";
          case QZSS:
            return "QZ";
          case GALILEO:
            return "GA";
          case BEIDOU:
            return "BD";
          case SBS:
            return "SB";
          default:
            return "UN";
        }
    } else if (len == 1){
        switch(this.system){
          case GPS:
            return "G";
          case GLONASS:
            return "L";
          case QZSS:
            return "Q";
          case GALILEO:
            return "E";
          case BEIDOU:
            return "B";
          case SBS:
            return "S";
          default:
            return "U";
        }
    } else {
      return "";
    }
  }

  public String getName() {
    int satnum;
    switch(this.system){
      case GLONASS:
        satnum = this.rpn - 64;
        break;
      case QZSS:
        satnum = this.rpn - 192;
        break;
      case BEIDOU:
        //satnum = this.rpn - 200;
        satnum = this.rpn;
        break;
      default:
        satnum = this.rpn;
    }

    return getSystemPrefix(1)+Integer.toString(satnum);
  }

  public float getElevation(){
    return this.elevation;
  }
  public void setElevation(float ele){
    this.elevation = ele;
  }

  public float getAzimuth(){
    return this.azimuth;
  }
  public void setAzimuth(float azi){
    this.azimuth = azi;
  }

  public float getSnr(){
    return this.snr;
  }
  public void setSnr(float snr){
    this.snr = snr;
  }

  public boolean isQzss(){
    return (this.system == SatelliteSystem.QZSS);
  }

  public boolean isGlonass(){
    return (this.system == SatelliteSystem.GLONASS);
  }

  public boolean isGalileo(){
    return (this.system == SatelliteSystem.GALILEO);
  }

  public boolean isGps(){
    return (this.system == SatelliteSystem.GPS);
  }

  public boolean isSBS(){
    return (this.system == SatelliteSystem.SBS);
  }

  public boolean isBeidou(){
    return (this.system == SatelliteSystem.BEIDOU);
  }
}
