package com.juanvvc.comicviewer.readers;

import android.graphics.drawable.Drawable;

public interface Reader {
	public void load(String uri) throws Exception;
	public void close();
	public Drawable next();
	public Drawable prev();
	public Drawable current();
	public int countPages();
	public int currentPage();
	public void moveTo(int pos);
}
