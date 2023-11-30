LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := CasProviderLib

#LOCAL_AAPT_FLAGS := --auto-add-overlay \
#   --extra-packages android.support.v7.appcompat

#LOCAL_RESOURCE_DIR := frameworks/support/v7/appcompat/res

LOCAL_STATIC_JAVA_LIBRARIES += \
   androidx.annotation_annotation \
   android-support-v4 \
   android-support-v7-appcompat

LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_VENDOR_MODULE := true

LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
include $(BUILD_STATIC_JAVA_LIBRARY)

