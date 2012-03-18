/**
 * 
 */
package com.juanvvc.comicviewer.readers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import de.innosystec.unrar.Archive;
import de.innosystec.unrar.rarfile.FileHeader;

/** A reader for RAR files.
 * 
 * Comics in ZIP usually have the extension .cbr
 * @author juanvi
 */
public class CBRReader extends Reader {
	private Archive archive=null;
	private List<? extends FileHeader> entries = null;
	
	public CBRReader(Context context, String uri) throws ReaderException{
		super(context, uri);
		if(uri!=null) this.load(uri);
	}


	public void load(String uri) throws ReaderException {
		super.load(uri);
		Log.i(TAG, "Loading URI"+uri);
		// tries to open the RAR file
		try{
			this.archive = new Archive(new File(uri));			
		}catch(Exception e){
			this.uri = null;
			throw new ReaderException(e.getMessage());
		}
		// throws an exception if the file is encrypted
		if(this.archive.isEncrypted()){
			this.archive = null;
			throw new ReaderException(this.context.getString(com.juanvvc.comicviewer.R.string.encrypted_file));
		}
		this.entries=this.archive.getFileHeaders();
		// removes files that are not .jpg or .png
		Iterator<? extends FileHeader> itr=this.entries.iterator();
		while(itr.hasNext()){
			FileHeader e = itr.next();
			String name = e.getFileNameString().toLowerCase();
			if(e.isDirectory() || !(name.endsWith(".jpg") || name.endsWith(".png")))
				itr.remove();
		}
		// sort the names alphabetically
		Collections.sort(this.entries, new Comparator<FileHeader>(){
			public int compare(FileHeader lhs, FileHeader rhs) {
				String n1=lhs.getFileNameString();
				String n2=rhs.getFileNameString();
				return n1.compareTo(n2);
			}
			
		});
	}


	public void close() {
		try{
			this.archive.close();
		}catch(IOException e){}
		this.archive = null;
		this.currentPage = -1;

	}

	private Drawable getDrawableFromRarEntry(FileHeader entry, int initialscale) throws ReaderException{
		try{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			// sometimes, a outofmemory is triggered here. Try to save as much memory as possible
			System.gc();
			this.archive.extractFile(entry, baos);
			baos.close();
			return new BitmapDrawable(this.byteArrayToBitmap(baos.toByteArray(), initialscale));
		}catch(Exception e){
			throw new ReaderException("Cannot read page: "+e.getMessage());
		}catch(OutOfMemoryError err){
			throw new ReaderException(this.context.getString(com.juanvvc.comicviewer.R.string.outofmemory));
		}
	}

	public Drawable getPage(int page) throws ReaderException{
		if(page<0 || page>=this.countPages())
			return null;
		return this.getDrawableFromRarEntry(this.entries.get(page), 1);
	}
	public Drawable getFastPage(int page, int initialscale) throws ReaderException{
		if(page<0 || page>=this.countPages())
			return null;
		return this.getDrawableFromRarEntry(this.entries.get(page), initialscale);
	}
	
	public static boolean manages(String uri){
		File file=new File(uri);
		if(!file.exists() || file.isDirectory())
			return false;
		String name = file.getName().toLowerCase();
		if(name.endsWith(".rar") || name.endsWith(".cbr"))
			return true;
		return false;
	}


	@Override
	public int countPages() {
		if(this.entries==null)
			return NOFILE;
		return this.entries.size();
	}
}
