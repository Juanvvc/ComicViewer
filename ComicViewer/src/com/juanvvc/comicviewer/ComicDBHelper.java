package com.juanvvc.comicviewer;

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/** Access to the internal comic database.
 * @author juanvi
 */
public class ComicDBHelper extends SQLiteOpenHelper {
	private static final String DATABASE_NAME="comicdb.db";
	private static final int DATABASE_VERSION=3;
	private static final String TAG="database";
	
	public ComicDBHelper(Context context){
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.v(TAG, "Creating the database");
		db.execSQL("CREATE TABLE comics(_id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT NOT NULL, read INTEGER, last_page INTEGER, pages INTEGER, last_access TEST);");
		db.execSQL("CREATE TABLE bookmarks(_id INTEGER PRIMARY KEY, comicid INTEGER NOT NULL, page INTEGER NOT NULL);");
		db.close();
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.w(TAG, "Updating database from "+oldVersion+" to "+newVersion);
		db.execSQL("DROP TABLE IF EXISTS comics");
		db.execSQL("DROP TABLE IF EXISTS bookmarks");
		this.onCreate(db);
	}
	
	/** Created a new comic entry in the database
	 * @param uri
	 * @return the ID of the created comic
	 */
	private long createNewComic(String uri){
		Log.v(TAG, "New comic in the database: "+uri);
		SQLiteDatabase db=this.getWritableDatabase();
		ContentValues cv=new ContentValues();
		cv.put("last_page", 0);
		cv.put("read", 0);
		cv.put("path", uri);
		long id=db.insert("comics", null, cv);
		db.close();
		return id;
	}

	/**
	 * @param uri
	 * @param create If true and the comic is not in the database, create it.
	 * @return The ID to use in other calls to the database of a Comic, given its URI. If the
	 * comic is not found and create is not set, returns -1
	 */
	public long getComicID(String uri, boolean create){
		if(uri==null) return -1;
		SQLiteDatabase db=this.getReadableDatabase();
		Cursor cur=db.query("comics", new String[]{"_id"}, "path=?", new String[]{uri}, null, null, null);
		long id;
		if(!cur.moveToFirst()){
			if(create)
				id=this.createNewComic(uri);
			else{
				cur.close();
				db.close();
				return -1;
			}
		}else{
			id=cur.getInt(cur.getColumnIndex("_id"));
		}
		cur.close();
		db.close();
		Log.v(TAG, "Comic '"+uri+"': "+id);
		return id;
	}
	
	/** Gets a ComicInfo object from the database.
	 * The database does not set neither collection nor reader.
	 * @param comicid
	 * @return The ComicInfo, or null if not found
	 */
	public ComicInfo getComicInfo(long comicid){
		if(comicid==-1) return null;
		SQLiteDatabase db=this.getReadableDatabase();
		Cursor cur=db.query("comics", new String[]{"_id", "path", "read", "last_page", "pages", "last_access"}, "_id=?", new String[]{""+comicid}, null, null, null);
		if(!cur.moveToFirst()){
			cur.close();
			return null;
		}
		ComicInfo i=new ComicInfo();
		i.id=cur.getInt(cur.getColumnIndex("_id"));
		i.lastdate=cur.getString(cur.getColumnIndex("last_access"));
		i.page=cur.getInt(cur.getColumnIndex("last_page"));
		i.uri=cur.getString(cur.getColumnIndex("path"));
		i.read=(cur.getInt(cur.getColumnIndex("read"))==1);
		i.countpages=cur.getInt(cur.getColumnIndex("pages"));
		cur.close();
		cur=db.query("bookmarks", new String[]{"page"}, "comicid=?", new String[]{""+i.id}, null, null, null);
		i.bookmarks=new ArrayList<Integer>();
		if(cur.moveToFirst()){
			do{
				i.bookmarks.add(cur.getInt(cur.getColumnIndex("page")));
			}while(cur.moveToNext());
		}
		cur.close();
		db.close();
		return i;
	}
	
	/** Update the information of a comic in the database.
	 * info.page and info.countpages are never used in the updating, but
	 * info.reader.getCurrentPage() and info.reader.countPages()
	 * @param info
	 */
	public void updateComicInfo(ComicInfo info){
		if(info==null || info.id==-1) return;
		SQLiteDatabase db=this.getWritableDatabase();
		ContentValues cv=new ContentValues();
		if(info.reader!=null)
			cv.put("last_page", info.reader.getCurrentPage());
		cv.put("read", (info.read?1:0));
		cv.put("path", info.uri);
		cv.put("last_access", info.lastdate);
		if(info.reader!=null)
			cv.put("pages", info.reader.countPages());
		db.update("comics", cv, "_id=?", new String[]{String.valueOf(info.id)});
		// Update bookrmaks
		// First: remove all current bookmarks
		db.delete("bookmarks", "comicid=?", new String[]{new Long(info.id).toString()});
		// Second: add current bookmarks
		String comicid=new Long(info.id).toString();
		if(info.bookmarks!=null){
			for(int i=0; i<info.bookmarks.size(); i++){
				Integer b=info.bookmarks.get(i);
				cv=new ContentValues();
				cv.put("page", b);
				cv.put("comicid", comicid);
				db.insert("bookmarks", null, cv);
			}
		}
		db.close();
	}
	
	private void removeBookmarks(ComicInfo info){
		SQLiteDatabase db=this.getWritableDatabase();
		db.delete("bookmarks", "comicid=", new String[]{new Long(info.id).toString()});
		db.close();
	}
	
	/** Removes a comic from the database
	 * @param comicid
	 */
	public void removeComic(long comicid){
		if(comicid==-1) return;
		SQLiteDatabase db=this.getWritableDatabase();
		db.delete("comics", "_id=?", new String[]{String.valueOf(comicid)});
		db.close();
		// TODO: remove bookmarks
	}
}