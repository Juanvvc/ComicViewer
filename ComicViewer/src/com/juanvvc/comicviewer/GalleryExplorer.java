package com.juanvvc.comicviewer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.juanvvc.comicviewer.readers.CBRReader;
import com.juanvvc.comicviewer.readers.CBZReader;
import com.juanvvc.comicviewer.readers.Reader;
import com.juanvvc.comicviewer.readers.ReaderException;

/** Shows the comic collection. This class makes extensive use of Lists and Adapters
 * 
 * Within this scope, a "collection" is a directory with comics inside. Thre is only one level of collections,
 * and subdirectories are top-level collections.
 * @author juanvi
 *
 */
public class GalleryExplorer extends Activity implements OnItemClickListener {
	/** An arbitrary name to help debugging */
	private static final String TAG="GalleryExplorer";
	/** The name of the thumbnails directory */
	static final String THUMBNAILS=".thumbnails";
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	// only in portrait mode
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    	this.setContentView(R.layout.galleryexplorer);
    	
    	ListView collections = (ListView) findViewById(R.id.collections);
    	// TODO: ask for the initial directory
        collections.setAdapter(new CollectionListAdapter(this, new File("/mnt/sdcard")));
        

    }
    
    /** This adapter manages a list of collections. Each row of the list are a pair <collection name, gallery> */
    private class CollectionListAdapter extends BaseAdapter{
    	private Context context;
    	private ArrayList<ComicCollection> entries;
    	
    	CollectionListAdapter(Context context, File dir){
    		this.context=context;
    		
    		// create the list of comic collections
    		this.entries = ComicCollection.getCollections(GalleryExplorer.this, dir);
			// sort directories alphabetically
			Collections.sort(this.entries, new Comparator<ComicCollection>(){
				public int compare(ComicCollection lhs, ComicCollection rhs) {
					String n1=lhs.getName();
					String n2=rhs.getName();
					return n1.compareTo(n2);
				}
				
			});
    	}
    	
//    	/** Returns the list of collections within a directory, searching recursively subdirectories
//    	 * @param d
//    	 * @return
//    	 */
//    	private ArrayList<File> scanDirTree(File d){
//    		ArrayList<File> f=new ArrayList<File>(Arrays.asList(d.listFiles()));
//    		ArrayList<File> children=new ArrayList<File>();
//    		Iterator<File> itr=f.iterator();
//			while(itr.hasNext()){
//				File nf=itr.next();
//				// remove from the list normal files and the thumbnails directory
//				if(!nf.isDirectory() || nf.getName().equals(THUMBNAILS))
//					itr.remove();
//				else
//					children.addAll(scanDirTree(nf));
//			}
//			f.addAll(children);
//			return f;
//    	}

    	
		/** This method creates a row for the collection list
		 * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
		 */
		public View getView(int position, View convertView, ViewGroup parent) {
			ComicCollection collection=this.entries.get(position);
			View v=convertView;
			if(convertView==null)
				v=View.inflate(this.context, R.layout.galleryrow, null);
			// create the cover gallery with an adapter
			Gallery g=(Gallery)v.findViewById(R.id.cover_gallery);
			g.setAdapter(new CoverListAdapter(GalleryExplorer.this, collection));
			// create the gallery name
			((TextView)v.findViewById(R.id.collection_name)).setText(collection.getName());
			// gallery items listen to clicks!
	        g.setOnItemClickListener(GalleryExplorer.this);
			return v;
		}

    	////////// An adapter needs these methods, even if we do not use them directly////////
		public int getCount() {
			return this.entries.size();
		}
		public Object getItem(int position) {
			return this.entries.get(position);
		}
		public long getItemId(int position) {
			return position;
		}
    }
    

	/** The only registered event is clicking in a cover: load the comic.
	 * @see android.widget.AdapterView.OnItemClickListener#onItemClick(android.widget.AdapterView, android.view.View, int, long)
	 */
	public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
		// get the file name (we know that the item is going to be a file)
		ComicInfo ci=(ComicInfo)arg0.getAdapter().getItem(position);
		File f=new File(ci.uri);
		Toast.makeText(this, "Loading "+f.getName(), Toast.LENGTH_LONG).show();
		// start the comic viewer
		Intent data=new Intent(this, ComicViewerActivity.class);
		data.putExtra("uri", f.getAbsolutePath());
		this.startActivity(data);
	}
    
    /** This adapter manages a list of covers inside a collection. Each row of the list are a pair <comic name, cover>.
     * We will use the THUMBNAIL directory to load the covers. If the thumbnail is not available, we will use
     * an AsyncTask to create the cover out of the UI thread */
    private class CoverListAdapter extends BaseAdapter{
    	private Context context;
    	private ComicCollection entries=null;
    	private int background;
    	
    	CoverListAdapter(Context context, ComicCollection collection){
    		this.context = context;
    		
    		// create the list of the files in this collection
    		this.entries = collection;
			
    		// This sets the style of the gallery. From:
    		// http://developer.android.com/guide/tutorials/views/hello-gallery.html
    		TypedArray attr = context.obtainStyledAttributes(R.styleable.ComicExplorer);
    		this.background = attr.getResourceId(R.styleable.ComicExplorer_android_galleryItemBackground, 0);
    		attr.recycle();
    	}

		/** This creates an item of the list of covers. An item is a cover and its name.
		 * @see android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)
		 */
		public View getView(int position, View convertView, ViewGroup parent) {
			ComicInfo ci=this.entries.get(position);
			View v=convertView;
			if(convertView==null){
				v=View.inflate(this.context, R.layout.coveritem, null);
				v.setBackgroundResource(this.background);
			}
			// Creates a view holder to speed the UI thread
			// See: http://developer.android.com/training/improving-layouts/smooth-scrolling.html
			ViewHolder holder = new ViewHolder();
			holder.text=(TextView)v.findViewById(R.id.coveritem_text);
			holder.img=(ImageView)v.findViewById(R.id.coveritem_img);
			holder.file=new File(ci.uri);
			// create the comic name: it is the file name without a suffix
			String name=holder.file.getName();
			if(name.lastIndexOf(".")>0)
				name=name.substring(0, name.lastIndexOf("."));
			// add some information about the state of the comic
			if(ci.read){
				name+=" (read)";
			}else if(ci.page>0 && ci.countpages>-1){
				name+=" ("+(ci.page+1)+"/"+ci.countpages+")";
			}
			holder.name=name;
			holder.text.setText(name);
			// the cover is loaded in a separate thread
			(new LoadCover()).execute(holder);
			return v;
		}
		
		////////// An adapter needs these methods, even if we do not use them directly////////
		public int getCount() {
			return this.entries.size();
		}
		public Object getItem(int position) {
			return this.entries.get(position);
		}
		public long getItemId(int position) {
			return position;
		}
    }
    
    /** The ViewHolder class, as explained in http://developer.android.com/training/improving-layouts/smooth-scrolling.html   */
    static class ViewHolder{
    	TextView text;
    	ImageView img;
    	File file;
    	String name;
    }
    
	/** This is an AsyncTask to load the covers outside the UI thread */
	private class LoadCover extends AsyncTask<ViewHolder, Void, Drawable>{
		private ViewHolder holder;
		
		@Override
		protected Drawable doInBackground(ViewHolder... params) {
			Reader reader=null;
			this.holder = params[0];
			String uri=this.holder.file.getAbsolutePath();
			File cachefile=new File(holder.file.getParent()+File.separator+THUMBNAILS+File.separator+holder.name+".png");
			try{
				// look for the cover in the thumbnails directory. If found, we are done
				if(cachefile.exists()){
					Log.v(TAG, "Cache found: "+cachefile.getName());
					return new BitmapDrawable(BitmapFactory.decodeFile(cachefile.getAbsolutePath()));
				}
				
				// if we are here, the thumbnail was not found
				Log.v(TAG, "Cache not found, creating: "+cachefile.getName());
				
				// Load the comic file, and then the first image
				// select the comic reader
				reader=Reader.getReader(GalleryExplorer.this, uri);
				// if the first page it is not a bitmapdrawable, this will trigger an exception. Pity, but it's OK
				BitmapDrawable bd=((BitmapDrawable)reader.getPage(0));
				// in case of fail, return the broken image
				if(bd==null)
					return new BitmapDrawable(BitmapFactory.decodeResource(getResources(), R.drawable.broken));
				// scale
				Bitmap s=Bitmap.createScaledBitmap(bd.getBitmap(), 200, 300, true);
				// recycle the original bitmap
				bd.getBitmap().recycle();
				
				try{
					// save the cache file for the next time, if you can
					// THIS NEEDS WRITING PERMISSIONS
					if(!cachefile.getParentFile().exists())
						// create the thumbnails directory
						// TODO: check if the directory was really created.
						// I suppose that if not, an exception will be triggered while saving the file
						cachefile.getParentFile().mkdir();
					// save the thumbnail
					FileOutputStream out=new FileOutputStream(cachefile.getAbsoluteFile());
					s.compress(Bitmap.CompressFormat.PNG, 90, out);
					out.close();
					Log.v(TAG, "Cache file created: "+cachefile.getName());
				}catch(IOException eio){
					Log.w(TAG, "Cannot create the cache file: "+eio.toString());
				}
				
				return new BitmapDrawable(s);
			}catch(ReaderException e){
				Log.e(TAG, e.toString());
				return new BitmapDrawable(BitmapFactory.decodeResource(getResources(), R.drawable.broken));
			}
		}
		protected void onPostExecute(Drawable d){
			super.onPostExecute(d);
			this.holder.img.setImageDrawable(d);
		}
	}
}
