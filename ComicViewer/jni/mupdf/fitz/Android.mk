LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CFLAGS := -O3 -DARCH_ARM

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
  LOCAL_CFLAGS += -DJDCT_FASTEST=JDCT_FLOAT
endif

LOCAL_ARM_MODE := arm

LOCAL_C_INCLUDES := $(LOCAL_PATH)/../mupdf $(LOCAL_PATH)/../../jpeg \
	$(LOCAL_PATH)/../../freetype-overlay/include $(LOCAL_PATH)/../../freetype/include \
	$(LOCAL_PATH)/../../jbig2dec $(LOCAL_PATH)/../../openjpeg
LOCAL_MODULE := fitz
LOCAL_SRC_FILES := \
	base_error.c \
	base_object.c \
	base_hash.c \
	base_memory.c \
	base_string.c \
	base_geometry.c \
	\
	crypt_aes.c \
	crypt_arc4.c \
	crypt_md5.c \
	crypt_sha2.c \
	\
	obj_print.c \
	\
	stm_buffer.c \
	stm_open.c \
	stm_read.c \
	\
	filt_basic.c \
	\
	filt_dctd.c \
	filt_faxd.c \
	filt_flate.c \
	filt_lzwd.c \
	filt_predict.c \
	filt_jbig2d.c \
	filt_jpxd.c \
	\
	res_colorspace.c \
	res_font.c \
	res_pixmap.c \
	res_shade.c \
	res_text.c \
	res_path.c \
	\
	dev_list.c \
	dev_text.c \
	dev_bbox.c \
	dev_null.c


include $(BUILD_STATIC_LIBRARY)

# vim: set sts=8 sw=8 ts=8 noet:

