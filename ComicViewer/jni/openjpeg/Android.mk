LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CFLAGS := -O3 \
	-DHAVE_INTTYPES_H \
	-DHAVE_SSIZE_T \
	-DHAVE_STDINT_H \
	-DOPJ_PACKAGE_VERSION='"2.0.0"' \
	-DOPJ_STATIC \
	-DUSE_JPIP
LOCAL_ARM_MODE := arm

LOCAL_MODULE    := openjpeg
LOCAL_SRC_FILES := \
	bio.c \
	cio.c \
	dwt.c \
	event.c \
	function_list.c \
	image.c \
	invert.c \
	j2k.c \
	jp2.c \
	mct.c \
	mqc.c \
	openjpeg.c \
	opj_clock.c \
	pi.c \
	raw.c \
	t1.c \
	t1_generate_luts.c \
	t2.c \
	tcd.c \
	tgt.c \
	cidx_manager.c \
	tpix_manager.c \
	ppix_manager.c \
	thix_manager.c \
	phix_manager.c


include $(BUILD_STATIC_LIBRARY)


# vim: set sts=8 sw=8 ts=8 noet:
