LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    org_droidlogic_fvp_signcsr_client.cpp

LOCAL_C_INCLUDES += \
    frameworks/base/core/jni/include

ifeq ($(PLATFORM_TDK_PATH),)
LOCAL_C_INCLUDES += \
    vendor/amlogic/common/tdk_v3/ca_export_arm/include
else
LOCAL_C_INCLUDES += \
    $(PLATFORM_TDK_PATH)/ca_export_arm/include
endif

LOCAL_MODULE := libfvp_signcsr_jni
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice

LOCAL_HEADER_LIBRARIES := jni_headers
LOCAL_SHARED_LIBRARIES :=  \
    libcutils \
    libutils \
    liblog \
    libteec

LOCAL_PRELINK_MODULE := false
LOCAL_MODULE_TAGS := optional
LOCAL_VENDOR_MODULE := true

include $(BUILD_SHARED_LIBRARY)
