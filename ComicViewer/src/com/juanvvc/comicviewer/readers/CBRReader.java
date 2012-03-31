
package com.juanvvc.comicviewer.readers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import com.juanvvc.comicviewer.myLog;

import de.innosystec.unrar.Archive;
import de.innosystec.unrar.exception.RarException;
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
			throw new ReaderException(e.getMessage());
		}
		// throws an exception if the file is encrypted
		if (this.archive.isEncrypted()) {
			this.archive = null;
			throw new ReaderException(getContext().getString(com.juanvvc.comicviewer.R.string.encrypted_file));
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
				if (IGNORE_CASE) {
					n1 = n1.toLowerCase();
					n2 = n2.toLowerCase();
				}
				return n1.compareTo(n2);
			}

		});
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
		if (page < 0 || page >= this.countPages()) {
			return null;
		}
		try {
			return this.streamToTiledDrawable(this.extractToInputStream(this.entries.get(page)),  COLUMNS, ROWS);
		} catch (Exception e) {
			myLog.e(TAG, "Cannot read page: " + e.toString());
		} catch (OutOfMemoryError err) {
			throw new ReaderException(getContext().getString(com.juanvvc.comicviewer.R.string.outofmemory));
		}
		return null;
	}
	@Override
	public final Bitmap getBitmapPage(final int page, final int initialscale) throws ReaderException {
		if (page < 0 || page >= this.countPages()) {
			return null;
		}
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			// sometimes, a outofmemory is triggered here. Try to save as much memory as possible
			System.gc();
			this.archive.extractFile(this.entries.get(page), baos);
			baos.close();
			return this.byteArrayToBitmap(baos.toByteArray(), initialscale);
		} catch (Exception e) {
			throw new ReaderException("Cannot read page: " + e.getMessage());
		} catch (OutOfMemoryError err) {
			throw new ReaderException(getContext().getString(com.juanvvc.comicviewer.R.string.outofmemory));
		}
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

	/**
	 * Returns an {@link InputStream} that will allow to read the file and
	 * stream it. Please note that this method will create a new Thread and an a
	 * pair of Pipe streams.
	 *
	 * (From: https://github.com/edmund-wagner/junrar/blob/master/unrar/src/main/java/com/github/junrar/Archive.java)
	 *
	 * @param hd the header to be extracted
	 * @throws IOException if any IO error occurs
	 * @return The input stream of the entry
	 */
	public final InputStream extractToInputStream(final FileHeader hd) throws IOException {
		final PipedInputStream in = new PipedInputStream(32 * 1024);
		final PipedOutputStream out = new PipedOutputStream(in);

		// creates a new thread that will write data to the pipe. Data will be
		// available in another InputStream, connected to the OutputStream.
		// Warning: using this method, we cannot handle exceptions!
		new Thread(new Runnable() {
			public void run() {
				try {
					archive.extractFile(hd, out);
				} catch (RarException e) {
					myLog.e(TAG, e.toString());
				} catch (OutOfMemoryError e) {
					myLog.e(TAG, e.toString());
				} finally {
					try {
						out.close();
					} catch (IOException e) {
						myLog.e(TAG, e.toString());
					}
				}
			}
		}).start();

		return in;
	}
}
