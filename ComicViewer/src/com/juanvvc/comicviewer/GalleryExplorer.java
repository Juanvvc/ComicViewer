package com.juanvvc.comicviewer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.juanvvc.comicviewer.readers.Reader;
import com.juanvvc.comicviewer.readers.ReaderException;

/**
 * Shows the comic collection. This class makes extensive use of Lists and
 * Adapters
 *
 * Within this scope, a "collection" is a directory with comics inside. Thre is
 * only one level of collections, and subdirectories are top-level collections.
 *
 * TODO rescans are forced too much. In large directories this is not a good
 * idea
 *
 * @author juanvi
 *
 */
public class GalleryExplorer extends Activity implements OnItemClickListener {
	/** An arbitrary name to help debugging. */
	private static final String TAG = "GalleryExplorer";
	/** The name of the thumbnails directory. */
	static final String THUMBNAILS = ".thumbnails";
	/** Random number to identify request of directories. */
	private static final int REQUEST_DIRECTORY = 0x8e;
	/** Random number to identify request of bookmarks. */
	private static final int REQUEST_BOOKMARKS = 0x22;
	/** Random number to identify request of a comic viewer. */
	private static final int REQUEST_VIEWER = 0x5a;
	/** The directory that contents the comics. */
	private String comicDir = null;

	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.galleryexplorer);

		// Restore preferences
		SharedPreferences settings = getPreferences(MODE_PRIVATE);
		this.comicDir = settings.getString("comicDir", null);

		// check preferences
		if (comicDir == null) {
			new AlertDialog.Builder(this).setIcon(R.drawable.icon)
					.setTitle(this.getText(R.string.please_select_directory))
					.setPositiveButton(android.R.string.ok, null).show();
		} else {
			ListView collections = (ListView) findViewById(R.id.collections);
			collections.setAdapter(new CollectionListAdapter(this, new File(
					this.comicDir)));
		}

	}

	/** The Activity is going to be killed: save preferences. */
	public final void onDestroy() {
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
		private Context context;
		/** The comic collections that are available to the user. */
		private ArrayList<ComicCollection> entries;

		/** Creates an adapter for a list of collections.
		 * @param context
		 * @param dir The base dir to create the list of collections. The collections
		 * are this directory and all subdirectories of this.
		 */
		CollectionListAdapter(final Context context, final File dir) {
			this.context = context;

			// create the list of comic collections
			this.entries = ComicCollection.getCollections(GalleryExplorer.this,
					dir);
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
		 * @see android.widget.Adapter#getView(int, android.view.View,
		 *      android.view.ViewGroup)
		 * @param convertView Currently, the view is not converted and a new one is created each time
		 * @return A row. The main view of this row is a Gallery that shows the covers
		 * of all available comics inside the collection.
		 */
		public View getView(final int position, final View convertView, final ViewGroup parent) {
			ComicCollection collection = this.entries.get(position);
			View v = View.inflate(this.context, R.layout.galleryexplorerrow, null);
			// create the cover gallery with an adapter
			Gallery g = (Gallery) v.findViewById(R.id.cover_gallery);
			GalleryExplorer.this.registerForContextMenu(g);
			g.setAdapter(new CoverListAdapter(GalleryExplorer.this, collection,
					position));
			// create the gallery name
			((TextView) v.findViewById(R.id.collection_name))
					.setText(collection.getName());
			// gallery items listen to clicks!
			g.setOnItemClickListener(GalleryExplorer.this);
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
		 * @see android.widget.Adapter#getView(int, android.view.View,
		 *      android.view.ViewGroup)
		 */
		public View getView(int position, View convertView, ViewGroup parent) {
			ComicInfo ci = this.entries.get(position);
			// TODO: I tried to reuse convertView, but some images and text do
			// not change fast enough.
			View v = View.inflate(this.context, R.layout.galleryexploreritem,
					null);
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
						+ GalleryExplorer.this.getText(R.string.read));
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

		public Object getItem(int position) {
			return this.entries.get(position);
		}

		public long getItemId(int position) {
			return this.parentID * MAX_CHILDREN + position;
		}
	}

	/**
	 * The only registered event is clicking on a cover: load the comic.
	 *
	 * @see android.widget.AdapterView.OnItemClickListener#onItemClick(android.widget.AdapterView,
	 *      android.view.View, int, long)
	 */
	public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
		// get the file name (we know that the item is going to be a file)
		ComicInfo ci = (ComicInfo) arg0.getAdapter().getItem(position);
		File f = new File(ci.uri);
		// start the comic viewer
		Intent data = new Intent(this, ComicViewerActivity.class);
		data.putExtra("uri", f.getAbsolutePath());
		this.startActivity(data);
	}

	/**
	 * Creates a contextual menu. The only items with a contextual menu are
	 * comics
	 */
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.covermenu, menu);
	}

	/** Manages the contextual menu over comics */
	public final boolean onContextItemSelected(final MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();

		// get the comicinfo of the selected item
		ComicDBHelper db = new ComicDBHelper(this);
		// get the adapters
		BaseAdapter colAdapter = (BaseAdapter) ((ListView) this.findViewById(R.id.collections)).getAdapter();
		ListView list = (ListView) this.findViewById(R.id.collections);
		BaseAdapter comicAdapter = (BaseAdapter) ((Gallery) info.targetView.getParent()).getAdapter();
		String comicuri = ((ComicInfo) comicAdapter.getItem(info.position)).uri;
		ComicInfo comicinfo = db.getComicInfo(db.getComicID(comicuri, true));

		switch (item.getItemId()) {
		case R.id.switch_read: // switches the read status of a comic
			comicinfo.read = !comicinfo.read;
			db.updateComicInfo(comicinfo);
			// TODO: this do not work.
			((ComicCollection) colAdapter.getItem(info.position
					/ CoverListAdapter.MAX_CHILDREN)).invalidate(this);
			comicAdapter.notifyDataSetChanged();
			list.invalidateViews();
			db.close();
			return true;
		case R.id.remove_comic: // deletes the comic
			myLog.i(TAG, "Removing comic");
			File comicfile = new File(comicinfo.uri);
			if (!comicfile.isDirectory()) {
				// TODO: we cannot remove directories at the moment
				// removes the comic from the database
				db.removeComic(comicinfo.id);
				// removes the comic from the filesystem
				comicfile.delete();
				// removes the thumbnail from the filesystem
				getThumbnailFile(comicfile).delete();
				// update the view
				((ComicCollection) colAdapter.getItem(info.position
						/ CoverListAdapter.MAX_CHILDREN)).invalidate(this);
				comicAdapter.notifyDataSetChanged();
				colAdapter.notifyDataSetChanged();
				list.invalidateViews();
				Toast.makeText(this,
						comicfile.getName() + getText(R.string.removed),
						Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(this,
						getText(R.string.cannot_remove_directories),
						Toast.LENGTH_LONG).show();
			}
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	/**  
	 * @param file The original comic file
	 * @return The thumbnail for that file.
	 */
	private File getThumbnailFile(File file) {
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
		TextView text;
		ImageView img;
		File file;
	}

	/** This is an AsyncTask to load the covers outside the UI thread. */
	private class LoadCover extends AsyncTask<ViewHolder, Void, Drawable> {
		private ViewHolder holder;

		@Override
		protected Drawable doInBackground(final ViewHolder... params) {
			Reader reader = null;
			this.holder = params[0];
			// if the file is deleted externally, it maybe in the collection but
			// no longer in the filesystem
			// then, this check is mandatory
			if (!this.holder.file.exists()) {
				return new BitmapDrawable(BitmapFactory.decodeResource(getResources(), R.drawable.broken));
				// TODO: automatic scan?
			}
			String uri = this.holder.file.getAbsolutePath();
			File cachefile = GalleryExplorer.this.getThumbnailFile(this.holder.file);
			try {
				// look for the cover in the thumbnails directory. If found, we
				// are done
				if (cachefile.exists()) {
					myLog.v(TAG, "Cache found: " + cachefile.getName());
					return new BitmapDrawable(
							BitmapFactory.decodeFile(cachefile
									.getAbsolutePath()));
				}

				// if we are here, the thumbnail was not found
				myLog.v(TAG, "Cache not found, creating: " + cachefile.getName());

				// Load the comic file, and then the first image
				// select the comic reader
				reader = Reader.getReader(GalleryExplorer.this, uri);
				if (reader == null) {
					throw new ReaderException("Not a suitable reader");
				}
				// if the first page it is not a bitmapdrawable, this will
				// trigger an exception. Pity, but it's OK
				BitmapDrawable bd = ((BitmapDrawable) reader.getPage(0));
				// in case of fail, return the broken image
				if (bd == null) {
					return new BitmapDrawable(BitmapFactory.decodeResource(getResources(), R.drawable.broken));
				}
				// scale
				Bitmap s = Bitmap.createScaledBitmap(bd.getBitmap(), 200, 300, true);
				// recycle the original bitmap
				bd.getBitmap().recycle();

				try {
					// save the cache file for the next time, if you can
					// THIS NEEDS WRITING PERMISSIONS
					if (!cachefile.getParentFile().exists()) {
						// create the thumbnails directory
						// I suppose that if the directory cannot be created, an
						// exception will be triggered in the next line
						cachefile.getParentFile().mkdir();
					}
					// save the thumbnail
					FileOutputStream out = new FileOutputStream(cachefile.getAbsoluteFile());
					s.compress(Bitmap.CompressFormat.PNG, 90, out);
					out.close();
					myLog.v(TAG, "Cache file created: " + cachefile.getName());
				} catch (IOException eio) {
					myLog.w(TAG, "Cannot create the cache file: " + eio.toString());
				}

				return new BitmapDrawable(s);
			} catch (ReaderException e) {
				myLog.e(TAG, e.toString());
				return new BitmapDrawable(BitmapFactory.decodeResource(getResources(), R.drawable.broken));
			}
		}

		protected void onPostExecute(Drawable d) {
			super.onPostExecute(d);
			this.holder.img.setImageDrawable(d);
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
			collections.setAdapter(new CollectionListAdapter(this, new File(
					this.comicDir)));
			return true;
		case R.id.bookmarks: // show the bookmarks activity
			intent = new Intent(getBaseContext(), BookmarksExplorer.class);
			startActivityForResult(intent, REQUEST_BOOKMARKS);
			return true;
		case R.id.settings: // change settings (currently not implemented)
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/** Get the result of a called activity. */
	public synchronized void onActivityResult(final int requestCode,
			int resultCode, final Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			if (requestCode == REQUEST_DIRECTORY) {
				// Activity "select a directory for the comic collection"

				System.out.println("Saving...");
				String filePath = data.getStringExtra(DirExplorer.RESULT_PATH);
				if (filePath == null) {
					return;
				}
				myLog.i(TAG, "Selected: " + filePath);

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
					// 3.- the user selected an unexisting directory.
					// TODO: ask if sure
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
			}
		}
	}

}
