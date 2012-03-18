package com.juanvvc.comicviewer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.juanvvc.comicviewer.readers.DirReader;
import com.juanvvc.comicviewer.readers.Reader;
import com.juanvvc.comicviewer.readers.ReaderException;

public class ComicCollection extends ArrayList<File> {
	private static final long serialVersionUID = 1L;
	private ArrayList<File> entries;
	private File root;
	private String name;

	public ComicCollection(String name, List<File> list){
		super(list);
		this.name = name;
	}
	
	public ComicCollection(String name) {
		super();
		this.name = name;
	}
	
	public String getName(){
		return name;
	}

	public static ArrayList<ComicCollection> getCollections(File root){
		ArrayList<ComicCollection> collections=new ArrayList<ComicCollection>();
		ComicCollection rootCol = ComicCollection.populate(root);
		if(rootCol!=null) collections.add(rootCol);
		
		ArrayList<File> contents=new ArrayList<File>(Arrays.asList(root.listFiles()));
		Iterator<File> itr=contents.iterator();
		while(itr.hasNext()){
			File nf=itr.next();
			// remove from the list normal files and the thumbnails directory
			if(!nf.isDirectory() || nf.getName().equals(GalleryExplorer.THUMBNAILS))
				itr.remove();
			else{
				collections.addAll(ComicCollection.getCollections(nf));
			}
		}
		return collections;
	}
	
	public static ComicCollection populate(File root){
		ComicCollection f=new ComicCollection(root.getName(), Arrays.asList(root.listFiles()));
		Iterator<File> itr=f.iterator();
		while(itr.hasNext()){
			File nf=itr.next();
			// remove from the list the thumbnails directory
			if(nf.getName().equals(GalleryExplorer.THUMBNAILS))
				itr.remove();
			// check if there is a manager for the file
			else if(!Reader.existsReader(nf.getAbsolutePath()))
				itr.remove();
		}
		// if we have no items, return: we do not allow empty collections
		if(f.size()==0) return null;
		// sort the names alphabetically
		Collections.sort(f, new Comparator<File>(){
			public int compare(File lhs, File rhs) {
				String n1=lhs.getName();
				String n2=rhs.getName();
				return n1.compareTo(n2);
			}			
		});
		return f;
	}
	
	public File next(File current){
		return null;
	}
	
	public Reader getReader() throws ReaderException{
		if(DirReader.manages(this.root.getAbsolutePath())) return new DirReader(null, this.root.getAbsolutePath());
		return null;
	}
}