LOCAL_PATH := $(call my-dir)
ifeq ($(PRODUCT_SUPPORT_TUNER_FRAMEWORK), true)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-subdir-cpp-files)

LOCAL_C_INCLUDES += frameworks/base/core/jni/include \
               $(LOCAL_PATH)/include \

ifeq (,$(wildcard /vendor/amlogic/common/ASPlayer))
    $(warning "$(LOCAL_PATH)/../../src/lib/ASPlayer")
    LOCAL_C_INCLUDES += $(LOCAL_PATH)/../../lib/ASPlayer
else
    LOCAL_C_INCLUDES += vendor/amlogic/common/ASPlayer/libs/JNI-ASPlayer-library/src/main/jni/include
endif

LOCAL_MODULE := libdtvkit_tuner_jni

LOCAL_HEADER_LIBRARIES := jni_headers \

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    libutils \
    liblog \
    libbase \
    libjniasplayer-jni

#LOCAL_STATIC_LIBRARIES += libdtvkit_platform
LOCAL_PRELINK_MODULE := false

LOCAL_MODULE_TAGS := optional

LOCAL_VENDOR_MODULE := true

LOCAL_LICENSE_KINDS := legacy_notice
LOCAL_LICENSE_CONDITIONS := notice

include $(BUILD_SHARED_LIBRARY)
endif
