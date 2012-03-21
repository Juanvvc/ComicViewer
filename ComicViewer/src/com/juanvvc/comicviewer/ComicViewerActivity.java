package com.juanvvc.comicviewer;

import java.io.File;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.gesture.GestureLibrary;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.method.DigitsKeyListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.juanvvc.comicviewer.readers.Reader;
import com.juanvvc.comicviewer.readers.ReaderException;

/**
 * Shows a comic on the screen. This class implements ViewFactory because it
 * generates the Views for the internal ImageSwitcher.
 *
 * @author juanvi
 */
public class ComicViewerActivity extends Activity implements OnTouchListener {
	/** The TAG constant for the myLogger. */
	private static final String TAG = "ComicViewerActivity";
	/** A task to load current page on the background and free the main thread. */
	private LoadCurrentPage currentPage = null;
	/**
	 * A reference to the animations.
	 *
	 * @see com.juanvvc.comicviewer.ComicViewerActivity#configureAnimations(int,
	 *      int, int, int, int)
	 */
	private Animation[] anims = {null, null, null, null };
	/** The gestures library. */
	private GestureLibrary geslibrary;
	/**
	 * Information in the DB about the loaded comic. If null, no comic was
	 * loaded
	 */
	private ComicInfo comicInfo = null;
	/** Random number to identify request of bookmarks. */
	private static final int REQUEST_BOOKMARKS = 0x21;

	// TODO: make these things options
	/** If set, horizontal pages are automatically rotated. */
	private static final int ANIMATION_DURATION = 500;
	/** The scale of the fast pages. If ==1, no fast pages are used */
	private static final int FAST_PAGES_SCALE = 2;
	/** If set, at the end of the comic loads the next issue. */
	private static final boolean LOAD_NEXT_ISSUE = true;

	/** Called when the activity is first created. */
	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// sets the orientation portrait, mandatory
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setContentView(R.layout.comicvieweractivity);

		// load the intent, if any
		Intent intent = this.getIntent();
		String uri = null;
		int savedPage = -1;
		ComicInfo info;
		if (intent.getExtras() != null && intent.getExtras().containsKey("uri")) {
			uri = intent.getExtras().getString("uri");
			if (intent.getExtras().containsKey("page")) {
				savedPage = intent.getExtras().getInt("page");
			}
		} else if (savedInstanceState != null
				&& savedInstanceState.containsKey("uri")) {
			// load the save state, if any
			uri = savedInstanceState.getString("uri");
			if (savedInstanceState.containsKey("page")) {
				savedPage = savedInstanceState.getInt("page");
			}
		} else {
			new AlertDialog.Builder(this).setIcon(R.drawable.icon)
					.setTitle(this.getText(R.string.no_comic))
					.setPositiveButton(android.R.string.ok, null).show();
			return;
		}

		// get the information of this Comic from the database
		ComicDBHelper db = new ComicDBHelper(this);
		long id = db.getComicID(uri, true);
		info = db.getComicInfo(id);
		db.close();
		// if we still have no information of the comic, create it
		// Note: info==null only after an error in the database. Possible, but
		// rare
		if (info == null) {
			info = new ComicInfo();
			info.page = 0;
			info.uri = uri;
		}

		// if savedPage is set, it has preference
		// saved page is set when the activity is on pause, or was pased by the
		// intent
		if (savedPage > -1) {
			info.page = savedPage;
		}

