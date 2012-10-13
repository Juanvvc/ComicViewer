#include <string.h>
#include <wctype.h>
#include <jni.h>

#include "android/log.h"

#include "pdfview2.h"


#define PDFVIEW_LOG_TAG "cx.hell.android.pdfview"
#define PDFVIEW_MAX_PAGES_LOADED 16

#define BITMAP_STORE_MAX_AGE  1
#define FIND_STORE_MAX_AGE    4
#define TEXT_STORE_MAX_AGE    4

static jintArray get_page_image_bitmap(JNIEnv *env,
      pdf_t *pdf, int pageno, int zoom_pmil, int left, int top, int rotation,
      int gray, int skipImages,
      int *width, int *height);
static void copy_alpha(unsigned char* out, unsigned char *in, unsigned int w, unsigned int h);


extern char fz_errorbuf[150*20]; /* defined in fitz/apv_base_error.c */

#define NUM_BOXES 5

const char boxes[NUM_BOXES][MAX_BOX_NAME+1] = {
    "ArtBox",
    "BleedBox",
    "CropBox",
    "MediaBox",
    "TrimBox"
};

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *jvm, void *reserved) {
    __android_log_print(ANDROID_LOG_INFO, PDFVIEW_LOG_TAG, "JNI_OnLoad");
    fz_accelerate();
    /* pdf_setloghandler(pdf_android_loghandler); */
    return JNI_VERSION_1_2;
}


/**
 * Implementation of native method PDF.parseFile.
 * Opens file and parses at least some bytes - so it could take a while.
 * @param file_name file name to parse.
 */
JNIEXPORT void JNICALL
Java_com_juanvvc_comicviewer_readers_PDF_parseFile(
        JNIEnv *env,
        jobject jthis,
        jstring file_name,
        jint box_type,
        jstring password
        ) {
    const char *c_file_name = NULL;
    const char *c_password = NULL;
    jboolean iscopy;
    jclass this_class;
    jfieldID pdf_field_id;
    jfieldID invalid_password_field_id;
    pdf_t *pdf = NULL;

    c_file_name = (*env)->GetStringUTFChars(env, file_name, &iscopy);
    c_password = (*env)->GetStringUTFChars(env, password, &iscopy);
    this_class = (*env)->GetObjectClass(env, jthis);
    pdf_field_id = (*env)->GetFieldID(env, this_class, "pdf_ptr", "I");
    invalid_password_field_id = (*env)->GetFieldID(env, this_class, "invalid_password", "I");
    __android_log_print(ANDROID_LOG_INFO, PDFVIEW_LOG_TAG, "Parsing");
    pdf = parse_pdf_file(c_file_name, 0, c_password);

    if (pdf != NULL && pdf->invalid_password) {
       (*env)->SetIntField(env, jthis, invalid_password_field_id, 1);
       free (pdf);
       pdf = NULL;
    }
    else {
       (*env)->SetIntField(env, jthis, invalid_password_field_id, 0);
    }

    if (pdf != NULL) {
        if (NUM_BOXES <= box_type)
            strcpy(pdf->box, "CropBox");
        else
            strcpy(pdf->box, boxes[box_type]);
    }

    (*env)->ReleaseStringUTFChars(env, file_name, c_file_name);
    (*env)->ReleaseStringUTFChars(env, password, c_password);

    (*env)->SetIntField(env, jthis, pdf_field_id, (int)pdf);

    if (pdf != NULL)
       __android_log_print(ANDROID_LOG_INFO, PDFVIEW_LOG_TAG, "Loading %s in page mode %s.", c_file_name, pdf->box);
}


/**
 * Create pdf_t struct from opened file descriptor.
 */
JNIEXPORT void JNICALL
Java_com_juanvvc_comicviewer_readers_PDF_parseFileDescriptor(
        JNIEnv *env,
        jobject jthis,
        jobject fileDescriptor,
        jint box_type,
        jstring password
        ) {
    int fileno;
    jclass this_class;
    jfieldID pdf_field_id;
    pdf_t *pdf = NULL;
    jfieldID invalid_password_field_id;
    jboolean iscopy;
    const char* c_password;

    c_password = (*env)->GetStringUTFChars(env, password, &iscopy);
	this_class = (*env)->GetObjectClass(env, jthis);
	pdf_field_id = (*env)->GetFieldID(env, this_class, "pdf_ptr", "I");
    invalid_password_field_id = (*env)->GetFieldID(env, this_class, "invalid_password", "I");

    fileno = get_descriptor_from_file_descriptor(env, fileDescriptor);
	pdf = parse_pdf_file(NULL, fileno, c_password);

    if (pdf != NULL && pdf->invalid_password) {
       (*env)->SetIntField(env, jthis, invalid_password_field_id, 1);
       free (pdf);
       pdf = NULL;
    }
    else {
       (*env)->SetIntField(env, jthis, invalid_password_field_id, 0);
    }

    if (pdf != NULL) {
        if (NUM_BOXES <= box_type)
            strcpy(pdf->box, "CropBox");
        else
            strcpy(pdf->box, boxes[box_type]);
    }
    (*env)->ReleaseStringUTFChars(env, password, c_password);
    (*env)->SetIntField(env, jthis, pdf_field_id, (int)pdf);
}


/**
 * Implementation of native method PDF.getPageCount - return page count of this PDF file.
 * Returns -1 on error, eg if pdf_ptr is NULL.
 * @param env JNI Environment
 * @param this PDF object
 * @return page count or -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_juanvvc_comicviewer_readers_PDF_getPageCount(
		JNIEnv *env,
		jobject this) {
	pdf_t *pdf = NULL;
    pdf = get_pdf_from_this(env, this);
	if (pdf == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "pdf is null");
        return -1;
    }
	return pdf_count_pages(pdf->xref);
}


JNIEXPORT jintArray JNICALL
Java_com_juanvvc_comicviewer_readers_PDF_renderPage(
        JNIEnv *env,
        jobject this,
        jint pageno,
        jint zoom,
        jint left,
        jint top,
        jint rotation,
        jboolean gray,
        jboolean skipImages,
        jobject size) {

    jint *buf; /* rendered page, freed before return, as bitmap */
    jintArray jints; /* return value */
    pdf_t *pdf; /* parsed pdf data, extracted from java's "this" object */
    int width, height;

    get_size(env, size, &width, &height);

    __android_log_print(ANDROID_LOG_DEBUG, "cx.hell.android.pdfview", "jni renderPage(pageno: %d, zoom: %d, left: %d, top: %d, width: %d, height: %d) start",
            (int)pageno, (int)zoom,
            (int)left, (int)top,
            (int)width, (int)height);

    pdf = get_pdf_from_this(env, this);

    jints = get_page_image_bitmap(env, pdf, pageno, zoom, left, top, rotation, gray,
          skipImages, &width, &height);

    if (jints != NULL)
        save_size(env, size, width, height);

    return jints;
}


