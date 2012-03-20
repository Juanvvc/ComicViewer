package com.juanvvc.comicviewer;

import java.util.ArrayList;
import com.juanvvc.comicviewer.readers.Reader;

/** Holds information about a comic.
 * This is the "model" of the database.
 * @author juanvi */
class ComicInfo{
	boolean read;
	/** The id of the comic in the database. If -1, it is not in the database */
	long id;
	/** Last viewed page, only read. Prefer reader.getCurrentPage() if reader is not null */
	int page;
	/** Number of pages, only read. Prefer reader.getCurrentPage() if reader is not null */
	int countpages; // Only read. Prefer reader.countPages() if reader is not null
	String uri;
	/** The collection of this comic.
	 * This field is not set in the database helper, must be set manually. */
	ComicCollection collection;
	/** The bookmarks of this comic */
	ArrayList<Integer> bookmarks;
	/** The reader of this comic.
	 * This field is not set in the database helper, must be set manually. */
	Reader reader;
}

class BookmarkInfo{
	long id;
	long comicid;
	int page;
}