		// load the comic, on the background
		this.loadComic(info);
	}

	private class PageAdapter extends PagerAdapter {
		private ComicInfo comicInfo = null;
		public PageAdapter(ComicInfo ci) {
			this.comicInfo = ci;
		}

		@Override
		public int getCount() {
			if (comicInfo.reader != null) {
				return this.comicInfo.reader.countPages();
			} else {
				return 0;
			}
		}

    	@Override
    	public Object instantiateItem(View collection, int position) {
    		ImageView v = new ImageView(ComicViewerActivity.this);
    		if(this.comicInfo.reader != null) {
	    		try {
	    			v.setImageDrawable(this.comicInfo.reader.getFastPage(position, FAST_PAGES_SCALE));
	    		} catch(ReaderException e) {
	    			v.setImageResource(R.drawable.outofmemory);
	    		}
    		}
    		((ViewPager) collection).addView(v, 0);
    		return v;
    	}
		@Override
		public boolean isViewFromObject(View view, Object object) {
			return view == ((ImageView)object);
		}
		@Override
		public void destroyItem(View collection, int position, Object view) {
			((ViewPager) collection).removeView((ImageView) view);
		}
		@Override
		public void setPrimaryItem(ViewGroup view, int position, Object item) {
			ComicViewerActivity.this.currentPage = (LoadCurrentPage) (new LoadCurrentPage()).execute(item, position);
		}
	}

	/**
	 * Saves the current comic and page. This method updates the internal state
	 * and not the database, since the activity is not going to be stopped. We
	 * like to modify the database as less as possible.
	 *
	 * @param savedInstanceState
	 *            the place to save state information
	 * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	public final void onSaveInstanceState(final Bundle savedInstanceState) {
		if (this.comicInfo != null) {
			savedInstanceState.putString("uri", this.comicInfo.reader.getURI());
			savedInstanceState.putInt("page",
					this.comicInfo.reader.getCurrentPage());
		}
		super.onSaveInstanceState(savedInstanceState);
	}

	/**
	 * Stops all AsyncTasks. These tasks perform useful things in the
	 * background, such as loading the next page in memory to speed up changing
	 * pages, or loading a better version of the current page. These process are
	 * not really necessary, so they can be interrupted at any time.
	 */
	private void stopThreads() {
		// stop the AsyncTasks
		if (this.currentPage != null) {
			this.currentPage.cancel(true);
			this.currentPage = null;
		}
	}

	/**
	 * Closes the comic, freeing resources and saving current state on the
	 * database. Typically, this is never called manually
	 */
	public final void close() {
		myLog.i(TAG, "Closing comic viewer");

		this.stopThreads();

		if (this.comicInfo != null && this.comicInfo.reader != null) {
			if (this.comicInfo != null) {
				ComicDBHelper db = new ComicDBHelper(this);
				db.updateComicInfo(this.comicInfo);
				db.close();
			}
			this.comicInfo.reader.close();
			this.comicInfo = null;
		}
	}

	@Override
	public final void onDestroy() {
		this.close();
		super.onDestroy();
	}



	/**
	 * Called when the screen is pressed.
	 *
	 * @see android.view.View.OnTouchListener#onTouch(android.view.View,
	 *      android.view.MotionEvent)
	 */
	public boolean onTouch(View v, MotionEvent event) {
		if (this.comicInfo == null) {
			return false;
		}
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			int zone = this.getZone(v, event.getX(), event.getY());
			switch (zone) {
			case 1: // center of header. In landscape mode, go back a page
			case 3: // left margin
				this.changePage(false);
				break;
			case 4: // center
				// reload current image (it may help in some large pages)
				break;
			case 7: // center of footer. In landscape mode, advance a page
			case 5: // right margin
				this.changePage(true);

				break;
			case 2: // right side of the header
				this.switchBookmark();
				break;
			default:
			}
		}
		return true;
	}

	/**
	 * Returns the identifier of the zone (x, y) of a view. A zone is a
	 * geometric area inside the view. For example, the righ side, the left
	 * side... There are 9 zones. From 0 to 8: header-left-center-right;
	 * margin-left-center-margin right; footer-left-center-right
	 * 
	 * @param v
	 *            The view where the user clicked
	 * @param x
	 *            The X position of the click
	 * @param y
	 *            The Y position of the click
	 * @return the identifier of the zone
	 */
	private int getZone(final View v, final float x, final float y) {
		// currently, only two zones are used: left(0), center(1), right(2)
		// remember that this activity is intended for portrait mode
		if (x < v.getWidth() / 3) {
			if (y < 0.2 * v.getHeight()) {
				return 0;
			} else if (y > 0.8 * v.getHeight()) {
				return 6;
			}
			return 3;
		}
		if (x > 2 * v.getWidth() / 3) {
			if (y < 0.2 * v.getHeight()) {
				return 2;
			} else if (y > 0.8 * v.getHeight()) {
				return 8;
			}
			return 5;
		}
		if (y < 0.2 * v.getHeight()) {
			return 1;
		} else if (y > 0.8 * v.getHeight()) {
			return 7;
		}
		return 4;
	}


	/**
	 * Given a URI (actually, a file path), this method chooses the right reader
	 * and loads the comic.
	 * 
	 * @param info
	 *            The ComicInfo to load. info.bookmarks are set inside this
	 *            method. The page loaded is info.page
	 */
	public final void loadComic(final ComicInfo info) {
		myLog.i(TAG, "Loading comic " + info.uri + " at page " + info.page);

		close();

		Toast.makeText(this, getText(R.string.loading) + info.uri,
				Toast.LENGTH_LONG).show();

		// load information about the bookmarks from the database
		ComicDBHelper db = new ComicDBHelper(this);
		ComicInfo ci = db.getComicInfo(db.getComicID(info.uri, false));
		if (ci != null) {
			info.bookmarks = ci.bookmarks;
		} else {
			info.bookmarks = new ArrayList<Integer>();
		}

		// the comic is loaded in the background, since there is lots of things to do
		(new AsyncTask<ComicInfo, Void, ComicInfo>() {
			@Override
			protected ComicInfo doInBackground(final ComicInfo... params) {
				ComicInfo info = params[0];
				if (info.uri == null) {
					return null;
				}
				try {
					// chooses the right reader to use
					info.reader = Reader.getReader(ComicViewerActivity.this, info.uri);
					if (info.reader == null) {
						throw new ReaderException(getText(R.string.no_suitable_reader) + info.uri);
					}
					File colRoot = new File(info.uri).getParentFile();
					info.collection = new ComicCollection(colRoot.getName()).populate(ComicViewerActivity.this, colRoot);
					return info;
				} catch (ReaderException e) {
					return null;
				}
			}

			protected void onPostExecute(final ComicInfo info) {
				ComicViewerActivity.this.comicInfo = info;
				if (info != null) {
					((ViewPager)ComicViewerActivity.this.findViewById(R.id.pager)).setAdapter(new PageAdapter(info));
					ComicViewerActivity.this.moveToPage(info.page);
				}
			}
		}).execute(info);
	}

	/** Switches the bookmark in the current page. */
	private void switchBookmark() {
		if (this.comicInfo != null) {
			int cp = this.comicInfo.reader.getCurrentPage();
			if (cp < 0) {
				return;
			} else if (this.comicInfo.bookmarks.contains(cp)) {
				this.comicInfo.bookmarks.remove(new Integer(cp));
				this.findViewById(R.id.bookmark).setVisibility(View.GONE);
			} else {
				this.comicInfo.bookmarks.add(cp);
				this.findViewById(R.id.bookmark).setVisibility(View.VISIBLE);
			}
		}
	}

	/**
	 * Moves the view to a certain page. This movement makes a long jump: use
	 * changePage() to flip a page
	 * 
	 * @param page
	 *            The page to move to.
	 */
	private void moveToPage(final int page) {
		// check that the movement makes sense
		if (this.comicInfo.reader == null) {
			return;
		} else if (this.comicInfo.reader.countPages() == 1) {
			return;
		} else if (page < 0 || page >= this.comicInfo.reader.countPages()) {
			return;
		} else if (page == this.comicInfo.reader.getCurrentPage()) {
			return;
		}
		this.stopThreads();
		// move onwards or backwards, according to the current page
		if (this.comicInfo.reader.getCurrentPage() < page) {
			this.comicInfo.reader.moveTo(page - 1);
			this.changePage(true);
		} else {
			this.comicInfo.reader.moveTo(page + 1);
			this.changePage(false);
		}

		// shows the position of the user in the comic on the screen
		if (this.comicInfo != null && this.comicInfo.reader != null) {
			Toast.makeText(
					this,
					(this.comicInfo.reader.getCurrentPage() + 1) + "/"
							+ this.comicInfo.reader.countPages(),
					Toast.LENGTH_SHORT).show();
		}

		// if the page is bookmarked, show the bookmark
		if (this.comicInfo.bookmarks.contains(this.comicInfo.reader
				.getCurrentPage())) {
			this.findViewById(R.id.bookmark).setVisibility(View.VISIBLE);
		} else {
			this.findViewById(R.id.bookmark).setVisibility(View.GONE);
		}
	}

	/**
	 * Changes the page currently on screen, doing an animation. TODO:
	 * currently, this method only supports left-to-right comics.
	 * 
	 * @param forward
	 *            True if the user is moving forward, false is backward
	 */
	public final void changePage(final boolean forward) {
//		if (this.comicInfo == null) {
//			return;
//		}
//		ImageSwitcher imgs = (ImageSwitcher) this.findViewById(R.id.switcher);
//		Reader reader = this.comicInfo.reader;
//		// drawable of the next page
//		Drawable n = null;
//		try {
//			// set animations according to the movement of the user
//			this.setAnimations(forward);
//			if (forward) {
//				// check that we are not in the last page
//				if (reader.getCurrentPage() >= reader.countPages() - 1) {
//					// load the next issue in the collection
//					myLog.i(TAG, "At the end of the comic");
//					if (LOAD_NEXT_ISSUE && this.comicInfo.collection != null) {
//						myLog.d(TAG, "Loading next issue");
//						ComicInfo nextIssue = this.comicInfo.collection
//								.next(this.comicInfo);
//						if (nextIssue != null) {
//							myLog.i(TAG, "Next issue: " + nextIssue.uri);
//							nextIssue.page = 0; // we load the next issue at the
//												// first page. It is weird
//												// otherwise
//							this.loadComic(nextIssue);
//						}
//					}
//					return;
//				}
//
//				// if moving forward, we will check if we loaded the next page
//				// in the background
//				// We assume that this method is running in the UI thread
//				if (this.nextFastPage == null) {
//					// load the page from the filesystem
//					n = this.comicInfo.reader.next();
//					// Cache the next page in the background
//					this.nextFastPage = (LoadNextPage) new LoadNextPage()
//							.execute(reader.getCurrentPage() + 1);
//				} else {
//					// get the cached page.
//					n = this.nextFastPage.get();
//					// move to the next page "by hand"
//					this.comicInfo.reader.moveTo(reader.getCurrentPage() + 1);
//				}
//				// create a new thread to load the next page in the background.
//				// This supposes that
//				// the natural move is onward and the user will see the next
//				// page next
//				this.nextFastPage = (LoadNextPage) new LoadNextPage()
//						.execute(reader.getCurrentPage() + 1);
//			} else {
//				// check that we are not in the first page
//				if (reader.getCurrentPage() == 0) {
//					return;
//				}
//				// move to the prev page "by hand".
//				// This is faster and safer than this.comicInfo.reader.prev()
//				// since we may be using scaled images
//				this.comicInfo.reader.moveTo(reader.getCurrentPage() - 1);
//				// if the user is moving backwards, the background thread (if
//				// existed) was
//				// loading the NEXT page. Stop it now.
//				if (this.nextFastPage != null) {
//					this.nextFastPage.cancel(true);
//				}
//				n = this.comicInfo.reader.getFastPage(reader.getCurrentPage(),
//						FAST_PAGES_SCALE);
//				// and load the next page from the prev. That is, the currently
//				// displayed page.
//				// TODO: I'm sure that there is room for improvements here
//				this.nextFastPage = (LoadNextPage) new LoadNextPage()
//						.execute(reader.getCurrentPage() + 1);
//			}
//
//		} catch (Exception e) {
//			Writer result = new StringWriter();
//			PrintWriter printWriter = new PrintWriter(result);
//			e.printStackTrace(printWriter);
//
//			myLog.e(TAG, e.toString() + result.toString());
//			n = getResources().getDrawable(R.drawable.outofmemory);
//			this.stopThreads();
//		}
//
//		if (n != null) {
//			imgs.setImageDrawable(n);
//		}
//
//		// shows the position of the user in the comic on the screen
//		if (this.comicInfo != null && this.comicInfo.reader != null) {
//			Toast.makeText(
//					this,
//					(this.comicInfo.reader.getCurrentPage() + 1) + "/"
//							+ this.comicInfo.reader.countPages(),
//					Toast.LENGTH_SHORT).show();
//		}
//
//		// if the current page is bookmarked, show the bookmark
//		if (this.comicInfo.bookmarks != null
//				&& this.comicInfo.bookmarks.contains(this.comicInfo.reader
//						.getCurrentPage())) {
//			this.findViewById(R.id.bookmark).setVisibility(View.VISIBLE);
//		} else {
//			this.findViewById(R.id.bookmark).setVisibility(View.GONE);
//		}

	}

	/**
	 * This task is used to load a page in a background thread and improve the
	 * GUI response time. This thread is used to load an unscaled version of the
	 * current page Use (page is an integer):
	 * 
	 * page=new LoadCurrentPage().execute(page); (...do whatever...) Drawable
	 * newpage = page.get()
	 * 
	 * @author juanvi
	 */
	private class LoadCurrentPage extends AsyncTask<Object, Void, Drawable> {
		/** The view that shows the low quality version of the current page. */
		private ImageView view = null;

		@Override
		protected Drawable doInBackground(final Object... params) {
			Reader reader = ComicViewerActivity.this.comicInfo.reader;
			if (reader == null) {
				return null;
			}
			view = (ImageView) params[0];
			int page = ((Integer) params[1]).intValue();
			myLog.d(TAG, "Loading page " + page);
			try {
				return reader.getPage(page);
			} catch (ReaderException e) {
				return null;
			}
		}

		protected void onPostExecute(Drawable d) {
			if (d != null) {
				((BitmapDrawable) view.getDrawable()).getBitmap().recycle();
				view.setImageDrawable(d);
				// TODO: free the last view
			}
		}
	}

	// //////////////////////////////MANAGE THE MENU
	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.comicmenu, menu);
		return true;
	}

	@Override
	public final boolean onOptionsItemSelected(final MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.first_page: // go to the first page
			if (this.comicInfo != null
					&& this.comicInfo.reader.countPages() > 1) {
				this.moveToPage(0);
				return true;
			}
			break;
		case R.id.last_page: // go to the last page
			if (this.comicInfo != null
					&& this.comicInfo.reader.countPages() > 1) {
				this.moveToPage(this.comicInfo.reader.countPages() - 1);
				return true;
			}
			break;
		case R.id.switch_bookmark: // switch the bookmark state. Not sure if
									// useful
			if (this.comicInfo != null) {
				switchBookmark();
			}
			break;
		case R.id.bookmarks:
			if (this.comicInfo != null) {
				// the information of the bookmarks must be updated in the
				// database
				ComicDBHelper db = new ComicDBHelper(this);
				db.updateComicInfo(this.comicInfo);
				db.close();
				// show the bookmark list
				Intent intent = new Intent(this, BookmarksExplorer.class);
				intent.putExtra("comicid", this.comicInfo.id);
				startActivityForResult(intent, REQUEST_BOOKMARKS);
			}
			break;
		case R.id.go_to_page:
			if (this.comicInfo != null) {
				final EditText input = new EditText(this);
				input.setInputType(DEFAULT_KEYS_DIALER);
				input.setKeyListener(new DigitsKeyListener());

				new AlertDialog.Builder(this)
						.setTitle(this.getText(R.string.go_to_page))
						.setView(input)
						.setPositiveButton(android.R.string.ok,
								new DialogInterface.OnClickListener() {

									public void onClick(
											final DialogInterface dialog,
											final int which) {
										try {
											int page = Integer.parseInt(input
													.getText().toString());
											ComicViewerActivity.this
													.moveToPage(page - 1);
										} catch (NumberFormatException e) {
											myLog.e(TAG, e.toString());
										}
									}

								}).setNegativeButton("Cancel", null).show();
			}
			break;
		case R.id.close: // close this viewer
			this.finish();
			return true;
		default:
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Gets the result of calling BookmarkExplroer. The result is the page to
	 * show next
	 */
	public final synchronized void onActivityResult(final int requestCode,
			int resultCode, final Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			if (requestCode == REQUEST_BOOKMARKS) {
				int page = data.getIntExtra("page", 0);
				myLog.i(TAG, "Bookmark to page " + page);
				this.moveToPage(page);
			}
		}
	}
}
