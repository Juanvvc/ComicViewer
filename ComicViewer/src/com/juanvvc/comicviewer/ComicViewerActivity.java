package com.juanvvc.comicviewer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.Toast;

import com.juanvvc.comicviewer.readers.CBRReader;
import com.juanvvc.comicviewer.readers.CBZReader;
import com.juanvvc.comicviewer.readers.Reader;
import com.juanvvc.comicviewer.readers.ReaderException;

/** Shows a comic on the screen
 * @author juanvi */
public class ComicViewerActivity extends Activity implements OnClickListener{
	private Reader reader=null;
	private static int REQUEST_FILE = 0x67f;
	private final static int ANIMATION_DURATION=500;
	
	private Animation anims[]={null, null, null, null};
    
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.comicviewer);
        
		this.configureAnimations(
				R.anim.slide_in_right, R.anim.slide_out_left,
				android.R.anim.slide_in_left, android.R.anim.slide_out_right,
				ANIMATION_DURATION);
        
     
//        this.loadReader("/mnt/sdcard/Creepy/Paying For It (2011).cbz", 0);
        this.loadReader("/mnt/sdcard/Creepy/STO (Ladroncorps).cbr", 0);
        
        // show a file manager to choose a file:
//        Intent sharingIntent = new Intent(this, FileExplorer.class);
//        startActivityForResult(sharingIntent, REQUEST_FILE);
        
    }

    
    
    /** Called when a screen button was pressed. Event binding is in XML */
    public void onClick(View sender){
    	if(this.reader!=null){
    		this.changePage(sender==this.findViewById(R.id.buttonRight));
    		Toast.makeText(this.getApplicationContext(),
    				this.reader.getCurrentPage()+"/"+this.reader.countPages(),
    				Toast.LENGTH_SHORT).show();
    	}
    }
    
    public void loadReader(String uri, int page){
    	if(uri==null) return;
		try{
			this.reader=null;
			if(uri.toLowerCase().endsWith(".cbz"))
				reader = new CBZReader(this.getApplicationContext(), uri);
			else if(uri.toLowerCase().endsWith(".cbr"))
				reader = new CBRReader(this.getApplicationContext(), uri);
			reader.moveTo(page-1);
			this.changePage(true);
		}catch(ReaderException e){
			Toast.makeText(this.getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
		}
    }
   
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
    	if(this.reader!=null){
	    	savedInstanceState.putString("uri", this.reader.getURI());
	    	savedInstanceState.putInt("page", reader.getCurrentPage());
    	}
    	super.onSaveInstanceState(savedInstanceState);
    }
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
    	super.onRestoreInstanceState(savedInstanceState);
    	if(savedInstanceState.containsKey("uri") && savedInstanceState.containsKey("page"))
    		this.loadReader(savedInstanceState.getString("uri"), savedInstanceState.getInt("page"));
    }
    
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
    

	public void changePage(boolean pageRight){
		if(this.reader==null)
			return;
		ImageSwitcher imgs=(ImageSwitcher)this.findViewById(R.id.switcher);
		ImageView img1=(ImageView)this.findViewById(R.id.img1);
		ImageView img2=(ImageView)this.findViewById(R.id.img2);
		try{
			// drawable of the next page
			Drawable n=(pageRight?this.reader.next():this.reader.prev());
			// the ImageView that will hold the new drawable
			ImageView newView=(imgs.getCurrentView()==img1?img2:img1);
			// the drawable that the ImageView is holding now
			Drawable p=newView.getDrawable();
			// set animations according to the movement
			this.setAnimations(pageRight);
			if(n!=null){
				// release memory of the unused page
				// Bitmaps must be explicitly removed from memory, or OutOfMemory errors appear very fast
				// Try to remove the next line and load a file with large pages
				if(p!=null && p instanceof BitmapDrawable){
					Bitmap b=((BitmapDrawable)p).getBitmap();
					if(b!=null) b.recycle();
				}
				// set the new page
				newView.setImageDrawable(n);
				// show the next page, with an animation
				imgs.showNext();
			}
			// we loaded large images: ask to system to clean the memory and avoid problems in the future
			System.gc();
		}catch(ReaderException e){
			Toast.makeText(this.getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
		}

	}
	
	public void configureAnimations(int inAnim, int outAnim, int inRevAnim, int outRevAnim, int duration){
		Context context = this.getApplicationContext();
		anims[0]=AnimationUtils.loadAnimation(context, inAnim);
		anims[1]=AnimationUtils.loadAnimation(context, outAnim);
		anims[2]=AnimationUtils.loadAnimation(context, inRevAnim);
		anims[3]=AnimationUtils.loadAnimation(context, outRevAnim);
		for(int i=0; i<anims.length; i++) anims[i].setDuration(duration);
		this.setAnimations(true);
	}

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
}