package com.juanvvc.comicviewer.readers;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.juanvvc.comicviewer.MyLog;

/**
 * This class manages comics. It is used to load a comic, get a page, and move
 * through next() and prev() movements.
 *
 * This class must be extended to particular readers.
 *
 * @author juanvi
 *
 */
public abstract class Reader {
	/** The URI of the currently loaded reader. */
	private String uri = null;
	/** The current page. */
	private int currentPage = -1;
	/** The context of the application. */
	private Context context;
	/** A constant tag name, for logging. */
	protected static final String TAG = "Reader";
	/** Constant to return in getPageCount() when there is file. */
	public static final int NOFILE = -100;
	/** The width of the viewport. -1 if not set. */
	private int viewportWidth = -1;
	/** The height of the viewport. -1 if not set. */
	private int viewportHeight = -1;

	// TODO: make these options configurable
	/** If set, ignore case when ordering pages of the comic. */
	public static final boolean IGNORE_CASE = true;
	/**
	 * If set, horizontal pages rotate to match the screen.
	 * This assumes that screen is portrait, and this was mandatory in the XML.
	 */
	public static final boolean AUTOMATIC_ROTATION = true;
	/** The max size of the tiles to load.
	 * In Android, Bitmaps are managed as OpenGL textures. There is
	 * a (hardware dependent) limit for the texture size. It seems that
	 * the current minimum is 2048 pixels. Anyway, large bitmaps cause
	 * OutOfMemoryExceptions event if they are below the limit.
	 * Unfortunately, small  bitmaps cause large load times for
	 * example in the PDFReader. Tweak this with care. */
	public static final int MAX_BITMAP_SIZE = 512;

	/** Create a new reader from a uri.
	 * @param newContext Context of the application
	 * @param newUri The uri of the RAR file in the filesystem
	 * @throws ReaderException If the file cannot be loaded
	 */
	public Reader(final Context newContext, final String newUri) throws ReaderException {
		this.uri = newUri;
		this.currentPage = -1;
		this.context = newContext;
	}

	/**
	 * @return The Drawable of the current page, unscaled. Equals to
	 *         getPage(this.currentPage)
	 * @throws ReaderException after any problem. Out of memory is the more likely problem.
	 */
	public final Drawable current() throws ReaderException {
		return this.getPage(this.currentPage);
	}

	/**
	 * You have to override this method in your reader.
	 *
	 * @param page The index of the page to return, starting in 0.
	 * @return The Drawable of a page, unscaled.
	 * @throws ReaderException after any problem. Out of memory is the more likely problem.
	 */
	public abstract Drawable getPage(int page) throws ReaderException;

	/**
	 * The bitmap of a page of a drawable.
	 *
	 * @param page The index of the page to return, starting at 0.
	 * @param initialscale
	 *            The bitmap will be scaled at least this factor, maybe more
	 *            if there are memory problems.
	 * @throws ReaderException after any problem. Out of memory is the more likely problem.
	 * @return A scaled version of the page
	 */
	public abstract Bitmap getBitmapPage(final int page, final int initialscale) throws ReaderException;

	/**
	 * Loads a URI into this reader. You need to override this method in you
	 * reader, calling to the parent.load(uri)
	 *
	 * @param uri The URI of the comic to load
	 * @throws ReaderException
	 */
	public void load(String uri) throws ReaderException {
		this.uri = uri;
		// current page is -1, since user didn't turn over the page yet. First
		// thing: call to next()
		this.currentPage = -1;
	}

	/** Closes the reader.
	 * Always call to this method in any class you extend. */
	public void close() {
		this.uri = null;
		this.currentPage = -1;
	}

	/** @return the number pages of the reader. */
	public abstract int countPages();

	/** @return the index of the current page, starting at 0. */
	public final int getCurrentPage() {
		return this.currentPage;
	}

	/** Moves to a page.
	 *
	 * @param page The index of the page to move, starting at 0.
	 */
	public final void moveTo(final int page) {
		MyLog.v(TAG, "Moving to " + page);
		if (page < 0 || page >= this.countPages()) {
			return;
		}
		this.currentPage = page;
	}

	/** @return The URI of this reader. */
	public final String getURI() {
		return this.uri;
	}

	/**
	 * @return The next page, or null if there are not any more
	 * @throws ReaderException after any problem. OutOfMemory is the most likely problem.
	 */
	public final Drawable next() throws ReaderException {
		if (this.uri == null) {
			return null;
		}
		if (this.currentPage < -1 || this.currentPage >= this.countPages()) {
			return null;
		}
		this.currentPage += 1;
		return this.current();
	}

	/**
	 * @return The previous page, or null if there are not any more
	 * @throws ReaderException after any problem. OutOfMemory is the most likely problem.
	 */
	public final Drawable prev() throws ReaderException {
		if (this.uri == null) {
			return null;
		}
		if (this.currentPage <= 0) {
			return null;
		}
		this.currentPage -= 1;
		return this.current();
	}

	/**
	 * Convert a byte array into a Bitmap
	 *
	 * This method should be a single line: return new
	 * BitmapDrawable(BitmapFactory.decodeByteArray(ba, 0, ba.length); or even:
	 * Drawable.createFromStream(new ByteArrayInputStream(ba), "name"); These
	 * work only with small images. This method manages large images (and they
	 * are very usual in comic files)
	 *
	 * The last versions of Android have a very annoying feature: graphics are
	 * always HW accelerated, bitmaps are always loaded as OPENGL_TEXTURES, and
	 * a HW limit applies: MAX_BITMAP_SIZE at most.
	 * http://groups.google.com/group/android-developers/browse_thread/thread/2352c776651b6f99
	 * Some report
	 * (http://stackoverflow.com/questions/7428996/hw-accelerated-activity-how-to-get-opengl-texture-size-limit)
	 * that the minimum is 2048. In my device,
	 * that does not work. 1024 does. Conclusion: in current devices, you cannot
	 * load a bitmap larger (width or height) than MAX_BITMAP_SIZE pixels. Fact:
	 * many CBRs use images larger than that. OutOfMemory errors appear.
	 * Solution: Options.inSampleSize to the rescue.
	 *
	 * Remember: we have to do this with every image because is very common CBR
	 * files where pages have different sizes for example, double/single pages.
	 *
	 * This method is in this class because I think that any reader will find
	 * this useful.
	 *
	 * @param ba
	 *            The byte array to convert
	 * @param initialscale
	 *            The initial scale to use, 1 for original size, 2 for half the
	 *            size...
	 * @return A Bitmap object
	 */
	protected final Bitmap byteArrayToBitmap(final byte[] ba, final int initialscale) {
		Bitmap bitmap = null;
		/*
		 * First strategy: 1.- load only the image information
		 * (inJustDecodeBounds=true) 2.- read the image size 3.- if larger than
		 * MAX_BITMAP_SIZE, apply a scale 4.- load the image scaled Problem: in
		 * my experience, some images are unnecessarily scaled down and quality
		 * suffers
		 */
		// Options opts=new Options();
		// opts.inSampleSize=initialscale;
		// opts.inJustDecodeBounds=true;
		// BitmapFactory.decodeByteArray(ba, 0, ba.length, opts);
		// // now, set the scale according to the image size: 1, 2, 3...
		// opts.inSampleSize = Math.max(opts.outHeight,
		// opts.outWidth)/MAX_BITMAP_SIZE+1;
		// opts.inScaled=true;
		// // set a high quality scale (did really works?)
		// opts.inPreferQualityOverSpeed=true;
		// opts.inJustDecodeBounds=false;
		// // finally, load the scaled image
		// bitmap = BitmapFactory.decodeByteArray(ba, 0, ba.length, opts);

		/*
		 * Second strategy: 1.- load the complete image 2.- if error, scale down
		 * and try again Problem: this method is slower, and sometimes a page
		 * does not throw an OutOfMemoryError, but a warning "bitmap too large"
		 * that cannot be caught and the image is not shown. Quality is much
		 * better.
		 */
		Options opts = new Options();
		opts.inSampleSize = initialscale;
		opts.inPreferQualityOverSpeed = true;
		// finally, load the scaled image
		while (true) {
			try {
				bitmap = BitmapFactory.decodeByteArray(ba, 0, ba.length, opts);
				break; // if we arrive here, the last line didn't trigger an
						// outofmemory error
			} catch (OutOfMemoryError e) {
				System.gc();
			}
			opts.inSampleSize *= 2;
			MyLog.d(TAG, "Using scale " + opts.inSampleSize);
		}

		if (AUTOMATIC_ROTATION && bitmap.getHeight() < bitmap.getWidth()) {
			Matrix matrix = new Matrix();
			matrix.postRotate(90);
			Bitmap b = bitmap;
			bitmap = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
			b.recycle();
		}

		return bitmap;
	}

