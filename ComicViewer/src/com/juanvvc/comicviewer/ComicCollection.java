package com.juanvvc.comicviewer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import android.content.Context;

import com.juanvvc.comicviewer.readers.Reader;

/**
 * Manages a collection of comics. A collection of comics is an ordered set of
 * comics that are (hopefully) realted. Usually, it is a directory in the
 * filesystem
 * 
 * @author juanvi
 * 
 */
public class ComicCollection extends ArrayList<ComicInfo> {
	private static final long serialVersionUID = 1L;
	/** The name of the collection. */
	private String name;
	/**
	 * The directory of the root of the collection. Used in populate() and
	 * invalidate()
	 */
	private File rootDir;

	/**
	 * Creates a collection from a list of files.
	 * 
	 * @param name
	 * @param list
	 */
	public ComicCollection(final String name, List<ComicInfo> list) {
		super(list);
		this.name = name;
	}

	/**
	 * Creates an empty collection with a name
	 * 
	 * @param name
	 */
	public ComicCollection(final String name) {
		super();
		this.name = name;
	}

	/**
	 * @return The name of this collection
	 */
	public final String getName() {
		return name;
	}

	/**
	 * Given a directory, identify the ComicCollections that are inside them.
	 * This method only creates one level of collections: top directories and
	 * subdirectories are all of them collections of the same level.
	 *
	 * @param context
	 * @param root
	 * @return A list of collections inside a directory
	 */
	public static ArrayList<ComicCollection> getCollections(Context context, File root) {
		if (root == null) {
			return null;
		}
		ArrayList<ComicCollection> collections = new ArrayList<ComicCollection>();
		ComicCollection rootCol = new ComicCollection(root.getName()).populate(context, root);
		if (rootCol != null && !rootCol.isEmpty()) {
			collections.add(rootCol);
		}

		ArrayList<File> contents = new ArrayList<File>(Arrays.asList(root.listFiles()));
		Iterator<File> itr = contents.iterator();
		while (itr.hasNext()) {
			File nf = itr.next();
			// remove from the list normal files and the thumbnails directory
			if (!nf.isDirectory() || nf.getName().equals(GalleryExplorer.THUMBNAILS)) {
				itr.remove();
			} else {
				collections.addAll(ComicCollection.getCollections(context, nf));
			}
		}
		return collections;
	}

	/**
	 * Creates a collection from the contents of a directory. The contents of
	 * the directory are scanned, and if comics are found inside, the collection
	 * is created. Subdirectories are scanned only to test if they have images
	 * (i.e., they can be read by a DirReader) Subdirectories that are not
	 * managed by a DirReader are not added to this collection. Keep in mind
	 * that this include subdirectories with CBZ files, for example: these
	 * collections are of a single level.
	 *
	 * @param context
	 * @param root
	 * @return A reference to self
	 */
	public final ComicCollection populate(Context context, File root) {
		this.clear();

		// get the files in the directory
		ArrayList<File> f = new ArrayList<File>(Arrays.asList(root.listFiles()));
		// sort the names alphabetically
		Collections.sort(f, new Comparator<File>() {
			public int compare(final File lhs, final File rhs) {
				String n1 = lhs.getName();
				String n2 = rhs.getName();
				return n1.compareTo(n2);
			}
		});

		// create the collection
		Iterator<File> itr = f.iterator();
		ComicDBHelper db = new ComicDBHelper(context);
		while (itr.hasNext()) {
			File nf = itr.next();
			// remove from the list the thumbnails directory
			if (nf.getName().equals(GalleryExplorer.THUMBNAILS)) {
				continue;
			} else if (!Reader.existsReader(nf.getAbsolutePath())) {
				// if there is not a manager for the file, continue
				continue;
			}

			// get the information from the database, if exists
			// we do not want to UPDATE the database: if the information is no
			// there, create one
			// Comics are only inserted into the database when read for the
			// first time, to save resources
			ComicInfo c = db.getComicInfo(db.getComicID(nf.getAbsolutePath(),
					false));
			if (c == null) {
				c = new ComicInfo();
				c.uri = nf.getAbsolutePath();
				c.reader = null;
				c.page = 0;
				c.countpages = -1;
				c.id = -1;
			}
			c.collection = this;
			add(c);
		}
		db.close();
		return this;
	}

	/**
	 * Given a comic in this directory, return the next one in the collection.
	 *
	 * @param current The information of the current comic
	 * @return The next comic, or null if there is no next comic of the comic is
	 *         not in the collection
	 */
	public final ComicInfo next(final ComicInfo current) {
		int n = -1;
		// since the ComicInfo object may be created by an external entity such
		// as ComicDBHelper,
		// we cannot use this.indexOf(current)
		// an alternative may be implementing ComicInfo.equals().
		for (int i = 0; i < this.size(); i++) {
			if (this.get(i).uri.equals(current.uri)) {
				n = i;
				break;
			}
		}
		if (n > -1 && n < size() - 1) {
			return this.get(n + 1);
		} else {
			return null;
		}
	}

	/** Re-populates the collection.
	 * For example, call this method when you detect that a file has been removed
	 * @param context
	 */
	public final void invalidate(final Context context) {
		if (this.rootDir == null) {
			return;
		}
		this.populate(context, this.rootDir);
	}
}