JNIEXPORT jint JNICALL
Java_com_juanvvc_comicviewer_readers_PDF_getPageSize(
        JNIEnv *env,
        jobject this,
        jint pageno,
        jobject size) {
    int width, height, error;
    pdf_t *pdf = NULL;

    pdf = get_pdf_from_this(env, this);
    if (pdf == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, "cx.hell.android.pdfview", "this.pdf is null");
        return 1;
    }

    error = get_page_size(pdf, pageno, &width, &height);
    if (error != 0) {
        __android_log_print(ANDROID_LOG_ERROR, "cx.hell.android.pdfview", "get_page_size error: %d", (int)error);
        /*
        __android_log_print(ANDROID_LOG_ERROR, "cx.hell.android.pdfview", "fitz error is:\n%s", fz_errorbuf);
        */
        return 2;
    }

    save_size(env, size, width, height);
    return 0;
}


// #ifdef pro
// /**
//  * Get document outline.
//  */
// JNIEXPORT jobject JNICALL
// Java_com_juanvvc_comicviewer_readers_PDF_getOutlineNative(
//         JNIEnv *env,
//         jobject this) {
//     int error;
//     pdf_t *pdf = NULL;
//     jobject joutline = NULL;
//     fz_outline *outline = NULL; /* outline root */
//     fz_outline *curr_outline = NULL; /* for walking over fz_outline tree */
// 
//     pdf = get_pdf_from_this(env, this);
//     if (pdf == NULL) {
//         __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "this.pdf is null");
//         return NULL;
//     }
// 
//     outline = pdf_load_outline(pdf->xref);
//     if (outline == NULL) return NULL;
// 
//     /* recursively copy fz_outline to PDF.Outline */
//     /* TODO: rewrite pdf_load_outline to create Java's PDF.Outline objects directly */
//     joutline = create_outline_recursive(env, NULL, outline);
//     __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "joutline converted");
//     return joutline;
// }
// #endif


/**
 * Free resources allocated in native code.
 */
JNIEXPORT void JNICALL
Java_com_juanvvc_comicviewer_readers_PDF_freeMemory(
        JNIEnv *env,
        jobject this) {
    pdf_t *pdf = NULL;
	jclass this_class = (*env)->GetObjectClass(env, this);
	jfieldID pdf_field_id = (*env)->GetFieldID(env, this_class, "pdf_ptr", "I");

    __android_log_print(ANDROID_LOG_DEBUG, "cx.hell.android.pdfview", "jni freeMemory()");
	pdf = (pdf_t*) (*env)->GetIntField(env, this, pdf_field_id);
	(*env)->SetIntField(env, this, pdf_field_id, 0);

    if ( pdf != NULL && pdf != 0) {
        if (pdf->pages) {
            int i;
            int pagecount;
            pagecount = pdf_count_pages(pdf->xref);

            for(i = 0; i < pagecount; ++i) {
                if (pdf->pages[i]) {
                    pdf_free_page(pdf->pages[i]);
                    pdf->pages[i] = NULL;
                }
            }

            free(pdf->pages);
        }

        /*
        if (pdf->textlines) {
            int i;
            int pagecount;
            pagecount = pdf_getpagecount(pdf->xref);
            for(i = 0; i < pagecount; ++i) {
                if (pdf->textlines[i]) {
                    pdf_droptextline(pdf->textlines[i]);
                }
            }
            free(pdf->textlines);
            pdf->textlines = NULL;
        }
        */

        /*
        if (pdf->drawcache) {
            fz_freeglyphcache(pdf->drawcache);
            pdf->drawcache = NULL;
        }
        */

        /* pdf->fileno is dup()-ed in parse_pdf_fileno */
        if (pdf->fileno >= 0) close(pdf->fileno);
        if (pdf->glyph_cache)
            fz_free_glyph_cache(pdf->glyph_cache);
        if (pdf->xref)
            pdf_free_xref(pdf->xref);

        free(pdf);
    }
}


#if 0
JNIEXPORT void JNICALL
Java_cx_hell_android_pdfview_PDF_export(
        JNIEnv *env,
        jobject this) {
    pdf_t *pdf = NULL;
    jobject results = NULL;
    pdf_page *page = NULL;
    fz_text_span *text_span = NULL, *ln = NULL;
    fz_device *dev = NULL;
    char *textlinechars;
    char *found = NULL;
    fz_error error = 0;
    jobject find_result = NULL;
    int pageno = 0;
    int pagecount;
    int fd;

    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "export to txt");

    pdf = get_pdf_from_this(env, this);

    pagecount = pdf_count_pages(pdf->xref);

    fd = open("/tmp/pdfview-export.txt", O_WRONLY|O_CREAT, 0666);
    if (fd < 0) {
         __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "Error opening /tmp/pdfview-export.txt");
        return;
    }

    for(pageno = 0; pageno < pagecount ; pageno++) {
        page = get_page(pdf, pageno);

        if (pdf->last_pageno != pageno && NULL != pdf->xref->store) {
            pdf_age_store(pdf->xref->store, TEXT_STORE_MAX_AGE);
            pdf->last_pageno = pageno;
        }

      text_span = fz_new_text_span();
      dev = fz_new_text_device(text_span);
      error = pdf_run_page(pdf->xref, page, dev, fz_identity);
      if (error)
      {
          /* TODO: cleanup */
          fz_rethrow(error, "text extraction failed");
          return;
      }

      /* TODO: Detect paragraph breaks using bbox field */
      for(ln = text_span; ln; ln = ln->next) {
          int i;
          textlinechars = (char*)malloc(ln->len + 1);
          for(i = 0; i < ln->len; ++i) textlinechars[i] = ln->text[i].c;
          textlinechars[i] = '\n';
          write(fd, textlinechars, ln->len+1);
          free(textlinechars);
      }

      fz_free_device(dev);
      fz_free_text_span(text_span);
    }

    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "export complete");

    close(fd);
}
#endif


