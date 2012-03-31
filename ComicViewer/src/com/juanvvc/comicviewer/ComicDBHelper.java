package com.juanvvc.comicviewer;

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Access to the internal comic database.
 *
 * @author juanvi
 */
public class ComicDBHelper extends SQLiteOpenHelper {
	/** The name of this database. */
	private static final String DATABASE_NAME = "comicdb.db";
	/** The version of this database. */
	private static final int DATABASE_VERSION = 5;
	/** A tag to be used in debugging. */
	private static final String TAG = "database";
	/** The max number of bookmarks to return. */
	public static final int MAX_NUMBER_BOOKMARKS = 100;

	/** Creates this helper.
	 * @param context The context of the application
	 */
	public ComicDBHelper(final Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public final void onCreate(final SQLiteDatabase db) {
		myLog.v(TAG, "Creating the database");
		db.execSQL("CREATE TABLE comics(_id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT NOT NULL, read INTEGER, last_page INTEGER, pages INTEGER, last_access TEST);");
		db.execSQL("CREATE TABLE bookmarks(_id INTEGER PRIMARY KEY, comicid INTEGER NOT NULL, page INTEGER NOT NULL);");
	}

	@Override
	public final void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
		myLog.w(TAG, "Updating database from " + oldVersion + " to "
				+ newVersion);
		db.execSQL("DROP TABLE IF EXISTS comics");
		db.execSQL("DROP TABLE IF EXISTS bookmarks");
		this.onCreate(db);
	}

	/**
	 * Created a new comic entry in the database.
	 *
	 * @param uri the path in the filesystem to the comic
	 * @return the ID of the created comic
	 */
	private long createNewComic(final String uri) {
		myLog.v(TAG, "New comic in the database: " + uri);
		SQLiteDatabase db = this.getWritableDatabase();
		ContentValues cv = new ContentValues();
		cv.put("last_page", 0);
		cv.put("read", 0);
		cv.put("path", uri);
		long id = db.insert("comics", null, cv);
		db.close();
		return id;
	}

	/** Gets the identifier inside the DB of a comic in the filesystem.
	 * @param uri The path to the comic
	 * @param create
	 *            If true and the comic is not in the database, create it.
	 * @return The ID to use in other calls to the database of a Comic, given
	 *         its URI. If the comic is not found and create is not set, returns -1
	 */
	public final long getComicID(final String uri, final boolean create) {
		if (uri == null) {
			return -1;
		}
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cur = db.query("comics", new String[] {"_id"}, "path=?",
				new String[] {uri}, null, null, null);
		long id;
		if (!cur.moveToFirst()) {
			if (create) {
				id = this.createNewComic(uri);
			} else {
				cur.close();
				db.close();
				return -1;
			}
		} else {
			id = cur.getInt(cur.getColumnIndex("_id"));
		}
		cur.close();
		db.close();
		myLog.v(TAG, "Comic '" + uri + "': " + id);
		return id;
	}

	/**
	 * Gets a ComicInfo object from the database. The database does not set
	 * neither collection nor reader.
	 *
	 * @param comicid The id of the comic to retrieve
	 * @return The ComicInfo, or null if not found
	 */
	public final ComicInfo getComicInfo(final long comicid) {
		if (comicid == -1) {
			return null;
		}
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cur = db.query("comics", new String[] {"_id", "path", "read",
				"last_page", "pages", "last_access" }, "_id=?",
				new String[] {"" + comicid }, null, null, null);
		if (!cur.moveToFirst()) {
			cur.close();
			return null;
		}
		ComicInfo i = new ComicInfo();
		i.id = cur.getInt(cur.getColumnIndex("_id"));
		i.page = cur.getInt(cur.getColumnIndex("last_page"));
		i.uri = cur.getString(cur.getColumnIndex("path"));
		i.read = (cur.getInt(cur.getColumnIndex("read")) == 1);
		i.countpages = cur.getInt(cur.getColumnIndex("pages"));
		cur.close();
		cur = db.query("bookmarks", new String[] {"page"}, "comicid=?",
				new String[] {"" + i.id }, null, null, null);
		i.bookmarks = new ArrayList<Integer>();
		if (cur.moveToFirst()) {
			do {
				i.bookmarks.add(cur.getInt(cur.getColumnIndex("page")));
			} while (cur.moveToNext());
		}
		cur.close();
		db.close();
		return i;
	}

