
#define _GNU_SOURCE
#include <string.h>
#include <wctype.h>

#include "apvcore.h"

#include "mupdf-internal.h"


/* We're manipulating internal xref structures which is bad, but I didn't find any better way yet */
/* Because of that, we need definitions of those internal structures. */
/* Those definitions must match exactly those defined in mupdf .c files. */


typedef enum pdf_objkind_e
{
   PDF_NULL,
   PDF_BOOL,
   PDF_INT,
   PDF_REAL,
   PDF_STRING,
   PDF_NAME,
   PDF_ARRAY,
   PDF_DICT,
   PDF_INDIRECT
} pdf_objkind;


struct pdf_obj_s
{
   int refs;
   pdf_objkind kind;
   fz_context *ctx;
   union
   {
       int b;
       int i;
       float f;
       struct {
           unsigned short len;
           char buf[1];
       } s;
       char n[1];
       struct {
           int len;
           int cap;
           pdf_obj **items;
       } a;
       struct {
           char sorted;
           char marked;
           int len;
           int cap;
           struct keyval *items;
       } d;
       struct {
           int num;
           int gen;
           struct pdf_xref_s *xref;
       } r;
   } u;
};



void *apv_malloc(void *user, unsigned int size) {
    apv_alloc_state_t *state = user;
    // fprintf(stderr, "aptn_malloc: current size %u, max size %u, asked for %u\n", conf->current_size, conf->max_size, size);
    if (state->max_size > 0 && state->current_size + size > state->max_size) {
        // fprintf(stderr, "aptn_malloc: failing because of limit\n");
        APV_LOG_PRINT(APV_LOG_WARN, "refusing to allocate %d bytes, current_size: %d, max_size: %d", size, state->current_size, state->max_size);
        return NULL;
    } else {
        void *buf = NULL;
        apv_alloc_header_t *header= NULL;
        buf = malloc(size + sizeof(apv_alloc_header_t));
        if (buf == NULL) {
            return NULL;
        }
        header = buf;
        header->size = size;
        state->current_size += size;
#ifndef NDEBUG
        header->magic = state->magic;
        if (state->current_size > state->peak_size) {
            state->peak_size = state->current_size;
            if (rand() % 10000 < 10) {
                APV_LOG_PRINT(APV_LOG_DEBUG, "apv_malloc: peak size is now %d", state->peak_size);
            }
        }
#endif
        // fprintf(stderr, "info addr: %p, buf addr: %p\n", info, info + sizeof(alloc_info_t));
        return buf + sizeof(apv_alloc_header_t);
    }
}


void *apv_realloc(void *user, void *old, unsigned int size) {
    if (old == NULL && size > 0) {
        return apv_malloc(user, size);
    } else if (old != NULL && size == 0) {
        apv_free(user, old);
        return NULL;
    } else {
        apv_alloc_state_t *state = user;
        apv_alloc_header_t *header = NULL;
        int change = 0;
        void *buf = NULL;
        buf = old - sizeof(apv_alloc_header_t);
        header = buf;
#ifndef NDEBUG
        if (header->magic != state->magic) {
            APV_LOG_PRINT(APV_LOG_ERROR, "apv_realloc: magic does not match");
            abort();
        }
#endif
        // fprintf(stderr, "aptn_realloc: old size: %u, asked for: %u, current size: %u\n", info->size, size, conf->current_size);
        change = size - header->size;
        if (state->max_size > 0) {
            if (change > 0) {
                if (state->current_size + change > state->max_size) {
                    /* too much, simulate fail */
                    APV_LOG_PRINT(APV_LOG_WARN, "refusing to reallocate %d to %d, current_size: %d, max_size: %d", header->size, size, state->current_size, state->max_size);
                    state->current_size -= header->size;
                    free(buf);
                    return NULL;
                }
            }
        }
        /* didn't exceed, do realloc */
        buf = realloc(buf, size + sizeof(apv_alloc_header_t));
        header = buf; /* possibly moved by realloc */
        header->size = size;
        state->current_size += change;
#ifndef NDEBUG
        if (state->current_size > state->peak_size) {
            state->peak_size = state->current_size;
        }
#endif
        return buf + sizeof(apv_alloc_header_t);
    }
}


