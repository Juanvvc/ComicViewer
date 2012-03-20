package com.juanvvc.comicviewer;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
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
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ViewSwitcher.ViewFactory;

import com.juanvvc.comicviewer.readers.Reader;
import com.juanvvc.comicviewer.readers.ReaderException;

/** Shows a comic on the screen
 * This class implements ViewFactory because it generates the Views for the internal ImageSwitcher
 * @author juanvi */
public class ComicViewerActivity extends Activity implements ViewFactory, OnTouchListener, OnGesturePerformedListener, AnimationListener{
	/** The TAG constant for the Logger */
	private static final String TAG="ComicViewerActivity";
	/** A task to load pages on the background and free the main thread */
	private LoadNextPage nextFastPage=null;
	/** A task to load current page on the background and free the main thread */
	private LoadCurrentPage currentPage=null;
	/** A reference to the animations.
	 * @see com.juanvvc.comicviewer.ComicViewerActivity#configureAnimations(int, int, int, int, int) */
	private Animation anims[]={null, null, null, null};
	/** The gestures library */
	private GestureLibrary geslibrary;
	/** Information in the DB about the loaded comic.
	 * If null, no comic was loaded */
	private ComicInfo comicInfo=null;
	/** Random number to identify request of bookmarks */
	private static final int REQUEST_BOOKMARKS=0x21;

	///////////// TODO: make these things options
	/** If set, horizontal pages are automatically rotated */
	private final static int ANIMATION_DURATION=500;
	/** The scale of the fast pages. If ==1, no fast pages are used */
	private static int FAST_PAGES_SCALE=2;
	/** If set, at the end of the comic loads the next issue */
	private static final boolean LOAD_NEXT_ISSUE=true;
    
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // sets the orientation portrait, mandatory
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.comicvieweractivity);
        
		ImageSwitcher imgs=(ImageSwitcher)this.findViewById(R.id.switcher);
		imgs.setFactory(this);
		this.configureAnimations(
				R.anim.slide_in_right, R.anim.slide_out_left,
				R.anim.slide_in_left, R.anim.slide_out_right,
				ANIMATION_DURATION);

    	// load the intent, if any
    	Intent intent=this.getIntent();
    	String uri=null;
    	int savedPage=-1;
    	ComicInfo info;
    	if(intent.getExtras()!=null && intent.getExtras().containsKey("uri")){
    		uri=intent.getExtras().getString("uri");
    		if(intent.getExtras().containsKey("page"))
    			savedPage=intent.getExtras().getInt("page");
    	}else if(savedInstanceState!=null && savedInstanceState.containsKey("uri")){
    		// load the save state, if any
    		uri=savedInstanceState.getString("uri");
        	if(savedInstanceState.containsKey("page")){
        		savedPage=savedInstanceState.getInt("page");
        	}
    	}else{
    		new AlertDialog.Builder(this)
    			.setIcon(R.drawable.icon)
    			.setTitle(this.getText(R.string.no_comic))
    			.setPositiveButton(android.R.string.ok, null).show();
    		return;
    	}
    	
    	// get the information of this Comic from the database
    	ComicDBHelper db=new ComicDBHelper(this);
    	long id=db.getComicID(uri, true);
   		info=db.getComicInfo(id);
   		db.close();
    	// if we still have no information of the comic, create it
   		// Note: info==null only after an error in the database. Possible, but rare
    	if(info==null){
    		info=new ComicInfo();
    		info.page=0;
    		info.uri=uri;    		
    	}
    	
    	// if savedPage is set, it has preference
    	// saved page is set when the activity is on pause, or was pased by the intent
    	if(savedPage>-1)
    		info.page=savedPage;
    	
    	// load the comic, on the background
    	this.loadComic(info);

    	// we listen to the events from the user
    	((View)this.findViewById(R.id.comicvieweractivity_layout)).setOnTouchListener(this);
    	
    	// open the gestures library
    	// TODO: gestures are not working
//    	this.geslibrary= GestureLibraries.fromRawResource(this, R.raw.gestures);
//    	if(this.geslibrary.load()){
//    		GestureOverlayView gestures = (GestureOverlayView) findViewById(R.id.gestures);
//    		gestures.addOnGesturePerformedListener(this);
//    	}else{
//    		Log.w(TAG, "No gestures available");
//    	}
        
    }
    
    
    /** Saves the current comic and page.
     * This method updates the internal state and not the database, since the activity is not
     * going to be stopped. We like to modify the database as less as possible.
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
    	if(this.comicInfo!=null){
	    	savedInstanceState.putString("uri", this.comicInfo.reader.getURI());
	    	savedInstanceState.putInt("page", this.comicInfo.reader.getCurrentPage());
    	}
    	super.onSaveInstanceState(savedInstanceState);
    }

    
    /** Closes the comic, freeing resources and saving current state on the database.
     * Typically, this is never called manually */
    public void close(){
    	Log.i(TAG, "Closing comic viewer");
    	
    	// stop the AsyncTasks
    	if(this.nextFastPage!=null){
        	this.nextFastPage.cancel(true);
        	this.nextFastPage=null;
        }
        if(this.currentPage!=null){
        	this.currentPage.cancel(true);
        	this.currentPage=null;
        }
    	
    	if(this.comicInfo!=null && this.comicInfo.reader!=null){
    		if(this.comicInfo!=null){
        		ComicDBHelper db=new ComicDBHelper(this);
        		db.updateComicInfo(this.comicInfo);
        		db.close();
    		}
    		if(this.nextFastPage!=null)
    			this.nextFastPage.cancel(true);
    		if(this.currentPage!=null)
    			this.currentPage.cancel(true);
    		this.comicInfo.reader.close();
    		this.comicInfo=null;
    	}    		
    }
    
    @Override
    public void onDestroy(){
    	this.close();
    	super.onDestroy();
    }
    
    /* This method supplies a View for the ImageSwitcher.
     * The supplied view is just an ImageView that fills the parent.
     * @see android.widget.ViewSwitcher.ViewFactory#makeView()
     */
    public View makeView(){
    	ImageView img=new ImageView(this);
    	img.setScaleType(ImageView.ScaleType.FIT_CENTER);
    	img.setLayoutParams(new ImageSwitcher.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
    	img.setBackgroundColor(0xff000000);
    	return img;
    }

      
	/** Called when the screen is pressed
	 * @see android.view.View.OnTouchListener#onTouch(android.view.View, android.view.MotionEvent)
	 */
	public boolean onTouch(View v, MotionEvent event) {
		if(this.comicInfo==null)
			return false;
		if(event.getAction()==MotionEvent.ACTION_DOWN){
			int zone = this.getZone(v, event.getX(), event.getY());
			switch(zone){
			case 3: // left margin
				this.changePage(false);
				break;
			case 4: // center
				// reload current image (it may help in some large pages)
				try{
					ImageSwitcher imgs=(ImageSwitcher)this.findViewById(R.id.switcher);
					ImageView iv=(ImageView) imgs.getCurrentView();
					((BitmapDrawable)iv.getDrawable()).getBitmap().recycle();
					iv.setImageDrawable(this.comicInfo.reader.current());
					
					// shows the position of the user in the comic on the screen
					if(this.comicInfo!=null && this.comicInfo.reader!=null)
						Toast.makeText(this,
							(this.comicInfo.reader.getCurrentPage()+1)+"/"+this.comicInfo.reader.countPages(),
							Toast.LENGTH_SHORT).show();
				}catch(Exception e){
					Log.e(TAG, e.toString());
				}
				break;
			case 5: // right margin
				this.changePage(true);

				break;
			case 2: // right side of the header
				this.switchBookmark();
				break;
			}
		}
		return true;
	}
	
	/** Returns the identifier of the zone (x, y) of a view.
	 * A zone is a geometric area inside the view. For example, the righ side, the left side...
	 * There are 9 zones. From 0 to 8: header-left-center-right; margin-left-center-margin right; footer-left-center-right 	 */
	private int getZone(View v, float x, float y){
		// currently, only two zones are used: left(0), center(1), right(2)
		// remember that this activity is intended for portrait mode
		if(x<v.getWidth()/3){
			if(y<0.2*v.getHeight()) return 0;
			if(y>0.8*v.getHeight()) return 6;
			return 3;
		}
		if(x>2*v.getWidth()/3){
			if(y<0.2*v.getHeight()) return 2;
			if(y>0.8*v.getHeight()) return 8;
			return 5;
		}
		if(y<0.2*v.getHeight()) return 1;
		if(y>0.8*v.getHeight()) return 7;
		return 4;
	}
	
	/** Responds to a gestures.
	 * TODO: gestures not working. Check: http://developer.android.com/resources/articles/gestures.html
	 * @see android.gesture.GestureOverlayView.OnGesturePerformedListener#onGesturePerformed(android.gesture.GestureOverlayView, android.gesture.Gesture)
	 */
	public void onGesturePerformed(GestureOverlayView overlay, Gesture gesture) {
	    ArrayList<Prediction> predictions = this.geslibrary.recognize(gesture);
	    // We want at least one prediction
	    if (predictions.size() > 0) {
	        Prediction prediction = predictions.get(0);
	        // We want at least some confidence in the result
	        if (prediction.score > 1.0) {
	            // Show the spell
	            Toast.makeText(this, prediction.name, Toast.LENGTH_SHORT).show();
	            Log.d(TAG, prediction.name);
	        }
	    }
	}
    
    /** Given a URI (actually, a file path), this method chooses the right reader and loads the comic.
     * @param uri The file path of the comic to load. The reader is chosen according to the file extension.
     * @param page The page of the comic to load
     */
    public void loadComic(ComicInfo info){
    	Log.i(TAG, "Loading comic "+info.uri+" at page "+info.page);
        
    	close();
    	
    	Toast.makeText(this, getText(R.string.loading)+info.uri, Toast.LENGTH_LONG).show();
        
        // load information about the bookmarks from the database
        ComicDBHelper db=new ComicDBHelper(this);
        ComicInfo ci=db.getComicInfo(db.getComicID(info.uri, false));
        if(ci!=null)
        	info.bookmarks=ci.bookmarks;
        else
        	info.bookmarks=new ArrayList<Integer>();
        
    	// the comic is loaded in the background, since there is lots of things to do
    	(new AsyncTask<ComicInfo, Void, ComicInfo>(){
			@Override
			protected ComicInfo doInBackground(ComicInfo... params) {
				ComicViewerActivity.this.close();
				ComicInfo info=params[0];
		    	if(info.uri==null) return null;
				try{
					// chooses the right reader to use
					info.reader=Reader.getReader(ComicViewerActivity.this, info.uri);
					if(info.reader==null) throw new ReaderException(getText(R.string.no_suitable_reader)+info.uri);
					info.collection = ComicCollection.populate(ComicViewerActivity.this, new File(info.uri).getParentFile());
					info.reader.countPages();
					return info;
				}catch(ReaderException e){
					return null;
				}
			}
			protected void onPostExecute(ComicInfo info){
				ComicViewerActivity.this.comicInfo = info;
				if(info!=null)
					ComicViewerActivity.this.moveToPage(info.page);
			}
    	}).execute(info);
    }
    
    /** Switches the bookmark in the current page */
    private void switchBookmark(){
    	if(this.comicInfo!=null){
    		int cp = this.comicInfo.reader.getCurrentPage();
    		if(cp<0)
    			return;
    		if(this.comicInfo.bookmarks.contains(cp)){
    			this.comicInfo.bookmarks.remove(new Integer(cp));
    			this.findViewById(R.id.bookmark).setVisibility(View.GONE);
    		}else{
    			this.comicInfo.bookmarks.add(cp);
    			this.findViewById(R.id.bookmark).setVisibility(View.VISIBLE);
    		}
    	}
    }
    
    /** Moves the view to a certain page.
     * This movement makes a long jump: use changePage() to flip a page 
     * @param page
     */
    private void moveToPage(int page){
    	// check that the movement makes sense
    	if(this.comicInfo.reader==null) return;
    	if(this.comicInfo.reader.countPages()==1) return;
    	if(page<0 || page>=this.comicInfo.reader.countPages()) return;
    	if(page==this.comicInfo.reader.getCurrentPage()) return;
    	// stop the AsyncTasks
    	if(this.nextFastPage!=null){
        	this.nextFastPage.cancel(true);
        	this.nextFastPage=null;
        }
        if(this.currentPage!=null)
        	this.currentPage.cancel(true);
    	// move onwards or backwards, according to the current page
    	if(this.comicInfo.reader.getCurrentPage()<page){
    		this.comicInfo.reader.moveTo(page-1);
    		this.changePage(true);
    	}else{
    		this.comicInfo.reader.moveTo(page+1);
    		this.changePage(false);
    	}
    	
		// shows the position of the user in the comic on the screen
		if(this.comicInfo!=null && this.comicInfo.reader!=null)
			Toast.makeText(this,
				(this.comicInfo.reader.getCurrentPage()+1)+"/"+this.comicInfo.reader.countPages(),
				Toast.LENGTH_SHORT).show();
    	
    	// if the page is bookmarked, show the bookmark
    	if(this.comicInfo.bookmarks.contains(this.comicInfo.reader.getCurrentPage())){
    		this.findViewById(R.id.bookmark).setVisibility(View.VISIBLE);
    	}else{
    		this.findViewById(R.id.bookmark).setVisibility(View.GONE);
    	}
    }
    
	/** Changes the page currently on screen, doing an animation.
	 * TODO: currently, this method only supports left-to-right comics.
	 * @param forward True if the user is moving forward, false is backward
	 */
	public void changePage(boolean forward){
		if(this.comicInfo==null)
			return;
		ImageSwitcher imgs=(ImageSwitcher)this.findViewById(R.id.switcher);
		Reader reader=this.comicInfo.reader;
		try{
			// set animations according to the movement of the user
			this.setAnimations(forward);
			// drawable of the next page
			Drawable n=null;
			if(forward){
				// check that we are not in the last page
				if(reader.getCurrentPage()>=reader.countPages()-1){
					// load the next issue in the collection
					Log.i(TAG, "At the end of the comic");
					if(LOAD_NEXT_ISSUE && this.comicInfo.collection!=null){
						Log.d(TAG, "Loading next issue");
						ComicInfo nextIssue=this.comicInfo.collection.next(this.comicInfo);
						if(nextIssue!=null){
							Log.i(TAG, "Next issue: "+nextIssue.uri);
							nextIssue.page=0; // we load the next issue at the first page. It is weird otherwise
							this.loadComic(nextIssue);
						}
					}
					return;
				}
				// if moving forward, we will check if we loaded the next page in the background
				// We assume that this method is running in the UI thread
				if(this.nextFastPage==null)
					// if there is no background thread, create one. Note that this means
					// that the UI thread will block in the next line
					this.nextFastPage=(LoadNextPage)new LoadNextPage().execute(reader.getCurrentPage()+1);
				// get the loaded page. If the task was trigger in the past, this page should be
				// available immediately. If it was created in the last "if" statement, this
				// line will block the thread for a few milliseconds (while the page loads)
				n=this.nextFastPage.get();
				// move to the next page "by hand"
				this.comicInfo.reader.moveTo(reader.getCurrentPage()+1);
				// create a new thread to load the next page in the background. This supposes that
				// the natural move is onward and the user will see the next page next
				this.nextFastPage=(LoadNextPage)new LoadNextPage().execute(reader.getCurrentPage()+1);
			}else{
				// check that we are not in the first page
				if(reader.getCurrentPage()==0)
					return;
				// if the user is moving backwards, the background thread (if existed) was
				// loading the NEXT page. Stop it now.
				if(this.nextFastPage!=null)
					this.nextFastPage.cancel(true);
				// move to the prev page "by hand".
				//This is faster and safer than this.comicInfo.reader.prev() since we are using scaled images
				this.comicInfo.reader.moveTo(reader.getCurrentPage()-1);
				n=this.comicInfo.reader.getFastPage(reader.getCurrentPage(), FAST_PAGES_SCALE);
				// and load the next page from the prev. That is, the currently displayed page.
				// TODO: I'm sure that there is room for improvements here
				this.nextFastPage=(LoadNextPage)new LoadNextPage().execute(reader.getCurrentPage()+1);
			}
			if(n!=null)
				imgs.setImageDrawable(n);
		}catch(ReaderException e){
			Log.e(TAG, e.toString());
		}catch(ExecutionException e){
			Log.e(TAG, e.toString());
		}catch(InterruptedException e){
			Log.e(TAG, e.toString());
		}
		
		// shows the position of the user in the comic on the screen
		if(this.comicInfo!=null && this.comicInfo.reader!=null)
			Toast.makeText(this,
				(this.comicInfo.reader.getCurrentPage()+1)+"/"+this.comicInfo.reader.countPages(),
				Toast.LENGTH_SHORT).show();
		
    	// if the current page is bookmarked, show the bookmark
    	if(this.comicInfo.bookmarks!=null && this.comicInfo.bookmarks.contains(this.comicInfo.reader.getCurrentPage())){
    		this.findViewById(R.id.bookmark).setVisibility(View.VISIBLE);
    	}else{
    		this.findViewById(R.id.bookmark).setVisibility(View.GONE);
    	}

	}
	
	/** Configures the animations of the ImageSwitcher
	 * @param inAnim Animation of the page that enters during a forward movement
	 * @param outAnim Animation of the page that goes out during a forward movement
	 * @param inRevAnim Animation of the page that enters during a backward movement
	 * @param outRevAnim Animation of the page that goes out during a backward movement
	 * @param duration Duration of animations.
	 */
	public void configureAnimations(int inAnim, int outAnim, int inRevAnim, int outRevAnim, int duration){
		Context context = this.getApplicationContext();
		anims[0]=AnimationUtils.loadAnimation(context, inAnim);
		anims[1]=AnimationUtils.loadAnimation(context, outAnim);
		anims[2]=AnimationUtils.loadAnimation(context, inRevAnim);
		anims[3]=AnimationUtils.loadAnimation(context, outRevAnim);
		// if the fast pages is on, we set the listener for the animations:
		// the real page is loaded AFTER the animations
		if(FAST_PAGES_SCALE>1){
			anims[0].setAnimationListener(this);
			anims[2].setAnimationListener(this);
		}
		for(int i=0; i<anims.length; i++)
			anims[i].setDuration(duration);
		this.setAnimations(true);
	}

	/** Set the animations for the next change according to the movement of the user
	 * @param forward True if the user is moving the comic forward, false otherwise
	 */
	private void setAnimations(boolean forward){
		ImageSwitcher imgs=(ImageSwitcher)this.findViewById(R.id.switcher);
		if(imgs!=null && anims[0]!=null){
			if(forward){
				imgs.setInAnimation(this.anims[0]);
				imgs.setOutAnimation(this.anims[1]);
			}else{
				imgs.setInAnimation(this.anims[2]);
				imgs.setOutAnimation(this.anims[3]);
			}
		}
	}
	
	/** If fast pages are used, animations show a scaled version of the pages. When the animation ends,
	 * the application will load the final, unscaled image in the background.
	 * @see android.view.animation.Animation.AnimationListener#onAnimationEnd(android.view.animation.Animation)
	 */
	public void onAnimationEnd(Animation animation) {
		if(this.currentPage!=null)
			this.currentPage.cancel(true);
		ImageSwitcher imgs=(ImageSwitcher)this.findViewById(R.id.switcher);
		ImageView iv=(ImageView) imgs.getCurrentView();
		this.currentPage=(LoadCurrentPage)new LoadCurrentPage().execute(iv, this.comicInfo.reader.getCurrentPage());
	}
	public void onAnimationRepeat(Animation animation) {}
	public void onAnimationStart(Animation animation) {}
	
	/** This task is used to load a page in a background thread and improve the GUI response time.
	 * Use (page is an integer):
	 * 
	 * page=new LoadNextPage().execute(page);
	 * (...do whatever...)
	 * Drawable newpage = page.get()
	 * 
	 * @author juanvi
	 */
	private class LoadNextPage extends AsyncTask<Integer, Void, Drawable>{
		@Override
		protected Drawable doInBackground(Integer... params) {
			if(ComicViewerActivity.this.comicInfo==null)
				return null;
			int page=params[0].intValue();
			Log.d(TAG, "Loading fast page "+page);
			try {
				return ComicViewerActivity.this.comicInfo.reader.getFastPage(page, FAST_PAGES_SCALE);
			} catch (ReaderException e) {
				return null;
			}
		}
		protected void onPostExecute(Drawable d){
			Log.d(TAG, "Next page loaded");			
		}
	}
	
	/** This task is used to load a page in a background thread and improve the GUI response time.
	 * This thread is used to load an unscaled version of the current page
	 * Use (page is an integer):
	 * 
	 * page=new LoadCurrentPage().execute(page);
	 * (...do whatever...)
	 * Drawable newpage = page.get()
	 * 
	 * @author juanvi
	 */
	private class LoadCurrentPage extends AsyncTask<Object, Void, Drawable>{
		ImageView view=null;
		@Override
		protected Drawable doInBackground(Object... params) {
			Reader reader=ComicViewerActivity.this.comicInfo.reader;
			if(reader==null)
				return null;
			view=(ImageView)params[0];
			int page=((Integer)params[1]).intValue();
			Log.d(TAG, "Loading page "+page);
			try {
				return reader.getPage(page);
			} catch (ReaderException e) {
				return null;
			}
		}
		protected void onPostExecute(Drawable d){
			if(d!=null){
				((BitmapDrawable)view.getDrawable()).getBitmap().recycle();
				view.setImageDrawable(d);
				// TODO: free the last view
			}
		}
	}
	
	
	////////////////////////////////MANAGE THE MENU
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.comicmenu, menu);
	    return true;
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	        case R.id.first_page: // go to the first page
	        	if(this.comicInfo!=null && this.comicInfo.reader.countPages()>1){
		            this.moveToPage(0);
		            return true;
	        	}
	        	break;
	        case R.id.last_page: // go to the last page
	        	if(this.comicInfo!=null && this.comicInfo.reader.countPages()>1){
		            this.moveToPage(this.comicInfo.reader.countPages()-1);
		            return true;
	        	}
	        	break;
	        case R.id.switch_bookmark:
	        	if(this.comicInfo!=null)
	        		switchBookmark();
	        	break;
	        case R.id.bookmarks:
	        	if(this.comicInfo!=null){
		        	// the information of the bookmarks must be updated in the database
		        	ComicDBHelper db=new ComicDBHelper(this);
		        	db.updateComicInfo(this.comicInfo);
		        	db.close();
		        	// show the bookmark list
		    		Intent intent = new Intent(this, BookmarksExplorer.class);
		    		intent.putExtra("comicid", this.comicInfo.id);
	                startActivityForResult(intent, REQUEST_BOOKMARKS);
	        	}
	        	break;
	        case R.id.close: // close this viewer
	        	this.finish();
	        	return true;
	    }
        return super.onOptionsItemSelected(item);
	}
	
    public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			if(requestCode == REQUEST_BOOKMARKS){
				int page=data.getIntExtra("page", 0);
				Log.i(TAG, "Bookmark to page "+page);
				this.moveToPage(page);
			}
		}
    }
}