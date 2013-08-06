package cx.hell.android.lib.pdf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;

// #ifdef pro
// import java.util.ArrayList;
// import java.util.Stack;
// import cx.hell.android.lib.view.TreeView;
// import cx.hell.android.lib.view.TreeView.TreeNode;
// #endif


/**
 * Native PDF - interface to native code.
 */
public class PDF {
	
	private final static String TAG = "cx.hell.android.pdfview";
	
	private static Map<String,String> fontNameToFile = null;
	
    static {
        /* there's also DroidSansFallback, but it's too big and we're handling it specially */
        HashMap<String,String> m = new HashMap<String,String>();

        m.put("Courier", "NimbusMonL-Regu.cff");
        m.put("Courier-Bold", "NimbusMonL-Bold.cff");
        m.put("Courier-Oblique", "NimbusMonL-ReguObli.cff");
        m.put("Courier-BoldOblique", "NimbusMonL-BoldObli.cff");

        m.put("Helvetica", "NimbusSanL-Regu.cff");
        m.put("Helvetica-Bold", "NimbusSanL-Bold.cff");
        m.put("Helvetica-Oblique", "NimbusSanL-ReguItal.cff");
        m.put("Helvetica-BoldOblique", "NimbusSanL-BoldItal.cff");

        m.put("Times-Roman", "NimbusRomNo9L-Regu.cff");
        m.put("Times-Bold", "NimbusRomNo9L-Medi.cff");
        m.put("Times-Italic", "NimbusRomNo9L-ReguItal.cff");
        m.put("Times-BoldItalic", "NimbusRomNo9L-MediItal.cff");

        m.put("Symbol", "StandardSymL.cff");
        m.put("ZapfDingbats", "Dingbats.cff");
        m.put("DroidSans", "droid/DroidSans.ttf");
        m.put("DroidSansMono", "droid/DroidSansMono.ttf");
        PDF.fontNameToFile = m;
    }
	
	/**
	 * Application context is needed by cmap and font loading code since it
	 * accesses assets.
	 */
	private static Context applicationContext = null;
	
    static {
        System.loadLibrary("apv");

        /* use at most 1/2 of available runtime memory in native code,
           unless we have more than 1024 MiB */
        long maxMemory = Runtime.getRuntime().maxMemory();
        int pdfMaxStore = 0;
        if (maxMemory < 1024 * 1024 * 1024) {
            pdfMaxStore = (int)(maxMemory / 2); 
        }
        PDF.init(pdfMaxStore);
    }

    public static native void init(int maxStore);
    
    public static void setApplicationContext(Context context) {
        PDF.applicationContext = context;
    }
	
    public static byte[] getFontData(String name) {
        if (name == null) throw new IllegalArgumentException("name can't be null");
        if (name.equals("")) throw new IllegalArgumentException("name can't be empty");
        if (name.equals("DroidSansFallback")) return PDF.getDroidSansFallbackData();
        String assetFontName = null;
        if (PDF.fontNameToFile.containsKey(name)) {
            assetFontName = PDF.fontNameToFile.get(name);
        } else {
            Log.w(TAG, "font name \"" + name + "\" not found in file name mapping");
            assetFontName = name;
        }
        Log.i(TAG, "trying to load font data " + name + " from " + assetFontName);
        return PDF.getAssetBytes("font/" + assetFontName);
    }
    
