package com.juanvvc.comicviewer.readers;

import com.juanvvc.comicviewer.myLog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;

/** This class manages comics. It is used to load a comic, get a page, and move through next() and prev() movements.
 * 
 * This class must be extended to particular readers.
 * @author juanvi
 *
 */
public abstract class Reader {
	/** The URI of the currently loaded reader */
	String uri=null;
	/** The current page */
	int currentPage = -1;
	/** In some load page strategies, the max page to load
	 * (Currently, not in use) */
	public static final int MAX_BITMAP_SIZE=1024;
	/** The context of the application */
	Context context;
	/** A constant tag name, for logging */
	final static String TAG="Reader";
	/** If set, horizontal pages rotate to match the screen.
	 * TODO: do not assume that the screen is portrait */
	private final static boolean AUTOMATIC_ROTATION=true;
	/** Constant to return in getPageCount() when there is no loaded file */
	public static final int NOFILE=-100;
	
	
	public Reader(Context context, String uri) throws ReaderException{
		this.uri = uri;
		this.currentPage = -1;
		this.context = context;
	}
		

	/**
	 * @return The Drawable of the current page, unscaled.
	 * Equals to getPage(this.currentPage)
	 * @throws ReaderException
	 */
	public Drawable current()  throws ReaderException {
		return this.getPage(this.currentPage);
	}
	
	/** You have to override this method in your reader.
	 * @param page
	 * @return The Drawable of a page, unscaled.
	 * @throws ReaderException
	 */
	public abstract Drawable getPage(int page) throws ReaderException;
	
	/** The scaled version of a drawable.
	 * In the default implementation it is equal to getPage(), so you probably would like to override this method.
	 * @param page
	 * @param initialscale The drawable will be scaled at least this factor, maybe more if there
	 * are memory problems.
	 * @throws ReaderException
	 */	
	public Drawable getFastPage(int page, int initialscale) throws ReaderException{
		return this.getPage(page);
	}

	/** Loads a URI into this reader. You need to override this method in you reader, calling to the parent.load(uri)
	 * @param uri
	 * @throws ReaderException
	 */
	public void load(String uri) throws ReaderException{
		this.uri = uri;
		// current page is -1, since user didn't turn over the page yet. First thing: call to next()
		this.currentPage = -1;
	}
	/** Closes the reader. You need to override this method */
	public abstract void close();
	/** Counts the pages of the reader. You need to override this method */
	public abstract int countPages();

	public int getCurrentPage() {
		return this.currentPage;
	}
	
	public void moveTo(int page) {
		myLog.v(TAG, "Moving to "+page);
		if(page<0 || page>=this.countPages())
			return;
		this.currentPage = page;
	}
	
	public String getURI(){
		return this.uri;
	}
	
	/**
	 * @return The next page, or null if there are not any more
	 * @throws ReaderException
	 */
	public Drawable next() throws ReaderException{
		if(this.uri==null)
			return null;
		if(this.currentPage<-1 || this.currentPage>=this.countPages())
			return null;
		this.currentPage += 1;
		return this.current();
	}

	/**
	 * @return The previous page, or null if there are not any more
	 * @throws ReaderException
	 */
	public Drawable prev() throws ReaderException{
		if(this.uri==null)
			return null;
		if(this.currentPage<=0)
			return null;
		this.currentPage -= 1;
		return this.current();
	}
	
