LOCAL_PATH := $(call my-dir)

### shared library

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    org_droidlogic_dtvkit_DtvkitGlueClient.cpp

LOCAL_C_INCLUDES += frameworks/base/core/jni/include \
    system/libhidl/transport/include/hidl \
    system/libhidl/libhidlmemory/include \
    system/libhidl/libhidlcache/include \
    system/libhidl/transport/token/1.0/utils/include \
    hardware/libhardware/include \
    $(LOCAL_PATH)/../../../app/src/main/client \
    system/libfmq/include

LOCAL_MODULE := libdtvkit_jni
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_MULTILIB := 32
LOCAL_HEADER_LIBRARIES := jni_headers

LOCAL_SHARED_LIBRARIES := \
    android.hidl.memory@1.0 \
    vendor.amlogic.hardware.subtitleserver@1.0 \
    libbase \
    libcutils \
    libutils \
    liblog \
    libhardware \
    libhidlbase \
    libhidltransport \
    libhidlmemory \

ifeq ($(PRODUCT_SUPPORT_TUNER_FRAMEWORK), true)
    LOCAL_SHARED_LIBRARIES += libdtvkitserver
    LOCAL_CFLAGS += -DSUPPORT_TUNER_FRAMEWORK
else
    LOCAL_SHARED_LIBRARIES += \
    vendor.amlogic.hardware.dtvkitserver@1.0 \
    libdtvkithidlclient
endif

LOCAL_LDLIBS := -lfmq_vendor

SUBTITLE_INCLUDES := \
    vendor/amlogic/common/frameworks/services/subtitleserver/client

ifeq ($(PRODUCT_DTVKIT_SUPPORT_ISDBT),true)
LOCAL_CFLAGS += -DSUPPORT_ISDBT
endif

LOCAL_C_INCLUDES += $(SUBTITLE_INCLUDES)
LOCAL_STATIC_LIBRARIES := libsubtitleclient_static

LOCAL_PRELINK_MODULE := false
LOCAL_MODULE_TAGS := optional

LOCAL_VENDOR_MODULE := true

#LOCAL_PRIVATE_PLATFORM_APIS := true
include $(BUILD_SHARED_LIBRARY)