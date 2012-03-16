package com.juanvvc.comicviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.juanvvc.comicviewer.readers.Reader;
import com.juanvvc.comicviewer.readers.ReaderException;

public class TransitionView extends RelativeLayout {
	private final static int ANIMATION_DURATION=500;
	private ImageView _img1;
	private ImageView _img2;
	private ImageSwitcher _imgs;
	private int _currentImage=0;
	private Reader reader=null;
	private Animation anims[]={null, null, null, null};
	
	public TransitionView(Context context){
		super(context);
		this.customInit(context);
	}
	public TransitionView(Context context, AttributeSet attrs){
		super(context, attrs);
		this.customInit(context);
	}
	public TransitionView(Context context, AttributeSet attrs, int defStyle){
		super(context, attrs, defStyle);
		this.customInit(context);
	}
	

	public void configureAnimations(int inAnim, int outAnim, int inRevAnim, int outRevAnim, int duration){
		anims[0]=AnimationUtils.loadAnimation(this.getContext(), inAnim);
		anims[1]=AnimationUtils.loadAnimation(this.getContext(), outAnim);
		anims[2]=AnimationUtils.loadAnimation(this.getContext(), inRevAnim);
		anims[3]=AnimationUtils.loadAnimation(this.getContext(), outRevAnim);
		for(int i=0; i<anims.length; i++) anims[i].setDuration(duration);
		this.lastChange=false;
		this.setAnimations(true);
	}
	
	private boolean lastChange=true;
	
	private void setAnimations(boolean forward){
		if(this._imgs!=null && anims[0]!=null){
			if(this.lastChange==forward) return;
			if(forward){
				this._imgs.setInAnimation(this.anims[0]);
				this._imgs.setOutAnimation(this.anims[1]);
			}else{
				this._imgs.setInAnimation(this.anims[2]);
				this._imgs.setOutAnimation(this.anims[3]);
			}
			this.lastChange=forward;
		}
	}

	private void customInit(Context context){
		_imgs = new ImageSwitcher(context);
		this.configureAnimations(
				R.anim.slide_in_right, R.anim.slide_out_left,
				android.R.anim.slide_in_left, android.R.anim.slide_out_right,
				ANIMATION_DURATION);


		_img1 = new MyImageView(context);
		_img2 = new MyImageView(context);

		LayoutParams fullScreenLayout = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		_imgs.addView(_img1, 0, fullScreenLayout);
		_imgs.addView(_img2, 1, fullScreenLayout);
		addView(_imgs, fullScreenLayout);
	}
	
	public void setReader(Reader reader){
		this.reader = reader;
		this.changePage(true);
	}

	public void changePage(boolean pageRight){
		if(this.reader==null)
			return;
		try{
			// drawable of the next page
			Drawable n=(pageRight?this.reader.next():this.reader.prev());
			// the ImageView that will hold the new drawable
			ImageView newView=(_imgs.getCurrentView()==this._img1?this._img2:this._img1);
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
				_imgs.showNext();
			}
			// we loaded large images: ask to system to clean the memory and avoid problems in the future
			System.gc();
		}catch(ReaderException e){
			Toast.makeText(this.getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
		}

	}

}

// This is a helper class to get an idea of the max image size
// In my device, it outputs 2048x2048
class MyImageView extends ImageView{
	private final static String TAG="MyImageView";
	public MyImageView(Context context){
		super(context);
		Log.w(TAG, "Using custom ImageView");
	}
	
	@Override
	public void onDraw(Canvas c){
		Log.v("MyImageView", "max size: "+c.getMaximumBitmapWidth()+"x"+c.getMaximumBitmapHeight());
		super.onDraw(c);
	}
}