/* wcsstr() seems broken--it matches too much */
wchar_t* widestrstr(wchar_t* haystack, int haystack_length, wchar_t* needle, int needle_length) {
    char* found;
    int byte_haystack_length;
    int byte_needle_length;

    if (needle_length == 0)
         return haystack;
         
    byte_haystack_length = haystack_length * sizeof(wchar_t);
    byte_needle_length = needle_length * sizeof(wchar_t);

    while(haystack_length >= needle_length &&
        NULL != (found = memmem(haystack, byte_haystack_length, needle, byte_needle_length))) {
          int delta = found - (char*)haystack;
          int new_offset;

          /* Check if the find is wchar_t-aligned */
          if (delta % sizeof(wchar_t) == 0)
              return (wchar_t*)found;

          new_offset = (delta + sizeof(wchar_t) - 1) / sizeof(wchar_t);

          haystack += new_offset;
          haystack_length -= new_offset;
          byte_haystack_length = haystack_length * sizeof(wchar_t);
    }

    return NULL;
}

/* TODO: Specialcase searches for 7-bit text to make them faster */
JNIEXPORT jobject JNICALL
Java_com_juanvvc_comicviewer_readers_PDF_find(
        JNIEnv *env,
        jobject this,
        jstring text,
        jint pageno) {
    pdf_t *pdf = NULL;
    const jchar *jtext = NULL;
    wchar_t *ctext = NULL;
    jboolean is_copy;
    jobject results = NULL;
    pdf_page *page = NULL;
    fz_text_span *text_span = NULL, *ln = NULL;
    fz_device *dev = NULL;
    wchar_t *textlinechars;
    wchar_t *found = NULL;
    fz_error error = 0;
    jobject find_result = NULL;
    int length;
    int i;

    jtext = (*env)->GetStringChars(env, text, &is_copy);

    if (jtext == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "text cannot be null");
        (*env)->ReleaseStringChars(env, text, jtext);
        return NULL;
    }

    length = (*env)->GetStringLength(env, text);

    ctext = malloc((length+1) * sizeof(wchar_t));

    for (i=0; i<length; i++) {
        ctext[i] = towlower(jtext[i]);
        __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "find(%x)", ctext[i]);
    }
    ctext[length] = 0; /* This will be needed if wcsstr() ever starts to work */

    pdf = get_pdf_from_this(env, this);
    page = get_page(pdf, pageno);

    if (pdf->last_pageno != pageno && NULL != pdf->xref->store) {
        pdf_age_store(pdf->xref->store, FIND_STORE_MAX_AGE);
        pdf->last_pageno = pageno;
    }

    text_span = fz_new_text_span();
    dev = fz_new_text_device(text_span);
    error = pdf_run_page(pdf->xref, page, dev, fz_identity);
    if (error)
    {
        /* TODO: cleanup */
        fz_rethrow(error, "text extraction failed");
        return NULL;
    }

    for(ln = text_span; ln; ln = ln->next) {
        if (length <= ln->len) {
            textlinechars = (wchar_t*)malloc((ln->len + 1)*sizeof(wchar_t));
            for(i = 0; i < ln->len; ++i) textlinechars[i] = towlower(ln->text[i].c);
            textlinechars[ln->len] = 0; /* will be needed if wccstr starts to work */
            found = widestrstr(textlinechars, ln->len, ctext, length);
            if (found) {
                __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "found something, creating empty find result");
                find_result = create_find_result(env);
                if (find_result == NULL) {
                    __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "tried to create empty find result, but got NULL instead");
                    /* TODO: free resources */
                    free(ctext);
                    (*env)->ReleaseStringChars(env, text, jtext);
                    pdf_age_store(pdf->xref->store, 0);
                    return;
                }
                __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "found something, empty find result created");
                set_find_result_page(env, find_result, pageno);
                /* now add markers to this find result */
                {
                    int i = 0;
                    int i0, i1;
                    /* int x, y; */
                    fz_bbox charbox;
                    i0 = (found-textlinechars);
                    i1 = i0 + length;
                    for(i = i0; i < i1; ++i) {
                        __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "adding marker for letter %d: %c", i, textlinechars[i]);
                        /* 
                        x = ln->text[i].x;
                        y = ln->text[i].y;
                        convert_point_pdf_to_apv(pdf, pageno, &x, &y);
                        */
                        charbox = ln->text[i].bbox;
                        convert_box_pdf_to_apv(pdf, pageno, &charbox);
                        /* add_find_result_marker(env, find_result, x-2, y-2, x+2, y+2); */
                        add_find_result_marker(env, find_result, charbox.x0-2, charbox.y0-2, charbox.x1+2, charbox.y1+2); /* TODO: check errors */

                    }
                    /* TODO: obviously this sucks massively, good God please forgive me for writing this; if only I had more time... */
                    /*
                    x = ((float)(ln->text[i1-1].x - ln->text[i0].x)) / (float)strlen(ctext) + ln->text[i1-1].x;
                    y = ((float)(ln->text[i1-1].y - ln->text[i0].y)) / (float)strlen(ctext) + ln->text[i1-1].y;
                    convert_point_pdf_to_apv(pdf, pageno, &x, &y);
                    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "adding final marker");
                    add_find_result_marker(env,
                            find_result,
                            x-2, y-2,
                            x+2, y+2
                        );
                    */
                }
                __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "adding find result to list");
                add_find_result_to_list(env, &results, find_result);
                __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "added find result to list");
            }
            free(textlinechars);
        }
    }

    fz_free_device(dev);
    fz_free_text_span(text_span);

    free(ctext);
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "releasing text back to jvm");
    (*env)->ReleaseStringChars(env, text, jtext);
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "returning results");
    pdf_age_store(pdf->xref->store, 0);
    return results;
}




// #ifdef pro
// /**
//  * Return text of given page.
//  */
// JNIEXPORT jobject JNICALL
// Java_com_juanvvc_comicviewer_readers_PDF_getText(
//         JNIEnv *env,
//         jobject this,
//         jint pageno) {
//     char *text = NULL;
//     pdf_t *pdf = NULL;
//     pdf = get_pdf_from_this(env, this);
//     jstring jtext = NULL;
// 
//     if (pdf == NULL) {
//         __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "getText: pdf is NULL");
//         return NULL;
//     }
//     text = extract_text(pdf, pageno);
//     jtext = (*env)->NewStringUTF(env, text);
//     if (text) free(text);
//     return jtext;
// }
// #endif


/**
 * Create empty FindResult object.
 * @param env JNI Environment
 * @return newly created, empty FindResult object
 */
jobject create_find_result(JNIEnv *env) {
    static jmethodID constructorID;
    jclass findResultClass = NULL;
    static int jni_ids_cached = 0;
    jobject findResultObject = NULL;

    findResultClass = (*env)->FindClass(env, "cx/hell/android/lib/pagesview/FindResult");

    if (findResultClass == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "create_find_result: FindClass returned NULL");
        return NULL;
    }

    if (jni_ids_cached == 0) {
        constructorID = (*env)->GetMethodID(env, findResultClass, "<init>", "()V");
        if (constructorID == NULL) {
            (*env)->DeleteLocalRef(env, findResultClass);
            __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "create_find_result: couldn't get method id for FindResult constructor");
            return NULL;
        }
        jni_ids_cached = 1;
    }

    findResultObject = (*env)->NewObject(env, findResultClass, constructorID);
    (*env)->DeleteLocalRef(env, findResultClass);
    return findResultObject;
}


