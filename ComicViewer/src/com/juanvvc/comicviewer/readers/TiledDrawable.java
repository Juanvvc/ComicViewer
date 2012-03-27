package com.juanvvc.comicviewer.readers;

import java.util.ArrayList;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;

public class TiledDrawable extends Drawable {
	public static final int STRECHED = 0;
	public static final int CENTERED_ORIGINAL_SIZE = 1;
	public static final int CENTERED_FILL_SCREEN = 2;
	
	private ArrayList<Bitmap> tiles=null;
	private int cols;
	private int rows;
	private int mode;
	private int tileWidth;
	private int tileHeight;
	
	public TiledDrawable(ArrayList<Bitmap> tiles, int cols, int rows) {
		this.tiles = tiles;
		this.cols = cols;
		this.rows = rows;
		this.setMode(CENTERED_FILL_SCREEN);
		Bitmap b = tiles.get(0);
		tileHeight = b.getHeight();
		tileWidth = b.getWidth();
	}

	@Override
	public final void draw(Canvas canvas) {
		switch(this.mode) {
		case CENTERED_FILL_SCREEN:
			this.drawCenteredFillScreen(canvas);
			break;
		case CENTERED_ORIGINAL_SIZE:
			this.drawCenteredOriginal(canvas);
			break;
		case STRECHED:
			this.drawStreched(canvas);
			break;
		default:
		}
	}
	
	private void drawCenteredFillScreen(final Canvas canvas) {
		float scale = Math.min(
				(((float)canvas.getWidth()) / cols) / tileWidth,
				(((float)canvas.getHeight()) / rows) / tileHeight
				);
		int ox = Math.round(canvas.getWidth() - scale * tileWidth * this.cols) / 2;
		int oy = Math.round(canvas.getHeight() - scale * tileHeight * this.rows) / 2;
		
		for(int i=0; i<this.rows; i++) {
			for(int j=0; j<this.cols; j++) {
				Bitmap d = this.tiles.get(i * this.cols + j);
				Matrix matrix = new Matrix();
				matrix.preScale(scale, scale);
				matrix.postTranslate(scale * tileWidth * j + ox, scale * tileHeight * i + oy);
				canvas.drawBitmap(d, matrix, null);
			}
		}
	}
	
	private void drawStreched(final Canvas canvas) {
		// the final tile width and height
		float tw = ((float)canvas.getWidth()) / cols;
		float th = ((float)canvas.getHeight()) / rows;
		for(int i=0; i<this.rows; i++) {
			for(int j=0; j<this.cols; j++) {
				Bitmap d = this.tiles.get(i * this.cols + j);
				Matrix matrix = new Matrix();
				matrix.preScale(tw / d.getWidth(), th / d.getHeight());
				matrix.postTranslate(tw * j, th * i);
				canvas.drawBitmap(d, matrix, null);
			}
		}
	}
	
	private void drawCenteredOriginal(final Canvas canvas) {
		for(int i=0; i<this.rows; i++) {
			for(int j=0; j<this.cols; j++) {
				Bitmap d = this.tiles.get(i * this.cols + j);
				Matrix matrix = new Matrix();
				float tw = d.getWidth();
				float th = d.getHeight();
				matrix.postTranslate(tw * j, th * i);
				canvas.drawBitmap(d, matrix, null);
			}
		}
	}

	@Override
	public int getOpacity() {
		return 0;
	}

	@Override
	public void setAlpha(int alpha) {}

	@Override
	public void setColorFilter(ColorFilter cf) {}
	
	public void recycle() {
		for(Bitmap d: tiles) {
			d.recycle();
		}
	}

	public int getMode() {
		return this.mode;
	}

	public void setMode(int m) {
		this.mode = m;
	}

}
