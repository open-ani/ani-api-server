package me.him188.ani.danmaku.server.util

interface DistributionSuffixParser {
    fun getPlatformArchFromAssetName(assetName: String): String
}

class DistributionSuffixParserImpl : DistributionSuffixParser {
    override fun getPlatformArchFromAssetName(assetName: String): String {
        val arch = assetName.substringAfterLast('-').substringBeforeLast('.')
        return when {
            assetName.endsWith(".apk") -> {
                if (arch in setOf("arm64_v8a", "armeabi_v7a")) "android-$arch"
                else "android-universal"
            }
            assetName.endsWith(".zip") -> "windows-$arch"
            assetName.endsWith(".dmg") -> "macos-$arch"
            assetName.endsWith(".deb") -> "debian-$arch"
            else -> throw IllegalArgumentException("Unknown client arch from asset name: $assetName")
        }
    }
}