	/** Convert a byte array into a Bitmap
	 * 
	 * 	 This method should be a single line:
		 return new BitmapDrawable(BitmapFactory.decodeByteArray(ba, 0, ba.length);
		 or even:
		 Drawable.createFromStream(new ByteArrayInputStream(ba), "name");
		 These work only with small images. This method manages large images (and they are very usual in comic files) 

		 The last versions of Android have a very annoying feature: graphics are always HW accelerated, bitmaps
		 are always loaded as OPENGL_TEXTURES, and a HW limit applies: MAX_BITMAP_SIZE at most.
		 http://groups.google.com/group/android-developers/browse_thread/thread/2352c776651b6f99
		 Some report (http://stackoverflow.com/questions/7428996/hw-accelerated-activity-how-to-get-opengl-texture-size-limit)
		 that the minimum is 2048. In my device, that does not work. 1024 does.
		 Conclusion: in current devices, you cannot load a bitmap larger (width or height) than MAX_BITMAP_SIZE pixels.
		 Fact: many CBRs use images larger than that. OutOfMemory errors appear.
		 Solution: Options.inSampleSize to the rescue. 
		 
		 Remember: we have to do this with every image because is very common CBR files where pages have different sizes
		 for example, double/single pages.
		 
		 This method is in this class because I think that any reader will find this useful.
		 
	 * @param ba The byte array to convert
	 * @param initialscale The initial scale to use, 1 for original size, 2 for half the size...
	 * @return A Bitmap object
	 */
	protected Bitmap byteArrayToBitmap(byte[] ba, int initialscale){
		Bitmap bitmap=null;
		/* First strategy:
		 1.- load only the image information (inJustDecodeBounds=true)
		 2.- read the image size
		 3.- if larger than MAX_BITMAP_SIZE, apply a scale
		 4.- load the image scaled
		 Problem: in my experience, some images are unnecessarily scaled down and quality suffers
		*/
//		Options opts=new Options();
//		opts.inSampleSize=initialscale;
//		opts.inJustDecodeBounds=true;
//		BitmapFactory.decodeByteArray(ba, 0, ba.length, opts);
//		// now, set the scale according to the image size: 1, 2, 3...
//		opts.inSampleSize = Math.max(opts.outHeight, opts.outWidth)/MAX_BITMAP_SIZE+1;
//		//TODO: apply a smart scaler
//		opts.inScaled=true;
//		// set a high quality scale (did really works?)
//		opts.inPreferQualityOverSpeed=true;
//		opts.inJustDecodeBounds=false;
//		// finally, load the scaled image
//		bitmap = BitmapFactory.decodeByteArray(ba, 0, ba.length, opts);

		/* Second strategy:
		  1.- load the complete image
		  2.- if error, scale down and try again
		  Problem: this method is slower, and sometimes a page does not throw an OutOfMemoryError, but a warning
		  "bitmap too large" that cannot be caught and the image is not shown. Quality is much better.
		 */
		Options opts=new Options();
		opts.inSampleSize=initialscale;
		opts.inPreferQualityOverSpeed=true;
		// finally, load the scaled image
		while(true){
			try{
				bitmap = BitmapFactory.decodeByteArray(ba, 0, ba.length, opts);
				break; // if we arrive here, the last line did'nt trigger an outofmemory error
			}catch(OutOfMemoryError e){
				System.gc();
			}
			opts.inSampleSize*=2;
			myLog.d(TAG, "Using scale "+opts.inSampleSize);
		}
		
		if(AUTOMATIC_ROTATION && bitmap.getHeight()<bitmap.getWidth()){
			Matrix matrix=new Matrix();
			matrix.postRotate(90);
			Bitmap b=bitmap;
			bitmap=Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);
			b.recycle();
		}
		
		return bitmap;
	}
	
	
	/** You must override this method in your reader
	 * @param uri
	 * @return If the reader manages this type of URI.
	 */
	public static boolean manages(String uri){
		return false;
	}
	
	/**
	 * @param context
	 * @param uri
	 * @return  A suitable reader for the uri, or null if none was found 
	 * @throws ReaderException
	 */
	public static Reader getReader(Context context, String uri) throws ReaderException{
		if(CBRReader.manages(uri))
			return new CBRReader(context, uri);
		if(CBZReader.manages(uri))
			return new CBZReader(context, uri);
		if(DirReader.manages(uri))
			return new DirReader(context, uri);
		return null;
	}
	
	/** This method is equivalent to Reader.getReader(uri)!=null, but significantly faster. 
	 * @param uri
	 * @return True if there is a known reader that manages this type of URI
	 */
	public static boolean existsReader(String uri){
		return CBRReader.manages(uri) || CBZReader.manages(uri) || DirReader.manages(uri);
	}
	
}
