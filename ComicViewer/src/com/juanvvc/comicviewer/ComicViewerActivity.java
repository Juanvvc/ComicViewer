package com.juanvvc.comicviewer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ViewSwitcher.ViewFactory;

import com.juanvvc.comicviewer.readers.CBRReader;
import com.juanvvc.comicviewer.readers.CBZReader;
import com.juanvvc.comicviewer.readers.Reader;
import com.juanvvc.comicviewer.readers.ReaderException;

/** Shows a comic on the screen
 * @author juanvi */
public class ComicViewerActivity extends Activity implements OnClickListener, ViewFactory{
	/** A reference to the reader in use. If null, there is not any comic. */
	private Reader reader=null;
	/** An arbitrary number to identify requests */
	private static int REQUEST_FILE = 0x67f;
	/** If set, horizontal pages are automatically rotated */
	private final static int ANIMATION_DURATION=500;
	/** The TAG constant for the Logger */
	private static final String TAG="ComicViewerActivity";
	/** A task to load pages on the background and free the main thread */
	private AsyncTask<Integer, Integer, Drawable> nextPage=null;
	/** A reference to the animations.
	 * @see com.juanvvc.comicviewer.ComicViewerActivity#configureAnimations(int, int, int, int, int) */
	private Animation anims[]={null, null, null, null};
    
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
    	if(intent.getExtras().containsKey("uri")){
    		this.loadReader(intent.getExtras().getString("uri"), 0);
    	}else if(savedInstanceState!=null && savedInstanceState.containsKey("uri")){
    		// load the save state, if any
    		if(savedInstanceState.containsKey("page")){
    			this.loadReader(savedInstanceState.getString("uri"), savedInstanceState.getInt("page"));
    		}else{
    			this.loadReader(savedInstanceState.getString("uri"), 0);
    		}
    	}else{
    		Toast.makeText(this,"No comic", Toast.LENGTH_LONG).show();
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
    public void onClick(View sender){
    	if(this.reader!=null){
    		this.changePage(sender==this.findViewById(R.id.buttonRight));
    		Toast.makeText(this.getApplicationContext(),
    				this.reader.getCurrentPage()+"/"+this.reader.countPages(),
    				Toast.LENGTH_SHORT).show();
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
			if(uri.toLowerCase().endsWith(".cbz"))
				reader = new CBZReader(this.getApplicationContext(), uri);
			else if(uri.toLowerCase().endsWith(".cbr"))
				reader = new CBRReader(this.getApplicationContext(), uri);
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
    
    /* Gets a response from the FileExplorer: shows the comic selected by the user
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
    	if(resultCode == RESULT_OK && requestCode==REQUEST_FILE){
    		String msg;
    		if(data.hasExtra("file")){
    			this.loadReader(data.getExtras().getString("file"), -1);
    			msg=data.getExtras().getString("file");
    		}else
    			msg="No file";
    		Toast.makeText(this.getApplicationContext(), msg, Toast.LENGTH_LONG).show();
    	}
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
			// set animations according to the movement of the yser
			this.setAnimations(forward);
			// drawable of the next page
			Drawable n=null;
			if(forward){
				// if moving forward, we will check if we loaded the next page in the background
				// We assume that this method is running in the UI thread
				if(this.nextPage==null)
					// if there is no background thread, create one. Note that this means
					// that the UI thread will block in the next line
					this.nextPage=new LoadNextPage().execute(this.reader.getCurrentPage()+1);
				// get the loaded page. If the task was trigger in the past, this page should be
				// available immediately. If it was created in the last "if" statement, this
				// line will block the thread for a few milisecons (while the page loads)
				n=this.nextPage.get();
				// move to the next page "by hand"
				this.reader.moveTo(this.reader.getCurrentPage()+1);
				// create a new thread to load the next page in the background. This supposes that
				// the natural move is onward and the user will see the next page next
				this.nextPage=new LoadNextPage().execute(this.reader.getCurrentPage()+1);
			}else{
				n=this.reader.prev();
				// if the user is moving backwards, the background thread (if existed) was
				// loading the NEXT page. Stop it now.
				if(this.nextPage!=null)
					this.nextPage.cancel(true);
				// and load the next page from the prev. That is, the currently displayed page.
				// I'm sure that there is room to the improvement here
				this.nextPage=new LoadNextPage().execute(this.reader.getCurrentPage()+1);
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
		for(int i=0; i<anims.length; i++) anims[i].setDuration(duration);
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
			Log.d(TAG, "Loading page "+page);
			try {
				return reader.getPage(page);
			} catch (ReaderException e) {
				return null;
			}
		}
		protected void onPostExecute(Drawable d){
			Log.d(TAG, "Next page loaded");			
		}
	}
}