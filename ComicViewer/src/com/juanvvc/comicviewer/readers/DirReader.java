package com.juanvvc.comicviewer.readers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Locale;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import com.juanvvc.comicviewer.MyLog;

/**
 * A reader for directories of images.
 *
 * This readers manages directories of jpg and png images.
 *
 * @author juanvi
 */
public class DirReader extends Reader {
	/** The entries in this directory, sorted. */
	private ArrayList<File> entries;
	/** The minimum number of images in the directory to consider it "manageable". */
	private static final int MIN_IMGS_NUMBER = 1;

	/** Create a new DirReader from a uri.
	 * @param newContext Context of the application
	 * @param newUri The uri of the RAR file in the filesystem
	 * @throws ReaderException If the file cannot be loaded
	 */
	public DirReader(final Context newContext, final String newUri) throws ReaderException {
		super(newContext, newUri);
		if (newUri != null) {
			MyLog.v(TAG, "Usig DirReader on " + newUri);
			this.load(newUri);
		}
	}

	@Override
	public final void load(final String uri) throws ReaderException {
		super.load(uri);
		File root = new File(uri);
		if (!root.isDirectory()) {
			throw new ReaderException("Not a directory");
		}
		// throws an exception if the file is encrypted
		this.entries = new ArrayList<File>(Arrays.asList(root.listFiles()));
		// removes files that are not .jpg or .png
		Iterator<File> itr = this.entries.iterator();
		while (itr.hasNext()) {
			File e = itr.next();
			String name = e.getName().toLowerCase(Locale.US);
			if (e.isDirectory() || !(name.endsWith(".jpg") || name.endsWith(".png"))) {
				itr.remove();
			}
		}
		// sort the names alphabetically
		Collections.sort(this.entries, new Comparator<File>() {
			public int compare(final File lhs, final File rhs) {
				String n1 = lhs.getName();
				String n2 = rhs.getName();
				if (IGNORE_CASE) {
					n1 = n1.toLowerCase(Locale.US);
					n2 = n2.toLowerCase(Locale.US);
				}
				return n1.compareTo(n2);
			}
		});
	}

	@Override
	public final Drawable getPage(final int page) throws ReaderException {
		try {
			if (page < 0 || page >= this.countPages()) {
				return null;
			}
			InputStream is = new FileInputStream(this.entries.get(page));
			return this.streamToTiledDrawable(is);

		} catch (Exception ex) {
			throw new ReaderException(ex.getMessage());
		} catch (OutOfMemoryError err) {
			throw new ReaderException(
					this.getContext().getString(com.juanvvc.comicviewer.R.string.outofmemory));
		}
	}

	@Override
	public final Bitmap getBitmapPage(final int page, final int initialscale) throws ReaderException {
		try {
			if (page < 0 || page >= this.countPages()) {
				return null;
			}

			// you cannot use:
			// Drawable.createFromStream(this.archive.getInputStream(entry),
			// entry.getName());
			// this will trigger lots of OutOfMemory errors.
			// see Reader.byteArrayBitmap for an explanation.
			InputStream is = new FileInputStream(this.entries.get(page));
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] tmp = new byte[4096];
			int ret = 0;

			while ((ret = is.read(tmp)) > 0) {
				bos.write(tmp, 0, ret);
			}
			
			is.close();

			return this.byteArrayToBitmap(bos.toByteArray(), initialscale);

		} catch (Exception ex) {
			throw new ReaderException(ex.getMessage());
		} catch (OutOfMemoryError err) {
			throw new ReaderException(
					this.getContext().getString(com.juanvvc.comicviewer.R.string.outofmemory));
		}
	}

	@Override
	public final int countPages() {
		if (this.entries == null) {
			return NOFILE;
		}
		return this.entries.size();
	}

	/** This reader manages a directory is it contents some image file.
	 * @param uri The uri of the file/directory to test
	 * @return True if the reader manages this type of URI.
	 */
	public static boolean manages(final String uri) {
		File file = new File(uri);
		if (!file.exists() || !file.isDirectory()) {
			return false;
		}
		// look for a minimum number of image files
		File[] contents = file.listFiles();
		if (contents == null) {
			return false; // if contents==null, the file is not a directory. But
							// it passed the directory check! Shit happens.
		}
		int numimgs = 0;
		for (int i = 0; i < contents.length; i++) {
			if (contents[i].isDirectory()) {
				continue;
			}
			String name = contents[i].getName().toLowerCase(Locale.US);
			if (name.endsWith(".jpg") || name.endsWith(".png")) {
				numimgs++;
			}
		}
		return numimgs >= MIN_IMGS_NUMBER;
	}
}