void add_find_result_to_list(JNIEnv *env, jobject *list, jobject find_result) {
    static int jni_ids_cached = 0;
    static jmethodID list_add_method_id = NULL;
    jclass list_class = NULL;
    if (list == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "list cannot be null - it must be a pointer jobject variable");
        return;
    }
    if (find_result == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "find_result cannot be null");
        return;
    }
    if (*list == NULL) {
        jmethodID list_constructor_id;
        __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "creating ArrayList");
        list_class = (*env)->FindClass(env, "java/util/ArrayList");
        if (list_class == NULL) {
            __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "couldn't find class java/util/ArrayList");
            return;
        }
        list_constructor_id = (*env)->GetMethodID(env, list_class, "<init>", "()V");
        if (!list_constructor_id) {
            __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "couldn't find ArrayList constructor");
            return;
        }
        *list = (*env)->NewObject(env, list_class, list_constructor_id);
        if (*list == NULL) {
            __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "failed to create ArrayList: NewObject returned NULL");
            return;
        }
    }

    if (!jni_ids_cached) {
        if (list_class == NULL) {
            list_class = (*env)->FindClass(env, "java/util/ArrayList");
            if (list_class == NULL) {
                __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "couldn't find class java/util/ArrayList");
                return;
            }
        }
        list_add_method_id = (*env)->GetMethodID(env, list_class, "add", "(Ljava/lang/Object;)Z");
        if (list_add_method_id == NULL) {
            __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "couldn't get ArrayList.add method id");
            return;
        }
        jni_ids_cached = 1;
    } 

    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "calling ArrayList.add");
    (*env)->CallBooleanMethod(env, *list, list_add_method_id, find_result);
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "add_find_result_to_list done");
}


/**
 * Set find results page member.
 * @param JNI environment
 * @param findResult find result object that should be modified
 * @param page new value for page field
 */
void set_find_result_page(JNIEnv *env, jobject findResult, int page) {
    static char jni_ids_cached = 0;
    static jfieldID page_field_id = 0;
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "trying to set find results page number");
    if (jni_ids_cached == 0) {
        jclass findResultClass = (*env)->GetObjectClass(env, findResult);
        page_field_id = (*env)->GetFieldID(env, findResultClass, "page", "I");
        jni_ids_cached = 1;
    }
    (*env)->SetIntField(env, findResult, page_field_id, page);
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "find result page number set");
}


/**
 * Add marker to find result.
 */
void add_find_result_marker(JNIEnv *env, jobject findResult, int x0, int y0, int x1, int y1) {
    static jmethodID addMarker_methodID = 0;
    static unsigned char jni_ids_cached = 0;
    if (!jni_ids_cached) {
        jclass findResultClass = NULL;
        findResultClass = (*env)->FindClass(env, "cx/hell/android/lib/pagesview/FindResult");
        if (findResultClass == NULL) {
            __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "add_find_result_marker: FindClass returned NULL");
            return;
        }
        addMarker_methodID = (*env)->GetMethodID(env, findResultClass, "addMarker", "(IIII)V");
        if (addMarker_methodID == NULL) {
            __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "add_find_result_marker: couldn't find FindResult.addMarker method ID");
            return;
        }
        jni_ids_cached = 1;
    }
    (*env)->CallVoidMethod(env, findResult, addMarker_methodID, x0, y0, x1, y1); /* TODO: is always really int jint? */
}


/**
 * Get pdf_ptr field value, cache field address as a static field.
 * @param env Java JNI Environment
 * @param this object to get "pdf_ptr" field from
 * @return pdf_ptr field value
 */
pdf_t* get_pdf_from_this(JNIEnv *env, jobject this) {
    static jfieldID field_id = 0;
    static unsigned char field_is_cached = 0;
    pdf_t *pdf = NULL;
    if (! field_is_cached) {
        jclass this_class = (*env)->GetObjectClass(env, this);
        field_id = (*env)->GetFieldID(env, this_class, "pdf_ptr", "I");
        field_is_cached = 1;
        __android_log_print(ANDROID_LOG_DEBUG, "cx.hell.android.pdfview", "cached pdf_ptr field id %d", (int)field_id);
    }
	pdf = (pdf_t*) (*env)->GetIntField(env, this, field_id);
    return pdf;
}


/**
 * Get descriptor field value from FileDescriptor class, cache field offset.
 * This is undocumented private field.
 * @param env JNI Environment
 * @param this FileDescriptor object
 * @return file descriptor field value
 */
int get_descriptor_from_file_descriptor(JNIEnv *env, jobject this) {
    static jfieldID field_id = 0;
    static unsigned char is_cached = 0;
    if (!is_cached) {
        jclass this_class = (*env)->GetObjectClass(env, this);
        field_id = (*env)->GetFieldID(env, this_class, "descriptor", "I");
        is_cached = 1;
        __android_log_print(ANDROID_LOG_DEBUG, "cx.hell.android.pdfview", "cached descriptor field id %d", (int)field_id);
    }
    __android_log_print(ANDROID_LOG_DEBUG, "cx.hell.android.pdfview", "will get descriptor field...");
    return (*env)->GetIntField(env, this, field_id);
}


void get_size(JNIEnv *env, jobject size, int *width, int *height) {
    static jfieldID width_field_id = 0;
    static jfieldID height_field_id = 0;
    static unsigned char fields_are_cached = 0;
    if (! fields_are_cached) {
        jclass size_class = (*env)->GetObjectClass(env, size);
        width_field_id = (*env)->GetFieldID(env, size_class, "width", "I");
        height_field_id = (*env)->GetFieldID(env, size_class, "height", "I");
        fields_are_cached = 1;
        __android_log_print(ANDROID_LOG_DEBUG, "cx.hell.android.pdfview", "cached Size fields");
    }
    *width = (*env)->GetIntField(env, size, width_field_id);
    *height = (*env)->GetIntField(env, size, height_field_id);
}


/**
 * Store width and height values into PDF.Size object, cache field ids in static members.
 * @param env JNI Environment
 * @param width width to store
 * @param height height field value to be stored
 * @param size target PDF.Size object
 */
