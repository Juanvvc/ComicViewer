
#include <string.h>
#include <wctype.h>
#include <jni.h>

#include "android/log.h"

#include "apvcore.h"
#include "apvandroid.h"

#include "mupdf-internal.h"


#define PDFVIEW_LOG_TAG "cx.hell.android.pdfview"

static JavaVM *cached_jvm = NULL;

apv_alloc_state_t *apv_alloc_state = NULL;
fz_alloc_context *fitz_alloc_context = NULL;
fz_context *fitz_context = NULL;


int get_descriptor_from_file_descriptor(JNIEnv *env, jobject this);


void apv_log_print(const char *file, int line, int level, const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    char buf[1024] = "";
    snprintf(buf, sizeof buf, "%s:%d: %s", file, line, fmt); /* TODO: fails if file contains '%' (which should be never, but) */
    __android_log_vprint(level, PDFVIEW_LOG_TAG, buf, args);
    va_end(args);
}


JavaVM *apv_get_cached_jvm() {
    return cached_jvm;
}


JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *jvm, void *reserved) {
    __android_log_print(ANDROID_LOG_INFO, PDFVIEW_LOG_TAG, "JNI_OnLoad");
    cached_jvm = jvm;
    return JNI_VERSION_1_4;
}


JNIEXPORT void JNICALL
Java_cx_hell_android_lib_pdf_PDF_init(
        JNIEnv *env,
        jobject this,
        jint max_store) {
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "jni init");
    if (apv_alloc_state != NULL) {
        __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "apv_alloc_state is not NULL");
    } else {
        apv_alloc_state = malloc(sizeof(apv_alloc_state_t));
        apv_alloc_state->current_size = 0;
        apv_alloc_state->max_size = max_store;
#ifndef NDEBUG
        apv_alloc_state->peak_size = 0;
        apv_alloc_state->magic = rand();
#endif
    }
    if (fitz_alloc_context != NULL) {
        __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "fitz_alloc_context is not NULL");
    } else {
        fitz_alloc_context = malloc(sizeof(fz_alloc_context));
        fitz_alloc_context->user = apv_alloc_state;
        fitz_alloc_context->malloc = apv_malloc;
        fitz_alloc_context->realloc = apv_realloc;
        fitz_alloc_context->free = apv_free;
    }
    if (fitz_context != NULL) {
        __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "fitz_context is not NULL");
    } else {
        // fz_context *fz_new_context(fz_alloc_context *alloc, fz_locks_context *locks, unsigned int max_store);
        __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "creating fitz_context with max_store: %d", (int)max_store);
        fitz_context = fz_new_context(fitz_alloc_context, NULL, max_store);
        if (fitz_context == NULL) {
            __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "failed to create fitz_context"); // TODO: display error to user
        }
    }
}


JNIEXPORT void JNICALL
Java_cx_hell_android_lib_pdf_PDF_deinit(
        JNIEnv *env,
        jobject this) {
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "jni deinit");
    /* TODO: free context, alloc_state and alloc_context */
}


/**
 * Implementation of native method PDF.parseFile.
 * Opens file and parses at least some bytes - so it could take a while.
 * @param file_name file name to parse.
 */
JNIEXPORT void JNICALL
Java_cx_hell_android_lib_pdf_PDF_parseFile(
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
    pdf = parse_pdf_file(c_file_name, 0, c_password, fitz_context, fitz_alloc_context, apv_alloc_state);

    if (pdf != NULL && pdf->invalid_password) {
        (*env)->SetIntField(env, jthis, invalid_password_field_id, 1);
        free_pdf_t(pdf);
        pdf = NULL;
    } else {
        (*env)->SetIntField(env, jthis, invalid_password_field_id, 0);
    }

    if (pdf != NULL) {
        if (box_type >= 0 && box_type < NUM_BOXES) {
            strcpy(pdf->box, boxes[box_type]);
        } else {
            strcpy(pdf->box, "CropBox");
        }
    }

    (*env)->ReleaseStringUTFChars(env, file_name, c_file_name);
    (*env)->ReleaseStringUTFChars(env, password, c_password);
    (*env)->SetIntField(env, jthis, pdf_field_id, (int)pdf);

    maybe_free_cache(pdf);
}


/**
 * Create pdf_t struct from opened file descriptor.
 */
