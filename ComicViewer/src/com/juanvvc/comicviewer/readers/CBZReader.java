package com.juanvvc.comicviewer.readers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.content.Context;
import android.graphics.drawable.Drawable;

public class CBZReader implements Reader {
	private ZipFile archive = null;
	ArrayList<? extends ZipEntry> entries = null;
	private int currentPage = -1;
	Context context=null;
	
	public CBZReader(Context context){
		this.context = context;
	}
	public CBZReader(Context context, String uri) throws ReaderException{
		this.context = context;
		this.load(uri);
	}

	public void load(String uri) throws ReaderException{
		try{
			this.archive = new ZipFile(uri);
			// current page is -1, since user didn't turn over the page yet. First thing: call to next()
			this.currentPage = -1;
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
			// sort alphabetically the names
			Collections.sort(this.entries, new Comparator<ZipEntry>(){
				public int compare(ZipEntry lhs, ZipEntry rhs) {
					String n1=lhs.getName();
					String n2=rhs.getName();
					return n1.compareTo(n2);
				}
				
			});
		}catch(IOException e){
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
			return Drawable.createFromStream(this.archive.getInputStream(entry), entry.getName());
		}catch(Exception ex){
			throw new ReaderException(ex.getMessage());
		}catch(OutOfMemoryError err){
			throw new ReaderException(this.context.getString(com.juanvvc.comicviewer.R.string.outofmemory));
		}
	}

	public Drawable next() throws ReaderException{
		if(this.archive==null)
			return null;
		if(this.currentPage<-1 || this.currentPage>=this.entries.size())
			return null;
		this.currentPage += 1;
		return this.current();
	}

	public Drawable prev() throws ReaderException{
		if(this.archive==null)
			return null;
		if(this.currentPage<=0)
			return null;
		this.currentPage -= 1;
		return this.current();
	}

	public Drawable current()  throws ReaderException {
		return this.getDrawableFromZipEntry(this.entries.get(this.currentPage));
	}

	public int countPages() {
		if(this.archive!=null)
			return this.entries.size();
		else
			return -1;
	}

	public int currentPage() {
		return this.currentPage;
	}
}