void save_size(JNIEnv *env, jobject size, int width, int height) {
    static jfieldID width_field_id = 0;
    static jfieldID height_field_id = 0;
    static unsigned char fields_are_cached = 0;
    if (! fields_are_cached) {
        jclass size_class = (*env)->GetObjectClass(env, size);
        width_field_id = (*env)->GetFieldID(env, size_class, "width", "I");
        height_field_id = (*env)->GetFieldID(env, size_class, "height", "I");
        fields_are_cached = 1;
        __android_log_print(ANDROID_LOG_DEBUG, "cx.hell.android.pdfview", "cached Size fields");
    }
    (*env)->SetIntField(env, size, width_field_id, width);
    (*env)->SetIntField(env, size, height_field_id, height);
}


/**
 * pdf_t "constructor": create empty pdf_t with default values.
 * @return newly allocated pdf_t struct with fields set to default values
 */
pdf_t* create_pdf_t() {
    pdf_t *pdf = NULL;
    pdf = (pdf_t*)malloc(sizeof(pdf_t));
    pdf->xref = NULL;
    pdf->outline = NULL;
    pdf->fileno = -1;
    pdf->pages = NULL;
    pdf->glyph_cache = NULL;
    
    return pdf;
}


#if 0
/**
 * Parse bytes into PDF struct.
 * @param bytes pointer to bytes that should be parsed
 * @param len length of byte buffer
 * @return initialized pdf_t struct; or NULL if loading failed
 */
pdf_t* parse_pdf_bytes(unsigned char *bytes, size_t len, jstring box_name) {
    pdf_t *pdf;
    const char* c_box_name;
    fz_error error;

    pdf = create_pdf_t();
    c_box_name = (*env)->GetStringUTFChars(env, box_name, &iscopy);
    strncpy(pdf->box, box_name, 9);
    pdf->box[MAX_BOX_NAME] = 0;

    pdf->xref = pdf_newxref();
    error = pdf_loadxref_mem(pdf->xref, bytes, len);
    if (error) {
        __android_log_print(ANDROID_LOG_ERROR, "cx.hell.android.pdfview", "got err from pdf_loadxref_mem: %d", (int)error);
        __android_log_print(ANDROID_LOG_ERROR, "cx.hell.android.pdfview", "fz errors:\n%s", fz_errorbuf);
        /* TODO: free resources */
        return NULL;
    }

    error = pdf_decryptxref(pdf->xref);
    if (error) {
        return NULL;
    }

    if (pdf_needspassword(pdf->xref)) {
        int authenticated = 0;
        authenticated = pdf_authenticatepassword(pdf->xref, "");
        if (!authenticated) {
            /* TODO: ask for password */
            __android_log_print(ANDROID_LOG_ERROR, "cx.hell.android.pdfview", "failed to authenticate with empty password");
            return NULL;
        }
    }

    pdf->xref->root = fz_resolveindirect(fz_dictgets(pdf->xref->trailer, "Root"));
    fz_keepobj(pdf->xref->root);

    pdf->xref->info = fz_resolveindirect(fz_dictgets(pdf->xref->trailer, "Info"));
    fz_keepobj(pdf->xref->info);

    pdf->outline = pdf_loadoutline(pdf->xref);

    return pdf;
}
#endif


/**
 * Parse file into PDF struct.
 * Use filename if it's not null, otherwise use fileno.
 */
pdf_t* parse_pdf_file(const char *filename, int fileno, const char* password) {
    pdf_t *pdf;
    fz_error error;
    int fd;
    fz_stream *file;

    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "parse_pdf_file(%s, %d)", filename, fileno);

    pdf = create_pdf_t();

    if (filename) {
        fd = open(filename, O_BINARY | O_RDONLY, 0666);
        if (fd < 0) {
            free(pdf);
            return NULL;
        }
    } else {
        pdf->fileno = dup(fileno);
        fd = pdf->fileno;
    }

    file = fz_open_fd(fd);
    error = pdf_open_xref_with_stream(&(pdf->xref), file, NULL);
    if (!pdf->xref) {
        __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "got NULL from pdf_openxref");
        /* __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "fz errors:\n%s", fz_errorbuf); */
        free(pdf);
        return NULL;
    }

    /*
    error = pdf_decryptxref(pdf->xref);
    if (error) {
        return NULL;
    }
    */

    pdf->invalid_password = 0;

    if (pdf_needs_password(pdf->xref)) {
        int authenticated = 0;
        authenticated = pdf_authenticate_password(pdf->xref, (char*)password);
        if (!authenticated) {
            /* TODO: ask for password */
            __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "failed to authenticate");
            pdf->invalid_password = 1;
            return pdf;
        }
    }

    /* pdf->xref->root = fz_resolveindirect(fz_dictgets(pdf->xref->trailer, "Root"));
    fz_keepobj(pdf->xref->root);
    pdf->xref->info = fz_resolveindirect(fz_dictgets(pdf->xref->trailer, "Info"));
    if (pdf->xref->info) fz_keepobj(pdf->xref->info);
    */
    pdf->outline = pdf_load_outline(pdf->xref);

    error = pdf_load_page_tree(pdf->xref);
    if (error) {
        __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "pdf_loadpagetree failed: %d", error);
        /* TODO: clean resources */
        return NULL;
    }

    {
        int c = 0;
        c = pdf_count_pages(pdf->xref);
        __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "page count: %d", c);
    }
    
    pdf->last_pageno = -1;

    return pdf;
}


/**
 * Calculate zoom to best match given dimensions.
 * There's no guarantee that page zoomed by resulting zoom will fit rectangle max_width x max_height exactly.
 * @param max_width expected max width
 * @param max_height expected max height
 * @param page original page
 * @return zoom required to best fit page into max_width x max_height rectangle
 */
/*double get_page_zoom(pdf_page *page, int max_width, int max_height) {
    double page_width, page_height;
    double zoom_x, zoom_y;
    double zoom;
    page_width = page->mediabox.x1 - page->mediabox.x0;
    page_height = page->mediabox.y1 - page->mediabox.y0;

    zoom_x = max_width / page_width;
    zoom_y = max_height / page_height;

    zoom = (zoom_x < zoom_y) ? zoom_x : zoom_y;

    return zoom;
}*/


/**
 * Lazy get-or-load page.
 * Only PDFVIEW_MAX_PAGES_LOADED pages can be loaded at the time.
 * @param pdf pdf struct
 * @param pageno 0-based page number
 * @return pdf_page
 */