void apv_free(void *user, void *ptr) {
    if (ptr) {
        apv_alloc_state_t *state = user;
        apv_alloc_header_t *header = NULL;
        void *buf = ptr - sizeof(apv_alloc_header_t);
        header = buf;
#ifndef NDEBUG
        if (header->magic != state->magic) {
            APV_LOG_PRINT(APV_LOG_ERROR, "apv_free: magic does not match");
            abort();
        }
#endif
        if (state->current_size < header->size) {
            abort();
        }
        // fprintf(stderr, "aptn_free: ptr: %p, info: %p, size to free: %u, current size: %u\n", ptr, info, info->size, conf->current_size);
        state->current_size -= header->size;
        free(buf);
    }
}


const char boxes[NUM_BOXES][MAX_BOX_NAME+1] = {
    "ArtBox",
    "BleedBox",
    "CropBox",
    "MediaBox",
    "TrimBox"
};


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


/**
 * pdf_t "constructor": create empty pdf_t with default values.
 * @return newly allocated pdf_t struct with fields set to default values
 */
pdf_t* create_pdf_t(fz_context *context, fz_alloc_context *alloc_context, apv_alloc_state_t *alloc_state) {
    pdf_t *pdf = NULL;
    
#ifndef NDEBUG
    /* simple assert based on apv_alloc_state->magic */
    if (alloc_context && alloc_state) {
        if (((apv_alloc_state_t*)alloc_context->user)->magic != alloc_state->magic) {
            APV_LOG_PRINT(APV_LOG_ERROR, "magic does not match");
            return NULL;
        }
    }
#endif

    pdf = malloc(sizeof(pdf_t));

    pdf->ctx = context;
    pdf->alloc_context = alloc_context;
    pdf->alloc_state = alloc_state;
    pdf->doc = NULL;
    pdf->fileno = -1;
    pdf->invalid_password = 0;

    pdf->box[0] = 0;
    
    return pdf;
}


/**
 * free pdf_t
 */
void free_pdf_t(pdf_t *pdf) {
    if (pdf->doc) {
        fz_close_document(pdf->doc);
        pdf->doc = NULL;
    }
    /* pdf->ctx is a "reference" pointer */
    pdf->ctx = NULL;
    /* pdf->alloc_state is a "reference" pointer */
    pdf->alloc_state = NULL;
    free(pdf);
}


/**
 * Free some of the xref cache table.
 * All pdf_t instances share alloc_state, so chances are that we'll not succeed
 * to free enough bytes, because other pdf_t instances could hold too much
 * memory. But we still try, it's better than nothing.
 */
