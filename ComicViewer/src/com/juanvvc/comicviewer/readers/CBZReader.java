package com.juanvvc.comicviewer.readers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.graphics.drawable.Drawable;

public class CBZReader implements Reader {
	private ZipFile zipfile = null;
	ArrayList<? extends ZipEntry> entries = null;
	private int currentPage = -1;

	public void load(String uri) throws Exception{
		try{
			this.zipfile = new ZipFile(uri);
			// current page is -1, since user didn't turn over the page yet. First thing: call to next()
			this.currentPage = -1;
			// get the entries of the file and sort them alphabetically
			this.entries = Collections.list(this.zipfile.entries());
			// TODO: manage directories inside the ZipFile
			// TODO: check if the file contains images
			Collections.sort(this.entries, new Comparator<ZipEntry>(){
				public int compare(ZipEntry lhs, ZipEntry rhs) {
					String n1=((ZipEntry)lhs).getName();
					String n2=((ZipEntry)rhs).getName();
					return n1.compareTo(n2);
				}
				
			});
		}catch(IOException e){
			throw new Exception("ZipFile cannot be read: "+e.toString());
		}
	}
	
	public void close(){
		try{
			this.zipfile.close();
		}catch(IOException e){}
		this.zipfile = null;
		this.currentPage = -1;
	}

	public Drawable next() {
		if(this.zipfile==null)
			return null;
		if(this.currentPage>=this.entries.size())
			return null;
		this.currentPage += 1;
		ZipEntry e = this.entries.get(this.currentPage);
		try{
			return Drawable.createFromStream(this.zipfile.getInputStream(e), e.getName());
		}catch(IOException ex){
			return null;
		}
	}

	public Drawable prev() {
		if(this.zipfile==null)
			return null;
		if(this.currentPage<=0)
			return null;
		this.currentPage -= 1;
		ZipEntry e = this.entries.get(this.currentPage);
		try{
			return Drawable.createFromStream(this.zipfile.getInputStream(e), e.getName());
		}catch(IOException ex){
			return null;
		}

	}

	public Drawable current() {
		ZipEntry e = this.entries.get(this.currentPage);
		try{
			return Drawable.createFromStream(this.zipfile.getInputStream(e), e.getName());
		}catch(IOException ex){
			return null;
		}
	}

	public int countPages() {
		if(this.zipfile!=null)
			return this.zipfile.size();
		else
			return -1;
	}

	public int currentPage() {
		return this.currentPage;
	}

	public void moveTo(int pos) {
		if(this.zipfile!=null)
			this.currentPage = pos;
	}

}