pdf_page* get_page(pdf_t *pdf, int pageno) {
    fz_error error = 0;
    int loaded_pages = 0;
    int pagecount;

    pagecount = pdf_count_pages(pdf->xref);

    if (!pdf->pages) {
        int i;
        pdf->pages = (pdf_page**)malloc(pagecount * sizeof(pdf_page*));
        for(i = 0; i < pagecount; ++i) pdf->pages[i] = NULL;
    }

    if (!pdf->pages[pageno]) {
        pdf_page *page = NULL;
        int loaded_pages = 0;
        int i = 0;

        for(i = 0; i < pagecount; ++i) {
            if (pdf->pages[i]) loaded_pages++;
        }

        #if 0
        if (loaded_pages >= PDFVIEW_MAX_PAGES_LOADED) {
            int page_to_drop = 0; /* not the page number */
            int j = 0;
            __android_log_print(ANDROID_LOG_INFO, PDFVIEW_LOG_TAG, "already loaded %d pages, going to drop random one", loaded_pages);
            page_to_drop = rand() % loaded_pages;
            __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "will drop %d-th loaded page", page_to_drop);
            /* search for page_to_drop-th loaded page and then drop it */
            for(i = 0; i < pagecount; ++i) {
                if (pdf->pages[i]) {
                    /* one of loaded pages, the j-th one */
                    if (j == page_to_drop) {
                        __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "found %d-th loaded page, it's %d-th in document, dropping now", page_to_drop, i);
                        pdf_droppage(pdf->pages[i]);
                        pdf->pages[i] = NULL;
                        break;
                    } else {
                        j++;
                    }
                }
            }
        }
        #endif

        error = pdf_load_page(&page, pdf->xref, pageno);
        if (error) {
            __android_log_print(ANDROID_LOG_ERROR, "cx.hell.android.pdfview", "pdf_loadpage -> %d", (int)error);
            /* __android_log_print(ANDROID_LOG_ERROR, "cx.hell.android.pdfview", "fitz error is:\n%s", fz_errorbuf); */
            return NULL;
        }
        pdf->pages[pageno] = page;
    }
    return pdf->pages[pageno];
}


/**
 * Get part of page as bitmap.
 * Parameters left, top, width and height are interprted after scalling, so if we have 100x200 page scalled by 25% and
 * request 0x0 x 25x50 tile, we should get 25x50 bitmap of whole page content.
 * pageno is 0-based.
 */
static jintArray get_page_image_bitmap(JNIEnv *env,
      pdf_t *pdf, int pageno, int zoom_pmil, int left, int top, int rotation,
      int gray, int skipImages,
      int *width, int *height) {
    unsigned char *bytes = NULL;
    fz_matrix ctm;
    double zoom;
    fz_rect bbox;
    fz_error error = 0;
    pdf_page *page = NULL;
    fz_pixmap *image = NULL;
    static int runs = 0;
    fz_device *dev = NULL;
    int num_pixels;
    jintArray jints; /* return value */
    int *jbuf; /* pointer to internal jint */
    fz_obj *pageobj;
    fz_obj *trimobj;
    fz_rect trimbox;

    zoom = (double)zoom_pmil / 1000.0;

    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "get_page_image_bitmap(pageno: %d) start", (int)pageno);

    if (!pdf->glyph_cache) {
        pdf->glyph_cache = fz_new_glyph_cache();
        if (!pdf->glyph_cache) {
            __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "failed to create glyphcache");
            return NULL;
        }
    }

    if (pdf->last_pageno != pageno && NULL != pdf->xref->store) {
        pdf_age_store(pdf->xref->store, BITMAP_STORE_MAX_AGE);
        pdf->last_pageno = pageno;
    }

    page = get_page(pdf, pageno);
    if (!page) return NULL; /* TODO: handle/propagate errors */

    ctm = fz_identity;
    pageobj = pdf->xref->page_objs[pageno];
    trimobj = fz_dict_gets(pageobj, pdf->box);
    if (trimobj != NULL)
        trimbox = pdf_to_rect(trimobj);
    else
        trimbox = page->mediabox;

    ctm = fz_concat(ctm, fz_translate(-trimbox.x0, -trimbox.y1));
    ctm = fz_concat(ctm, fz_scale(zoom, -zoom));
    rotation = page->rotate + rotation * -90;
    if (rotation != 0) ctm = fz_concat(ctm, fz_rotate(rotation));
    bbox = fz_transform_rect(ctm, trimbox);

    /* not bbox holds page after transform, but we only need tile at (left,right) from top-left corner */

    bbox.x0 = bbox.x0 + left;
    bbox.y0 = bbox.y0 + top;
    bbox.x1 = bbox.x0 + *width;
    bbox.y1 = bbox.y0 + *height;


#if 0
    error = fz_rendertree(&image, pdf->renderer, page->tree, ctm, fz_roundrect(bbox), 1);
    if (error) {
        fz_rethrow(error, "rendering failed");
        /* TODO: cleanup mem on error, so user can try to open many files without causing memleaks; also report errors nicely to user */
        return NULL;
    }
#endif

    image = fz_new_pixmap(gray ? fz_device_gray : fz_device_bgr, *width, *height);
    image->x = bbox.x0;
    image->y = bbox.y0;
    fz_clear_pixmap_with_color(image, gray ? 0 : 0xff);
    memset(image->samples, gray ? 0 : 0xff, image->h * image->w * image->n);
    dev = fz_new_draw_device(pdf->glyph_cache, image);

    if (skipImages)
        dev->hints |= FZ_IGNORE_IMAGE;

    error = pdf_run_page(pdf->xref, page, dev, ctm);

    if (error)
    {
        /* TODO: cleanup */
        fz_rethrow(error, "rendering failed");
        return NULL;
    }

    fz_free_device(dev);

    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "got image %d x %d, asked for %d x %d",
            (int)(image->w), (int)(image->h),
            *width, *height);

    /* TODO: learn jni and avoid copying bytes ;) */
    num_pixels = image->w * image->h;

    jints = (*env)->NewIntArray(env, num_pixels);
	jbuf = (*env)->GetIntArrayElements(env, jints, NULL);
    if (gray) {
        copy_alpha((unsigned char*)jbuf, image->samples, image->w, image->h);
    }
    else {
        memcpy(jbuf, image->samples, num_pixels * 4);
    }
    (*env)->ReleaseIntArrayElements(env, jints, jbuf, 0);

    *width = image->w;
    *height = image->h;
    fz_drop_pixmap(image);

    runs += 1;
    return jints;
}


void copy_alpha(unsigned char* out, unsigned char *in, unsigned int w, unsigned int h) {
        unsigned int count = w*h;
        while(count--) {
            out+= 3;
            *out++ = 255-((255-in[0]) * in[1])/255;
            in += 2;
        }
}


/**
 * Get page size in APV's convention.
 * @param page 0-based page number
 * @param pdf pdf struct
 * @param width target for width value
 * @param height target for height value
 * @return error code - 0 means ok
 */