    /**
     * TODO: mmap, because theoretically it might be almost free.
     */
    public static byte[] getDroidSansFallbackData() {
        try {
            InputStream i = new FileInputStream("/system/fonts/DroidSansFallback.ttf");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(i.available(), 1024));
            byte tmp[] = new byte[256 * 1024];
            int read = 0;
            while(true) {
                read = i.read(tmp);
                if (read == -1) {
                    break;
                } else {
                    bytes.write(tmp, 0, read);
                }
            }
            byte d[] = bytes.toByteArray();
            Log.i(TAG, "loaded " + d.length + " bytes for DroidSansFallback.ttf");
            return d;
        } catch (IOException e) {
            Log.e(TAG, "got exception while trying to load DroidSansFallback.ttf: " + e);
            return null;
        }
    }
    
    /**
     * Get cmap as bytes.
     */
    public static byte[] getCmapData(String name) {
        String cmapPath = "cmap/" + name;
        
        /*
        AssetManager assets = PDF.applicationContext.getAssets();
        try {
            AssetFileDescriptor afd = assets.openFd(cmapPath);
            if (afd == null) {
                Log.e(TAG, "failed to open cmap file \"" + name + "\"");
                return null;
            }
            FileDescriptor fd = afd.getFileDescriptor();
            Log.i(TAG, "opened cmap file \"" + name + "\": " + fd);
            return fd;
        } catch (IOException e) {
            Log.e(TAG, "failed to open cmap file \"" + name + "\": " + e);
            return null;
        }
        */

        byte[] d =  getAssetBytes(cmapPath);
        Log.d(TAG, "loaded cmap " + name + " (size: " + d.length + ")");
        return d;
    }

    public static byte[] getAssetBytes(String path) {
        if (PDF.applicationContext == null) {
            throw new RuntimeException("PDF needs application context to load font and cmap files");
        }
        AssetManager assets = PDF.applicationContext.getAssets();
        try {
            InputStream i = assets.open(path, AssetManager.ACCESS_BUFFER);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(i.available(), 1024));
            byte tmp[] = new byte[256 * 1024];
            int read = 0;
            while(true) {
                read = i.read(tmp);
                if (read == -1) {
                    break;
                } else {
                    bytes.write(tmp, 0, read);
                }
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            Log.e(TAG, "failed to read asset \"" + path + "\": " + e);
            return null;
        }
    }
	
	/**
	 * Simple size class used in JNI to simplify parameter passing.
	 * This shouldn't be used anywhere outside of pdf-related code.
	 */
	public static class Size implements Cloneable {
		public int width;
		public int height;
		
		public Size() {
			this.width = 0;
			this.height = 0;
		}
		
		public Size(int width, int height) {
			this.width = width;
			this.height = height;
		}
		
		public Size clone() {
			return new Size(this.width, this.height);
		}
	}
	
	// #ifdef pro
// 	/**
// 	 * Java version of fz_outline.
// 	 */
// 	public static class Outline implements TreeView.TreeNode {
// 
// 		
// 		/**
// 		 * Numeric id. Used in TreeView.
// 		 * Must uniquely identify each element in tree.
// 		 */
// 		private long id = -1;
// 		
// 		/**
// 		 * Text of the outline entry.
// 		 */
// 		public String title = null;
// 		
// 		/**
// 		 * Page number.
// 		 */
// 		public int page = 0;
// 		
// 		/**
// 		 * Next element at this level of TOC.
// 		 */
// 		public Outline next = null;
// 		
// 		/**
// 		 * Child.
// 		 */
// 		public Outline down = null;
// 		
// 		/**
// 		 * Level in TOC. Top level elements have level 0, children of top level elements have level 1 and so on.
// 		 */
// 		public int level = -1;
// 		
// 		
// 		/**
// 		 * Set id.
// 		 * This is local to this TOC and its 0-based index of the element
// 		 * when list is displayed with all children expanded.
// 		 * @param id new id
// 		 */
// 		public void setId(long id) {
// 			this.id = id;
// 		}
// 		
// 		/**
// 		 * Get numeric id.
// 		 * @see id
// 		 */
// 		public long getId() {
// 			return this.id;
// 		}
// 		
// 		/**
// 		 * Get next element.
// 		 */
// 		public TreeNode getNext() {
// 			return this.next;
// 		}
// 		
// 		/**
// 		 * Get first child.
// 		 */
// 		public TreeNode getDown() {
// 			return this.down;
// 		}
// 		
// 		/**
// 		 * Return true if this outline element has children.
// 		 * @return true if has children
// 		 */
// 		public boolean hasChildren() {
// 			return this.down != null;
// 		}
// 		
// 		/**
// 		 * Get list of children of this tree node.
// 		 */
// 		public List<TreeNode> getChildren() {
// 			ArrayList<TreeNode> children = new ArrayList<TreeNode>();
// 			for(Outline child = this.down; child != null; child = child.next) {
// 				children.add(child);
// 			}
// 			return children;
// 		}
// 		
// 		/**
// 		 * Return text.
// 		 */
// 		public String getText() {
// 			return this.title;
// 		}
// 		
// 		/**
// 		 * Get level.
// 		 * This is calculated in getOutline.
// 		 * @return value of level field
// 		 */
// 		public int getLevel() {
// 			return this.level;
// 		}
// 		
// 		/**
// 		 * Set level.
// 		 * @param level new level
// 		 */
// 		public void setLevel(int level) {
// 			this.level = level;
// 		}
// 		
// 		/**
// 		 * Return human readable description.
// 		 * @param human readable description of this object
// 		 */
// 		public String toString() {
// 			return "Outline(" + this.id + ", \"" + this.title + "\", " + this.page + ")";
// 		}
// 	}
	// #endif

	/**
	 * Holds pointer to native pdf_t struct.
	 */
	private int pdf_ptr = -1;
	private int invalid_password = 0;
	
	private ParcelFileDescriptor fileDescriptor = null;
	
	public boolean isValid() {
		return pdf_ptr != 0;
	}
	
	public boolean isInvalidPassword() {
		return invalid_password != 0;
	}

	/**
	 * Parse bytes as PDF file and store resulting pdf_t struct in pdf_ptr.
	 * @return error code
	 */