JNIEXPORT void JNICALL
Java_cx_hell_android_lib_pdf_PDF_parseFileDescriptor(
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
	pdf = parse_pdf_file(NULL, fileno, c_password, fitz_context, fitz_alloc_context, apv_alloc_state);

    if (pdf != NULL && pdf->invalid_password) {
       (*env)->SetIntField(env, jthis, invalid_password_field_id, 1);
       free_pdf_t(pdf);
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
Java_cx_hell_android_lib_pdf_PDF_getPageCount(
		JNIEnv *env,
		jobject this) {
	pdf_t *pdf = NULL;
    pdf = get_pdf_from_this(env, this);
	if (pdf == NULL) {
        // __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "pdf is null");
        return -1;
    }
	return fz_count_pages(pdf->doc);
}


JNIEXPORT jintArray JNICALL
Java_cx_hell_android_lib_pdf_PDF_renderPage(
        JNIEnv *env,
        jobject this,
        jint pageno,
        jint zoom,
        jint left,
        jint top,
        jint rotation,
        jboolean skipImages,
        jobject size) {
    jintArray jints; /* return value */
    int *jbuf = NULL; /* points to jints internal array */
    pdf_t *pdf = NULL; /* parsed pdf data, extracted from java's "this" object */
    int width = 0;
    int height = 0;
    int num_pixels = 0;
    fz_pixmap *image = NULL;

    get_size(env, size, &width, &height);

    /*
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "jni renderPage(pageno: %d, zoom: %d, left: %d, top: %d, width: %d, height: %d) start",
            (int)pageno, (int)zoom,
            (int)left, (int)top,
            (int)width, (int)height);
    */

    pdf = get_pdf_from_this(env, this);

    APV_LOG_PRINT(APV_LOG_DEBUG, "rendering page %d", pageno);
    image = get_page_image_bitmap(pdf, pageno, zoom, left, top, rotation, skipImages, width, height);
    num_pixels = fz_pixmap_width(pdf->ctx, image) * fz_pixmap_height(pdf->ctx, image);
    jints = (*env)->NewIntArray(env, num_pixels);
	jbuf = (*env)->GetIntArrayElements(env, jints, NULL);
    memcpy(jbuf, fz_pixmap_samples(pdf->ctx, image), num_pixels * 4);
    (*env)->ReleaseIntArrayElements(env, jints, jbuf, 0);
    width = fz_pixmap_width(pdf->ctx, image);
    height = fz_pixmap_height(pdf->ctx, image);
    fz_drop_pixmap(pdf->ctx, image);

    if (jints != NULL)
        save_size(env, size, width, height);

    APV_LOG_PRINT(APV_LOG_DEBUG, "rendered page, width: %d, height: %d", width, height);

    maybe_free_cache(pdf);

    return jints;
}


JNIEXPORT jint JNICALL
Java_cx_hell_android_lib_pdf_PDF_getPageSize(
        JNIEnv *env,
        jobject this,
        jint pageno,
        jobject size) {
    int width, height, error;
    pdf_t *pdf = NULL;

    pdf = get_pdf_from_this(env, this);
    if (pdf == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "this.pdf is null");
        return 1;
    }

    error = get_page_size(pdf, pageno, &width, &height);
    if (error != 0) {
        __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "get_page_size error: %d", (int)error);
        return 2;
    }

    // APV_LOG_PRINT(APV_LOG_DEBUG, "page size %d: %d %d", pageno, width, height);

    save_size(env, size, width, height);

    maybe_free_cache(pdf);

    return 0;
}


// #ifdef pro
// /**
//  * Get document outline.
//  */
// JNIEXPORT jobject JNICALL
// Java_cx_hell_android_lib_pdf_PDF_getOutlineNative(
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
//     outline = fz_load_outline(pdf->doc);
//     if (outline == NULL) return NULL;
// 
//     /* recursively copy fz_outline to PDF.Outline */
//     /* TODO: rewrite pdf_load_outline to create Java's PDF.Outline objects directly */
//     joutline = create_outline_recursive(env, NULL, outline);
//     // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "joutline converted");
//     return joutline;
// }
// #endif


/**
 * Get current netto heap size.
 */
JNIEXPORT jint JNICALL
Java_cx_hell_android_lib_pdf_PDF_getHeapSize(
        JNIEnv *env,
        jobject this) {
    pdf_t *pdf = NULL;
    jclass this_class = (*env)->GetObjectClass(env, this);
    jfieldID pdf_field_id = (*env)->GetFieldID(env, this_class, "pdf_ptr", "I");
    pdf = (pdf_t*) (*env)->GetIntField(env, this, pdf_field_id);
    return pdf->alloc_state->current_size;
}


/**
 * Free resources allocated in native code.
 * Frees memory directly associated with this pdf_t instance. Does not destroy fitz_context.
 */