int get_page_size(pdf_t *pdf, int pageno, int *width, int *height) {
    fz_error error = 0;
    fz_obj *pageobj = NULL;
    fz_obj *sizeobj = NULL;
    fz_rect bbox;
    fz_obj *rotateobj = NULL;
    int rotate = 0;

    pageobj = pdf->xref->page_objs[pageno];
    sizeobj = fz_dict_gets(pageobj, pdf->box);
    if (sizeobj == NULL)
         sizeobj = fz_dict_gets(pageobj, "MediaBox");
    rotateobj = fz_dict_gets(pageobj, "Rotate");
    if (fz_is_int(rotateobj)) {
        rotate = fz_to_int(rotateobj);
    } else {
        rotate = 0;
    }
    bbox = pdf_to_rect(sizeobj);
    if (rotate != 0 && (rotate % 180) == 90) {
        *width = bbox.y1 - bbox.y0;
        *height = bbox.x1 - bbox.x0;
    } else {
        *width = bbox.x1 - bbox.x0;
        *height = bbox.y1 - bbox.y0;
    }
    return 0;
}


#if 0
/**
 * Convert coordinates from pdf to APVs.
 * TODO: faster? lazy?
 * @return error code, 0 means ok
 */
int convert_point_pdf_to_apv(pdf_t *pdf, int page, int *x, int *y) {
    fz_error error = 0;
    fz_obj *pageobj = NULL;
    fz_obj *rotateobj = NULL;
    fz_obj *sizeobj = NULL;
    fz_rect bbox;
    int rotate = 0;
    fz_point p;

    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "convert_point_pdf_to_apv()");

    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "trying to convert %d x %d to APV coords", *x, *y);

    pageobj = pdf_getpageobject(pdf->xref, page+1);
    if (!pageobj) return -1;
    sizeobj = fz_dictgets(pageobj, pdf->box);
    if (sizeobj == NULL)
        sizeobj = fz_dictgets(pageobj, "MediaBox");
    if (!sizeobj) return -1;
    bbox = pdf_torect(sizeobj);
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "page bbox is %.1f, %.1f, %.1f, %.1f", bbox.x0, bbox.y0, bbox.x1, bbox.y1);
    rotateobj = fz_dictgets(pageobj, "Rotate");
    if (fz_isint(rotateobj)) {
        rotate = fz_toint(rotateobj);
    } else {
        rotate = 0;
    }
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "rotate is %d", (int)rotate);

    p.x = *x;
    p.y = *y;

    if (rotate != 0) {
        fz_matrix m;
        m = fz_rotate(-rotate);
        bbox = fz_transformrect(m, bbox);
        p = fz_transformpoint(m, p);
    }

    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "after rotate bbox is: %.1f, %.1f, %.1f, %.1f", bbox.x0, bbox.y0, bbox.x1, bbox.y1);
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "after rotate point is: %.1f, %.1f", p.x, p.y);

    *x = p.x - MIN(bbox.x0,bbox.x1);
    *y = MAX(bbox.y1, bbox.y0) - p.y;

    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "result is: %d, %d", *x, *y);

    return 0;
}
#endif


/**
 * Convert coordinates from pdf to APV.
 * Result is stored in location pointed to by bbox param.
 * This function has to get page TrimBox relative to which bbox is located.
 * This function should not allocate any memory.
 * @return error code, 0 means ok
 */
int convert_box_pdf_to_apv(pdf_t *pdf, int page, fz_bbox *bbox) {
    fz_error error = 0;
    fz_obj *pageobj = NULL;
    fz_obj *rotateobj = NULL;
    fz_obj *sizeobj = NULL;
    fz_rect page_bbox;
    fz_rect param_bbox;
    int rotate = 0;
    float height = 0;
    float width = 0;

    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "convert_box_pdf_to_apv(page: %d, bbox: %d %d %d %d)", page, bbox->x0, bbox->y0, bbox->x1, bbox->y1);

    /* copying field by field becuse param_bbox is fz_rect (floats) and *bbox is fz_bbox (ints) */
    param_bbox.x0 = bbox->x0;
    param_bbox.y0 = bbox->y0;
    param_bbox.x1 = bbox->x1;
    param_bbox.y1 = bbox->y1;

    pageobj = pdf->xref->page_objs[page];
    if (!pageobj) return -1;
    sizeobj = fz_dict_gets(pageobj, pdf->box);
    if (sizeobj == NULL)
         sizeobj = fz_dict_gets(pageobj, "MediaBox");
    if (!sizeobj) return -1;
    page_bbox = pdf_to_rect(sizeobj);
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "page bbox is %.1f, %.1f, %.1f, %.1f", page_bbox.x0, page_bbox.y0, page_bbox.x1, page_bbox.y1);
    rotateobj = fz_dict_gets(pageobj, "Rotate");
    if (fz_is_int(rotateobj)) {
        rotate = fz_to_int(rotateobj);
    } else {
        rotate = 0;
    }
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "rotate is %d", (int)rotate);

    if (rotate != 0) {
        fz_matrix m;
        m = fz_rotate(-rotate);
        param_bbox = fz_transform_rect(m, param_bbox);
        page_bbox = fz_transform_rect(m, page_bbox);
    }

    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "after rotate page bbox is: %.1f, %.1f, %.1f, %.1f", page_bbox.x0, page_bbox.y0, page_bbox.x1, page_bbox.y1);
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "after rotate param bbox is: %.1f, %.1f, %.1f, %.1f", param_bbox.x0, param_bbox.y0, param_bbox.x1, param_bbox.y1);

    /* set result: param bounding box relative to left-top corner of page bounding box */

    /*
    bbox->x0 = MIN(param_bbox.x0, param_bbox.x1) - MIN(page_bbox.x0, page_bbox.x1);
    bbox->y0 = MIN(param_bbox.y0, param_bbox.y1) - MIN(page_bbox.y0, page_bbox.y1);
    bbox->x1 = MAX(param_bbox.x0, param_bbox.x1) - MIN(page_bbox.x0, page_bbox.x1);
    bbox->y1 = MAX(param_bbox.y0, param_bbox.y1) - MIN(page_bbox.y0, page_bbox.y1);
    */

    width = ABS(page_bbox.x0 - page_bbox.x1);
    height = ABS(page_bbox.y0 - page_bbox.y1);

    bbox->x0 = (MIN(param_bbox.x0, param_bbox.x1) - MIN(page_bbox.x0, page_bbox.x1));
    bbox->y1 = height - (MIN(param_bbox.y0, param_bbox.y1) - MIN(page_bbox.y0, page_bbox.y1));
    bbox->x1 = (MAX(param_bbox.x0, param_bbox.x1) - MIN(page_bbox.x0, page_bbox.x1));
    bbox->y0 = height - (MAX(param_bbox.y0, param_bbox.y1) - MIN(page_bbox.y0, page_bbox.y1));

    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "result after transformations: %d, %d, %d, %d", bbox->x0, bbox->y0, bbox->x1, bbox->y1);

    return 0;
}


