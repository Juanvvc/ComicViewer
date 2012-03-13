package com.juanvvc.comicviewer;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class ComicViewerActivity extends Activity implements OnClickListener{
	private Button _left;
	private Button _right;
	private TransitionView _mainView;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.main);
        
        _left = (Button) this.findViewById(R.id.buttonLeft);
        _right = (Button) this.findViewById(R.id.buttonRight);
        _mainView = (TransitionView) this.findViewById(R.id.mainView);
    }
    
    public void onClick(View sender){
    	this._mainView.changePage(sender==this._right);
    }
}