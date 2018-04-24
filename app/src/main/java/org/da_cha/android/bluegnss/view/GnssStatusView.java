/*
 * Copyright 2014, Hiroshi Miura <miurahr@linux.com>
 * Copyright 2014, Kennichi Ohwada
 *
 * This software is distributed under the term of GPLv3
 *
 */

package org.da_cha.android.bluegnss.view;

import java.util.ArrayList;
import java.util.List;

import org.da_cha.android.bluegnss.GnssProviderService;
import org.da_cha.android.bluegnss.GnssSatellite;
import org.da_cha.android.bluegnss.GnssStatus;
import org.da_cha.android.bluegnss.util.nmea.NmeaParser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

/**
* GnssStatusView
*/
public class GnssStatusView extends View {

  private final static String LOG_TAG = "BlueGNSS";

  private final static int COLOR_GRAY = Color.argb( 255, 128, 128, 128 );
  private final static int COLOR_RED = Color.argb( 255, 255, 0, 0 );
  private final static int COLOR_BLUE = Color.argb( 255, 0, 0, 255 );
  private final static int COLOR_MAGENTA = Color.argb( 255, 255, 0, 255 );
  private final static int COLOR_DARK_GREEN = Color.argb( 255, 0, 128, 0 );
  private final static int COLOR_SKY_BLUE = Color.argb( 255, 128, 224, 224 );
  private final static int COLOR_DARK_GRAY = Color.argb( 255, 32, 32, 32);

  private final static String LABEL_NORTH = "N";
  private final static String LABEL_SOUTH = "S";
  private final static String LABEL_EAST = "E";
  private final static String LABEL_WEST = "W";

  private final static double DEG_TO_RAD = Math.PI / 180.0;
  private final static float AXIS_TEXT_SIZE = 16;
  private final static float AXIS_TEXT_MARGIN = 4;
  private final static float SAT_TEXT_SIZE = 16;
  private final static float SAT_RADIUS = 12;

  private final static double ELEVATION_RANGE = 90;
  private final static double AZIMUTH_OFFSET = 270;
            
  private Bitmap mBitmapAxis = null;
  private Paint mPaintName= null;
  private Paint mPaintSat = null;
    
  private float mDensity = 0;
   
  private float mCenterX = 0;
  private float mCenterY = 0;

  private float mAxisRadius = 0;
  private float mTextHalfHeight = 0;
  private float mSatRadius = 0;

  private List<SatellitePoint> mSatelliteList = new ArrayList<SatellitePoint>();

  public GnssStatusView( Context context, AttributeSet attrs, int defStyleAttr ) {
    super( context, attrs, defStyleAttr );
    initView( context );
  }

  public GnssStatusView( Context context, AttributeSet attrs ) {
    super( context, attrs );
    initView( context );
  }
     
  public GnssStatusView( Context context ) {
    super( context );
    initView( context );
  }

  private void initView( Context context ) {
    getScaledDensity();
    mPaintSat = new Paint();
    mPaintSat.setStyle( Paint.Style.STROKE );
    mPaintSat.setColor( COLOR_DARK_GREEN );
    mPaintSat.setStrokeWidth( 2 );	
    mPaintName = new Paint( Paint.ANTI_ALIAS_FLAG );
    mPaintName.setTextSize( SAT_TEXT_SIZE * mDensity );
    FontMetrics metrics = mPaintName.getFontMetrics();
    mTextHalfHeight = (metrics.ascent + metrics.descent) / 2;
  }

  private void getScaledDensity() {
    DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
    mDensity = metrics.scaledDensity;
  }
 
  @Override
  public void onWindowFocusChanged( boolean hasFocus ) {
    super.onWindowFocusChanged( hasFocus );
    int width = getWidth();
    int height = getHeight();
    float text_hm = ( AXIS_TEXT_SIZE + AXIS_TEXT_MARGIN ) * mDensity;

    mCenterX = width / 2;
    mCenterY = height / 2;
    float h = ( height - text_hm ) / 2;
    float r = Math.min( mCenterX, h );
    mAxisRadius = (float) ( 0.9 * r );
    mSatRadius = SAT_RADIUS * mDensity;
    initAxis( width, height );
    invalidate();
  }

