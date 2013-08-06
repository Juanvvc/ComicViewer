
#ifdef PDFVIEW2_H__
#error PDFVIEW2_H__ can be included only once
#endif


#define PDFVIEW2_H__


#include "fitz.h"
#include "mupdf.h"

#define MAX_BOX_NAME 8

#define ABS(x) ((x) < 0 ? -(x) : (x))
#define MIN(x,y) ((x) < (y) ? (x) : (y))
#define MAX(x,y) ((x) > (y) ? (x) : (y))


pdf_t* get_pdf_from_this(JNIEnv *env, jobject this);
void get_size(JNIEnv *env, jobject size, int *width, int *height);
void save_size(JNIEnv *env, jobject size, int width, int height);
void pdf_android_loghandler(const char *m);
jobject create_find_result(JNIEnv *env);
void set_find_result_page(JNIEnv *env, jobject findResult, int page);
void add_find_result_marker(JNIEnv *env, jobject findResult, int x0, int y0, int x1, int y1);
void add_find_result_to_list(JNIEnv *env, jobject *list, jobject find_result);
int find_next(JNIEnv *env, jobject this, int direction);


// #ifdef pro
// jobject create_outline_recursive(JNIEnv *env, jclass outline_class, const fz_outline *outline);
// char* extract_text(pdf_t *pdf, int pageno);
// #endif



