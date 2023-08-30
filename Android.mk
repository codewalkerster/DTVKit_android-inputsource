LOCAL_PATH := $(call my-dir)

PRODUCT_SUPPORT_TUNER_FRAMEWORK ?= false
include $(CLEAR_VARS)
include $(LOCAL_PATH)/companionlibrary/src/main/Android.mk \
    $(LOCAL_PATH)/logicdtvkit/src/Android.mk \
    $(LOCAL_PATH)/fvp/src/main/Android.mk \
    $(LOCAL_PATH)/fvp/src/jni/Android.mk \
    $(LOCAL_PATH)/app/src/main/Android.mk \
    $(LOCAL_PATH)/hbbtvclient/Android.mk \
    $(LOCAL_PATH)/exoplayer/Android.mk
