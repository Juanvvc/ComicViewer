package com.juanvvc.comicviewer;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.gesture.Gesture;
import android.gesture.GestureLibrary;
import android.gesture.GestureOverlayView;
import android.gesture.GestureOverlayView.OnGesturePerformedListener;
import android.gesture.Prediction;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.DigitsKeyListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.ViewSwitcher.ViewFactory;

import com.juanvvc.comicviewer.readers.DrawingReader;
import com.juanvvc.comicviewer.readers.Reader;
import com.juanvvc.comicviewer.readers.ReaderException;

/**
 * Shows a comic on the screen. This class implements ViewFactory because it
 * generates the Views for the internal ImageSwitcher.
 *
 * @author juanvi
 */
public class ComicViewerActivity extends Activity implements ViewFactory, OnTouchListener, OnGesturePerformedListener {
	/** The TAG constant for the MyLogger. */
	private static final String TAG = "ComicViewerActivity";
	/** A task to load pages on the background and free the main thread. */
	private LoadNextPage nextFastPage = null;
	/** A reference to the animations of the images. */
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
	/** The directory for draws. */
	public static final String DRAWDIR = ".draws";
	/** In drawing mode, the drawing reader to save drawings. */
	private DrawingReader drawingReader = null;

	/** In loadComic(), if comifInfo.current == FIRST_PAGE, load the first page. */
	private static final int FIRST_PAGE = 0;
	/** In loadComic(), if comifInfo.current == LAST_PAGE, load the last page.
	 * comicInfo may be instantiated before having a reader, and comicInfo.countpages
	 * cannot be trusted. Then, we use an arbitrary page number to force loadComic()
	 * to move to the last page when the reader is available. */
	private static final int LAST_PAGE = -100;

	// TODO: make these things options
	/** If set, horizontal pages are automatically rotated. */
	private static final int ANIMATION_DURATION = 500;
	/** If set, at the end of the comic loads the next issue. */
	private static final boolean LOAD_NEXT_ISSUE = true;
	/** If set, the draw mode is available. */
	private static final boolean DRAW_MODE_AVAILABLE = true;
	/** The color for the background. */
	public static final int BACK_COLOR = 0xff000000;


	/** Called when the activity is first created.
	 * @param savedInstanceState the Bundle to manage the lifecycle */
	@Override
	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// sets the orientation portrait, mandatory
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setContentView(R.layout.comicvieweractivity);

