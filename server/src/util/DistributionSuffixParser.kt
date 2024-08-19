package me.him188.ani.danmaku.server.util

interface DistributionSuffixParser {
    fun getPlatformArchFromAssetName(assetName: String): String
    fun matchAssetNameByPlatformArch(assetNames: Set<String>, platformArch: String): String?
}

class DistributionSuffixParserImpl : DistributionSuffixParser {
    override fun getPlatformArchFromAssetName(assetName: String): String {
        var arch = assetName.substringAfterLast('-').substringBeforeLast('.')
        when (arch) {
            "v8a" -> if (assetName.contains("arm64-v8a")) arch = "arm64-v8a"
            "v7a" -> if (assetName.contains("armeabi-v7a")) arch = "armeabi-v7a"
        }
        return when {
            assetName.endsWith(".apk") -> {
                if (arch in setOf("arm64-v8a", "armeabi-v7a")) "android-$arch"
                else "android-universal"
            }

            assetName.endsWith(".zip") -> "windows-$arch"
            assetName.endsWith(".dmg") -> "macos-$arch"
            assetName.endsWith(".deb") -> "debian-$arch"
            else -> throw IllegalArgumentException("Unknown client arch from asset name: $assetName")
        }
    }

    override fun matchAssetNameByPlatformArch(assetNames: Set<String>, platformArch: String): String? {
        return assetNames.mapNotNull { assetName ->
            val assetPlatformArch: String
            try {
                assetPlatformArch = getPlatformArchFromAssetName(assetName)
            } catch (e: IllegalArgumentException) {
                return@mapNotNull null
            }
            assetPlatformArch to assetName
        }.let {
            it.firstOrNull { (assetPlatformArch, _) ->
                assetPlatformArch == platformArch
            }?.second ?: it.firstOrNull { (assetPlatformArch, _) ->
                assetPlatformArch == "android-universal" && platformArch.startsWith(
                    "android"
                )
            }?.second
        }
    }
}