package com.juanvvc.comicviewer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.RelativeLayout;

public class TransitionView extends RelativeLayout {
	private final static int ANIMATION_DURATION=1000;
	private ImageView _img1;
	private ImageView _img2;
	private ImageSwitcher _imgs;
	private int _currentImage=0;

	private final Integer[] _imageIds = {
			R.drawable.pic01,
			R.drawable.pic02,
			R.drawable.pic03,
			R.drawable.pic04,
			R.drawable.pic05,
			R.drawable.pic06
	};

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

	private void customInit(Context context){

		Animation fadeIn = AnimationUtils.loadAnimation(context, android.R.anim.fade_in);
		Animation fadeOut = AnimationUtils.loadAnimation(context, android.R.anim.fade_out);
		fadeIn.setDuration(ANIMATION_DURATION);
		fadeOut.setDuration(ANIMATION_DURATION);

		_imgs = new ImageSwitcher(context);
		_imgs.setInAnimation(fadeIn);
		_imgs.setOutAnimation(fadeOut);

		_img1 = new ImageView(context);
		_img1.setImageResource(_imageIds[this._currentImage]);

		_img2 = new ImageView(context);
		_img2.setImageResource(_imageIds[this._currentImage]);

		LayoutParams fullScreenLayout = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		_imgs.addView(_img1, 0, fullScreenLayout);
		_imgs.addView(_img2, 1, fullScreenLayout);
		addView(_imgs, fullScreenLayout);

	}
	
	public void loadCBZ(String name){
		
	}

	public void changePage(boolean pageRight){
		_currentImage = (pageRight)?(_currentImage+1):(_currentImage-1);
		if (_currentImage<0)
			_currentImage=_imageIds.length-1;
		else if (_currentImage >=_imageIds.length)
			_currentImage = 0;

		if(_imgs.getCurrentView() == _img1){
			_img2.setImageResource(_imageIds[_currentImage]);
			_imgs.showNext();
		}else{
			_img1.setImageResource(_imageIds[_currentImage]);
			_imgs.showNext();
		}

	}
}
