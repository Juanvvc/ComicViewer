LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_ARM_MODE := arm

LOCAL_MODULE := jbig2dec
LOCAL_CFLAGS := -O3 -DHAVE_CONFIG_H
LOCAL_SRC_FILES := \
	jbig2.c \
	jbig2_arith.c \
	jbig2_arith_iaid.c \
	jbig2_arith_int.c \
	jbig2_generic.c \
	jbig2_halftone.c \
	jbig2_huffman.c \
	jbig2_image.c \
	jbig2_image_pbm.c \
	jbig2_metadata.c \
	jbig2_mmr.c \
	jbig2_page.c \
	jbig2_refinement.c \
	jbig2_segment.c \
	jbig2_symbol_dict.c \
	jbig2_text.c \
	jbig2dec.c \
	sha1.c


#	getopt.c
#	getopt1.c
#	memcmp.c
#	snprintf.c
#   jbig2_image_png.c

include $(BUILD_STATIC_LIBRARY)

