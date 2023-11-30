LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := inputsource
LOCAL_MULTILIB := 32
LOCAL_AAPT_FLAGS := --auto-add-overlay \
   --extra-packages android.support.v17.leanback \
   --extra-packages com.google.android.exoplayer2

ifneq (0, $(shell expr $(PLATFORM_SDK_VERSION) \<= 28))
LOCAL_AAPT_FLAGS += --extra-packages android.support.constraint

LOCAL_STATIC_ANDROID_LIBRARIES:= \
    android-support-constraint-layout

LOCAL_STATIC_JAVA_LIBRARIES += android-support-constraint-layout-solver

LOCAL_USE_AAPT2 := true
endif

LOCAL_RESOURCE_DIR := frameworks/support/leanback/src/main/res \
   $(LOCAL_PATH)/res \
   $(LOCAL_PATH)/droidlogic/res

LOCAL_STATIC_JAVA_LIBRARIES += \
   androidx.annotation_annotation \
   companionlibrary \
   android-support-v17-leanback \
   amlogic-hbbtv-client \
   guava-android-31

LOCAL_STATIC_JAVA_AAR_LIBRARIES += exo-player

ifneq (0, $(shell expr $(PLATFORM_SDK_VERSION) \>= 29))
LOCAL_STATIC_JAVA_LIBRARIES += android-support-constraint-layout
endif

LOCAL_AIDL_INCLUDES := $(LOCAL_PATH)/aidl

LOCAL_SRC_FILES := $(call all-subdir-java-files) $(call all-subdir-Iaidl-files)

#TARGET_BUILD_APPS := inputsource # for normal app (embedded ndk jni)
#LOCAL_JNI_SHARED_LIBRARIES := libplatform
#LOCAL_REQUIRED_MODULES := libplatform
LOCAL_CERTIFICATE := platform
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_JNI_SHARED_LIBRARIES := libdtvkit_jni

ifeq (1, $(strip $(shell expr $(PLATFORM_VERSION) \< 12)))
LOCAL_JAVA_LIBRARIES += droidlogic droidlogic.dtvkit.software.core
LOCAL_REQUIRED_MODULES := droidlogic droidlogic.dtvkit.software.core
LOCAL_FULL_LIBS_MANIFEST_FILES := \
  $(LOCAL_PATH)/AndroidManifest.xml
else
LOCAL_REQUIRED_MODULES := droidlogic.software.core droidlogic.dtvkit.software.core
LOCAL_JAVA_LIBRARIES += droidlogic.software.core droidlogic.dtvkit.software.core
LOCAL_USES_LIBRARIES := droidlogic.software.core droidlogic.dtvkit.software.core
LOCAL_FULL_LIBS_MANIFEST_FILES := \
  $(LOCAL_PATH)/AndroidManifest.xml \
  $(LOCAL_PATH)/AndroidManifest-alarm-permissions.xml
endif
LOCAL_DEX_PREOPT := false
LOCAL_VENDOR_MODULE := true

ifneq (,$(findstring cas,$(TARGET_PRODUCT)))
LOCAL_FULL_LIBS_MANIFEST_FILES += \
    $(LOCAL_PATH)/AndroidManifest_cas_provider.xml

LOCAL_STATIC_JAVA_LIBRARIES += CasProviderLib
endif

#LOCAL_PRIVATE_PLATFORM_APIS := true
include $(BUILD_PACKAGE)

include $(call all-makefiles-under, $(LOCAL_PATH))

