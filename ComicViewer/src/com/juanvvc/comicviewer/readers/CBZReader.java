package com.juanvvc.comicviewer.readers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import com.juanvvc.comicviewer.myLog;

/**
 * A reader for ZIP files.
 *
 * Comics in ZIP usually have the extension .cbz
 *
 * @author juanvi
 */
public class CBZReader extends Reader {
	/** The ZIP file archive. */
	private ZipFile archive = null;
	/** Entries in the archive, sorted. */
	private ArrayList<? extends ZipEntry> entries = null;

	/** Create a new CBRReader from a uri.
	 * @param context Context of the application
	 * @param uri The uri of the RAR file in the filesystem
	 * @throws ReaderException If the file cannot be loaded
	 */
	public CBZReader(final Context context, final String uri) throws ReaderException {
		super(context, uri);
		if (uri != null) {
			this.load(uri);
		}
	}

	@Override
	public final void load(final String uri) throws ReaderException {
		try {
			super.load(uri);
			myLog.i(TAG, "Loading URI" + uri);
			this.archive = new ZipFile(uri);
			// get the entries of the file and sort them alphabetically
			this.entries = Collections.list(this.archive.entries());
			// removes files that are not .jpg or .png
			Iterator<? extends ZipEntry> itr = this.entries.iterator();
			while (itr.hasNext()) {
				ZipEntry e = itr.next();
				String name = e.getName().toLowerCase();
				if (e.isDirectory() || !(name.endsWith(".jpg") || name.endsWith(".png"))) {
					itr.remove();
				}
			}
			// sort the names alphabetically
			Collections.sort(this.entries, new Comparator<ZipEntry>() {
				public int compare(final ZipEntry lhs, final ZipEntry rhs) {
					String n1 = lhs.getName();
					String n2 = rhs.getName();
					if (IGNORE_CASE) {
						n1 = n1.toLowerCase();
						n2 = n2.toLowerCase();
					}
					return n1.compareTo(n2);
				}

			});
			super.load(uri);
		} catch (IOException e) {
			throw new ReaderException("ZipFile cannot be read: " + e.toString());
		}
	}

	@Override
	public final void close() {
		super.close();
		try {
			this.archive.close();
		} catch (IOException e) {
			myLog.e(TAG, e.toString());
		}
		this.archive = null;
	}

	@Override
	public final Drawable getPage(final int page) throws ReaderException {
//		if (page < 0 || page >= this.countPages()) {
//			return null;
//		}
//		return this.getDrawableFromZipEntry(res, this.entries.get(page), 1);
		try {
			InputStream is = this.archive.getInputStream(this.entries.get(page));
			return this.streamToTiledDrawable(is);
		} catch (IOException e) {
			myLog.e(TAG, e.toString());
		}
		return null;
	}

	@Override
	public final Bitmap getBitmapPage(final int page, final int initialscale) throws ReaderException {
		if (page < 0 || page >= this.countPages()) {
			return null;
		}
		try {
			// you cannot use:
			// Drawable.createFromStream(this.archive.getInputStream(entry),
			// entry.getName());
			// this will trigger lots of OutOfMemory errors.
			// see Reader.byteArrayBitmap for an explanation.
			InputStream is = this.archive.getInputStream(this.entries.get(page));
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] tmp = new byte[4096];
			int ret = 0;

			while ((ret = is.read(tmp)) > 0) {
				bos.write(tmp, 0, ret);
			}

			return this.byteArrayToBitmap(bos.toByteArray(), initialscale);

		} catch (Exception ex) {
			throw new ReaderException(ex.getMessage());
		} catch (OutOfMemoryError err) {
			throw new ReaderException(
					getContext().getString(com.juanvvc.comicviewer.R.string.outofmemory));
		}
	}

	@Override
	public final int countPages() {
		if (this.archive != null) {
			return this.entries.size();
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
		if (name.endsWith(".zip") || name.endsWith(".cbz")) {
			return true;
		}
		return false;
	}
}
