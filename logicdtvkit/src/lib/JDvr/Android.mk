ifeq ($(PRODUCT_SUPPORT_TUNER_FRAMEWORK), true)
LOCAL_PATH := $(call my-dir)

ifeq (, $(wildcard vendor/amlogic/reference/apps/JDvrLib))

$(warning "prebuilt JDvrLib jar")

include $(CLEAR_VARS)
    LOCAL_MODULE := libjdvrlib-jni
    LOCAL_SRC_FILES := libjdvrlib-jni.so
    LOCAL_MODULE_CLASS := SHARED_LIBRARIES
    LOCAL_MODULE_SUFFIX := .so
    LOCAL_VENDOR_MODULE := true
    LOCAL_SHARED_LIBRARIES :=  \
        libbase \
        libcutils \
        libutils \
        liblog \

    LOCAL_LICENSE_KINDS := legacy_notice
    LOCAL_LICENSE_CONDITIONS := notice
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
    LOCAL_MODULE := jdvrlib.xml
    LOCAL_MODULE_CLASS := ETC
    LOCAL_SRC_FILES := jdvrlib.xml
    LOCAL_MODULE_TAGS := optional
    LOCAL_VENDOR_MODULE := true
    LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR)/etc/permissions
    LOCAL_LICENSE_KINDS := legacy_notice
    LOCAL_LICENSE_CONDITIONS := notice
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
    LOCAL_MODULE := JDvrLib
    LOCAL_MODULE_CLASS := JAVA_LIBRARIES
    LOCAL_MODULE_TAGS := optional
    LOCAL_SDK_VERSION := current
    LOCAL_SRC_FILES := JDvrLib.jar
    LOCAL_MODULE_SUFFIX := $(COMMON_JAVA_PACKAGE_SUFFIX)
    LOCAL_BUILT_MODULE_STEM := javalib.jar
    LOCAL_UNINSTALLABLE_MODULE := true
include $(BUILD_PREBUILT)

endif

endif