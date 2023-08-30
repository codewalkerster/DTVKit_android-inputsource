ifeq ($(PRODUCT_SUPPORT_TUNER_FRAMEWORK), true)
LOCAL_PATH := $(call my-dir)

ifeq (, $(wildcard $(LOCAL_PATH)/../../../../android-rpcservice))

$(warning "prebuilt libdtvkit_tuner_jni_wrapper.so")

include $(CLEAR_VARS)
    LOCAL_MODULE := libdtvkit_tuner_jni_wrapper
    LOCAL_SRC_FILES := libdtvkit_tuner_jni_wrapper.so
    LOCAL_MODULE_CLASS := SHARED_LIBRARIES
    LOCAL_MODULE_SUFFIX := .so
    LOCAL_VENDOR_MODULE := true
    LOCAL_SHARED_LIBRARIES :=  \
        libcutils \
        libutils \
        liblog \
        libbase \
        libdtvkit_tuner_jni \
        libjdvrlib-jni \
        libjniasplayer-jni
    LOCAL_LICENSE_KINDS := legacy_notice
    LOCAL_LICENSE_CONDITIONS := notice
include $(BUILD_PREBUILT)

$(warning "prebuilt libdtvkitserver.so")

include $(CLEAR_VARS)
    LOCAL_MODULE := libdtvkitserver
    LOCAL_SRC_FILES := libdtvkitserver.so
    LOCAL_MODULE_CLASS := SHARED_LIBRARIES
    LOCAL_MODULE_SUFFIX := .so
    LOCAL_VENDOR_MODULE := true
    LOCAL_SHARED_LIBRARIES :=  \
        libteec \
        libft2-aml \
        libutils \
        liblog \
        libdtvkit_tuner_jni_wrapper \

    LOCAL_LICENSE_KINDS := legacy_notice
    LOCAL_LICENSE_CONDITIONS := notice
#    LOCAL_MODULE_RELATIVE_PATH := hw
include $(BUILD_PREBUILT)

endif

endif