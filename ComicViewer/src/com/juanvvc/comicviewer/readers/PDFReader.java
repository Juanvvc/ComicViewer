package com.juanvvc.comicviewer.readers;

import java.io.File;
import java.util.ArrayList;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;

import com.juanvvc.comicviewer.MyLog;
import com.juanvvc.comicviewer.R;

import cx.hell.android.lib.pdf.PDF;

/**
 * A reader for ZIP files.
 *
 * Comics in ZIP usually have the extension .cbz
 *
 * @author juanvi
 */
public class PDFReader extends Reader {
	/** The PDF file. */
	private PDF file = null;
	/** In MuPDF library, the zoom level that corresponds to 100%. */
	private static final int ZOOM100 = 1000;

	///// TODO: make this options configurable
	/** If not set, returns a generic image in getBitmapPage(0).
	 * That method is only used to get the cover of a page */
	private boolean USE_GENERIC_COVER = false;
	/** If set, zoom the pages to fill the screen.
	 * This enhances the quality of the render, but increases the process time and memory.
	 */
	private static final boolean AUTOMATIC_ZOOM = true;

	/** Create a new CBRReader from a uri.
	 * @param context Context of the application
	 * @param uri The uri of the RAR file in the filesystem
	 * @throws ReaderException If the file cannot be loaded
	 */
	public PDFReader(final Context context, final String uri) throws ReaderException {
		super(context, uri);
		if (uri != null) {
			this.load(uri);
		}
		
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		// use the current value as the default value
		USE_GENERIC_COVER = preferences.getBoolean("pref_pdf_cover", USE_GENERIC_COVER);
		
	}

	@Override
	public final void load(final String uri) throws ReaderException {
		super.load(uri);
		MyLog.i(TAG, "Loading URI" + uri);
		try {
			file = new PDF(new File(uri), 1);
		} catch (UnsatisfiedLinkError e) {
			MyLog.e(TAG, "PDF library not available");
			file = null;
		}
	}

	@Override
	public final void close() {
		if (file != null) {
			file.finalize();
			file = null;
		}
		super.close();
	}

	@Override
	public final Drawable getPage(final int page) throws ReaderException {
		// check limits
		if (this.file == null || page < 0 || page >= this.file.getPageCount()) {
			return null;
		}

		ArrayList<Bitmap> tiles = new ArrayList<Bitmap>();

		int zoom = ZOOM100; // 1000 means 100%

		// Should we rotate the bitmaps?
		boolean rotate = false;
		PDF.Size size = new PDF.Size();
		file.getPageSize(page, size);

		int cols = 2;
		int rows = 2;

		// test if we have to rotate the screen
		if (AUTOMATIC_ROTATION && size.width > size.height) {
			rotate = true;
			cols = this.getSuitableCols(size.height);
			rows = this.getSuitableRows(size.width);
		} else {
			cols = this.getSuitableCols(size.width);
			rows = this.getSuitableRows(size.height);
		}
		MyLog.d(TAG, "Using cols, rows: " + cols  + ", " + rows);

		// calculate an appropriate zoom level to fill the screen
		// this enhance the quality of the rendered page.
		if (AUTOMATIC_ZOOM && getWidth() != -1) {
			if (rotate) {
				if (size.height < getWidth()) {
					// calculate the zoom level
					zoom = ZOOM100 * getWidth() / size.height;
					// if the zoom changes, the pdf size changes accordingly
					size.width = (int) (1.0 * zoom * size.width / ZOOM100);
					size.height = (int) (1.0 * zoom * size.height / ZOOM100);
					MyLog.d(TAG, "Using zoom level of " + zoom);
				} else {
					MyLog.d(TAG, "PDF page larger than viewport");
				}
			} else {
				if (size.width < getWidth()) {
					// calculate the zoom level
					zoom = ZOOM100 * getWidth() / size.width;
					// if the zoom changes, the pdf size changes accordingly
					size.width = (int) (1.0 * zoom * size.width / ZOOM100);
					size.height = (int) (1.0 * zoom * size.height / ZOOM100);
					MyLog.d(TAG, "Using zoom level of " + zoom);
				} else {
					MyLog.d(TAG, "PDF page larger than viewport");
				}
			}
		} else {
			MyLog.w(TAG, "Viewport size not set or no automatic zoom");
		}

		// Get the closest width and height that divisible by cols and rows
		// note1: the last columns/rows of pixels in the image will be lost
		int ow, oh;
		if (rotate) {
			// if the image is rotated, width and height switch places
			oh = (size.width / rows) * rows;
			ow = (size.height / cols) * cols;
		} else {
			ow = (size.width / cols) * cols;
			oh = (size.height / rows) * rows;
		}

		// Get the final tiles width and height
		int tw = ow / cols;
		int th = oh / rows;

		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < cols; j++) {
				if (rotate) {
					int left = th * i;
					int top = tw * (cols - j - 1);

					PDF.Size tilesize = new PDF.Size(th, tw);
					int[] pixels = file.renderPage(page, zoom, left, top, 0, false, tilesize);
					Bitmap b = Bitmap.createBitmap(pixels, tilesize.width, tilesize.height, Bitmap.Config.RGB_565);

					Matrix matrix = new Matrix();
					matrix.postRotate(90);
					tiles.add(Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true));
					b.recycle();
				} else {
					int left = tw * j;
					int top = th * i;

					PDF.Size tilesize = new PDF.Size(tw, th);
					int[] pixels = file.renderPage(page, zoom, left, top, 0, false, tilesize);
					Bitmap b = Bitmap.createBitmap(pixels, tilesize.width, tilesize.height, Bitmap.Config.RGB_565);
					tiles.add(b);
				}
			}
		}
		return new TiledDrawable(tiles, cols, rows);
	}

	/** Returns a page as a bitmap.
	 * @param page The number of the page to load
	 * @param initialscale Unused
	 * @throws ReaderException if anything went wrong
	 * @return The bitmap of the selected page
	 */
	@Override
	public final Bitmap getBitmapPage(final int page, final int initialscale) throws ReaderException {
		Drawable d = this.getPage(page);
		PDF.Size size = new PDF.Size();
		file.getPageSize(page, size);
		Bitmap b = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.RGB_565);
		d.draw(new Canvas(b));
		return b;
	}
	
	 /** Loading a cover may be slow for the PDF library. Hence, to speed up the process for covers,
	 * if USE_GENERIC_COVER is set, this method returns R.drawable.pdf_cover */
	@Override
	public final Bitmap getCover() throws ReaderException {
		if ( USE_GENERIC_COVER ) {
			return BitmapFactory.decodeResource(this.getContext().getResources(), R.drawable.pdf_cover);
		} else {
			return super.getCover();
		}
	}
	
	/** Do not allow the creation of cover caches if USE_GENERIC_COVER is on */
	@Override
	public final boolean allowCoverCache() {
		return !USE_GENERIC_COVER;
	}

	@Override
	public final int countPages() {
		if (this.file != null) {
			return file.getPageCount();
		} else {
			return NOFILE;
		}
	}

	/**
	 * @param uri The uri of the file/directory to test
	 * @return True if the reader manages this type of URI.
	 */
	public static boolean manages(final String uri) {
		File file = new File(uri);
		if (!file.exists() || file.isDirectory()) {
			return false;
		}
		String name = file.getName().toLowerCase();
		if (name.endsWith(".pdf")) {
			return true;
		}
		return false;
	}
}
