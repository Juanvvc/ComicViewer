package com.juanvvc.comicviewer.readers;

import java.io.ByteArrayOutputStream;
import java.io.File;
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

/** A reader for ZIP files.
 * 
 * Comics in ZIP usually have the extension .cbz
 * @author juanvi
 */
public class CBZReader extends Reader {
	private ZipFile archive = null;
	private ArrayList<? extends ZipEntry> entries = null;
	
	public CBZReader(Context context, String uri) throws ReaderException {
		super(context, uri);
		if(uri!=null) this.load(uri);
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
	
	private Drawable getDrawableFromZipEntry(ZipEntry entry, int initialscale) throws ReaderException{
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
			
			return new BitmapDrawable(this.byteArrayToBitmap(bos.toByteArray(), initialscale));

		}catch(Exception ex){
			throw new ReaderException(ex.getMessage());
		}catch(OutOfMemoryError err){
			throw new ReaderException(this.context.getString(com.juanvvc.comicviewer.R.string.outofmemory));
		}
	}



	public Drawable getPage(int page)  throws ReaderException {
		if(page<0 || page>=this.countPages())
			return null;
		return this.getDrawableFromZipEntry(this.entries.get(page), 1);
	}
	public Drawable getFastPage(int page, int initialscale)  throws ReaderException {
		if(page<0 || page>=this.countPages())
			return null;
		return this.getDrawableFromZipEntry(this.entries.get(page), initialscale);
	}

	public int countPages() {
		if(this.archive!=null)
			return this.entries.size();
		else
			return NOFILE;
	}
	
	public static boolean manages(String uri){
		File file=new File(uri);
		if(!file.exists() || file.isDirectory()) return false;
		String name = file.getName().toLowerCase();
		if(name.endsWith(".zip") || name.endsWith(".cbz")) return true;
		return false;
	}
}
