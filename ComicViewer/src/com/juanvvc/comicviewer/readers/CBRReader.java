
package com.juanvvc.comicviewer.readers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.juanvvc.comicviewer.myLog;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import de.innosystec.unrar.Archive;
import de.innosystec.unrar.rarfile.FileHeader;

/** A reader for RAR files.
 *
 * Comics in ZIP usually have the extension .cbr
 * @author juanvi
 */
public class CBRReader extends Reader {
	/** The RAR archive to manage. */
	private Archive archive = null;
	/** Entries in the RAR archive, sorted. */
	private List<? extends FileHeader> entries = null;

	/** Create a new CBRReader from a uri.
	 * @param context Context of the application
	 * @param uri The uri of the RAR file in the filesystem
	 * @throws ReaderException If the file cannot be loaded
	 */
	public CBRReader(final Context context, final String uri) throws ReaderException {
		super(context, uri);
		if (uri != null) {
			this.load(uri);
		}
	}

	@Override
	public final void load(final String uri) throws ReaderException {
		super.load(uri);
		myLog.i(TAG, "Loading URI" + uri);
		// tries to open the RAR file
		try {
			this.archive = new Archive(new File(uri));
		} catch (Exception e) {
			this.uri = null;
			throw new ReaderException(e.getMessage());
		}
		// throws an exception if the file is encrypted
		if (this.archive.isEncrypted()) {
			this.archive = null;
			throw new ReaderException(this.context.getString(com.juanvvc.comicviewer.R.string.encrypted_file));
		}
		this.entries = this.archive.getFileHeaders();
		// removes files that are not .jpg or .png
		Iterator<? extends FileHeader> itr = this.entries.iterator();
		while (itr.hasNext()) {
			FileHeader e = itr.next();
			String name = e.getFileNameString().toLowerCase();
			if (e.isDirectory() || !(name.endsWith(".jpg") || name.endsWith(".png"))) {
				itr.remove();
			}
		}
		// sort the names alphabetically
		Collections.sort(this.entries, new Comparator<FileHeader>() {
			public int compare(final FileHeader lhs, final FileHeader rhs) {
				String n1 = lhs.getFileNameString();
				String n2 = rhs.getFileNameString();
				return n1.compareTo(n2);
			}

		});
	}

	@Override
	public final void close() {
		try {
			this.archive.close();
		} catch (IOException e) {
			myLog.e(TAG, e.toString());
		}
		this.archive = null;
		this.currentPage = -1;

	}

	/** Gets a drawable from a entry in the RAR file.
	 * @param entry The entry to load
	 * @param initialscale The initial scale of the image to load. If 1, load a high quality version of the image
	 * @return The drawable of the file
	 * @throws ReaderException If there is a problem loading the image. The most likely problem is OutOfMemory
	 */
	private Drawable getDrawableFromRarEntry(final FileHeader entry, final int initialscale) throws ReaderException {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			// sometimes, a outofmemory is triggered here. Try to save as much memory as possible
			System.gc();
			this.archive.extractFile(entry, baos);
			baos.close();
			return new BitmapDrawable(this.byteArrayToBitmap(baos.toByteArray(), initialscale));
		} catch (Exception e) {
			throw new ReaderException("Cannot read page: " + e.getMessage());
		} catch (OutOfMemoryError err) {
			throw new ReaderException(this.context.getString(com.juanvvc.comicviewer.R.string.outofmemory));
		}
	}

	@Override
	public final Drawable getPage(final int page) throws ReaderException {
		if (page < 0 || page >= this.countPages()) {
			return null;
		}
		return this.getDrawableFromRarEntry(this.entries.get(page), 1);
	}
	@Override
	public final Drawable getFastPage(final int page, final int initialscale) throws ReaderException {
		if (page < 0 || page >= this.countPages()) {
			return null;
		}
		return this.getDrawableFromRarEntry(this.entries.get(page), initialscale);
	}

	/**
	 * @param uri The uri of the file/directory to test
	 * @return True if the reader manages this type of URI.
	 */
	public static final boolean manages(final String uri) {
		File file = new File(uri);
		if (!file.exists() || file.isDirectory()) {
			return false;
		}
		String name = file.getName().toLowerCase();
		if (name.endsWith(".rar") || name.endsWith(".cbr")) {
			return true;
		}
		return false;
	}

	@Override
	public final int countPages() {
		if (this.entries == null) {
			return NOFILE;
		}
		return this.entries.size();
	}
}
