package com.juanvvc.comicviewer;

import java.io.File;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/** This activity shows a list of bookmarks on the screen.
 *
 * If the calling intent used the extra "comicid", only
 * the bookmarks of that comic are shown.
 * @author juanvi
 *
 */
public class BookmarksExplorer extends ListActivity {
	/** The bookmarks to show on the list */
	private BookmarkInfo[] bookmarks;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setResult(RESULT_CANCELED, getIntent());

		// get the comicid, if it was passed. If not, set to -1
		long comicid = -1;
		if (getIntent().getExtras() != null
				&& getIntent().getExtras().containsKey("comicid")) {
			comicid = getIntent().getExtras().getLong("comicid");
		}
		
		// get the bookmarks of this comic from the database
		ComicDBHelper db = new ComicDBHelper(this);
		this.bookmarks = db.getBookmarks(comicid);

		// if any bookmarks, construct the list
		if (this.bookmarks != null) {
			String[] values = new String[this.bookmarks.length];
			for (int i = 0; i < this.bookmarks.length; i++) {
				ComicInfo ci = db.getComicInfo(this.bookmarks[i].comicid);
				values[i] = getText(R.string.page) + " "
						+ (this.bookmarks[i].page + 1) + " "
						+ getText(R.string.in) + " \""
						+ (new File(ci.uri)).getName() + "\".";
			}

			ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
					android.R.layout.simple_list_item_1, android.R.id.text1,
					values);
			setListAdapter(adapter);
		}
	}

	/** An item was clicked.
	 * Finish and inform the caller about the page and comicid of the selected bookmark.
	 */
	protected final void onListItemClick(final ListView l, final View v, final int position, final long id) {
		getIntent().putExtra("page", this.bookmarks[position].page);
		getIntent().putExtra("comicid", this.bookmarks[position].comicid);
		setResult(RESULT_OK, getIntent());
		finish();
	}
}