void pdf_android_loghandler(const char *m) {
    __android_log_print(ANDROID_LOG_DEBUG, "cx.hell.android.pdfview.mupdf", m);
}

// #ifdef pro
// jobject create_outline_recursive(JNIEnv *env, jclass outline_class, const fz_outline *outline) {
//     static int jni_ids_cached = 0;
//     static jmethodID constructor_id = NULL;
//     static jfieldID title_field_id = NULL;
//     static jfieldID page_field_id = NULL;
//     static jfieldID next_field_id = NULL;
//     static jfieldID down_field_id = NULL;
//     int outline_class_found = 0;
//     jobject joutline = NULL;
//     jstring jtitle = NULL;
// 
//     if (outline == NULL) return NULL;
// 
//     if (outline_class == NULL) {
//         outline_class = (*env)->FindClass(env, "cx/hell/android/lib/pdf/PDF$Outline");
//         if (outline_class == NULL) {
//             __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "can't find outline class");
//             return NULL;
//         }
//         outline_class_found = 1;
//     }
// 
//     if (!jni_ids_cached) {
//         constructor_id = (*env)->GetMethodID(env, outline_class, "<init>", "()V");
//         if (constructor_id == NULL) {
//             (*env)->DeleteLocalRef(env, outline_class);
//             __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "create_outline_recursive: couldn't get method id for Outline constructor");
//             return NULL;
//         }
//         // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "got constructor id");
//         title_field_id = (*env)->GetFieldID(env, outline_class, "title", "Ljava/lang/String;");
//         if (title_field_id == NULL) {
//             (*env)->DeleteLocalRef(env, outline_class);
//             __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "create_outline_recursive: couldn't get field id for Outline.title");
//             return NULL;
//         }
//         // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "got title field id");
//         page_field_id = (*env)->GetFieldID(env, outline_class, "page", "I");
//         if (page_field_id == NULL) {
//             (*env)->DeleteLocalRef(env, outline_class);
//             __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "create_outline_recursive: couldn't get field id for Outline.page");
//             return NULL;
//         }
//         // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "got page field id");
//         next_field_id = (*env)->GetFieldID(env, outline_class, "next", "Lcx/hell/android/lib/pdf/PDF$Outline;");
//         if (next_field_id == NULL) {
//             (*env)->DeleteLocalRef(env, outline_class);
//             __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "create_outline_recursive: couldn't get field id for Outline.next");
//             return NULL;
//         }
//         // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "got down field id");
//         down_field_id = (*env)->GetFieldID(env, outline_class, "down", "Lcx/hell/android/lib/pdf/PDF$Outline;");
//         if (down_field_id == NULL) {
//             (*env)->DeleteLocalRef(env, outline_class);
//             __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "create_outline_recursive: couldn't get field id for Outline.down");
//             return NULL;
//         }
// 
//         jni_ids_cached = 1;
//     }
// 
//     joutline = (*env)->NewObject(env, outline_class, constructor_id);
//     if (joutline == NULL) {
//         (*env)->DeleteLocalRef(env, outline_class);
//         __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "failed to create joutline");
//         return NULL;
//     }
//     // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "joutline created");
//     if (outline->title) {
//         // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "title to set: %s", outline->title);
//         jtitle = (*env)->NewStringUTF(env, outline->title);
//         // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "jtitle created");
//         (*env)->SetObjectField(env, joutline, title_field_id, jtitle);
//         (*env)->DeleteLocalRef(env, jtitle);
//         // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "title set");
//     } else {
//         // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "title is null, won't create not set");
//     }
//     (*env)->SetIntField(env, joutline, page_field_id, outline->page);
//     // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "page set");
//     if (outline->next) {
//         jobject next_outline = NULL;
//         next_outline = create_outline_recursive(env, outline_class, outline->next);
//         (*env)->SetObjectField(env, joutline, next_field_id, next_outline);
//         (*env)->DeleteLocalRef(env, next_outline);
//         // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "next set");
//     }
//     if (outline->down) {
//         jobject down_outline = NULL;
//         down_outline = create_outline_recursive(env, outline_class, outline->down);
//         (*env)->SetObjectField(env, joutline, down_field_id, down_outline);
//         (*env)->DeleteLocalRef(env, down_outline);
//         // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "down set");
//     }
// 
//     if (outline_class_found) {
//         (*env)->DeleteLocalRef(env, outline_class);
//         // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "local ref deleted");
//     }
// 
//     return joutline;
// }
// #endif


/**
 * Extract text from given pdf page.
 */
char* extract_text(pdf_t *pdf, int pageno) {

    fz_device *dev = NULL;
    fz_text_span *text_span = NULL, *ln = NULL;
    fz_error error = 0;

    pdf_page *page = NULL;

    int text_len = 0;
    char *text = NULL; /* utf-8 text */
    int i = 0;

    if (pdf == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "extract_text: pdf is NULL");
        return NULL;
    }

    page = get_page(pdf, pageno);

    if (pdf->last_pageno != pageno && NULL != pdf->xref->store) {
        pdf_age_store(pdf->xref->store, FIND_STORE_MAX_AGE);
        pdf->last_pageno = pageno;
    }

    text_span = fz_new_text_span();
    dev = fz_new_text_device(text_span);
    error = pdf_run_page(pdf->xref, page, dev, fz_identity);
    if (error)
    {
        /* TODO: cleanup */
        fz_rethrow(error, "text extraction failed");
        return NULL;
    }

    /* count chars */
    text_len = 0;
    for(ln = text_span; ln; ln = ln->next) {
        int j = 0; /* rune idx */
        for(j = 0; j < ln->len; ++j) {
            text_len += runelen(ln->text[j].c); /* utf-8 chars of rune */
        }
        text_len += ln->len + 1; /* \n */
    }

    /* copy chars */
    text = (char*)malloc(text_len+1);
    i = 0; /* current pos in text when copying */
    for(ln = text_span; ln; ln = ln->next) {
        int j = 0; /* current rune in text span */
        int char_len = 0;
        for(j = 0; j < ln->len; ++j) {
            char_len = runetochar(text + i, &(ln->text[j].c));
            i += char_len;
        }
        text[i] = '\n';
        i++;
    }
    text[i] = 0; /* TODO: add buffer overrun checks */
    // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "extracted text, len: %d, chars: %s", text_len, text);
    return text;
}




/* vim: set sts=4 ts=4 sw=4 et: */

