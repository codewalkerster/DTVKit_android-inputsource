LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := droidlogic.dtvkit.atf.aidl
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_AIDL_INCLUDES := $(LOCAL_PATH)/aidl
LOCAL_AIDL_SOURCES := $(call all-subdir-Iaidl-files)
LOCAL_SRC_FILES := $(LOCAL_AIDL_SOURCES) java/org/droidlogic/dtvkit/ParceledListSlice.java
LOCAL_VENDOR_MODULE := true
include $(BUILD_STATIC_JAVA_LIBRARY)

ifeq ($(PRODUCT_SUPPORT_TUNER_FRAMEWORK), true)
##
include $(CLEAR_VARS)
LOCAL_MODULE := droidlogic.dtvkit.atf.wrapper
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice

LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_STATIC_JAVA_LIBRARIES := droidlogic.dtvkit.atf.aidl
LOCAL_JAVA_LIBRARIES := android-support-v4
LOCAL_SRC_FILES := $(call all-subdir-java-files) \
    ../../../logicdtvkit/src/main/java/org/droidlogic/dtvkit/DtvkitAudioManager.java \
    ../../../logicdtvkit/src/main/java/org/droidlogic/dtvkit/ISdbCcImplement.java \
    ../../../logicdtvkit/src/main/java/org/droidlogic/dtvkit/MhegOverlayView.java \
    ../../../logicdtvkit/src/main/java/org/droidlogic/dtvkit/SubtitleServerView.java

LOCAL_REQUIRED_MODULES := droidlogic.dtvkit.atf.wrapper.xml
LOCAL_VENDOR_MODULE := true
include $(BUILD_JAVA_LIBRARY)

##
include $(CLEAR_VARS)
LOCAL_MODULE := droidlogic.dtvkit.atf.wrapper.xml
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC

LOCAL_SRC_FILES := droidlogic.dtvkit.atf.wrapper.xml
LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR)/etc/permissions
LOCAL_VENDOR_MODULE := true
include $(BUILD_PREBUILT)

endif

