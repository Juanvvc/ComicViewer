package com.juanvvc.comicviewer;

import android.app.Activity;
import android.content.Intent;
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
	private Reader reader=null;
	private static int REQUEST_FILE = 0x67f;
    
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.main);
        
        _left = (Button) this.findViewById(R.id.buttonLeft);
        _right = (Button) this.findViewById(R.id.buttonRight);
        _mainView = (TransitionView) this.findViewById(R.id.mainView);
        
//        this.loadReader("/mnt/sdcard/Paying For It (2011).cbz", -1);
//        //this.loadReader("/mnt/sdcard/STO (Ladroncorps).cbr", -1);
        
        // show a file manager to choose a file:
        Intent sharingIntent = new Intent(this, FileExplorer.class);
        startActivityForResult(sharingIntent, REQUEST_FILE);
        
    }
    
    /** Called when a screen button was pressed. Event binding is in XML */
    public void onClick(View sender){
    	this._mainView.changePage(sender==this._right);
    }
    
    public void loadReader(String uri, int page){
		try{
			this.reader=null;
			if(uri.toLowerCase().endsWith(".cbz"))
				reader = new CBZReader(this.getApplicationContext(), uri);
			else if(uri.toLowerCase().endsWith(".cbr"))
				reader = new CBRReader(this.getApplicationContext(), uri);
			reader.moveTo(page);
			this._mainView.setReader(reader);
		}catch(ReaderException e){
			Toast.makeText(this.getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
		}
    }
   
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
    	if(this.reader!=null){
	    	savedInstanceState.putString("uri", this.reader.getURI());
	    	savedInstanceState.putInt("page", reader.currentPage());
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

}