package com.juanvvc.comicviewer.readers;

import android.graphics.drawable.Drawable;

public interface Reader {
	public void load(String uri) throws ReaderException;
	public void close();
	public Drawable next() throws ReaderException;
	public Drawable prev() throws ReaderException;
	public Drawable current() throws ReaderException;
	public int countPages();
	public int currentPage();
}