JNIEXPORT void JNICALL
Java_cx_hell_android_lib_pdf_PDF_freeMemory(
        JNIEnv *env,
        jobject this) {
    pdf_t *pdf = NULL;
	jclass this_class = (*env)->GetObjectClass(env, this);
	jfieldID pdf_field_id = (*env)->GetFieldID(env, this_class, "pdf_ptr", "I");

	pdf = (pdf_t*) (*env)->GetIntField(env, this, pdf_field_id);
	(*env)->SetIntField(env, this, pdf_field_id, 0);
    if (pdf) {
        free_pdf_t(pdf);
        pdf = NULL;
    }

#ifndef NDEBUG
    APV_LOG_PRINT(APV_LOG_DEBUG, "jni freeMemory: current size: %d, peak size: %d", apv_alloc_state->current_size, apv_alloc_state->peak_size);
#endif
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


/* TODO: Specialcase searches for 7-bit text to make them faster */
JNIEXPORT jobject JNICALL
Java_cx_hell_android_lib_pdf_PDF_find(
        JNIEnv *env,
        jobject this,
        jstring text,
        jint pageno,
        jint rotation) {
    int i = 0;
    pdf_t *pdf = NULL;
    const jchar *jtext = NULL;
    wchar_t *ctext = NULL;
    size_t needle_len = 0;
    jboolean is_copy;
    jobject results = NULL;
    fz_rect pagebox;
    fz_page *page = NULL;
    fz_text_sheet *text_sheet = NULL;
    fz_text_page *text_page = NULL;  /* contains text */
    fz_device *dev = NULL;
    wchar_t *textlinechars = NULL;
    size_t textlinechars_len = 0; /* not including \0 */
    fz_rect *textlineboxes = NULL; /* array of boxes of textlinechars, has textlinechars_len elements */
    wchar_t *found = NULL;
    jobject find_result = NULL;
    fz_text_block *text_block = NULL;
    fz_page_block *page_block = NULL;
    fz_text_line *text_line = NULL;
    fz_text_span *text_span = NULL;
    fz_text_char *text_char = NULL;
    int block_no = 0;
    int line_no = 0;
    int char_no = 0;
    fz_cookie cookie = { 0 };

    jtext = (*env)->GetStringChars(env, text, &is_copy);

    if (jtext == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "text cannot be null");
        (*env)->ReleaseStringChars(env, text, jtext);
        return NULL;
    }

    needle_len = (*env)->GetStringLength(env, text);

    ctext = malloc((needle_len + 1) * sizeof(wchar_t));
    for (i=0; i<needle_len; i++) {
        ctext[i] = towlower(jtext[i]);
    }
    ctext[needle_len] = 0; /* This will be needed if wcsstr() ever starts to work */

    pdf = get_pdf_from_this(env, this);

    page = fz_load_page(pdf->doc, pageno);
    text_sheet = fz_new_text_sheet(pdf->ctx);
    text_page = fz_new_text_page(pdf->ctx, fz_bound_page(pdf->doc, page, &pagebox));
    dev = fz_new_text_device(pdf->ctx, text_sheet, text_page);
    fz_run_page(pdf->doc, page, dev, &fz_identity, &cookie);
    fz_free_device(dev);
    dev = NULL;

    #ifndef NDEBUG
    APV_LOG_PRINT(APV_LOG_DEBUG, "%d blocks on page on page %d", text_page->len, pageno);
    #endif

    /* search text_page by extracting wchar_t text for each line */
    for(block_no = 0; block_no < text_page->len; ++block_no) {  /* for each page block */
        // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "checking block %d of %d", block_no, text_page->len);
        page_block = &(text_page->blocks[block_no]);
        if (page_block->type == FZ_PAGE_BLOCK_TEXT) {
            text_block = page_block->u.text;
        } else {
            #ifndef NDEBUG
            APV_LOG_PRINT(APV_LOG_WARN, "block %d is not text", block_no);
            #endif
            continue;
        }
        for(line_no = 0; line_no < text_block->len; ++line_no) {  /* for each line */
            // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "checking line %d of %d", line_no, text_block->len);
            text_line = &(text_block->lines[line_no]);
            /* cound chars in line */
            textlinechars_len = 0;
            for (text_span = text_line->first_span; text_span; text_span = text_span->next) {
                textlinechars_len += text_span->len;
            }
            // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "text line chars len: %d", textlinechars_len);
            textlinechars = (wchar_t*)malloc((textlinechars_len + 1) * sizeof(wchar_t));
            textlineboxes = (fz_rect*)malloc(textlinechars_len * sizeof(fz_rect));
            /* copy chars and boxes */
            char_no = 0;
            for (text_span = text_line->first_span; text_span; text_span = text_span->next) {
                int span_char_no = 0;
                for(span_char_no = 0; span_char_no < text_span->len; ++span_char_no) {
                    fz_rect bbox;
                    textlinechars[char_no] = towlower(text_span->text[span_char_no].c);
                    fz_text_char_bbox(&bbox, text_span, span_char_no);
                    textlineboxes[char_no] = bbox;
                    char_no += 1;
                }
            }
            textlinechars[textlinechars_len] = 0;

            // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "copied textlinechars");
            found = widestrstr(textlinechars, textlinechars_len, ctext, needle_len);
            // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "widestrstr result: %d", (int)found);
            if (found) {
                int i = 0; /* used for char in textlinechars */
                int i0 = 0, i1 = 0; /* indexes of match in textlinechars */
                find_result = create_find_result(env);
                set_find_result_page(env, find_result, pageno);
                fz_rect charbox;
                i0 = found - textlinechars;
                i1 = i0 + needle_len;
                // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "copying chars from %d to %d", i0, i1);
                for(i = i0; i < i1; ++i) {
                    charbox = textlineboxes[i];
                    convert_box_pdf_to_apv(pdf, pageno, rotation, &charbox);
                    add_find_result_marker(env, find_result,
                            charbox.x0, charbox.y0,
                            charbox.x1, charbox.y1);
                }
                // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "adding find result to list");
                add_find_result_to_list(env, &results, find_result);
                // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "added find result to list");
            }

            // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "freeing textlinechars and textlineboxes");
            free(textlinechars);
            textlinechars = NULL;
            free(textlineboxes);
            textlineboxes = NULL;
        }
    }

    if (page) {
        page = NULL;
    }

    if (text_sheet) {
        fz_free_text_sheet(pdf->ctx, text_sheet);
        text_sheet = NULL;
    }

    if (text_page) {
        fz_free_text_page(pdf->ctx, text_page);
        text_page = NULL;
    }

    free(ctext);
    (*env)->ReleaseStringChars(env, text, jtext);
    
    return results;
}