/*	synchronized private native int parseBytes(byte[] bytes, int box); */
	
	/**
	 * Parse PDF file.
	 * @param fileName pdf file name
	 * @return error code
	 */
	synchronized private native int parseFile(String fileName, int box, String password);
	
	/**
	 * Parse PDF file.
	 * @param fd opened file descriptor
	 * @return error code
	 */
	synchronized private native int parseFileDescriptor(FileDescriptor fd, int box, String password);

	/**
	 * Construct PDF structures from bytes stored in memory.
	 */
/*	public PDF(byte[] bytes, int box) {
		this.parseBytes(bytes, box);
	} */
	
	/**
	 * Construct PDF structures from file sitting on local filesystem.
	 */
	public PDF(File file, int box) {
		this.parseFile(file.getAbsolutePath(), box, "");
	}
	
	/**
	 * Construct PDF structures from opened file descriptor.
	 * @param file opened file descriptor
	 */
	public PDF(ParcelFileDescriptor file, int box) {
	    this.fileDescriptor = file;  // hold
		this.parseFileDescriptor(file.getFileDescriptor(), box, "");
	}
	
	/**
	 * Return page count from pdf_t struct.
	 */
	synchronized public native int getPageCount();
	
	/**
	 * Render a page.
	 * @param n page number, starting from 0
	 * @param zoom page size scaling
	 * @param left left edge
	 * @param right right edge
	 * @param passes requested size, used for size of resulting bitmap
	 * @return bytes of bitmap in Androids format
	 */
	synchronized public native int[] renderPage(int n, int zoom, int left, int top, 
			int rotation, boolean skipImages, PDF.Size rect);
	
	/**
	 * Get PDF page size, store it in size struct, return error code.
	 * @param n 0-based page number
	 * @param size size struct that holds result
	 * @return error code
	 */
	synchronized public native int getPageSize(int n, PDF.Size size);
	
	/**
	 * Export PDF to a text file.
	 */
//	synchronized public native void export();

	/**
	 * Find text on given page, return list of find results.
	 */
//	synchronized public native List<FindResult> find(String text, int page, int rotation);
	
	/**
	 * Clear search.
	 */
	synchronized public native void clearFindResult();
	
//	/**
//	 * Find text on page, return find results.
//	 */
//	synchronized public native List<FindResult> findOnPage(int page, String text);

	// #ifdef pro
// 	/**
// 	 * Get document outline.
// 	 */
// 	synchronized private native Outline getOutlineNative();
// 	
// 	/**
// 	 * Get outline.
// 	 * Calls getOutlineNative and then calculates ids and levels.
// 	 * @return outline with correct id and level fields set.
// 	 */
// 	synchronized public Outline getOutline() {
// 		Outline outlineRoot = this.getOutlineNative();
// 		if (outlineRoot == null) return null;
// 		Stack<Outline> stack = new Stack<Outline>();
// 
// 		/* ids */
// 		stack.push(outlineRoot);
// 		long id = 0;
// 		while(!stack.empty()) {
// 			Outline node = stack.pop();
// 			node.setId(id);
// 			id++;
// 			if (node.next != null) stack.push(node.next);
// 			if (node.down != null) stack.push(node.down);
// 		}
// 		
// 		/* levels */
// 		stack.clear();
// 		for(Outline node = outlineRoot; node != null; node = node.next) {
// 			node.setLevel(0);
// 			stack.push(node);
// 		}
// 		while(!stack.empty()) {
// 			Outline node = stack.pop();
// 			for(Outline child = node.down; child != null; child = child.next) {
// 				//parentMap.put(child.getId(), node);
// 				child.setLevel(node.getLevel() + 1);
// 				stack.push(child);
// 			}
// 		}
// 
// 		return outlineRoot;
// 	}
// 	
// 	/**
// 	 * Get page text (usually known as text reflow in some apps). Better text reflow coming... eventually.
// 	 */
// 	synchronized public native String getText(int page);
	// #endif
	
	/**
	 * Get current native heap size netto as reported by custom allocator.
	 * @return native heap size netto in bytes
	 */
	public native int getHeapSize();
	
	/**
	 * Free memory allocated in native code.
	 */
	synchronized public native void freeMemory();

	public void finalize() {
		try {
			super.finalize();
		} catch (Throwable e) {
		}
		this.freeMemory();
	}
}
