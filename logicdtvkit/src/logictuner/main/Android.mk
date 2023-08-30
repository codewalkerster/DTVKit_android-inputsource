LOCAL_PATH := $(call my-dir)

ifeq (1, $(strip $(shell expr $(PLATFORM_VERSION) \< 13)))

include $(CLEAR_VARS)

$(warning "prebuilt droidlogic.dtvkit.tuner")
LOCAL_MODULE := droidlogic.dtvkit.tuner
LOCAL_SRC_FILES := lib/droidlogic.dtvkit.tuner.jar
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_SDK_VERSION := current
LOCAL_UNINSTALLABLE_MODULE := true
include $(BUILD_PREBUILT)

else

include $(CLEAR_VARS)

$(warning "build droidlogic.dtvkit.tuner")
LOCAL_MODULE := droidlogic.dtvkit.tuner
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_VENDOR_MODULE := true
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice

include $(BUILD_STATIC_JAVA_LIBRARY)

endif
