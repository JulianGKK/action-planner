LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := pag_document_fs
LOCAL_SRC_FILES := document_rename.c
LOCAL_CFLAGS := -std=c11 -Wall -Wextra -Werror -fvisibility=hidden -D_GNU_SOURCE
LOCAL_LDFLAGS := -Wl,-z,relro -Wl,-z,now
include $(BUILD_SHARED_LIBRARY)
