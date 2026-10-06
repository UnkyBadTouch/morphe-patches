package app.morphe.patches.opencode.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi

object Constants {
    val COMPATIBILITY_OPENCODE_MOBILE = Compatibility(
        name = "OpenCode Mobile",
        packageName = "dev.opencode.mobile.morphe",
        apkFileType = ApkFileType.APK_REQUIRED,
        // The app icon is monochrome, black background.
        appIconColor = 0xFF000000.toInt(),
        targets = listOf(
            AppTarget(
                version = "1.1.0",
                versionCodes = mapOf(SupportedAbi.ARM64_V8A to 25)
            )
        )
    )
}