	/**
	 * @param is A stream to read the image and create a tiled drawable
	 * @return A tiled drawable with the contensts of the stream
	 * @throws IOException After any error
	 */
	protected final Drawable streamToTiledDrawable(final InputStream is) throws IOException {
		BitmapRegionDecoder bd = BitmapRegionDecoder.newInstance(is, true);

		// Should we rotate the bitmaps?
		boolean rotate = false;
		if (AUTOMATIC_ROTATION && bd.getHeight() < bd.getWidth()) {
			rotate = true;
		}

		int cols = 1;
		int rows = 1;

		// Get the closest width and height that divisible by cols and rows
		// note1: that the last columns/rows of the image will be lost
		int ow, oh;
		if (rotate) {
			cols = this.getSuitableCols(bd.getHeight());
			rows = this.getSuitableRows(bd.getWidth());
			// if the image is rotated, width and height switch places
			oh = (bd.getWidth() / rows) * rows;
			ow = (bd.getHeight() / cols) * cols;
		} else {
			cols = this.getSuitableCols(bd.getWidth());
			rows = this.getSuitableRows(bd.getHeight());
			ow = (bd.getWidth() / cols) * cols;
			oh = (bd.getHeight() / rows) * rows;
		}
		MyLog.d(TAG, "Using cols, rows: " + cols  + ", " + rows);

		// Get the final tiles width and height
		int tw = ow / cols;
		int th = oh / rows;
		// create the tiles
		ArrayList<Bitmap> tiles = new ArrayList<Bitmap>();

		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < cols; j++) {
				if (rotate) {
					int left = th * i;
					int top = tw * (cols - j - 1);
					int right = left + th;
					int bottom = top + tw;

					Matrix matrix = new Matrix();
					matrix.postRotate(90);
					Bitmap b = bd.decodeRegion(new Rect(left, top, right, bottom), null);
					tiles.add(Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true));
					b.recycle();
				} else {
					int left = tw * j;
					int top = th * i;
					int right = left + tw;
					int bottom = top + th;
					tiles.add(bd.decodeRegion(new Rect(left, top, right, bottom), null));
				}
			}
		}
		return new TiledDrawable(tiles, cols, rows);
	}

	/**
	 * You must override this method in your reader.
	 *
	 * @param uri
	 *            The uri of the file/directory to test
	 * @return True if the reader manages this type of URI.
	 */
	public static boolean manages(final String uri) {
		return false;
	}

	/** This reader is informed about the available screen size.
	 * The pages that are returned by the other methods may use this information
	 * to enhance the results
	 *
	 * @param width The width of the viewport, in pixels
	 * @param height The height of the viewport, in pixels
	 */
	public final void setViewportSize(final int width, final int height) {
		viewportHeight = height;
		viewportWidth = width;
	}

	/** @return The width of the viewport, or -1 if not set */
	public final int getWidth() {
		return viewportWidth;
	}

	/** @return The height of the viewport, or -1 if not set */
	public final int getHeight() {
		return viewportHeight;
	}


	/**
	 * @param context The context of the current application
	 * @param uri The uri of the document to test
	 * @return A suitable reader for the uri, or null if none was found.
	 */
	public static final Reader getReader(final Context context, final String uri) {
		try {
			if (CBRReader.manages(uri)) {
				return new CBRReader(context, uri);
			} else if (CBZReader.manages(uri)) {
				return new CBZReader(context, uri);
			} else if (DirReader.manages(uri)) {
				return new DirReader(context, uri);
			} else if (PDFReader.manages(uri)) {
				return new PDFReader(context, uri);
			}
		} catch (ReaderException e) {
			MyLog.w(TAG,  e.toString());
		}
		return null;
	}

	/**
	 * This method is equivalent to Reader.getReader(uri)!=null, but
	 * significantly faster.
	 *
	 * @param uri The uri of the document to test
	 * @return True if there is a known reader that manages this type of URI
	 */
	public static final boolean existsReader(final String uri) {
		return CBRReader.manages(uri) || CBZReader.manages(uri) || DirReader.manages(uri) || PDFReader.manages(uri);
	}

	/**
	 * @return The context of this reader
	 */
	public final Context getContext() {
		return this.context;
	}

	/** @param pw The width of the whole page
	 * @return A suitable number of columns for this page.
	 * @see com.juanvvc.comicviewer.readers.Reader.MAX_BITMAP_SIZE */
	public final int getSuitableCols(final int pw) {
		// tiles must have an integer number of columns, and each column has
		// MAX_BITMAP_SIZE pixels at most
		int col = 1;
		while (pw / col > MAX_BITMAP_SIZE) {
			col++;
		}
		return col;
	}

	/** @param ph The height of the whole page
	 * @return A suitable number of rows for this page.
	 * @see com.juanvvc.comicviewer.readers.Reader.MAX_BITMAP_SIZE */
	public final int getSuitableRows(final int ph) {
		// tiles must have an integer number of columns, and each column has
		// MAX_BITMAP_SIZE pixels at most
		int row = 1;
		while (ph / row > MAX_BITMAP_SIZE) {
			row++;
		}
		return row;
	}
}
