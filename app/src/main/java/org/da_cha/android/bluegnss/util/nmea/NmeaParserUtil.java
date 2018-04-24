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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import android.util.Log;

/**
 * This class is used to parse NMEA sentences.
 */
public class NmeaParserUtil {
  private String LOG_TAG = "BlueGNSS";

	public double parseNmeaLatitude(String lat,String orientation){
		double latitude = 0.0;
		if (lat != null && orientation != null && !lat.equals("") && !orientation.equals("")){
			double temp1 = Double.parseDouble(lat);
			double temp2 = Math.floor(temp1/100); 
			double temp3 = (temp1/100 - temp2)/0.6;
			if (orientation.equals("S")){
				latitude = -(temp2+temp3);
			} else if (orientation.equals("N")){
				latitude = (temp2+temp3);
			}
		}
		return latitude;
	}
	public double parseNmeaLongitude(String lon,String orientation){
		double longitude = 0.0;
		if (lon != null && orientation != null && !lon.equals("") && !orientation.equals("")){
			double temp1 = Double.parseDouble(lon);
			double temp2 = Math.floor(temp1/100); 
			double temp3 = (temp1/100 - temp2)/0.6;
			if (orientation.equals("W")){
				longitude = -(temp2+temp3);
			} else if (orientation.equals("E")){
				longitude = (temp2+temp3);
			}
		}
		return longitude;
	}
	public float parseNmeaSpeed(String speed,String metric){
		float meterSpeed = 0.0f;
		if (speed != null && metric != null && !speed.equals("") && !metric.equals("")){
			float temp1 = Float.parseFloat(speed)/3.6f;
			if (metric.equals("K")){
				meterSpeed = temp1;
			} else if (metric.equals("N")){
				meterSpeed = temp1*1.852f;
			}
		}
		return meterSpeed;
	}
	public long parseNmeaTime(String time){
		long timestamp = 0;
		SimpleDateFormat fmt = new SimpleDateFormat("HHmmss.SSS");
		fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
		try {
			if (time != null && time != null){
				long now = System.currentTimeMillis();
				long today = now - (now %86400000L);
				long temp1;
				// sometime we don't have millisecond in the time string, so we have to reformat it 
				temp1 = fmt.parse(String.format((Locale)null,"%010.3f", Double.parseDouble(time))).getTime();
				long temp2 = today+temp1;
				// if we're around midnight we could have a problem...
				if (temp2 - now > 43200000L) {
					timestamp  = temp2 - 86400000L;
				} else if (now - temp2 > 43200000L){
					timestamp  = temp2 + 86400000L;
				} else {
					timestamp  = temp2;
				}
			}
		} catch (ParseException e) {
			Log.e(LOG_TAG, "Error while parsing NMEA time", e);
		}
		return timestamp;
	}

  public Double parseNmeaAlt(String s, String u){
    if (s != null && u != null && !s.equals("") && !u.equals("")){
      if (u.equals("M") || u.equals("m")){
			  return Double.parseDouble(s);
      } else if (u.equals("KM") || u.equals("km")){
			  return Double.parseDouble(s) * 1000.0;
      } else {
			  return Double.parseDouble(s);
      }
		} else {
			return Double.NaN;
		}
  }

	public float parseNmeaFloat(String s){
		if (s!=null &&!s.equals("")){
			return Float.parseFloat(s);
		} else {
			return Float.NaN;
		}
	}

	public int parseNmeaInt(String s){
		if (s!=null &&!s.equals("")){
			return Integer.parseInt(s);
		} else {
			return 0;
		}
	}

}
