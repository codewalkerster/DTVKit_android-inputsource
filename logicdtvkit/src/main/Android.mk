LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := droidlogic.dtvkit.software.core
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_OVERRIDES_PACKAGES := droidlogic-dtvkit
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_SRC_FILES := $(call all-subdir-java-files)

ifeq ($(PRODUCT_SUPPORT_TUNER_FRAMEWORK), true)
#LOCAL_STATIC_JAVA_LIBRARIES := droidlogic.jniasplayer
LOCAL_JAVA_LIBRARIES := droidlogic.jniasplayer JDvrLib
LOCAL_REQUIRED_MODULES := droidlogic.jniasplayer JDvrLib
endif

LOCAL_STATIC_JAVA_LIBRARIES := droidlogic.dtvkit.tuner

LOCAL_DX_FLAGS := --core-library
LOCAL_VENDOR_MODULE := true
include $(BUILD_JAVA_LIBRARY)

#copy xml to permissions directory
include $(CLEAR_VARS)
LOCAL_MODULE := droidlogic.dtvkit.software.core.xml
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC

ifeq ($(PRODUCT_SUPPORT_TUNER_FRAMEWORK), true)
LOCAL_SRC_FILES := droidlogic.dtvkit.software.core.tuner.xml
else
LOCAL_SRC_FILES := droidlogic.dtvkit.software.core.xml
endif

LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR)/etc/permissions
LOCAL_VENDOR_MODULE := true

include $(BUILD_PREBUILT)
