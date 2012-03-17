package com.juanvvc.comicviewer.readers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.Log;

public abstract class Reader {
	String uri=null;
	int currentPage = -1;
	public static final int MAX_BITMAP_SIZE=1024;
	Context context;
	final static String TAG="Reader";
	private final static boolean AUTOMATIC_ROTATION=true;
	
	public Reader(Context context){
		this.uri = null;
		this.currentPage = -1;
		this.context = context;
	}
	
	public void load(String uri) throws ReaderException{
		this.uri = uri;
		// current page is -1, since user didn't turn over the page yet. First thing: call to next()
		this.currentPage = -1;
	}
	public abstract void close();
	public abstract int countPages();
	public abstract Drawable current() throws ReaderException;


	public int getCurrentPage() {
		return this.currentPage;
	}
	
	public void moveTo(int page) {
		this.currentPage = page;
	}
	
	public String getURI(){
		return this.uri;
	}
	
	public Drawable next() throws ReaderException{
		if(this.uri==null)
			return null;
		if(this.currentPage<-1 || this.currentPage>=this.countPages())
			return null;
		this.currentPage += 1;
		return this.current();
	}

	public Drawable prev() throws ReaderException{
		if(this.uri==null)
			return null;
		if(this.currentPage<=0)
			return null;
		this.currentPage -= 1;
		return this.current();
	}
	
	/** COnvert a byte array into a Bitmap
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
		 that the minimum is 2048. In my device, that does not work. 1024 does. TODO: set the minimum to the screen size.
		 Conclusion: in current devices, you cannot load a bitmap larger (width or height) than MAX_BITMAP_SIZE pixels.
		 Fact: many CBRs use images larger than that. OutOfMemory errors appear.
		 Solution: Options.inSampleSize to the rescue. 
		 
		 Remember: we have to do this with every image because is very common CBR files where pages have different sizes
		 for example, double/single pages.
		 
		 This method is in this class because I think that any reader will find this useful.
		 
	 * @param ba The byte array to convert
	 * @return A Bitmap object
	 */
	protected Bitmap byteArrayToBitmap(byte[] ba){
		Bitmap bitmap=null;
		/* First strategy:
		 1.- load only the image information (inJustDecodeBounds=true)
		 2.- read the image size
		 3.- if larger than MAX_BITMAP_SIZE, apply a scale
		 4.- load the image scaled
		 Problem: in my experience, some images are unnecessarely scaled down and quality suffers
		*/
//		Options opts=new Options();
//		opts.inSampleSize=1;
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
		opts.inSampleSize=1;
		opts.inPreferQualityOverSpeed=true;
		// finally, load the scaled image
		while(true){
			try{
				bitmap = BitmapFactory.decodeByteArray(ba, 0, ba.length, opts);
				break; // if we arrive here, the last line did'nt trigger an outofmemory error
			}catch(OutOfMemoryError e){
				System.gc();
			}
			opts.inSampleSize+=1;
			Log.d(TAG, "Using scale "+opts.inSampleSize);
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
	
}
