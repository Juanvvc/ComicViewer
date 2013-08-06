

#ifdef APVCORE_H__
#error APVCORE_H__ can be included only once
#endif

#define APVCORE_H__


#include "fitz.h"
#include "mupdf.h"

#define MAX_BOX_NAME 8
#define NUM_BOXES 5

#define ABS(x) ((x) < 0 ? -(x) : (x))
#define MIN(x,y) ((x) < (y) ? (x) : (y))
#define MAX(x,y) ((x) > (y) ? (x) : (y))


extern const char boxes[NUM_BOXES][MAX_BOX_NAME+1];


/**
 * Custom allocator state.
 */
typedef struct {
#ifndef NDEBUG
    int magic;
    int peak_size;
#endif
    int max_size;
    int current_size;
} apv_alloc_state_t;


/**
 * Custom allocator block header.
 */
typedef struct {
#ifndef NDEBUG
    int magic;
#endif
    int size;
} apv_alloc_header_t;


/**
 * Holds pdf info.
 */
typedef struct {
    int last_pageno;
    fz_context *ctx;
    fz_document *doc;
    int fileno; /* used only when opening by file descriptor */
    int invalid_password;
    char box[MAX_BOX_NAME + 1];
    fz_alloc_context *alloc_context;
    apv_alloc_state_t *alloc_state;
} pdf_t;


/*
 * Declarations
 */

#ifndef APV_LOG_PRINT
#   define APV_LOG_PRINT(level, ...) apv_log_print(__FILE__, __LINE__, level, __VA_ARGS__)
#endif
#define APV_LOG_DEBUG 3
#define APV_LOG_WARN 5
#define APV_LOG_ERROR 6
void apv_log_print(const char *file, int line, int level, const char *fmt, ...);

void *apv_malloc(void *user, unsigned int size);
void *apv_realloc(void *user, void *old, unsigned int size);
void apv_free(void *user, void *ptr);

pdf_t* create_pdf_t(fz_context *ctx, fz_alloc_context *alloc_context, apv_alloc_state_t *alloc_state);
void free_pdf_t(pdf_t *pdf);
void maybe_free_cache(pdf_t *pdf);
pdf_t* parse_pdf_file(const char *filename, int fileno, const char* password, fz_context *context, fz_alloc_context *alloc_context, apv_alloc_state_t *alloc_state);
void fix_samples(unsigned char *bytes, unsigned int w, unsigned int h);
void rgb_to_alpha(unsigned char *bytes, unsigned int w, unsigned int h);
int get_page_size(pdf_t *pdf, int pageno, int *width, int *height);
void pdf_android_loghandler(const char *m);
int convert_point_pdf_to_apv(pdf_t *pdf, int page, int *x, int *y);
int convert_box_pdf_to_apv(pdf_t *pdf, int page, int rotation, fz_rect *bbox);
pdf_page* get_page(pdf_t *pdf, int pageno);
fz_rect get_page_box(pdf_t *pdf, int pageno);
wchar_t* widestrstr(wchar_t *haystack, int haystack_length, wchar_t *needle, int needle_length);
fz_pixmap *get_page_image_bitmap(
      pdf_t *pdf,
      int pageno, int zoom_pmil,
      int left, int top, int rotation,
      int skipImages,
      int width,
      int height);

