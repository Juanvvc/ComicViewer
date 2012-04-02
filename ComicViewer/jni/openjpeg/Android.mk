LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CFLAGS := -O3
LOCAL_ARM_MODE := arm

LOCAL_MODULE    := openjpeg
LOCAL_SRC_FILES := \
	bio.c \
	cio.c \
	dwt.c \
	event.c \
	image.c \
	j2k.c \
	j2k_lib.c \
	jp2.c \
	jpt.c \
	mct.c \
	mqc.c \
	openjpeg.c \
	pi.c \
	raw.c \
	t1.c \
	t1_generate_luts.c \
	t2.c \
	tcd.c \
	tgt.c


include $(BUILD_STATIC_LIBRARY)


# vim: set sts=8 sw=8 ts=8 noet:
