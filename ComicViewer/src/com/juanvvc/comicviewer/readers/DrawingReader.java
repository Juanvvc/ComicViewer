package com.juanvvc.comicviewer.readers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;

import com.juanvvc.comicviewer.GalleryExplorerActivity;
import com.juanvvc.comicviewer.MyLog;

/** Manages a reader for drawings on pages of another reader.
 * You can browse drawings as any other document using prev() and next().
 * @author juanvi
 */
public class DrawingReader extends Reader {
	/** The directory to save drawings. */
	private File fileDir;

	/** Creates a new manager for drawings.
	 * @param ctx The context of the reader.
	 * @param uri The URI to load
	 * @throws ReaderException after any exception */
	public DrawingReader(final Context ctx, final String uri) throws ReaderException {
		super(ctx, uri);
		load(uri);
	}

	@Override
	public final void load(final String uri) throws ReaderException {
		this.close();
		super.load(uri);
		File u = new File(uri);
		this.fileDir = new File(u.getParentFile() + File.separator + GalleryExplorerActivity.THUMBNAILS + File.separator + new File(uri).getName());
	}

	@Override
	public final Drawable getPage(final int page) throws ReaderException {
		return null;
	}

	/** @param page The page of the drawing.
	 * @return The File for this drawing
	 */
	private File getFileForPage(final int page) {
		return new File(this.fileDir.getAbsolutePath() + File.separator + "page" + page);
	}

	@Override
	public final Bitmap getBitmapPage(final int page, final int initialscale) throws ReaderException {
		File f = getFileForPage(page);
		if (!f.exists()) {
			return null;
		} else {
			return BitmapFactory.decodeFile(f.getAbsolutePath(), null);
		}
	}

	@Override
	public final int countPages() {
		return 0;
	}

	/** Removes the drawing of a page.
	 * @param page The page of the drawing */
	public final void removeDrawing(final int page) {
		if (fileDir == null) {
			return;
		}
		File f = getFileForPage(page);
		if (f.exists()) {
			if (f.delete()) {
				MyLog.v(TAG, "Drawing removed for page: " + page);
			} else {
				MyLog.w(TAG, "Cannot remove drawing: " + f.getAbsolutePath());
			}
		}
	}

	/** Removes all drawings of a comic. */
	public final void removeAllDrawings() {
		if (fileDir == null) {
			return;
		}
		if (fileDir.exists()) {
			// remove all entries in directory
			for (File f: fileDir.listFiles()) {
				f.delete();
			}
			// next, delete dir
			if (fileDir.delete()) {
				MyLog.v(TAG, "All drawings deleted");
			} else {
				MyLog.w(TAG, "Cannot remove drawing directory");
			}
		}
	}

	/** Saves a drawing.
	 * @param page The index of the page to save
	 * @param b The Bitmap to save
	 */
	public final void saveDrawing(final int page, final Bitmap b) {
		if (fileDir == null) {
			return;
		}
		if (b == null) {
			return;
		}

		// create the drawing directory, if not exist
		if (!fileDir.exists()) {
			MyLog.i(TAG, "Creating drawing directory in " + fileDir.getAbsolutePath());
			if (!fileDir.mkdir()) {
				MyLog.w(TAG, "Cannot create the drawing directory. Reason unknown.");
				return;
			}
		}
		File f = this.getFileForPage(page);
		MyLog.v(TAG, "Saving drawing on file " + f.getAbsolutePath());

		try {
			FileOutputStream out = new FileOutputStream(f);
			b.compress(Bitmap.CompressFormat.PNG, 90, out);
			out.close();
		} catch (FileNotFoundException e) {
			MyLog.e(TAG, "File not found" + e.toString());
		} catch (IOException e) {
			MyLog.e(TAG, "Error writing drawing: " + e.toString());
		}
	}

	@Override
	public final void close() {
		super.close();
		this.fileDir = null;
	}
}
