package com.juanvvc.comicviewer;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import com.juanvvc.comicviewer.readers.CBRReader;
import com.juanvvc.comicviewer.readers.CBZReader;
import com.juanvvc.comicviewer.readers.Reader;
import com.juanvvc.comicviewer.readers.ReaderException;

/** Shows a comic on the screen
 * @author juanvi */
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
        
        //this.loadReader("/mnt/sdcard/Paying For It (2011).cbz");
        this.loadReader("/mnt/sdcard/STO (Ladroncorps).cbr");
    }
    
    /** Called when a screen button was pressed. Event binding is in XML */
    public void onClick(View sender){
    	this._mainView.changePage(sender==this._right);
    }
    
    public void loadReader(String uri){
		try{
			Reader reader=null;
			if(uri.toLowerCase().endsWith(".cbz"))
				reader = new CBZReader(this.getApplicationContext(), uri);
			else if(uri.toLowerCase().endsWith(".cbr"))
				reader = new CBRReader(this.getApplicationContext(), uri);
			this._mainView.setReader(reader);
		}catch(ReaderException e){
			Toast.makeText(this.getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
		}
    }

}