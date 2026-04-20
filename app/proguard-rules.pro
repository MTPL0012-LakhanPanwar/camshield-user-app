# ==============================================================================
#  CamShield – ProGuard / R8 rules  (production build, R8 full-mode)
#
#  DESIGN PHILOSOPHY
#  -----------------
#  This file intentionally contains ONLY rules that protect THIS app's own
#  code. Every third-party library we depend on (Retrofit, OkHttp, Gson,
#  CameraX, ML Kit, Material Components, AndroidX, Kotlin stdlib, Guava)
#  ships its own "Consumer ProGuard Rules" inside the AAR/JAR, which R8
#  merges automatically at build time. Adding blanket
#      -keep class com.library.** { *; }
#  rules would defeat R8's dead-code elimination for those libraries and
#  inflate the APK — we explicitly do NOT do that.
#
#  Manifest-declared components (Application, Activities, Service,
#  BroadcastReceivers) are kept automatically by the Android Gradle Plugin
#  through the generated `aapt_rules.txt`. Parcelable, Serializable, enum
#  values()/valueOf(), native methods, R$* fields, and View constructors
#  are kept by `proguard-android-optimize.txt` (applied via `proguardFiles
#  getDefaultProguardFile('proguard-android-optimize.txt'), ...`). We do
#  not re-declare any of that here.
# ==============================================================================


# ------------------------------------------------------------------------------
# 1. Crash-report readability
# ------------------------------------------------------------------------------
# Keep enough bytecode metadata in the release artifact to de-obfuscate
# stack traces with mapping.txt. `SourceFile` is normally stripped /
# renamed by proguard-android-optimize.txt; the second directive restores
# the original source filename so Play Console + Firebase Crashlytics can
# resolve frames cleanly.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile


# ------------------------------------------------------------------------------
# 2. Gson DTOs  (our API request / response models)
# ------------------------------------------------------------------------------
# WHY THIS RULE EXISTS FOR OUR CODE:
# Gson uses Java reflection to read field NAMES from JSON. Most of our
# models in `com.sierra.camblock.api.models.*` are safe because every
# field is tagged with @SerializedName — the annotation rule at the
# bottom of this section preserves them.
#
# BUT `ApiModels.kt:96-101` (EnrollmentResponse) has NO @SerializedName
# on its fields:
#     data class EnrollmentResponse(
#         val enrollmentId: String?,
#         val action: String?,
#         val facilityName: String?,
#         val visitorId: String?
#     )
# Once R8 renames `enrollmentId -> a`, Gson's reflective
# fromJson(...) silently produces a data class where every field is
# null. The scan-entry flow in ScanActivity (`apiBody.data?.visitorId`)
# would then always be null and the whole lock flow would break.
#
# The rule below freezes field names AND the no-arg / all-args
# constructors for our DTO package ONLY. Note: `allowobfuscation` is
# deliberately NOT used — it would still permit R8 to rename the fields,
# defeating the entire point of this rule (Gson reads the original
# Java field name via reflection). We also keep class names so Retrofit's
# generic type resolution (`Response<ApiResponse<EnrollmentResponse>>`)
# stays consistent at runtime.
-keep class com.sierra.camblock.api.models.** { *; }

# R8 full-mode belt-and-braces: even with full-mode disabled in
# gradle.properties, explicitly retain generic signatures on our DTOs so
# Gson can resolve T in ApiResponse<T>. `allowobfuscation,allowshrinking`
# here lets R8 still prune/rename the CLASS (fields are kept by the rule
# above); what matters is that the Signature attribute on each class
# survives so Retrofit can read the parameterised return type at runtime.
# Without this, T erases to Object, Gson produces a LinkedTreeMap, and
# the Kotlin-inserted CHECKCAST in `ScanActivity.kt:449`
# (`apiBody.data?.visitorId`) throws ClassCastException on release only.
-keep,allowobfuscation,allowshrinking class com.sierra.camblock.api.models.ApiResponse
-keep,allowobfuscation,allowshrinking class com.sierra.camblock.api.models.EnrollmentResponse
-keep,allowobfuscation,allowshrinking class com.sierra.camblock.api.models.ValidateQRResponse
-keep,allowobfuscation,allowshrinking class com.sierra.camblock.api.models.EnrollmentStatusResponse

# Project-wide safety net: any field annotated @SerializedName keeps its
# original name. Zero APK cost because only annotated fields qualify.
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Enum safety: Gson matches enum values via the exact constant name string
# (or @SerializedName on the constant). Obfuscation would break this.
-keepclassmembers enum * {
    @com.google.gson.annotations.SerializedName <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Gson relies on these attributes at runtime for generic type resolution
# and for reading @SerializedName. Retrofit's consumer rules already keep
# Signature; these duplicates are cheap insurance.
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses


# ------------------------------------------------------------------------------
# 3. Retrofit service interfaces
# ------------------------------------------------------------------------------
# WHY NO RULE HERE:
# Retrofit 2.9.0's bundled consumer rules already contain:
#     -if interface * { @retrofit2.http.* <methods>; }
#     -keep,allowobfuscation interface <1>
# which matches our `com.sierra.camblock.api.ApiService` automatically.
# The same consumer file keeps Signature / InnerClasses / EnclosingMethod
# / RuntimeVisibleAnnotations / RuntimeVisibleParameterAnnotations /
# AnnotationDefault, plus `-dontwarn javax.annotation.**` and
# `-dontwarn retrofit2.KotlinExtensions*` for Kotlin-only helpers.
# Nothing to add for our code.


# ------------------------------------------------------------------------------
# 4. Reflection probe for a hidden platform class
# ------------------------------------------------------------------------------
# WHY THIS RULE EXISTS FOR OUR CODE:
# Three call sites invoke a hidden Android system class reflectively to
# read MIUI / HyperOS build properties:
#   - utils/DeviceUtils.kt:71
#   - activity/ScanActivity.kt:578
#   - activity/MainActivity.kt:732
#       Class.forName("android.os.SystemProperties")
#           .getMethod("get", String::class.java)
#           .invoke(null, "ro.miui.ui.version.name")
# `android.os.SystemProperties` is an @hide class — it is not on the
# compile classpath, so R8 (which validates all referenced types) fails
# the release build with a MissingClasses error. We only need to silence
# that warning; no -keep is required because the class isn't in our APK.
-dontwarn android.os.SystemProperties


# ------------------------------------------------------------------------------
# 5. Strip verbose logging from release builds
# ------------------------------------------------------------------------------
# WHY THIS RULE EXISTS FOR OUR CODE:
# The codebase uses `Log.d(...)` liberally to print QR tokens, device IDs
# and API payloads (e.g. ScanActivity.kt:222, ScanActivity.kt:420-440).
# These calls are a PII / security leak in production and also pin string
# constants that R8 would otherwise drop. Declaring these methods as
# side-effect-free lets R8 remove both the call and its arguments. We
# keep `Log.w` and `Log.e` because production diagnostics rely on them.
#
# NOTE: This does NOT strip OkHttp's HttpLoggingInterceptor.Level.BODY
# logger configured in RetrofitClient.kt:20 — that prints through a
# different code path and should be gated with `BuildConfig.DEBUG` in a
# separate source change.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