// #ifdef pro
// /**
//  * Return text of given page.
//  */
// JNIEXPORT jobject JNICALL
// Java_cx_hell_android_lib_pdf_PDF_getText(
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
        // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "creating ArrayList");
        list_class = (*env)->FindClass(env, "java/util/ArrayList");
        if (list_class == NULL) {
            __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "couldn't find class java/util/ArrayList");
            return;
        }
        list_constructor_id = (*env)->GetMethodID(env, list_class, "<init>", "()V");
        if (!list_constructor_id) {
            __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "couldn't find ArrayList constructor");
            return;
        }
        *list = (*env)->NewObject(env, list_class, list_constructor_id);
        if (*list == NULL) {
            __android_log_print(ANDROID_LOG_ERROR, PDFVIEW_LOG_TAG, "failed to create ArrayList: NewObject returned NULL");
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

    // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "calling ArrayList.add");
    (*env)->CallBooleanMethod(env, *list, list_add_method_id, find_result);
    // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "add_find_result_to_list done");
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
    // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "trying to set find results page number");
    if (jni_ids_cached == 0) {
        jclass findResultClass = (*env)->GetObjectClass(env, findResult);
        page_field_id = (*env)->GetFieldID(env, findResultClass, "page", "I");
        jni_ids_cached = 1;
    }
    (*env)->SetIntField(env, findResult, page_field_id, page);
    // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "find result page number set");
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
    if (!field_is_cached) {
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
    if (!this) {
        APV_LOG_PRINT(APV_LOG_WARN, "can't get file descriptor from null");
        return -1;
    }
    if (!is_cached) {
        jclass this_class = (*env)->GetObjectClass(env, this);
        field_id = (*env)->GetFieldID(env, this_class, "descriptor", "I");
        is_cached = 1;
        __android_log_print(ANDROID_LOG_DEBUG, "cx.hell.android.pdfview", "cached descriptor field id %d", (int)field_id);
    }
    // __android_log_print(ANDROID_LOG_DEBUG, "cx.hell.android.pdfview", "will get descriptor field...");
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
//     if (outline->dest.kind == FZ_LINK_GOTO) {
//         (*env)->SetIntField(env, joutline, page_field_id, outline->dest.ld.gotor.page);
//     } else {
//         __android_log_print(ANDROID_LOG_WARN, PDFVIEW_LOG_TAG, "outline contains non-GOTO link");
//         (*env)->SetIntField(env, joutline, page_field_id, -1);
//     }
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


/* vim: set sts=4 ts=4 sw=4 et: */

