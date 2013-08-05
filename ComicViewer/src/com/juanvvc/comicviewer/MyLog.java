
package com.juanvvc.comicviewer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

import android.util.Log;

/** Use this class instead of android.util.Log: simplify the process of uploading to Google Play
 * @author juanvi
 */
public class MyLog{
	private static boolean DEBUG=true;
	
	public static void setDebug(boolean d) {
		MyLog.DEBUG = d;
	}
	public static boolean isDebug() {
		return MyLog.DEBUG;
	}
	
	public static void i(Object o, String msg){
		if(DEBUG) Log.i(o.getClass().getSimpleName(), msg);
	}
	public static void d(Object o, String msg){
		if(DEBUG) Log.d(o.getClass().getSimpleName(), msg);
	}
	public static void v(Object o, String msg){
		if(DEBUG) Log.v(o.getClass().getSimpleName(), msg);
	}
	public static void e(Object o, String msg){
		if(DEBUG) Log.e(o.getClass().getSimpleName(), msg);
	}
	public static void w(Object o, String msg){
		if(DEBUG) Log.e(o.getClass().getSimpleName(), msg);
	}
	
	public static void i(String t, String msg){
		if(DEBUG) Log.i(t, msg);
	}
	public static void d(String t, String msg){
		if(DEBUG) Log.d(t, msg);
	}
	public static void v(String t, String msg){
		if(DEBUG) Log.v(t, msg);
	}
	public static void e(String t, String msg){
		if(DEBUG) Log.e(t, msg);
	}
	public static void w(String t, String msg){
		if(DEBUG) Log.e(t, msg);
	}
	
	public static String stackToString(Exception e) {
		Writer result = new StringWriter();
		PrintWriter printWriter = new PrintWriter(result);
		e.printStackTrace(printWriter);
		return result.toString();
	}
}