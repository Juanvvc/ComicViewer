LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_LDLIBS := -llog
LOCAL_MODULE := xzx
LOCAL_SRC_FILES := jnixz.c xz_crc32.c xz_dec_stream.c xz_dec_lzma2.c xz_dec_bcj.c
LOCAL_CFLAGS := -DXZ_DEC_X86 -DXZ_DEC_POWERPC -DXZ_DEC_IA64 -DXZ_DEC_ARM -DXZ_DEC_ARMTHUMB -DXZ_DEC_SPARC


include $(BUILD_SHARED_LIBRARY)
