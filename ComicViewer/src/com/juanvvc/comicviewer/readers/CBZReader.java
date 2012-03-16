package com.juanvvc.comicviewer.readers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

public class CBZReader extends Reader {
	private ZipFile archive = null;
	private ArrayList<? extends ZipEntry> entries = null;
	
	public CBZReader(Context context){
		super(context);
		Log.v(TAG, "Using CBZReader");
		this.archive = null;
	}
	public CBZReader(Context context, String uri) throws ReaderException{
		super(context);
		Log.v(TAG, "Using CBZReader");
		this.archive = null;
		this.load(uri);
	}

	public void load(String uri) throws ReaderException{
		try{
			Log.i(TAG, "Loading URI"+uri);
			this.archive = new ZipFile(uri);
			// get the entries of the file and sort them alphabetically
			this.entries = Collections.list(this.archive.entries());
			// removes files that are not .jpg or .png
			Iterator<? extends ZipEntry> itr=this.entries.iterator();
			while(itr.hasNext()){
				ZipEntry e = itr.next();
				String name = e.getName().toLowerCase();
				if(e.isDirectory() || !(name.endsWith(".jpg") || name.endsWith(".png")))
					itr.remove();
			}
			// sort the names alphabetically
			Collections.sort(this.entries, new Comparator<ZipEntry>(){
				public int compare(ZipEntry lhs, ZipEntry rhs) {
					String n1=lhs.getName();
					String n2=rhs.getName();
					return n1.compareTo(n2);
				}
				
			});
			super.load(uri);
		}catch(IOException e){
			this.uri = null;
			throw new ReaderException("ZipFile cannot be read: "+e.toString());
		}
	}
	
	public void close(){
		try{
			this.archive.close();
		}catch(IOException e){}
		this.archive = null;
		this.currentPage = -1;
	}
	
	private Drawable getDrawableFromZipEntry(ZipEntry entry) throws ReaderException{
		try{
			// you cannot use:
			//Drawable.createFromStream(this.archive.getInputStream(entry), entry.getName());
			// this will trigger lots of OutOfMemory errors.
			// see Reader.byteArrayBitmap for an explanation.
			InputStream is = this.archive.getInputStream(entry);			
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] tmp = new byte[4096];
			int ret = 0;

			while((ret = is.read(tmp)) > 0)
			    bos.write(tmp, 0, ret);
			
			return new BitmapDrawable(this.byteArrayToBitmap(bos.toByteArray()));

		}catch(Exception ex){
			throw new ReaderException(ex.getMessage());
		}catch(OutOfMemoryError err){
			throw new ReaderException(this.context.getString(com.juanvvc.comicviewer.R.string.outofmemory));
		}
	}



	public Drawable current()  throws ReaderException {
		if(this.currentPage<0 || this.currentPage>=this.countPages())
			return null;
		return this.getDrawableFromZipEntry(this.entries.get(this.currentPage));
	}

	public int countPages() {
		if(this.archive!=null)
			return this.entries.size();
		else
			return -100;
	}

}
