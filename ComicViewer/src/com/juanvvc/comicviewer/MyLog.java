
package com.juanvvc.comicviewer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

import android.util.Log;

/** Use this class instead of android.util.Log.
 * Simplify the process of uploading to Google Play: just set DEBUG to false and there you are.
 * @author juanvi
 */
public final class MyLog {
	/** Set to false in final deployment, tru during development. */
	private static final boolean DEBUG = false;

	/** Do not use this constructor. */
	private MyLog() {
		// do not use this constructor
	}

	/** Shows an information message.
	 *
	 * @param tag Tag of the message
	 * @param msg The message
	 */
	public static void i(final String tag, final String msg) {
		if (DEBUG) {
			Log.i(tag, msg);
		}
	}

	/** Shows an information message.
	 *
	 * @param tag Tag of the message
	 * @param msg The message
	 */
	public static void d(final String tag, final String msg) {
		if (DEBUG) {
			Log.d(tag, msg);
		}
	}

	/** Shows an information message.
	 *
	 * @param tag Tag of the message
	 * @param msg The message
	 */
	public static void v(final String tag, final String msg) {
		if (DEBUG) {
			Log.v(tag, msg);
		}
	}

	/** Shows an information message.
	 *
	 * @param tag Tag of the message
	 * @param msg The message
	 */
	public static void e(final String tag, final String msg) {
		if (DEBUG) {
			Log.e(tag, msg);
		}
	}

	/** Shows an information message.
	 *
	 * @param tag Tag of the message
	 * @param msg The message
	 */
	public static void w(final String tag, final String msg) {
		if (DEBUG) {
			Log.e(tag, msg);
		}
	}

	/**
	 * @param e The exception to analyze.
	 * @return A String with the stack trace of the Exception. Useful during development.
	 */
	public static String stackToString(final Exception e) {
		Writer result = new StringWriter();
		PrintWriter printWriter = new PrintWriter(result);
		e.printStackTrace(printWriter);
		return result.toString();
	}
}