	/**
	 * Update the information of a comic in the database. info.page and
	 * info.countpages are never used in the updating, but
	 * info.reader.getCurrentPage() and info.reader.countPages()
	 *
	 * @param info The ComicInfo of the comic to update
	 */
	public final void updateComicInfo(final ComicInfo info) {
		if (info == null || info.id == -1) {
			return;
		}
		SQLiteDatabase db = this.getWritableDatabase();
		ContentValues cv = new ContentValues();
		if (info.reader != null) {
			cv.put("last_page", info.reader.getCurrentPage());
		}
		if (info.read) {
			cv.put("read", 1);
		} else {
			cv.put("read", 0);
		}
		cv.put("path", info.uri);
		if (info.reader != null) {
			cv.put("pages", info.reader.countPages());
		}
		db.update("comics", cv, "_id=?",
				new String[] {String.valueOf(info.id) });
		// Update bookrmaks
		// First: remove all current bookmarks
		db.delete("bookmarks", "comicid=?",
				new String[] {new Long(info.id).toString() });
		// Second: add current bookmarks
		String comicid = new Long(info.id).toString();
		if (info.bookmarks != null) {
			for (int i = 0; i < info.bookmarks.size(); i++) {
				Integer b = info.bookmarks.get(i);
				cv = new ContentValues();
				cv.put("page", b);
				cv.put("comicid", comicid);
				db.insert("bookmarks", null, cv);
			}
		}
		db.close();
	}

	/**
	 * Removes a comic from the database.
	 *
	 * @param comicid The id of the comic to remove
	 */
	public final void removeComic(final long comicid) {
		if (comicid == -1) {
			return;
		}
		SQLiteDatabase db = this.getWritableDatabase();
		db.delete("comics", "_id=?", new String[] {String.valueOf(comicid) });
		db.delete("bookmarks", "comicid=?",
				new String[] {new Long(comicid).toString() });
		db.close();
	}

	/**
	 * This method returns an array of bookmarks.
	 * TODO: This method supposes that there number of bookmarks is low (under 100)
	 *
	 * @param comicid The id of the comic. If -1, return all bookmarks
	 * @return The bookmarks or null if bookmarks are more than 100
	 */
	public final BookmarkInfo[] getBookmarks(final long comicid) {
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cur = null;
		if (comicid != -1) {
			// return selected bookmarks
			cur = db.query("bookmarks",
					new String[] {"_id", "comicid", "page" }, "comicid=?",
					new String[] {String.valueOf(comicid) }, null, null, null);
		} else {
			// return all bookmarks
			cur = db.query("bookmarks",
					new String[] {"_id", "comicid", "page" }, "page>?",
					new String[] {"-1" }, null, null, null);
		}
		if (cur.getCount() > MAX_NUMBER_BOOKMARKS) {
			return null;
		}
		BookmarkInfo[] ba = new BookmarkInfo[cur.getCount()];
		if (!cur.moveToFirst()) {
			return ba;
		}
		for (int i = 0; i < cur.getCount(); i++) {
			BookmarkInfo b = new BookmarkInfo();
			b.id = cur.getLong(cur.getColumnIndex("_id"));
			b.comicid = cur.getLong(cur.getColumnIndex("comicid"));
			b.page = cur.getInt(cur.getColumnIndex("page"));
			ba[i] = b;
			cur.moveToNext();
		}
		cur.close();
		db.close();
		return ba;
	}
}