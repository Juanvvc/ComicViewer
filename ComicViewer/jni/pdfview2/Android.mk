LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CFLAGS := -O3
LOCAL_ARM_MODE := arm

LOCAL_C_INCLUDES += $(LOCAL_PATH)/../mupdf/fitz $(LOCAL_PATH)/../mupdf/pdf $(LOCAL_PATH)/../freetype-overlay/include $(LOCAL_PATH)/../freetype/include $(LOCAL_PATH)/pdfview2/include
LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -lz -llog
LOCAL_STATIC_LIBRARIES := pdf fitz fitzdraw jpeg jbig2dec openjpeg freetype
LOCAL_MODULE    := pdfview2
LOCAL_SRC_FILES := pdfview2.c

include $(BUILD_SHARED_LIBRARY)
