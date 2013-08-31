package com.juanvvc.comicviewer;

/* ////////////////////////////////////////
check list: before uploading a new version to Google Play:

1.- Create the normal version:
- List the changes and date in res/raw/changelog.txt
- Check the version number in the manifiest: it should be higher than the one in Google Play
- Commit the version to git and set a new flag:
    git commit -a -m blahblahblah
    git tag v2.0
    git push --tags github
    git push --tags linsertel
- Set DEBUG to false in MyLog.java
- Export the project to ComicViewer.apk
- Signature: ~/.android/myjuanvvc.keystore

2.- Back to development version:
- Undo all changes. Easy way:
    Exit Eclipse
    git --hard reset
- Open Eclipse again, ok to the warning message
- Start a new iteration by updating the version number in the manifest: one higher
    
///////////////////////////////////////// */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ads.AdRequest;
import com.google.ads.AdSize;
import com.google.ads.AdView;
import com.juanvvc.comicviewer.readers.DrawingReader;
import com.juanvvc.comicviewer.readers.Reader;
import com.juanvvc.comicviewer.readers.ReaderException;

/**
 * Shows the comic collection. This class makes extensive use of Lists and
 * Adapters. This is the main activity of this application.
 *
 * Within this scope, a "collection" is a directory with comics inside. Thre is
 * only one level of collections, and subdirectories are top-level collections.
 *
 * @author juanvi
 *
 */
public class GalleryExplorerActivity extends Activity implements OnItemClickListener {
	/** An arbitrary name to help debugging. */
	private static final String TAG = "GalleryExplorer";
	/** The name of the thumbnails directory. */
	public static final String THUMBNAILS = ".thumbnails";
	/** Random number to identify request of directories. */
	private static final int REQUEST_DIRECTORY = 0x8e;
	/** Random number to identify request of bookmarks. */
	private static final int REQUEST_BOOKMARKS = 0x22;
	/** Random number to identify request of a comic viewer. */
	private static final int REQUEST_VIEWER = 0x5a;
	/** The directory that contents the comics. */
	private String comicDir = null;
	/** If true, it is the donate version */
	private static final boolean DONATE_VERSION = false;

	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.galleryexplorer);

		// Restore preferences
		SharedPreferences settings = getPreferences(MODE_PRIVATE);
		this.comicDir = settings.getString("comicDir", null);

		// check preferences
		if (comicDir == null) {
			// try to load a standard directory
			comicDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Books";
			
			
			new AlertDialog.Builder(this).setIcon(R.drawable.icon)
					.setTitle(this.getText(R.string.please_select_directory))
					.setPositiveButton(android.R.string.ok, null).show();
		} else {
			ListView collections = (ListView) findViewById(R.id.collections);
			// if the comic dir is set, but it does not exists (for example, the SD card was removed)
			// this lines throws a NullPointerException.
			try{
				collections.setAdapter(new CollectionListAdapter(this, new File(this.comicDir)));
			} catch(NullPointerException e) {
				new AlertDialog.Builder(this).setIcon(R.drawable.icon)
				.setTitle(this.getText(R.string.please_select_directory))
				.setPositiveButton(android.R.string.ok, null).show();
			}
		}
		
        // Create the ads
        if (!DONATE_VERSION) {
        	LinearLayout layout = (LinearLayout)findViewById(R.id.galleryexplorer_layout);
	        AdView adView = new AdView(this, AdSize.BANNER, "a1521fd980922d0");
	        layout.addView(adView, 0);	        
	        AdRequest adRequest = new AdRequest();
	        if (MyLog.isDebug()) {
	        	adRequest.addTestDevice("874C587B68F6782F0CD99504C02613A8"); // Tablet
	        	adRequest.addTestDevice("DD57E9E77A859C5F4EE8C1F52334557B"); // HTC Phone
	        	adRequest.addTestDevice("6BBDE7DC8D834F4C186AB2A8A4B64D9B"); //CHUWI
	        }
	        adView.loadAd(adRequest);
        } else {
        	// if we are in the donate version, make sure the debug options are not set. Useful for debugging.
        	MyLog.setDebug(false);
        }
		
		// if it is the first run of this version, show the changelog.txt
		ChangeLog cl = new ChangeLog(this);
		if (cl.firstRun() || MyLog.isDebug()) {
			cl.getFullLogDialog().show();
		}

	}

	/** The Activity is going to be stopped: save preferences. */
	public final void onStop() {
		super.onDestroy();
		// save preferences
		SharedPreferences settings = getPreferences(MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString("comicDir", comicDir);
		editor.commit();
	}

	/**
	 * This adapter manages a list of collections. Each row of the list are a
	 * pair <collection name, gallery>
	 */
	private class CollectionListAdapter extends BaseAdapter {
		/** The context of the application. */
		private Context context;
		/** The comic collections that are available to the user. */
		private ArrayList<ComicCollection> entries;

		/** Creates an adapter for a list of collections.
		 * @param c The context of the application
		 * @param dir The base dir to create the list of collections. The collections
		 * are this directory and all subdirectories of this.
		 */
		CollectionListAdapter(final Context c, final File dir) {
			this.context = c;

			// create the list of comic collections
			this.entries = ComicCollection.getCollections(GalleryExplorerActivity.this, dir);
			// sort directories alphabetically
			Collections.sort(this.entries, new Comparator<ComicCollection>() {
				public int compare(final ComicCollection lhs, final ComicCollection rhs) {
					String n1 = lhs.getName();
					String n2 = rhs.getName();
					return n1.compareTo(n2);
				}

			});
		}

		/**
		 * This method creates a row for the collection list.
		 *
		 * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
		 * @param position The position of the new view
		 * @param convertView Currently, the view is not converted and a new one is created each time
		 * @param parent A reference to the parent
		 * @return A row. The main view of this row is a Gallery that shows the covers
		 * of all available comics inside the collection.
		 */
		public View getView(final int position, final View convertView, final ViewGroup parent) {
			ComicCollection collection = this.entries.get(position);
			View v = View.inflate(this.context, R.layout.galleryexplorerrow, null);
			// create the cover gallery with an adapter
			Gallery g = (Gallery) v.findViewById(R.id.cover_gallery);
			GalleryExplorerActivity.this.registerForContextMenu(g);
			g.setAdapter(new CoverListAdapter(GalleryExplorerActivity.this, collection,	position));
			// create the gallery name
			((TextView) v.findViewById(R.id.collection_name)).setText(collection.getName());
			// gallery items listen to clicks!
			g.setOnItemClickListener(GalleryExplorerActivity.this);
			return v;
		}

		// An adapter needs these methods, even if we do not use them directly
		public int getCount() {
			return this.entries.size();
		}
		public Object getItem(int position) {
			return this.entries.get(position);
		}
		public long getItemId(int position) {
			return position;
		}
	}

	/**
	 * This adapter manages a list of comics inside a collection. Each row of
	 * the list are a pair <comic name, cover>. We will use the THUMBNAIL
	 * directory to load the covers. If the thumbnail is not available, we will
	 * use an AsyncTask to create the cover out of the UI thread
	 */
	private class CoverListAdapter extends BaseAdapter {
		/** The context of the application. */
		private Context context;
		/** The ComicCollection that this adapter controls.
		 * This is a list of available comics inside the collection.
		 */
		private ComicCollection entries = null;
		/** Used for themes. */
		private int background;
		/** The id of the parent. This id can be used on CollectionListAdapter */
		private int parentID = 0;
		/**
		 * The max number of children. Actually, this is not checked! Set a high
		 * number and wait for the best
		 */
		public static final int MAX_CHILDREN = 1000;

		CoverListAdapter(Context context, ComicCollection collection, int parentID) {
			this.context = context;
			this.parentID = parentID;

			// create the list of the files in this collection
			this.entries = collection;

			// This sets the style of the gallery. From:
			// http://developer.android.com/guide/tutorials/views/hello-gallery.html
			TypedArray attr = context
					.obtainStyledAttributes(R.styleable.ComicExplorer);
			this.background = attr.getResourceId(
					R.styleable.ComicExplorer_android_galleryItemBackground, 0);
			attr.recycle();
		}

		/**
		 * This creates an item of the list of covers. An item is a cover and
		 * its name.
		 *
		 * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
		 */
		public View getView(int position, View convertView, ViewGroup parent) {
			ComicInfo ci = this.entries.get(position);
			// I tried to reuse convertView, but some images and text do not change fast enough.
			View v = View.inflate(this.context, R.layout.galleryexploreritem, null);
			v.setBackgroundResource(this.background);

			// Creates a view holder to speed up the UI thread
			// See:
			// http://developer.android.com/training/improving-layouts/smooth-scrolling.html
			ViewHolder holder = new ViewHolder();
			holder.text = (TextView) v.findViewById(R.id.coveritem_text);
			holder.img = (ImageView) v.findViewById(R.id.coveritem_img);
			holder.file = new File(ci.uri);
			// create the comic name: it is the file name without a suffix
			String name = holder.file.getName();
			if (name.lastIndexOf(".") > 0) {
				name = name.substring(0, name.lastIndexOf("."));
			}
			// add some information about the state of the comic
			if (ci.read) {
				holder.text.setText(name
						+ GalleryExplorerActivity.this.getText(R.string.read));
			} else if (ci.page > 0 && ci.countpages > -1) {
				holder.text.setText(name + " (" + (ci.page + 1) + "/"
						+ ci.countpages + ")");
			} else {
				holder.text.setText(name);
			}
			// the cover is loaded in a separate thread
			(new LoadCover()).execute(holder);
			return v;
		}

		// An adapter needs these methods, even if we do not use them directly
		public int getCount() {
			return this.entries.size();
		}
		public Object getItem(final int position) {
			return this.entries.get(position);
		}
		public long getItemId(final int position) {
			return this.parentID * MAX_CHILDREN + position;
		}
		
		public void reloadCollection() {
			this.entries.invalidate(GalleryExplorerActivity.this);
			this.notifyDataSetChanged();
		}
	}

	/**
	 * The only registered event is clicking on a cover: load the comic.
	 *
	 * @param gallery A reference to the list. We know that is the comic gallery
	 * @param arg1 A reference to the clicked view. Not used.
	 * @param position The position in the list of the selected comic.
	 * @param id The identifier of the clicked view. Not used.
	 * @see android.widget.AdapterView.OnItemClickListener#onItemClick(android.widget.AdapterView,
	 *      android.view.View, int, long)
	 */
	public final void onItemClick(final AdapterView<?> gallery, final View arg1, final int position, final long id) {
		// get the file name (we know that the item is going to be a file)
		ComicInfo ci = (ComicInfo) gallery.getAdapter().getItem(position);
		File f = new File(ci.uri);
		// start the comic viewer
		Intent data = new Intent(this, ComicViewerActivity.class);
		data.putExtra("uri", f.getAbsolutePath());
		this.startActivity(data);
	}

	/**
	 * Creates a contextual menu. The only items with a contextual menu are comics.
	 * @param menu the context menu
	 * @param v The view that the user clicked
	 * @param menuInfo Information about this menu
	 */
	public final void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.covermenu, menu);
	}

	/** Manages the contextual menu on specific comics.
	 * @param item The item of the menu
	 * @return True always. */
	public final boolean onContextItemSelected(final MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

		// get the comicinfo of the selected item
		ComicDBHelper db = new ComicDBHelper(this);
		CoverListAdapter comicAdapter = (CoverListAdapter) ((Gallery) info.targetView.getParent()).getAdapter();
		String comicuri = ((ComicInfo) comicAdapter.getItem(info.position)).uri;
		ComicInfo comicinfo = db.getComicInfo(db.getComicID(comicuri, true));

		switch (item.getItemId()) {
		case R.id.switch_read: // switches the read status of a comic
			comicinfo.read = !comicinfo.read;
			db.updateComicInfo(comicinfo);
			db.close();
			comicAdapter.reloadCollection();

			return true;
		case R.id.remove_comic: // deletes the comic
			MyLog.i(TAG, "Removing comic");
			File comicfile = new File(comicinfo.uri);
			if (!comicfile.isDirectory()) {
				// removes the comic from the database
				db.removeComic(comicinfo.id);
				db.close();
				// removes the comic from the filesystem
				if (!comicfile.delete()) {
					MyLog.w(TAG, "Comic couldn't be deleted. Secured filesystem?");
				}
				// removes the drawing from the file system, if any
				try {
					(new DrawingReader(this, comicinfo.uri)).removeAllDrawings();
				} catch (Exception e) {
					MyLog.w(TAG, "Cannot remove drawings: " + e.toString());
				}
				// removes the thumbnail from the filesystem
				File th = getThumbnailFile(comicfile);
				if (!th.delete()) {
					MyLog.w(TAG, "Thumbnail couldn't be deleted. Secured filesystem?");
				}
				// if the thumbnails directory is empty, remove
				if (th.getParentFile().list().length == 0) {
					th.getParentFile().delete();
				}

				// update the view
				comicAdapter.reloadCollection();
				Toast.makeText(this, comicfile.getName() + getText(R.string.removed), Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(this, getText(R.string.cannot_remove_directories), Toast.LENGTH_LONG).show();
			}
		default:
		}
		return true;
	}

	/**
	 * @param file The original comic file
	 * @return The thumbnail for that file.
	 */
	private File getThumbnailFile(final File file) {
		String name = file.getName();
		if (name.lastIndexOf(".") > 0) {
			name = name.substring(0, name.lastIndexOf("."));
		}
		return new File(file.getParent() + File.separator + THUMBNAILS + File.separator + name + ".png");
	}

	/**
	 * The ViewHolder class.
	 * @see http://developer.android.com/training/improving-layouts/smooth-scrolling.html
	 */
	static class ViewHolder {
		/** The text view that shows information of the comic. */
		TextView text;
		/** The imageview that shows the cover. */
		ImageView img;
		/** The file of the comic in the filesystem. */
		File file;
	}

	/** This is an AsyncTask to load the covers outside the UI thread. */
	private class LoadCover extends AsyncTask<ViewHolder, Void, Drawable> {
		/** Information about the UI of a comic.
		 * @see http://developer.android.com/training/improving-layouts/smooth-scrolling.html
		 */
		private ViewHolder holder;

		@Override
		protected Drawable doInBackground(final ViewHolder... params) {
			Reader reader = null;
			this.holder = params[0];
			// if the file is deleted externally, it maybe in the collection but
			// no longer in the filesystem
			// then, this check is mandatory
			if (!this.holder.file.exists()) {
				return new BitmapDrawable(getResources(), BitmapFactory.decodeResource(getResources(), R.drawable.broken));
				// TODO: automatic scan?
			}
			String uri = this.holder.file.getAbsolutePath();
			File cachefile = GalleryExplorerActivity.this.getThumbnailFile(this.holder.file);
			try {
				// First, try to load the file.
				reader = Reader.getReader(GalleryExplorerActivity.this, uri);
				if (reader == null) {
					throw new ReaderException("Not a suitable reader");
				}
				
				// look for the cover in the thumbnails directory. If found, we
				// are done
				if (reader.allowCoverCache() && cachefile.exists()) {
					MyLog.v(TAG, "Cache found: " + cachefile.getName());
					return new BitmapDrawable(getResources(), BitmapFactory.decodeFile(cachefile.getAbsolutePath()));
				}

				// if we are here, the thumbnail was not found.. or not allowed
				MyLog.v(TAG, "Cache not found, creating: " + cachefile.getName());

				// Load the cover of the book
				Bitmap page = reader.getCover();
				// in case of fail, return the broken image
				if (page == null) {
					return new BitmapDrawable(getResources(), BitmapFactory.decodeResource(getResources(), R.drawable.broken));
				}
				// scale
				Bitmap s = Bitmap.createScaledBitmap(page, 200, 300, true);
				
				if(reader.allowCoverCache()) {
					try {
						// save the cache file for the next time, if you can
						// THIS NEEDS WRITING PERMISSIONS
						if (!cachefile.getParentFile().exists()) {
							// create the thumbnails directory
							// I suppose that if the directory cannot be created, an
							// exception will be triggered in the next line
							MyLog.d(TAG, "Creating thumbnails dir: " + cachefile.getParentFile().getAbsolutePath());
							if (!cachefile.getParentFile().mkdir()) {
								MyLog.w(TAG, "Thumbnails directory was not created");
							}
						}
						// save the thumbnail
						FileOutputStream out = new FileOutputStream(cachefile.getAbsoluteFile());
						s.compress(Bitmap.CompressFormat.PNG, 90, out);
						out.close();
						MyLog.v(TAG, "Cache file created: " + cachefile.getName());
					} catch (IllegalStateException e) {
						// trying to save a resource image
						MyLog.w(TAG, "Trying to save a resource!");
						// remove the thumbnail, if exists
						if(cachefile.exists()) {
							cachefile.delete();
						}
					} catch (IOException eio) {
						// any other error
						MyLog.w(TAG, "Cannot create the cache file: " + eio.toString());
						// remove the thumbnail, if exists
						if(cachefile.exists()) {
							cachefile.delete();
						}
					}
				}

				return new BitmapDrawable(getResources(), s);
			} catch (Exception e) {
				MyLog.e(TAG, e.toString());
				MyLog.e(TAG, stackTraceToString(e));
				return new BitmapDrawable(getResources(), BitmapFactory.decodeResource(getResources(), R.drawable.broken));
			}
		}

		@Override
		protected void onPostExecute(final Drawable d) {
			super.onPostExecute(d);
			try {
				this.holder.img.setImageDrawable(d);
			} catch (Exception e) {
				// if the view is not available, an exception is thrown. Just ignore.
			}
		}
	}

	// //////////////////////////////MANAGE THE MENU
	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.gallerymenu, menu);
		return true;
	}

	@Override
	public final boolean onOptionsItemSelected(final MenuItem item) {
		// Handle item selection
		Intent intent;
		switch (item.getItemId()) {
		case R.id.change_directory: // go to the last page
			intent = new Intent(getBaseContext(), DirExplorer.class);
			intent.putExtra(DirExplorer.START_PATH, "/");
			intent.putExtra(DirExplorer.CAN_SELECT_DIR, true);
			intent.putExtra(DirExplorer.FORMAT_FILTER, new String[] {"cbz",	"cbr", "png", "jpg"});
			startActivityForResult(intent, REQUEST_DIRECTORY);
			// notice that changing the comic directory will force a rescan of
			// the collection
			return true;
		case R.id.rescan: // rescan collection
			ListView collections = (ListView) findViewById(R.id.collections);
			collections.invalidate();
			if (this.comicDir == null) {
				Toast.makeText(this, R.string.please_select_directory, Toast.LENGTH_LONG);
			} else {
				collections.setAdapter(new CollectionListAdapter(this, new File(this.comicDir)));
			}
			// TODO: remove .thumbnails directories and old comics from the database
			return true;
		case R.id.bookmarks: // show the book marks activity
			intent = new Intent(getBaseContext(), BookmarksExplorer.class);
			startActivityForResult(intent, REQUEST_BOOKMARKS);
			return true;
		case R.id.settings: // change settings
	        intent = new Intent(this.getApplicationContext(), SettingsActivity.class);
	        this.startActivity(intent);
	        return true;
		case R.id.show_usage:
			ComicViewerActivity.showHelp(this);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/** Get the result of a called activity.
	 * Currently, we use these subactivities: DirExplorer to get the comics directories,
	 * BookmarkExplorer to show the current bookmarks and ComicViewer to show a comic.
	 * @param requestCode The code that we used to call the subactivity
	 * @param resultCode The code that the subactivity returned
	 * @param data The data that the subactivity returned */
	public final synchronized void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			if (requestCode == REQUEST_DIRECTORY) {
				// Activity "select a directory for the comic collection"

				System.out.println("Saving...");
				String filePath = data.getStringExtra(DirExplorer.RESULT_PATH);
				if (filePath == null) {
					return;
				}
				MyLog.i(TAG, "Selected: " + filePath);

				// Four cases:
				// 1.- The user selected an existing file. Remove the file and
				// select the parent dir
				File f = new File(filePath);
				if (f.exists() && !f.isDirectory()) {
					f = f.getParentFile();
				}
				// 2.- the user selected an important directory, such as / or
				// /mnt or /sdcard. The amount of subdirectories
				// is huge. Report and error
				if (f.getAbsolutePath().equals("/")
						|| f.getAbsolutePath().equals("/mnt")
						|| f.getAbsolutePath().equals("/sdcard")) {
					new AlertDialog.Builder(this)
							.setIcon(R.drawable.icon)
							.setTitle(
									"["
									+ f.getAbsolutePath()
									+ "] "
									+ getText(R.string.system_directory))
							.setPositiveButton(getText(android.R.string.ok),
									null).show();
					f = null;
				} else {
					// 3.- the user selected an not existing directory.
					if (!f.exists()) {
						f.mkdir();
					}
					// 4.- the user selected a directory. Do nothing
				}

				// force a rescan
				if (f != null) {
					ListView collections = (ListView) findViewById(R.id.collections);
					collections.invalidate();
					collections.setAdapter(new CollectionListAdapter(this, f));
					this.comicDir = f.getAbsolutePath();
				}
			} else if (requestCode == REQUEST_BOOKMARKS) {
				long comicid = data.getLongExtra("comicid", -1);
				if (comicid != -1) {
					int page = data.getIntExtra("page", 0);
					// get the file name (we know that the item is going to be a
					// file)
					ComicDBHelper db = new ComicDBHelper(this);
					ComicInfo ci = db.getComicInfo(comicid);
					if (ci == null) {
						return;
					}
					File f = new File(ci.uri);
					Toast.makeText(this, "Loading " + f.getName(),
							Toast.LENGTH_LONG).show();
					// start the comic viewer
					Intent intent = new Intent(this, ComicViewerActivity.class);
					intent.putExtra("uri", f.getAbsolutePath());
					intent.putExtra("page", page);
					this.startActivityForResult(intent, REQUEST_VIEWER);
				}
			} else {
				// TODO: return from comicvieweractivity: reload the collection
			}
		}
	}

	/** Utility method to print the stack trace of an exception.
	 * From: http://www.javapractices.com/topic/TopicAction.do?Id=78
	 *
	 * @param e An exception
	 * @return A string with the stack trace of an exception
	 */
	private static String stackTraceToString(final Exception e) {
		final Writer result = new StringWriter();
		final PrintWriter printWriter = new PrintWriter(result);
		e.printStackTrace(printWriter);
		return result.toString();
	}
}
