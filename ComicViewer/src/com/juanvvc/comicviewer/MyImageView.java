package com.juanvvc.comicviewer;

import java.io.FileOutputStream;
import java.io.IOException;

import com.juanvvc.comicviewer.readers.TiledDrawable;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;

/** Custom ImageView to allow drawings on the images.
 * @author juanvi
 */
public class MyImageView extends ImageView implements OnTouchListener {
	/** The painter to use for drawings. */
	private Paint painter;
	/** The current path to draw. */
	private Path currentPath = null;
	/** A custom tag for debugging. */
	private static final String TAG = "MyIMageView";
	/** If set, the drawing is visible. */
	private boolean drawVisible = false;
	/** If set, the drawing is allowed. */
	private boolean drawMode = false;
	/** The drawing. */
	private Bitmap buffer = null;

	/** Constructs a new MyImageView.
	 * @param context The context of the application
	 * @param dv Whether the drawing is visible or not
	 */
	public MyImageView(final Context context, final boolean dv) {
		super(context);

		  painter = new Paint();
		  painter.setDither(true);
		  setPainterColor(0xff000000);
		  painter.setStyle(Paint.Style.STROKE);
		  painter.setStrokeJoin(Paint.Join.ROUND);
		  painter.setStrokeCap(Paint.Cap.ROUND);
		  this.setPainterWidth(3);
		  this.removeDrawing();
		  this.setDrawMode(false, -1, -1);

		  this.setOnTouchListener(this);

		setDrawVisible(drawVisible);
	}

	/**
	 * @return True if the drawing is visible.
	 */
	public final boolean isDrawVisible() {
		return drawVisible;
	}


	/**
	 * @param dv true for the drawing to be visible
	 */
	public final void setDrawVisible(final boolean dv) {
		this.drawVisible = dv;
	}


	/**
	 * @param color The color to use on the painter.
	 */
	public final void setPainterColor(final int color) {
		this.painter.setColor(color);
	}

	/**
	 * @return The color of the current painter.
	 */
	public final int getPainterColor() {
		return this.painter.getColor();
	}

	/** Cleans the drawing. */
	public final void removeDrawing() {
		if (this.buffer != null) {
			Bitmap b = Bitmap.createBitmap(buffer.getWidth(), buffer.getHeight(), Bitmap.Config.ARGB_4444);
			buffer.recycle();
			this.buffer = b;
			this.invalidate();
		}
	}

	/**
	 * @param mode If true, drawing is allowed.
	 * @param width The width of the drawing zone. If negative, use all the ImageView
	 * @param height The height of the drawing zone. If negative, use all the ImageView
	 */
	public final void setDrawMode(final boolean mode, final int width, final int height) {
		this.drawMode = mode;
		if (mode) {
			if (this.buffer == null) {
				if (width == -1) {
					this.buffer = Bitmap.createBitmap(this.getWidth(), this.getHeight(), Bitmap.Config.ARGB_4444);
				} else {
					this.buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444);
				}
			}
		}
	}

	/**
	 * @return If drawing is allowed.
	 */
	public final boolean isDrawMode() {
		return this.drawMode;
	}

	/**
	 * @return The width of the current painter.
	 */
	public final float getPainterWidth() {
		return this.painter.getStrokeWidth();
	}

	/**
	 * @param width Sets the width of the current painter.
	 */
	public final void setPainterWidth(final float width) {
		this.painter.setStrokeWidth(width);
	}

	/** Receives onTouch events.
	 * @param v Current view (this)
	 * @param event Motion event
	 * @return true if the drawing is enabled, false otherwise. If drawing is enabled, parent views
	 * are not going to get the event.
	 * @see android.view.View.OnTouchListener#onTouch(android.view.View, android.view.MotionEvent)
	 */
	public final boolean onTouch(final View v, final MotionEvent event) {
		if (!this.drawMode || this.buffer == null) {
			return false;
		}
		// TODO: synchronize this using thread.getSurfaceHolder()
	    if (event.getAction() == MotionEvent.ACTION_DOWN) {
	    	// starting of a path
	      currentPath = new Path();
	      currentPath.moveTo(event.getX(), event.getY());
	      currentPath.lineTo(event.getX(), event.getY());
	      myLog.d(TAG, "Path started");
	    } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
	    	// add intermediate points to the path
	    	currentPath.lineTo(event.getX(), event.getY());
	    	myLog.d(TAG, "Path moved");
	    } else if (event.getAction() == MotionEvent.ACTION_UP) {
	    	// end of a path
			currentPath.lineTo(event.getX(), event.getY());
			Canvas c = new Canvas(this.buffer);
			c.drawPath(currentPath, this.painter);
			this.invalidate();
			myLog.d(TAG, "Path finished");
	    } else {
	    	myLog.d(TAG, "Event number: " + event.getAction());
	    }
	    // returning true is mandatory to recive ACTION_MOVE and ACTION_UP events.
	    // this also means that the container won't recive the event
	    // Probably, the user cannot change the page. This is OK.
	    return true;
	}

	/** If the drawing is visible, show it.
	 * @param c
	 * @see android.widget.ImageView#onDraw(android.graphics.Canvas)
	 */
	public final void onDraw(final Canvas c) {
		super.onDraw(c);
		if (drawVisible && this.buffer != null) {
			c.drawBitmap(this.buffer, 0, 0, null);
		}
	}

	@Override
	protected final void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
		if (this.buffer != null) {
			this.setDrawMode(this.drawMode, w, h);
		}
	}

	/** Recycles the memory of the current drawable. */
	private void onloadCurrentImage() {
		Drawable currentd = this.getDrawable();
		if (currentd != null) {
			if (currentd instanceof BitmapDrawable) {
				try {
					myLog.d(TAG, "Recycling old BitmapDrawable");
					((BitmapDrawable) currentd).getBitmap().recycle();
				} catch (Exception e) {
					myLog.w(TAG, e.toString());
				}
			} else if (currentd instanceof TiledDrawable) {
				myLog.d(TAG, "Recycling old TiledDrawable");
				((TiledDrawable) currentd).recycle();
			}
			super.setImageDrawable(null);
		}
	}

	/** Sets the current image drawable.
	 * We recycle the current image to save some memory.
	 * @param d The new drawable
	 */
	public final void setImageDrawable(final Drawable d) {
		this.onloadCurrentImage();
		super.setImageDrawable(d);
	}

	/** Sets the current image resource
	 * We recycle the current image to save some memory.
	 * @param rid the identifier of the image resource
	 */
	public final void setImageResource(final int rid) {
		this.onloadCurrentImage();
		super.setImageResource(rid);
	}

	// TODO: currently, these functions are not used anywhere
	public void saveDraw(final String drawLocation) throws IOException {
		if (this.buffer == null || drawLocation == null) {
			return;
		}
		FileOutputStream out = new FileOutputStream(drawLocation);
		this.buffer.compress(Bitmap.CompressFormat.PNG, 90, out);
		out.close();
	}
	public void loadDraw(final String drawLocation) {
		try {
			buffer = BitmapFactory.decodeFile(drawLocation, null);
		} catch (Exception e) {
			myLog.e(TAG, "Error reading saved draw: " +  e.toString());
		}
	}
}

