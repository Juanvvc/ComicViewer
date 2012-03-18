package com.juanvvc.comicviewer;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.GestureOverlayView;
import android.gesture.GestureOverlayView.OnGesturePerformedListener;
import android.gesture.Prediction;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
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
	/** A reference to the reader in use. If null, there is not any comic. */
	private Reader reader=null;
	/** An arbitrary number to identify requests */
	private static int REQUEST_FILE = 0x67f;
	/** If set, horizontal pages are automatically rotated */
	private final static int ANIMATION_DURATION=500;
	/** The TAG constant for the Logger */
	private static final String TAG="ComicViewerActivity";
	/** A task to load pages on the background and free the main thread */
	private AsyncTask<Integer, Integer, Drawable> nextFastPage=null;
	/** A task to load current page on the background and free the main thread */
	private AsyncTask<Object, Integer, Drawable> currentPage=null;
	/** A reference to the animations.
	 * @see com.juanvvc.comicviewer.ComicViewerActivity#configureAnimations(int, int, int, int, int) */
	private Animation anims[]={null, null, null, null};
	/** The gestures library */
	GestureLibrary geslibrary;
	/** The scale of the fast pages. If ==1, no fast pages are used */
	private static int FAST_PAGES_SCALE=2;
    
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // sets the orientation portrait, mandatory
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.comicviewer);
        
		ImageSwitcher imgs=(ImageSwitcher)this.findViewById(R.id.switcher);
		imgs.setFactory(this);
		this.configureAnimations(
				R.anim.slide_in_right, R.anim.slide_out_left,
				android.R.anim.slide_in_left, android.R.anim.slide_out_right,
				ANIMATION_DURATION);

    	// load the intent, if any
    	Intent intent=this.getIntent();    	
    	if(intent.getExtras()!=null && intent.getExtras().containsKey("uri")){
    		this.loadReader(intent.getExtras().getString("uri"), 0);
    	}else if(savedInstanceState!=null && savedInstanceState.containsKey("uri")){
    		// load the save state, if any
    		if(savedInstanceState.containsKey("page")){
    			this.loadReader(savedInstanceState.getString("uri"), savedInstanceState.getInt("page"));
    		}else{
    			this.loadReader(savedInstanceState.getString("uri"), 0);
    		}
    	}else{
//    		Toast.makeText(this,"No comic", Toast.LENGTH_LONG).show();
    		this.loadReader("/mnt/sdcard/Creepy/Creepy 001.cbr", 0);
    	}

    	// we listen to the events from the user
    	((View)this.findViewById(R.id.comicviewer_layout)).setOnTouchListener(this);
    	
    	// open the gestures library
    	this.geslibrary= GestureLibraries.fromRawResource(this, R.raw.gestures);
    	if(this.geslibrary.load()){
    		GestureOverlayView gestures = (GestureOverlayView) findViewById(R.id.gestures);
    		gestures.addOnGesturePerformedListener(this);
    	}else{
    		Log.w(TAG, "No gestures available");
    	}
        
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

    
    
    /* Called when a screen button was pressed. Event binding is in XML
     * @see android.view.View.OnClickListener#onClick(android.view.View)
     */
//    public void onClick(View sender){
//    	if(this.reader!=null){
//    		this.changePage(sender==this.findViewById(R.id.buttonRight));
//    		Toast.makeText(this,
//    				this.reader.getCurrentPage()+"/"+this.reader.countPages(),
//    				Toast.LENGTH_SHORT).show();
//    	}
//    }
    

	/* Called when the screen is pressed
	 * @see android.view.View.OnTouchListener#onTouch(android.view.View, android.view.MotionEvent)
	 */
	public boolean onTouch(View v, MotionEvent event) {
		if(event.getAction()==MotionEvent.ACTION_DOWN){
			int zone = this.getZone(v, event.getX(), event.getY());
			switch(zone){
			case 0: this.changePage(false); break; // left zone
			case 1:
				// reload current image (it may help in some large pages)
				// TODO: this does nothing
				try{
					ImageSwitcher imgs=(ImageSwitcher)this.findViewById(R.id.switcher);
					ImageView iv=(ImageView) imgs.getCurrentView();
					((BitmapDrawable)iv.getDrawable()).getBitmap().recycle();
					iv.setImageDrawable(this.reader.current());
				}catch(Exception e){}
				break;
			default: this.changePage(true); // right zone
			}
    		Toast.makeText(this,
    				this.reader.getCurrentPage()+"/"+this.reader.countPages(),
    				Toast.LENGTH_SHORT).show();
			
		}
		return false;
	}
	
	/** Returns the identifier of the zone (x, y) of a view.
	 * A zone is a geometric area inside the view. For example, the righ side, the left side...	 */
	private int getZone(View v, float x, float y){
		// currently, only two zones are used: left(0), center(1), right(2)
		if(x<v.getWidth()/3) return 0;
		if(x>2*v.getWidth()/3) return 2;
		return 1;
	}
	
	/** Responds to a gestures.
	 * TODO: this is not working. Check: http://developer.android.com/resources/articles/gestures.html
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
     * @param uri The file path of the comic to load. The reader is choosen according to the file extension.
     * @param page The page of the comic to load
     */
    public void loadReader(String uri, int page){
    	if(uri==null) return;
		try{
			this.reader=null;
			// chooses the right reader to use
			reader=Reader.getReader(this, uri);
			if(reader==null) throw new ReaderException("Not suitable reader for "+uri);
			// moves to the selected page. Notice that we move to the PREVIOS page and then we change to the NEXT
			// doing so, there is an animation and the screen is updated
			reader.moveTo(page-1);
			this.changePage(true);
		}catch(ReaderException e){
			Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
		}
    }
   
    /* Saves the current comic and page
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
    	if(this.reader!=null){
	    	savedInstanceState.putString("uri", this.reader.getURI());
	    	savedInstanceState.putInt("page", reader.getCurrentPage());
    	}
    	super.onSaveInstanceState(savedInstanceState);
    }
    
	/** Changes the page currently on screen, doing an animation.
	 * TODO: currently, this method only supports left-to-right comics.
	 * @param forward True if the user is moving forward, false is backward
	 */
	public void changePage(boolean forward){
		if(this.reader==null)
			return;
		ImageSwitcher imgs=(ImageSwitcher)this.findViewById(R.id.switcher);
		try{
			// set animations according to the movement of the user
			this.setAnimations(forward);
			// drawable of the next page
			Drawable n=null;
			if(forward){
				// if moving forward, we will check if we loaded the next page in the background
				// We assume that this method is running in the UI thread
				if(this.nextFastPage==null)
					// if there is no background thread, create one. Note that this means
					// that the UI thread will block in the next line
					this.nextFastPage=new LoadNextPage().execute(this.reader.getCurrentPage()+1);
				// get the loaded page. If the task was trigger in the past, this page should be
				// available immediately. If it was created in the last "if" statement, this
				// line will block the thread for a few milisecons (while the page loads)
				n=this.nextFastPage.get();
				// move to the next page "by hand"
				this.reader.moveTo(this.reader.getCurrentPage()+1);
				// create a new thread to load the next page in the background. This supposes that
				// the natural move is onward and the user will see the next page next
				this.nextFastPage=new LoadNextPage().execute(this.reader.getCurrentPage()+1);
			}else{
				n=this.reader.prev();
				// if the user is moving backwards, the background thread (if existed) was
				// loading the NEXT page. Stop it now.
				if(this.nextFastPage!=null)
					this.nextFastPage.cancel(true);
				// and load the next page from the prev. That is, the currently displayed page.
				// I'm sure that there is room to the improvement here
				this.nextFastPage=new LoadNextPage().execute(this.reader.getCurrentPage()+1);
			}
			if(n!=null)
				imgs.setImageDrawable(n);
		}catch(Exception e){
			Toast.makeText(this.getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
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
		this.currentPage=new LoadCurrentPage().execute(iv, this.reader.getCurrentPage());
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
	private class LoadNextPage extends AsyncTask<Integer, Integer, Drawable>{
		@Override
		protected Drawable doInBackground(Integer... params) {
			if(reader==null)
				return null;
			int page=params[0].intValue();
			Log.d(TAG, "Loading fast page "+page);
			try {
				return reader.getFastPage(page, FAST_PAGES_SCALE);
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
	private class LoadCurrentPage extends AsyncTask<Object, Integer, Drawable>{
		ImageView view=null;
		@Override
		protected Drawable doInBackground(Object... params) {
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
}