void maybe_free_cache(pdf_t *pdf) {
    if (pdf->alloc_state == NULL) {
        APV_LOG_PRINT(APV_LOG_WARN, "pdf->alloc_state is NULL, can't free memory");
        return;
    }
    if (!pdf->alloc_state->max_size > 0) {
        APV_LOG_PRINT(APV_LOG_DEBUG, "max_size is not set, will not free");
        return;
    }
    if (pdf->alloc_state->current_size > pdf->alloc_state->max_size / 2) {
        int i = 0;
        int old_size = 0;
        pdf_document *xref = NULL;

        old_size = pdf->alloc_state->current_size;
        xref = (pdf_document*)pdf->doc;

        for(i = 0; i < xref->len; ++i) {
            if (xref->table[i].obj && xref->table[i].obj->refs == 1) {
                // APV_LOG_PRINT(APV_LOG_DEBUG, "xref entry %d refs %d", i, xref->table[i].obj->refs);
                pdf_drop_obj(xref->table[i].obj);
                xref->table[i].obj = NULL;
            }
            if (pdf->alloc_state->current_size < pdf->alloc_state->max_size / 8) {
                break; /* enough freed */
            }
        }
#ifndef NDEBUG
        APV_LOG_PRINT(APV_LOG_DEBUG, "reduced alloc size from %d to %d (max_size: %d)",
            old_size, pdf->alloc_state->current_size, pdf->alloc_state->max_size);
#endif
    } else {
#ifndef NDEBUG
        APV_LOG_PRINT(APV_LOG_DEBUG, "current_size (%d) is less than 1/2 of max_size (%d), no need to free",
            pdf->alloc_state->current_size, pdf->alloc_state->max_size);
#endif
    }
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
 * Params:
 * - alloc_context - shared alloc context initialized on app start.
 */
pdf_t* parse_pdf_file(const char *filename, int fileno, const char* password, fz_context *context, fz_alloc_context *alloc_context, apv_alloc_state_t *alloc_state) {
    pdf_t *pdf;
    fz_stream *stream = NULL;

    // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "parse_pdf_file(%s, %d)", filename, fileno);

    pdf = create_pdf_t(context, alloc_context, alloc_state);

    if (filename) {
        stream = fz_open_file(pdf->ctx, (char*)filename);
    } else {
        stream = fz_open_fd(pdf->ctx, fileno);
    }
    pdf->doc = (fz_document*) pdf_open_document_with_stream(context, stream);
    fz_close(stream); /* pdf->doc holds ref */

    pdf->invalid_password = 0;

    if (fz_needs_password(pdf->doc)) {
        int authenticated = 0;
        authenticated = fz_authenticate_password(pdf->doc, (char*)password);
        if (!authenticated) {
            /* TODO: ask for password */
            APV_LOG_PRINT(APV_LOG_ERROR, "failed to authenticate");
            pdf->invalid_password = 1;
            return pdf;
        }
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
 * Get part of page as bitmap.
 * Parameters left, top, width and height are interprted after scalling, so if
 * we have 100x200 page scalled by 25% and request 0x0 x 25x50 tile, we should
 * get 25x50 bitmap of whole page content. pageno is 0-based.
 * Returns fz_image that needs to be freed by caller.
 */
fz_pixmap *get_page_image_bitmap(
        pdf_t *pdf,
        int pageno, int zoom_pmil,
        int left, int top, int rotation,
        int skipImages,
        int width, int height) {
    fz_matrix ctm;
    double zoom;
    fz_irect bbox;
    fz_page *page = NULL;
    fz_pixmap *image = NULL;
    fz_device *dev = NULL;
    static int runs = 0;

    // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "get_page_image_bitmap(pageno: %d) start", (int)pageno);

    zoom = (double)zoom_pmil / 1000.0;

    if (pdf->last_pageno != pageno) {
        pdf->last_pageno = pageno;
    }

    page = fz_load_page(pdf->doc, pageno);
    if (!page) return NULL; /* TODO: handle/propagate errors */

    fz_rect pagebox = get_page_box(pdf, pageno);

    /* translate coords to apv coords so we can easily cut out our tile */
    ctm = fz_identity;
    /* ctm = fz_concat(ctm, fz_scale(zoom, zoom)); */
    fz_scale(&ctm, zoom, zoom);
    if (rotation != 0) {
        // ctm = fz_concat(ctm, fz_rotate(-rotation * 90));
        fz_rotate(&ctm, -rotation * 90);
    }
    // bbox = fz_round_rect(fz_transform_rect(ctm, pagebox));
    fz_transform_rect(&pagebox, &ctm);
    fz_round_rect(&bbox, &pagebox);

    /* now bbox holds page after transform, but we only need tile at (left,right) from top-left corner */
    bbox.x0 = bbox.x0 + left;
    bbox.y0 = bbox.y0 + top;
    bbox.x1 = bbox.x0 + width;
    bbox.y1 = bbox.y0 + height;

    image = fz_new_pixmap_with_bbox(pdf->ctx, fz_device_bgr(pdf->ctx), &bbox);
    fz_clear_pixmap_with_value(pdf->ctx, image, 0xff);
    dev = fz_new_draw_device(pdf->ctx, image);

    if (skipImages)
        dev->hints |= FZ_IGNORE_IMAGE;

    fz_run_page(pdf->doc, page, dev, &ctm, NULL);

	fz_free_page(pdf->doc, page);
    fz_free_device(dev);

    /*
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "got image %d x %d, asked for %d x %d",
            fz_pixmap_width(pdf->ctx, image), fz_pixmap_height(pdf->ctx, image),
            *width, *height);
    */

    runs += 1;
    return image;
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
    fz_rect rect = get_page_box(pdf, pageno);
    *width = rect.x1 - rect.x0;
    *height = rect.y1 - rect.y0;
    // APV_LOG_PRINT(APV_LOG_DEBUG, "get_page_size(%d) -> %d %d", pageno, *width, *height);
    return 0;
}


/**
 * Get page box.
 */
fz_rect get_page_box(pdf_t *pdf, int pageno) {
    fz_rect box;
    fz_page *page = NULL;
    if (pdf->box && pdf->box[0] && strcmp(pdf->box, "MediaBox") != 0) {
        /* only get box this way if pdf->box and pdf->box != "MediaBox" */
        // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "getting page box using pdf_dict_gets (pdf->box: %s)", pdf->box);
        pdf_obj *pageobj = NULL;
        pdf_obj *obj = NULL;
        pdf_document *xref = NULL;
        xref = (pdf_document*)pdf->doc;
        pageobj = xref->page_objs[pageno];
        obj = pdf_dict_gets(pageobj, pdf->box);
        if (obj && pdf_is_array(obj)) {
            pdf_to_rect(pdf->ctx, obj, &box);
            obj = pdf_dict_gets(pageobj, "UserUnit");
            if (pdf_is_real(obj)) {
                float unit = pdf_to_real(obj);
                box.x0 *= unit;
                box.y0 *= unit;
                box.x1 *= unit;
                box.y1 *= unit;
            }
            return box;
        } else {
            // APV_LOG_PRINT(APV_LOG_DEBUG, "box not found %s", pdf->box);
        }
    }

    /* default box */
    // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "getting page box using fz_bound_page (pdf->box: %s)", pdf->box);
    page = fz_load_page(pdf->doc, pageno);
    if (!page) {
        APV_LOG_PRINT(APV_LOG_ERROR, "fz_load_page(..., %d) -> NULL", pageno);
        return box;
    }
    fz_bound_page(pdf->doc, page, &box);
    fz_free_page(pdf->doc, page);
    /*
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG,
            "got page %d box: %.2f %.2f %.2f %.2f",
            pageno, box.x0, box.y0, box.x1, box.y1);
    */

    return box;
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
 * This function has to get page box relative to which bbox is located.
 * This function should not allocate any memory.
 * @return error code, 0 means ok
 */
int convert_box_pdf_to_apv(pdf_t *pdf, int page, int rotation, fz_rect *bbox) {
    fz_rect page_bbox;
    fz_rect param_bbox;
    // float height = 0;
    // float width = 0;

    /*
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG,
            "convert_box_pdf_to_apv(page: %d, bbox: %.2f %.2f %.2f %.2f)",
            page, bbox->x0, bbox->y0, bbox->x1, bbox->y1);
    */

    param_bbox = *bbox;

    page_bbox = get_page_box(pdf, page);
    // __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "page bbox is %.1f, %.1f, %.1f, %.1f", page_bbox.x0, page_bbox.y0, page_bbox.x1, page_bbox.y1);

    if (rotation != 0) {
        fz_matrix m;
        fz_rotate(&m, -rotation * 90);
        fz_transform_rect(&param_bbox, &m);
        fz_transform_rect(&page_bbox, &m);
    }

    //__android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "after rotate page bbox is: %.1f, %.1f, %.1f, %.1f", page_bbox.x0, page_bbox.y0, page_bbox.x1, page_bbox.y1);
    //__android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG, "after rotate param bbox is: %.1f, %.1f, %.1f, %.1f", param_bbox.x0, param_bbox.y0, param_bbox.x1, param_bbox.y1);

    /* set result: param bounding box relative to left-top corner of page bounding box */

    bbox->x0 = MIN(param_bbox.x0, param_bbox.x1) - MIN(page_bbox.x0, page_bbox.x1);
    bbox->y0 = MIN(param_bbox.y0, param_bbox.y1) - MIN(page_bbox.y0, page_bbox.y1);
    bbox->x1 = MAX(param_bbox.x0, param_bbox.x1) - MIN(page_bbox.x0, page_bbox.x1);
    bbox->y1 = MAX(param_bbox.y0, param_bbox.y1) - MIN(page_bbox.y0, page_bbox.y1);

    /*
    width = ABS(page_bbox.x0 - page_bbox.x1);
    height = ABS(page_bbox.y0 - page_bbox.y1);

    bbox->x0 = (MIN(param_bbox.x0, param_bbox.x1) - MIN(page_bbox.x0, page_bbox.x1));
    bbox->y1 = height - (MIN(param_bbox.y0, param_bbox.y1) - MIN(page_bbox.y0, page_bbox.y1));
    bbox->x1 = (MAX(param_bbox.x0, param_bbox.x1) - MIN(page_bbox.x0, page_bbox.x1));
    bbox->y0 = height - (MAX(param_bbox.y0, param_bbox.y1) - MIN(page_bbox.y0, page_bbox.y1));
    */

    /*
    __android_log_print(ANDROID_LOG_DEBUG, PDFVIEW_LOG_TAG,
            "result after transformations: %.2f, %.2f, %.2f, %.2f",
            bbox->x0, bbox->y0, bbox->x1, bbox->y1);
    */

    return 0;
}


void append_chars(char **buf, size_t *buf_size, const char *new_chars, size_t new_chars_len) {
    /*
    __android_log_print(ANDROID_LOG_DEBUG,
            PDFVIEW_LOG_TAG,
            "appending chars %d chars, current buf len: %d, current buf size: %d",
            (int)new_chars_len,
            *buf != NULL ? (int)strlen(*buf) : -1,
            (int)*buf_size);
    */
    if (*buf == NULL) {
        *buf = (char*)malloc(256);
        (*buf)[0] = 0;
        *buf_size = 256;
    }

    size_t new_len = strlen(*buf) + new_chars_len; /* new_len is number of chars, new_len+1 is min buf size */
    if (*buf_size < (new_len + 1)) {
        size_t new_size = 0; 
        char *new_buf = NULL; 
        new_size = (new_len + 3) * 1.5;
        new_buf = (char*)realloc(*buf, new_size);
        *buf_size = new_size;
        *buf = new_buf;
    }
    /* now buf has space for new_char_len new_chars */
    strncat(*buf, new_chars, new_chars_len);
}


// #ifdef pro
// /**
//  * Extract text from given pdf page.
//  * Returns dynamically allocated string to be freed by caller or NULL.
//  */
// char* extract_text(pdf_t *pdf, int pageno) {
//     fz_device *dev = NULL;
//     fz_page *page = NULL;
//     fz_text_sheet *text_sheet = NULL;
//     fz_text_page *text_page = NULL;
//     int block_no = 0;
//     int line_no = 0;
//     int span_no = 0;
//     int char_no = 0;
//     char runechars[128] = "";
//     int runelen = 0;
//     fz_buffer *buf = NULL;
//     fz_output *out = NULL;
//     fz_cookie cookie = { 0 };
//     fz_rect pagebox;
// 
//     size_t text_buf_size = 0;
//     char *text = NULL; /* utf-8 text */
// 
//     if (pdf == NULL) {
//         APV_LOG_PRINT(APV_LOG_ERROR, "extract_text: pdf is NULL");
//         return NULL;
//     }
// 
//     page = fz_load_page(pdf->doc, pageno);
//     text_sheet = fz_new_text_sheet(pdf->ctx);
//     text_page = fz_new_text_page(pdf->ctx, fz_bound_page(pdf->doc, page, &pagebox));
//     dev = fz_new_text_device(pdf->ctx, text_sheet, text_page);
//     fz_run_page(pdf->doc, page, dev, &fz_identity, &cookie);
//     fz_free_device(dev);
//     dev = NULL;
//     buf = fz_new_buffer(pdf->ctx, 512);
//     out = fz_new_output_with_buffer(pdf->ctx, buf);
// 
//     fz_print_text_page(pdf->ctx, out, text_page);
// 
//     fz_free_text_page(pdf->ctx, text_page);
//     fz_free_text_sheet(pdf->ctx, text_sheet);
//     fz_close_output(out);  /* frees fz_output, but not buf */
//     text = strndup(buf->data, buf->len);
//     fz_drop_buffer(pdf->ctx, buf);  /* free both buf->data and buf itself */
// 
//     return text;
// }
// #endif



/* vim: set sts=4 ts=4 sw=4 et: */

