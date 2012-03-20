
package com.juanvvc.comicviewer;

import android.util.Log;

/** Use this class instead of android.util.Log: simplify the process of uploading to Google Play
 * @author juanvi
 */
public class myLog{
	private static final boolean debug=true;
	public static void i(String tag, String msg){
		if(debug) Log.i(tag, msg);
	}
	public static void d(String tag, String msg){
		if(debug) Log.d(tag, msg);
	}
	public static void v(String tag, String msg){
		if(debug) Log.v(tag, msg);
	}
	public static void e(String tag, String msg){
		if(debug) Log.e(tag, msg);
	}
	public static void w(String tag, String msg){
		if(debug) Log.e(tag, msg);
	}
}