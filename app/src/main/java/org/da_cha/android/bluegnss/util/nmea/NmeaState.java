/*
 * Copyright 2014, Hiroshi Miura <miurahr@linux.com>
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

package org.da_cha.android.bluegnss.util.nmea;

/**
 * This class is used to keep NMEA sentence status.
 *
 */
public class NmeaState {

  private long timestamp;
  private boolean fixed = false;
  private enum nmeastate {START, RECEIVE, COMPLETE}
  private nmeastate currentStatus = nmeastate.START;
  private boolean notified = false;

  private boolean hasGGA = false;
  private boolean hasRMC = false;
  private boolean hasGLL = false;
  
  public long getTimestamp(){
    return this.timestamp;
  }
  public boolean canNotify(){
    return (this.currentStatus == nmeastate.COMPLETE && this.notified == false);
  }

  public boolean isFixed(){
    return this.fixed;
  }

  public boolean canFixNotify(){
    return (this.fixed && this.currentStatus == nmeastate.COMPLETE && this.notified == false);
  }

  public void notified(){
    this.notified = true;
  }

  /*
   * NMEA-0183 sequence examples
   * there are variations how receiver produce sentences.
   *
   * GPGGA->GPGLL->GPGSA->GPGSV->GPRMC->GPVTG
   *
   * GPGGA->GNGLL->GNGSA(for GPS)->GNGSA(for others)->GNRMC->GPVTG
   * GNGGA->GNGLL->GNGSA(for GPS)->GNGSA(for others)->GNRMC->GPVTG
   *
   * GNGGA->GNGLL->GNGSA->GNGSA->GPGSV->GPGSV(for GPS)->GLGSV->GLGSV(for GLONASS)
   * ->GNRMC->GPVTG
   *
   * GPGSV->GPGGA->GPRMC->GPGSA->GPVTG
   * GPGGA->GPRMC->GPGGA
   * 
   * Some (BCM) produces QZGSV
   *
   * Assumes every receiver produce GPRMC/GNRMC and can send fix just after
   * RMC sentence.
   * Assumes just after GGA, starting new sequence of sentences.
   * When accepting VTG, sequence is lasted.
   *
   * An input file may have both GGA and GLL and RMC sentences for the exact
   * same position fix. If we see a single GGA, start ignoring GLL's and RMC's.
   * GLL's will also be ignored if RMC's are found and GGA's not found.
   *
   */
  public boolean recvGGA(boolean fixed, long time){
    this.hasGGA = true;
    this.fixed = fixed;
    this.timestamp = time;
    nmeastate previousStatus = currentStatus;
    this.currentStatus = nmeastate.RECEIVE;
    this.notified = false;
    return (previousStatus == nmeastate.START);
  }

  public boolean shouldUseRMC(){
    if (hasGGA) {
      return false;
    }
    return true;
  }

  public boolean shouldUseGLL(){
    if (hasGGA || hasRMC) {
      return false;
    }
    return true;
  }

    public boolean shouldUseVTG() {
        if (hasRMC) {
            return false;
        }
        return true;
    }

  public boolean recvRMC(boolean fixed, long time){
    this.hasRMC=true;
    if (this.timestamp != time){
      this.currentStatus = nmeastate.START;
      return false; // invalid
    }
    this.currentStatus = nmeastate.COMPLETE;
    return true;
  }
  public void recvVTG(){
    this.currentStatus = nmeastate.START;
  }
  public boolean recvGLL(long time){
    this.hasGLL=true;
    return (this.timestamp == time && this.currentStatus == nmeastate.RECEIVE);
  }
  public boolean recvGNS(long time){
    return (this.timestamp == time && this.currentStatus == nmeastate.RECEIVE);
  }
  public boolean recvGSA(){
    return (this.currentStatus == nmeastate.RECEIVE);
  }
  public boolean recvGSV(){
    return (this.currentStatus == nmeastate.RECEIVE);
  }
}
