package com.molvess.para_defteri_flutter

/** Runtime permissions differ from the per-document URI grants supplied by SAF. */
internal object AccessPolicy {
    const val READ = "android.permission.READ_EXTERNAL_STORAGE"
    const val WRITE = "android.permission.WRITE_EXTERNAL_STORAGE"
    const val IMAGES = "android.permission.READ_MEDIA_IMAGES"
    const val SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    fun permissionsFor(operation: Int, sdk: Int): List<String> = when {
        operation == 403 && sdk >= 34 -> listOf(IMAGES, SELECTED)
        operation == 403 && sdk >= 33 -> listOf(IMAGES)
        operation == 403 -> listOf(READ)
        operation == 402 && sdk <= 28 -> listOf(READ)
        operation == 401 && sdk <= 28 -> listOf(WRITE)
        else -> emptyList() // Modern documents use SAF, not photo/all-files permission.
    }

    fun allowed(operation: Int, sdk: Int, granted: (String) -> Boolean): Boolean {
        val required = permissionsFor(operation, sdk)
        return if (operation == 403) required.any(granted) else required.all(granted)
    }
}
