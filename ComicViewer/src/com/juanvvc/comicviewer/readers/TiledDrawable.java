package com.juanvvc.comicviewer.readers;

import java.util.ArrayList;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;

/** Manages a drawable that is created from a bitmap divided in tiles.
 * This class has different scale modes to show the tiles of the bitmap.
 * @author juanvi
 *
 */
public class TiledDrawable extends Drawable {
	/** Scale the image to fill the whole screen. */
	public static final int STRECHED = 0;
	/** Do not scale the image. */
	public static final int CENTERED_ORIGINAL_SIZE = 1;
	/** Scale the image to fill the screen, using original proportion. */
	public static final int CENTERED_FILL_SCREEN = 2;
	/** The list of tiles.
	 * All tiles must have the same size, but this is not checked anywhere!	 */
	private ArrayList<Bitmap> tiles = null;
	/** NUmber of columns. */
	private int cols;
	/** Number of rows. */
	private int rows;
	/** The scale mode. */
	private int mode;
	/** the width of a tile.
	 * All tiles must have the same size, but this is not checked anywhere!
	 */
	private int tileWidth;
	/** the height of a tile.
	 * All tiles must have the same size, but this is not checked anywhere!
	 */
	private int tileHeight;

	/**
	 * Construct a tiled drawable.
	 * All tiles must have the same size, but this is not checked.
	 * The tile size is calculated from the first tile in the collection.
	 * The collection should NOT change during the lifetime of this class.
	 * @param t The collection of tiles. The first row from left to right, then the second row...
	 * @param c Number of columns
	 * @param r Number of rows
	 */
	public TiledDrawable(final ArrayList<Bitmap> t, final int c, final int r) {
		this.tiles = t;
		this.cols = c;
		this.rows = r;
		this.setMode(CENTERED_FILL_SCREEN);
		Bitmap b = tiles.get(0);
		tileHeight = b.getHeight();
		tileWidth = b.getWidth();
	}

	@Override
	public final void draw(final Canvas canvas) {
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

	/**
	 * Mode "fill the screen, maintain proportions".
	 * @param canvas The canvas to draw this drawable on
	 */
	private void drawCenteredFillScreen(final Canvas canvas) {
		float scale = Math.min(
				(((float) canvas.getWidth()) / cols) / tileWidth,
				(((float) canvas.getHeight()) / rows) / tileHeight
				);
		int ox = Math.round(canvas.getWidth() - scale * tileWidth * this.cols) / 2;
		int oy = Math.round(canvas.getHeight() - scale * tileHeight * this.rows) / 2;

		for (int i = 0; i < this.rows; i++) {
			for (int j = 0; j < this.cols; j++) {
				Bitmap d = this.tiles.get(i * this.cols + j);
				Matrix matrix = new Matrix();
				matrix.preScale(scale, scale);
				matrix.postTranslate(scale * tileWidth * j + ox, scale * tileHeight * i + oy);
				canvas.drawBitmap(d, matrix, null);
			}
		}
	}

	/**
	 * Mode "fill the whole screen".
	 * @param canvas The canvas to draw this drawable on
	 */
	private void drawStreched(final Canvas canvas) {
		// the final tile width and height
		float tw = ((float) canvas.getWidth()) / cols;
		float th = ((float) canvas.getHeight()) / rows;
		for (int i = 0; i < this.rows; i++) {
			for (int j = 0; j < this.cols; j++) {
				Bitmap d = this.tiles.get(i * this.cols + j);
				Matrix matrix = new Matrix();
				matrix.preScale(tw / d.getWidth(), th / d.getHeight());
				matrix.postTranslate(tw * j, th * i);
				canvas.drawBitmap(d, matrix, null);
			}
		}
	}

	/**
	 * Mode "keep original size".
	 * @param canvas The canvas to draw this drawable on
	 */
	private void drawCenteredOriginal(final Canvas canvas) {
		for (int i = 0; i < this.rows; i++) {
			for (int j = 0; j < this.cols; j++) {
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
	public final int getOpacity() {
		return 0;
	}

	@Override
	public void setAlpha(final int alpha) {
	}

	@Override
	public void setColorFilter(final ColorFilter cf) {
	}

	/** Recycle the internal bitmaps.
	 * Not sure if necessary.
	 */
	public final void recycle() {
		for (Bitmap d: tiles) {
			d.recycle();
		}
	}

	/**
	 * @return The current scale mode
	 */
	public final int getMode() {
		return this.mode;
	}

	/**
	 * @param m Sets the current scale mode
	 */
	public final void setMode(final int m) {
		this.mode = m;
	}

}
