package com.juanvvc.comicviewer.readers;

import java.io.File;
import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;

import com.juanvvc.comicviewer.R;
import com.juanvvc.comicviewer.myLog;

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

	/** If not set, returns a generic image in getBitmapPage(0).
	 * That method is only usually used to get the cover of a page */
	public static final boolean USE_GENERIC_COVER = false;

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
	}

	@Override
	public final void load(final String uri) throws ReaderException {
		super.load(uri);
		myLog.i(TAG, "Loading URI" + uri);
		try {
			file = new PDF(new File(uri), 1);
		} catch (UnsatisfiedLinkError e) {
			myLog.e(TAG, "PDF library not available");
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
		ArrayList<Bitmap> tiles = new ArrayList<Bitmap>();

		// TODO: additional rows and columns make the decoding process too slow
		// Anyway, this must be set automatically and not manually
		int cols = 2; //COLUMNS;
		int rows = 2; //ROWS;
		int zoom = 1000; // 1000 means 100%

		// Should we rotate the bitmaps?
		boolean rotate = false;
		PDF.Size size = new PDF.Size();
		file.getPageSize(page, size);

		if (AUTOMATIC_ROTATION && size.width > size.height) {
			rotate = true;
		}
		rotate = false; // TODO: rotation is not working: deactivate
		myLog.d(TAG, "PDF page of size " + size.width + "x" + size.height);

		// Get the closest width and height that divisible by cols and rows
		// note1: that the last columns/rows of the image will be lost
		int ow, oh;
		if (rotate) {
			// if the image is rotated, width and height switch places
			oh = (size.width / cols) * cols;
			ow = (size.height / rows) * rows;
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

					PDF.Size tilesize = new PDF.Size(tw, th);
					int[] pixels = file.renderPage(page, zoom, left, top, 00, false, false, tilesize);
					Bitmap b = Bitmap.createBitmap(pixels, tilesize.width, tilesize.height, Bitmap.Config.RGB_565);

					Matrix matrix = new Matrix();
					matrix.postRotate(90);
					tiles.add(Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true));
					b.recycle();
				} else {
					int left = tw * j;
					int top = th * i;

					PDF.Size tilesize = new PDF.Size(tw, th);
					int[] pixels = file.renderPage(page, zoom, left, top, 0, false, false, tilesize);
					Bitmap b = Bitmap.createBitmap(pixels, tilesize.width, tilesize.height, Bitmap.Config.RGB_565);
					tiles.add(b);
				}
			}
		}
		return new TiledDrawable(tiles, cols, rows);
	}

	/** Returns a page as a bitmap.
	 * Loading a page using this method is slow, since we have to load the complete page with getPage() and
	 * then paint the resulting Drawable in a new Bitmap. Hence, to speed up the process for covers,
	 * if page == 0 and USE_GENERIC_COVER is set, this method returns R.drawable.pdf_cover.
	 * @param page The number of the page to load
	 * @param initialscale Unused
	 * @throws ReaderException if anything went wrong
	 * @return The bitmap of the selected page
	 */
	@Override
	public final Bitmap getBitmapPage(final int page, final int initialscale) throws ReaderException {
		if (page == 0 && USE_GENERIC_COVER) {
			return BitmapFactory.decodeResource(this.getContext().getResources(), R.drawable.pdf_cover);
		}
		Drawable d = this.getPage(page);
		PDF.Size size = new PDF.Size();
		file.getPageSize(page, size);
		Bitmap b = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.RGB_565);
		d.draw(new Canvas(b));
		return b;
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