		ImageSwitcher imgs = (ImageSwitcher) this.findViewById(R.id.switcher);
		imgs.setFactory(this);
		this.configureAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
				R.anim.slide_in_left, R.anim.slide_out_right,
				ANIMATION_DURATION);

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

		// we listen to the events from the user
		this.findViewById(R.id.comicvieweractivity_layout).setOnTouchListener(this);

		// set the colors for the toolbar
		this.findViewById(R.id.color_blue).setBackgroundColor(0xff0000ff);
		this.findViewById(R.id.color_green).setBackgroundColor(0xff00ff00);
		this.findViewById(R.id.color_red).setBackgroundColor(0xffff0000);
		this.findViewById(R.id.pentoolbar).setVisibility(View.GONE);

		// open the gestures library
		// TODO: gestures are not working
		// this.geslibrary= GestureLibraries.fromRawResource(this,
		// R.raw.gestures);
		// if(this.geslibrary.load()){
		// GestureOverlayView gestures = (GestureOverlayView)
		// findViewById(R.id.gestures);
		// gestures.addOnGesturePerformedListener(this);
		// }else{
		// MyLog.w(TAG, "No gestures available");
		// }

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
			savedInstanceState.putInt("page", this.comicInfo.reader.getCurrentPage());
		}
		super.onSaveInstanceState(savedInstanceState);
	}

	/**
	 * Stops all AsyncTasks. These tasks perform useful things in the
	 * background, such as loading the next page in memory to speed up changing
	 * pages. These processes are not really necessary, so they can be
	 * interrupted at any time.
	 */
	private void stopThreads() {
		// stop the AsyncTasks
		if (this.nextFastPage != null) {
			this.nextFastPage.cancel(true);
			this.nextFastPage = null;
		}
	}

	/**
	 * Closes the comic, freeing resources and saving current state on the
	 * database. Typically, this is never called manually
	 */
	public final void close() {
		MyLog.i(TAG, "Closing comic viewer");

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
	 * This method supplies a View for the internal ImageSwitcher. This is were
	 * comics pages are shown.
	 *
	 * @return A simple ImageView that fills the parent is enough.
	 * @see android.widget.ViewSwitcher.ViewFactory#makeView()
	 */
	public final View makeView() {
		MyImageView img = new MyImageView(this, true);
		img.setDrawMode(false, -1, -1);
		img.setScaleType(ImageView.ScaleType.FIT_CENTER);
		img.setLayoutParams(new ImageSwitcher.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		img.setBackgroundColor(BACK_COLOR);
		return img;
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
				try {
					this.stopThreads();
					System.gc();

					ImageSwitcher imgs = (ImageSwitcher) this.findViewById(R.id.switcher);
					ImageView iv = (ImageView) imgs.getCurrentView();
					iv.setImageDrawable(this.comicInfo.reader.current());

					// shows the position of the user in the comic on the screen
					if (this.comicInfo != null && this.comicInfo.reader != null) {
						Toast.makeText(
								this,
								(this.comicInfo.reader.getCurrentPage() + 1)
										+ "/"
										+ this.comicInfo.reader.countPages(),
								Toast.LENGTH_SHORT).show();
					}
				} catch (Exception e) {
					MyLog.e(TAG, e.toString());
					Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();

					try {
						ImageSwitcher imgs = (ImageSwitcher) this.findViewById(R.id.switcher);
						ImageView iv = (ImageView) imgs.getCurrentView();
						// try to load a scaled version
						iv.setImageDrawable(new BitmapDrawable(
								getResources(),
								this.comicInfo.reader.getBitmapPage(this.comicInfo.reader.getCurrentPage(), 4)));
					} catch (Exception e2) {
						MyLog.e(TAG, e2.toString());
					}
				}
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
		return false;
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
	 * Responds to a gestures.
	 * TODO: gestures are not working. Check:
	 * http://developer.android.com/resources/articles/gestures.html
	 *
	 * @param overlay
	 * @param gesture
	 * @see android.gesture.GestureOverlayView.OnGesturePerformedListener#onGesturePerformed(android.gesture.GestureOverlayView,
	 *      android.gesture.Gesture)
	 */
	public final void onGesturePerformed(final GestureOverlayView overlay,
			final Gesture gesture) {
		ArrayList<Prediction> predictions = this.geslibrary.recognize(gesture);
		// We want at least one prediction
		if (predictions.size() > 0) {
			Prediction prediction = predictions.get(0);
			// We want at least some confidence in the result
			if (prediction.score > 1.0) {
				// Show the spell
				Toast.makeText(this, prediction.name, Toast.LENGTH_SHORT).show();
				MyLog.d(TAG, prediction.name);
			}
		}
	}

	/**
	 * Given a URI (actually, a file path), this method chooses the right reader
	 * and loads the comic.
	 *
	 * @param info
	 *            The ComicInfo to load. info.bookmarks are set inside this
	 *            method. The page to be shown is info.page. If info.page == FIRST_PAGE,
	 *            go to the first page. If info.page == LAST_PAGE, go to last page.
	 */
	public final void loadComic(final ComicInfo info) {
		MyLog.i(TAG, "Loading comic " + info.uri + " at page " + info.page);

		close();

		Toast.makeText(this, getText(R.string.loading) + info.uri, Toast.LENGTH_LONG).show();

		// load information about the bookmarks from the database
		ComicDBHelper db = new ComicDBHelper(this);
		ComicInfo ci = db.getComicInfo(db.getComicID(info.uri, false));
		if (ci != null) {
			info.bookmarks = ci.bookmarks;
		} else {
			info.bookmarks = new ArrayList<Integer>();
		}

		// create a drawing reader for this comic
		try {
			// the constructor does nearly nothing, it is save to put this here.
			this.drawingReader = new DrawingReader(this, info.uri);
		} catch (ReaderException e) {
			// this is never thrown, as far as I know
			MyLog.w(TAG, "Exception while creating DrawingReader: " + e.toString());
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
					info.reader.countPages();
					return info;
				} catch (ReaderException e) {
					return null;
				}
			}

			protected void onPostExecute(final ComicInfo info) {
				ComicViewerActivity.this.comicInfo = info;
				if (info != null && info.reader != null) {
					// sets the width and height of the reader
					// TODO: probably, this is better set in MyImageView.onSizeChanged()
					View v = ComicViewerActivity.this.findViewById(R.id.switcher);
					info.reader.setViewportSize(v.getWidth(), v.getHeight());
					// moves to the selected page
					switch(info.page) {
					case FIRST_PAGE:
						ComicViewerActivity.this.moveToPage(0);
						break;
					case LAST_PAGE:
						// Remember: if the comic was not read in the past, you don't know which is the
						// last page index. Use LAST_PAGE constant to force "go to last page"
						// TODO: the animation is wrong in this case. No easy way to fix this.
						ComicViewerActivity.this.moveToPage(info.reader.countPages() - 1);
						break;
					default:
						ComicViewerActivity.this.moveToPage(info.page);
					}
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
		if (this.comicInfo.bookmarks.contains(this.comicInfo.reader.getCurrentPage())) {
			this.findViewById(R.id.bookmark).setVisibility(View.VISIBLE);
		} else {
			this.findViewById(R.id.bookmark).setVisibility(View.GONE);
		}
	}

	/**
	 * Changes the page currently on screen, doing an animation.
	 * TODO: currently, this method only supports left-to-right comics.
	 *
	 * @param forward
	 *            True if the user is moving forward, false is backward
	 */
	public final void changePage(final boolean forward) {
		if (this.comicInfo == null) {
			return;
		}
		ImageSwitcher imgs = (ImageSwitcher) this.findViewById(R.id.switcher);
		Reader reader = this.comicInfo.reader;
		// drawable of the next page
		Drawable n = null;
		// First, remove any draw on the current page. Views are reused, so we never know
		MyImageView myview = ((MyImageView) imgs.getCurrentView());
		if (myview.isEdited() && this.drawingReader != null) {
			this.drawingReader.saveDrawing(this.comicInfo.reader.getCurrentPage(), myview.getCurrentDrawing());
		}
		myview.removeDrawing();
		myview.setDrawMode(false, -1, -1);
		try {
			// set animations according to the movement of the user
			this.setAnimations(forward);
			if (forward) {
				// check if we are at the last page
				if (reader.getCurrentPage() >= reader.countPages() - 1) {
					// load the next issue in the collection
					MyLog.i(TAG, "At the end of the comic");
					if (LOAD_NEXT_ISSUE && this.comicInfo.collection != null) {
						MyLog.d(TAG, "Loading next issue");
						ComicInfo nextIssue = this.comicInfo.collection.next(this.comicInfo);
						if (nextIssue != null) {
							MyLog.i(TAG, "Next issue: " + nextIssue.uri);
							nextIssue.page = FIRST_PAGE; // we load the next issue at the first page. It is weird otherwise
							this.loadComic(nextIssue);
						} else {
							MyLog.i(TAG, "Last comic in collection");
						}
					}
					return;
				}

				// if moving forward, we will check if we loaded the next page
				// in the background
				// We assume that this method is running in the UI thread
				if (this.nextFastPage == null) {
					// load the page from the filesystem
					n = this.comicInfo.reader.next();
					// Cache the next page in the background
					this.nextFastPage = (LoadNextPage) new LoadNextPage().execute(reader.getCurrentPage() + 1);
				} else {
					// TODO: this blocks the UI thread!
					// get the cached page.
					n = this.nextFastPage.get();
					// move to the next page "by hand"
					this.comicInfo.reader.moveTo(reader.getCurrentPage() + 1);
				}
				// create a new thread to load the next page in the background.
				// This supposes that the natural move is onward
				if (this.comicInfo.reader.getCurrentPage() < this.comicInfo.reader.countPages() - 1) {
					this.nextFastPage = (LoadNextPage) new LoadNextPage().execute(reader.getCurrentPage() + 1);
				}
			} else {
				// Moving backwards
				
				this.stopThreads();

				// check that we are not in the first page
				if (reader.getCurrentPage() == 0) {
					// load the next issue in the collection
					MyLog.i(TAG, "First page of rhe comic");
					if (LOAD_NEXT_ISSUE && this.comicInfo.collection != null) {
						MyLog.d(TAG, "Loading prev issue");
						ComicInfo prevIssue = this.comicInfo.collection.prev(this.comicInfo);
						if (prevIssue != null) {
							MyLog.i(TAG, "Prev issue: " + prevIssue.uri);
							prevIssue.page = LAST_PAGE; // we load the last page of the prev issue. It is weird otherwise
							this.loadComic(prevIssue);
						} else {
							MyLog.i(TAG, "First comic in collection");
						}
					}
					return;
				}
				// move to the prev page "by hand".
				// This is faster and safer than this.comicInfo.reader.prev()
				// since we may be using scaled images
				this.comicInfo.reader.moveTo(reader.getCurrentPage() - 1);
				// TODO: this blocks the UI Thread!
				n = this.comicInfo.reader.getPage(reader.getCurrentPage());
				// and load the next page from the prev. That is, the currently
				// displayed page.
				// TODO: I'm sure that there is room for improvements here
				this.nextFastPage = (LoadNextPage) new LoadNextPage().execute(reader.getCurrentPage() + 1);
			}

		} catch (Exception e) {
			Writer result = new StringWriter();
			PrintWriter printWriter = new PrintWriter(result);
			e.printStackTrace(printWriter);

			MyLog.e(TAG, e.toString() + result.toString());
			n = getResources().getDrawable(R.drawable.outofmemory);
			this.stopThreads();
		}

		if (n != null) {
			imgs.setImageDrawable(n);
		}

		// shows the position of the user in the comic on the screen
		if (this.comicInfo != null && this.comicInfo.reader != null) {
			Toast.makeText(
					this,
					(this.comicInfo.reader.getCurrentPage() + 1) + "/"
							+ this.comicInfo.reader.countPages(),
					Toast.LENGTH_SHORT).show();
		
			// if the current page is bookmarked, show the bookmark
			if (this.comicInfo.bookmarks != null && this.comicInfo.bookmarks.contains(this.comicInfo.reader.getCurrentPage())) {
				this.findViewById(R.id.bookmark).setVisibility(View.VISIBLE);
			} else {
				this.findViewById(R.id.bookmark).setVisibility(View.GONE);
			}
			
			// load the drawing, if any
			// TODO: memory problems here? Out of this thread? Test this.
			if (this.drawingReader != null) {
				MyImageView m = (MyImageView) imgs.getCurrentView();
				try {
					m.setCurrentDrawing(drawingReader.getBitmapPage(comicInfo.reader.getCurrentPage(), 1));
				} catch (ReaderException e) {
					MyLog.w(TAG, "Exception reading drawing: " + e.toString());
				}
			}
		}
	}

	/**
	 * Configures the animations of the ImageSwitcher.
	 *
	 * @param inAnim
	 *            Animation of the page that enters during a forward movement
	 * @param outAnim
	 *            Animation of the page that goes out during a forward movement
	 * @param inRevAnim
	 *            Animation of the page that enters during a backward movement
	 * @param outRevAnim
	 *            Animation of the page that goes out during a backward movement
	 * @param duration
	 *            Duration of animations.
	 */
	public final void configureAnimations(
			final int inAnim, final int outAnim,
			final int inRevAnim, final int outRevAnim,
			final int duration) {
		Context context = this.getApplicationContext();
		anims[0] = AnimationUtils.loadAnimation(context, inAnim);
		anims[1] = AnimationUtils.loadAnimation(context, outAnim);
		anims[2] = AnimationUtils.loadAnimation(context, inRevAnim);
		anims[3] = AnimationUtils.loadAnimation(context, outRevAnim);
		for (int i = 0; i < anims.length; i++) {
			anims[i].setDuration(duration);
		}
		this.setAnimations(true);
	}

	/**
	 * Set the animations for the next change according to the movement of the user.
	 *
	 * @param forward
	 *            True if the user is moving the comic forward, false otherwise
	 */
	private void setAnimations(final boolean forward) {
		ImageSwitcher imgs = (ImageSwitcher) this.findViewById(R.id.switcher);
		if (imgs != null && anims[0] != null) {
			if (forward) {
				imgs.setInAnimation(this.anims[0]);
				imgs.setOutAnimation(this.anims[1]);
			} else {
				imgs.setInAnimation(this.anims[2]);
				imgs.setOutAnimation(this.anims[3]);
			}
		}
	}

	/**
	 * This task is used to cache a page in a background thread and improve the
	 * GUI response time. Use (page is an integer):
	 *
	 * page=new LoadNextPage().execute(page);
	 * (when necessary)
	 * Drawable newpage = page.get()
	 *
	 * @author juanvi
	 */
	private class LoadNextPage extends AsyncTask<Integer, Void, Drawable> {
		@Override
		protected Drawable doInBackground(final Integer... params) {
			if (ComicViewerActivity.this.comicInfo == null) {
				return null;
			}
			int page = params[0].intValue();
			MyLog.d(TAG, "Buffering page " + page);
			try {
				return ComicViewerActivity.this.comicInfo.reader.getPage(page);
			} catch (Exception e) {
				return ComicViewerActivity.this.getResources().getDrawable(R.drawable.outofmemory);
			}
		}

		protected void onPostExecute(final Drawable d) {
			MyLog.d(TAG, "Next page loaded");
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
		case R.id.switch_bookmark: // switch the bookmark state.
			// since you can do this by pressing a corner on the screen,
			// I'm not sure if it is useful to have a menu entry for this.
			if (this.comicInfo != null) {
				switchBookmark();
			}
			break;
		case R.id.bookmarks: // show the bookmark list
			if (this.comicInfo != null) {
				// first: if there are not bookmarks... why bother?
				if (this.comicInfo.bookmarks.size() > 0) {
					// the information of the current bookmarks must be updated in the database
					ComicDBHelper db = new ComicDBHelper(this);
					// first: if the comic is not in the database, insert
					if (this.comicInfo.id == -1) {
						// note that this call either insert the comic in
						// the database... or returns the right id.
						// comics cannot be inserted twice.
						comicInfo.id = db.getComicID(comicInfo.uri, true);
					}
					// update the information
					db.updateComicInfo(this.comicInfo);
					db.close();
					// show the bookmark list
					Intent intent = new Intent(this, BookmarksExplorer.class);
					intent.putExtra("comicid", this.comicInfo.id);
					startActivityForResult(intent, REQUEST_BOOKMARKS);
				} else {
					Toast.makeText(this, this.getString(R.string.no_bookmarks), Toast.LENGTH_SHORT).show();
				}
			}
			break;
		case R.id.go_to_page: // go to a page (ask the user)
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
											MyLog.e(TAG, e.toString());
										}
									}

								}).setNegativeButton("Cancel", null).show();
			}
			break;
		case R.id.close: // close this activity
			this.finish();
			return true;
		case R.id.switch_drawing: // switch to draw mode
			if (DRAW_MODE_AVAILABLE) {
				MyImageView i = (MyImageView) ((ImageSwitcher) this.findViewById(R.id.switcher)).getCurrentView();
				if (i.isDrawVisible()) {
					if (i.isDrawMode()) {
						i.setDrawMode(false, -1, -1);
						this.findViewById(R.id.pentoolbar).setVisibility(View.GONE);
					} else {
						i.setDrawMode(true, -1, -1);
						this.findViewById(R.id.pentoolbar).setVisibility(View.VISIBLE);
						this.onPenToolbarColor(null);
					}
				}
			} else {
				new AlertDialog.Builder(this).setIcon(R.drawable.icon)
					.setTitle(this.getText(R.string.draw_mode_not_available))
					.setPositiveButton(android.R.string.ok, null).show();
			}
			return true;
		case R.id.switch_drawing_visible: // switches the drawing on and off
			if (DRAW_MODE_AVAILABLE) {
				MyImageView im = (MyImageView) ((ImageSwitcher) this.findViewById(R.id.switcher)).getCurrentView();
				// remember: the imageswitcher has two MyImageViews: we switch the visible mode in both
				if (!im.isDrawMode()) { // do not change view if draw mode is active
					MyImageView i = (MyImageView) ((ImageSwitcher) this.findViewById(R.id.switcher)).getChildAt(0);
					i.setDrawVisible(!i.isDrawVisible());
					i = (MyImageView) ((ImageSwitcher) this.findViewById(R.id.switcher)).getChildAt(1);
					i.setDrawVisible(!i.isDrawVisible());
					// and redraw the current one
					im.invalidate();
				}
			}
			return true;
		default:
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Gets the result of calling BookmarkExplorer.
	 * The result is the page to show next
	 * @param requestCode The code of the requested subactivity.
	 * @param resultCode The resulting code
	 * @param data The resulting data
	 */
	public final synchronized void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			if (requestCode == REQUEST_BOOKMARKS) {
				int page = data.getIntExtra("page", 0);
				MyLog.i(TAG, "Bookmark to page " + page);
				this.moveToPage(page);
			}
		}
	}

	//////////////////MANAGE THE TOOLBAR
	/** Deletes the current drawing from screen.
	 * @param v ignored */
	public final void onPenToolbarDelete(final View v) {
		MyImageView i = (MyImageView) ((ImageSwitcher) this.findViewById(R.id.switcher)).getCurrentView();
		i.removeDrawing();
		if (this.drawingReader != null && comicInfo != null && comicInfo.reader != null) {
			this.drawingReader.removeDrawing(this.comicInfo.reader.getCurrentPage());
			i.setEdited(false);
		}
	}
	/** Changes the color of the pen.
	 * @param v Ignored. */
	public final void onPenToolbarColor(final View v) {
		boolean r = ((ToggleButton) this.findViewById(R.id.color_red)).isChecked();
		boolean g = ((ToggleButton) this.findViewById(R.id.color_green)).isChecked();
		boolean b = ((ToggleButton) this.findViewById(R.id.color_blue)).isChecked();
		MyImageView i = (MyImageView) ((ImageSwitcher) this.findViewById(R.id.switcher)).getCurrentView();
		int newColor = 0xff000000 | (r?0xff0000:0) | (g?0x00ff00:0) | (b?0x0000ff:0);
		i.setPainterColor(newColor);
	}
	/** Changes the width of the current pen.
	 * @param v ignored */
	public final void onPenToolbarWidth(final View v) {
		MyImageView i = (MyImageView) ((ImageSwitcher) this.findViewById(R.id.switcher)).getCurrentView();
		float currentWidth = i.getPainterWidth();
		if (currentWidth < 10) {
			i.setPainterWidth(30);
		} else {
			i.setPainterWidth(3);
		}
	}

	@Override
	public void finish() {
		// check the drawing
		if (this.drawingReader != null) {
			MyImageView mi = (MyImageView) ((ImageSwitcher) this.findViewById(R.id.switcher)).getCurrentView();
			if (mi.isEdited() && this.comicInfo != null && this.comicInfo.reader != null) {
				this.drawingReader.saveDrawing(this.comicInfo.reader.getCurrentPage(), mi.getCurrentDrawing());
			}
		}
		// if threads are not stopped, they run on the background. At the end, they may change
		// views that are not available, and the whole application crashes. True story.
		// In addition, save the current state of the comic
		this.close();
		super.finish();
	}
}
