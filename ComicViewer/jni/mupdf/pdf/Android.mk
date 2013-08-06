LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CFLAGS := -DHAVE_PTHREADS	

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
  LOCAL_CFLAGS += -DJDCT_FASTEST=JDCT_FLOAT -DARCH_ARM
  LOCAL_ARM_MODE := arm
endif

ifeq ($(TARGET_ARCH_ABI),armeabi)
  LOCAL_CFLAGS += -DARCH_ARM
  LOCAL_ARM_MODE := arm
endif

LOCAL_C_INCLUDES := $(LOCAL_PATH)/../fitz \
	$(LOCAL_PATH)/../../freetype-overtlay/include \
	$(LOCAL_PATH)/../../freetype/include \
	$(LOCAL_PATH)/..
LOCAL_MODULE    := pdf
LOCAL_SRC_FILES := \
	../../mupdf-apv/pdf/apv_pdf_cmap_table.c \
	../../mupdf-apv/pdf/apv_pdf_fontfile.c \
	hashmap.c \
	pdf_annot.c \
	pdf_cmap.c \
	pdf_cmap_load.c \
	pdf_cmap_parse.c \
	pdf_colorspace.c \
	pdf_crypt.c \
	pdf_device.c \
	pdf_encoding.c \
	pdf_event.c \
	pdf_field.c \
	pdf_font.c \
	pdf_form.c \
	pdf_function.c \
	pdf_image.c \
	pdf_interpret.c \
	pdf_js_none.c \
	pdf_lex.c \
	pdf_metrics.c \
	pdf_nametree.c \
	pdf_object.c \
	pdf_outline.c \
	pdf_page.c \
	pdf_parse.c \
	pdf_pattern.c \
	pdf_repair.c \
	pdf_shade.c \
	pdf_store.c \
	pdf_stream.c \
	pdf_type3.c \
	pdf_unicode.c \
	pdf_write.c \
	pdf_xobject.c \
	pdf_xref.c \
	pdf_xref_aux.c



include $(BUILD_STATIC_LIBRARY)


# vim: set sts=8 sw=8 ts=8 noet:
