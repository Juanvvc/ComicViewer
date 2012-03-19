package com.juanvvc.comicviewer;

import java.io.File;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class BookmarksExplorer extends ListActivity {
	private BookmarkInfo[] bookmarks;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setResult(RESULT_CANCELED, getIntent());
		
		long comicid=-1;
		if(getIntent().getExtras()!=null && getIntent().getExtras().containsKey("comicid")){
			comicid=getIntent().getExtras().getLong("comicid");
		}

		ComicDBHelper db=new ComicDBHelper(this);
		this.bookmarks=db.getBookmarks(comicid);
		
		if(this.bookmarks!=null){
			String[] values=new String[this.bookmarks.length];
			for(int i=0; i<this.bookmarks.length; i++){
				ComicInfo ci=db.getComicInfo(this.bookmarks[i].comicid);
				values[i]=getText(R.string.page)+" "+(this.bookmarks[i].page+1)+" "+getText(R.string.in)+" \""+(new File(ci.uri)).getName()+"\".";
			}
			
			ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, android.R.id.text1, values);
			setListAdapter(adapter);
		}
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		getIntent().putExtra("page", this.bookmarks[position].page);
		getIntent().putExtra("comicid", this.bookmarks[position].comicid);
		setResult(RESULT_OK, getIntent());
		finish();
	}
}
