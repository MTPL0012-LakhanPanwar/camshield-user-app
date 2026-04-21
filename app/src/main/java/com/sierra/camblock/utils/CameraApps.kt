package com.sierra.camblock.utils

/**
 * Shared helper for deciding whether a given package should be treated as a
 * "camera app" by both the Accessibility Service (foreground detection) and
 * the CameraBlockerService (overlay state).
 *
 * Keeping the logic in one place guarantees both code paths stay in sync,
 * which is important now that the Accessibility Service is the primary
 * source of truth for foreground changes.
 */
object CameraApps {

    /**
     * Well-known camera package names across major Android OEMs. The list is
     * intentionally exhaustive so that OEM-customised stock camera apps are
     * caught without relying solely on a "camera" substring match.
     */
    val KNOWN_CAMERA_PACKAGES: Set<String> = setOf(
        "com.android.camera",
        "com.android.camera2",
        "com.google.android.GoogleCamera",
        "com.google.android.apps.cameralite",
        "com.samsung.android.camera",
        "com.sec.android.app.camera",
        "com.xiaomi.camera",
        "com.mi.android.globalminusscreen", // MIUI camera widget
        "com.huawei.camera",
        "com.oppo.camera",
        "com.coloros.camera",
        "com.oneplus.camera",
        "com.motorola.camera2",
        "com.motorola.camera",
        "com.asus.camera",
        "com.sonyericsson.android.camera",
        "com.sony.playmemories.mobile",
        "com.lge.camera",
        "com.nothing.camera",
        "com.vivo.camera",
        "com.iqoo.camera",
        "com.realme.camera",
        "org.codeaurora.snapcam"
    )

    /**
     * Returns true when the given package is considered a camera application
     * that should be blocked while the app is locked. The caller's own
     * package is always exempt so our BlockedActivity / overlay is never
     * treated as a camera app.
     */
    fun isCameraApp(packageName: String?, selfPackage: String): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName == selfPackage) return false
        if (KNOWN_CAMERA_PACKAGES.contains(packageName)) return true
        // Substring heuristic catches OEM camera variants not in the set
        // above (e.g. "com.oem.camerax"). "cameralite" / "cameraroll" style
        // packages that simply view photos are rare; erring on the side of
        // blocking is acceptable for a facility-locked scenario.
        return packageName.contains("camera", ignoreCase = true)
    }
}