  private void initAxis( int width, int height ) {
    Paint paint_back = new Paint();
    paint_back.setColor( COLOR_SKY_BLUE );	
    Paint paint_line = new Paint();
    Paint paint_circle = new Paint();
    paint_circle.setStyle( Paint.Style.STROKE );
    Paint paint_edge = new Paint();
    paint_edge.setStyle( Paint.Style.STROKE );
    paint_edge.setStrokeWidth( 2 );	
    Paint paint_text = new Paint( Paint.ANTI_ALIAS_FLAG );
    paint_text.setTextSize( AXIS_TEXT_SIZE * mDensity );

    float text_h = AXIS_TEXT_SIZE * mDensity;
    float text_m = AXIS_TEXT_MARGIN * mDensity;
    float back_r = mAxisRadius + text_h + text_m;
    float line_x0 = mCenterX - mAxisRadius;
    float line_x1 = mCenterX + mAxisRadius;
    float line_y0 = mCenterY - mAxisRadius;
    float line_y1 = mCenterY + mAxisRadius;
    float text_w = paint_text.measureText( LABEL_NORTH );
    float n_x = mCenterX - text_w / 2;
    float n_y = line_y0 - text_m;
    float s_x = n_x;
    // todo
    float s_y = line_y1 + text_m + text_h - 4;
    float e_x = line_x0 - text_m - text_w;
    float e_y = mCenterY + text_h / 2 ;
    float w_x = line_x1 + text_m ;
    float w_y = e_y;

    mBitmapAxis = Bitmap.createBitmap( width, height, Bitmap.Config.RGB_565 );
    Canvas canvas = new Canvas();
    canvas.setBitmap( mBitmapAxis );
    canvas.drawColor( COLOR_GRAY );
    canvas.drawCircle( mCenterX, mCenterY, back_r, paint_back );
    canvas.drawLine( line_x0, mCenterY, line_x1, mCenterY, paint_line );
    canvas.drawLine( mCenterX, line_y0, mCenterX, line_y1, paint_line );
    canvas.drawText( LABEL_NORTH, n_x, n_y, paint_text );
    canvas.drawText( LABEL_SOUTH, s_x, s_y, paint_text );
    canvas.drawText( LABEL_EAST, e_x, e_y, paint_text );
    canvas.drawText( LABEL_WEST, w_x, w_y, paint_text );

    float div = mAxisRadius / 3;
    canvas.drawCircle( mCenterX, mCenterY, div, paint_circle );
    canvas.drawCircle( mCenterX, mCenterY, 2 * div, paint_circle );
    canvas.drawCircle( mCenterX, mCenterY, mAxisRadius, paint_edge );
  }

  @Override
  protected void onDraw( Canvas canvas ) {
    // axis
    if ( mBitmapAxis != null ) {
      canvas.drawBitmap( mBitmapAxis, 0, 0, null );
    }

    // satellite
    for ( int i = 0; i < mSatelliteList.size(); i++ ) {
      SatellitePoint p = mSatelliteList.get( i );
      canvas.drawCircle( p.sat_x, p.sat_y, mSatRadius, mPaintSat );
      canvas.drawText( p.name, p.name_x, p.name_y, mPaintName );
    }
  }

  public void setSatelliteList( List<GnssSatellite> satellites ) {
    mSatelliteList.clear();
    for( GnssSatellite sat : satellites ) {
      if ( sat != null ) {
        SatellitePoint point = new SatellitePoint( sat );
        if ( point.valid ) {
          mSatelliteList.add( point );
        }
      }
    }
    invalidate();
  }

  private float[] getXY( float elevation_deg, float azimuth_deg ) {
    float[] ret = new float[2];
    double r = ( ELEVATION_RANGE - elevation_deg ) / ELEVATION_RANGE;
    // north : right (0) -> top (90)
    double a = ( AZIMUTH_OFFSET + azimuth_deg ) * DEG_TO_RAD;
    ret[0] = (float)( r * Math.cos( a ) );
    ret[1] = (float)( r * Math.sin( a ) );
    return ret;
  }
 
  private class SatellitePoint {

    public boolean valid = false;
    public String name = "";	
    public float sat_x = 0;
    public float sat_y = 0;
    public float name_x = 0;
    public float name_y = 0;

    public SatellitePoint( GnssSatellite sat ) {
      float ele = sat.getElevation();
      float azi = sat.getAzimuth();
      float snr = sat.getSnr();
 
      if (( ele < 2 )&&( azi < 1 )) return;
      if ( snr < 1 ) return;
      valid = true;
      name = sat.getName();
      float[] xy = getXY( ele, azi );
      // todo
      sat_x = - xy[0] * mAxisRadius + mCenterX;
      sat_y = xy[1] * mAxisRadius + mCenterY;
      name_x = sat_x - mPaintName.measureText( name ) / 2;
      name_y = sat_y - mTextHalfHeight;
    }	

  }	
}
