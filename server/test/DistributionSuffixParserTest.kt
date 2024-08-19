package me.him188.ani.danmaku.server

import me.him188.ani.danmaku.server.util.DistributionSuffixParserImpl
import kotlin.test.Test

class DistributionSuffixParserTest {
    private val testData = mapOf(
        "ani-3.0.0-beta02-debian-amd64.deb" to "debian-x86_64",
        "ani-3.0.0-beta02-macos-amd64.dmg" to "macos-x86_64",
        "ani-3.0.0-beta02-redhat-amd64.rpm" to "redhat-x86_64",
        "ani-3.0.0-beta02.apk" to "android-arm64-v8a",
        "ani-3.7.0-macos-aarch64.dmg" to "macos-aarch64",
        "ani-3.7.0-macos-x86_64.dmg" to "macos-x86_64",
        "ani-3.7.0-windows-x86_64.zip" to "windows-x86_64",
        "ani-3.7.0.apk" to "android-arm64-v8a",
        "ani-3.8.0-beta01-arm64-v8a.apk" to "android-arm64-v8a",
        "ani-3.8.0-beta01-armeabi-v7a.apk" to "android-armeabi-v7a",
        "ani-3.8.0-beta01-universal.apk" to "android-universal",
        "ani-3.8.0-beta01-macos-aarch64.dmg" to "macos-aarch64",
        "ani-3.8.0-beta01-macos-x86_64.dmg" to "macos-x86_64",
    )

    private val testData380Beta01 = mapOf(
        "ani-3.8.0-beta01-arm64-v8a.apk" to "android-arm64-v8a",
        "ani-3.8.0-beta01-armeabi-v7a.apk" to "android-armeabi-v7a",
        "ani-3.8.0-beta01-universal.apk" to "android-universal",
        "ani-3.8.0-beta01-macos-aarch64.dmg" to "macos-aarch64",
        "ani-3.8.0-beta01-macos-x86_64.dmg" to "macos-x86_64",
    )

    @Test
    fun `test getPlatformArchFromAssetName`() {
        val parser = DistributionSuffixParserImpl()
        testData.forEach { (assetName, expected) ->
            val actual = parser.getPlatformArchFromAssetName(assetName)
            assert(actual == expected) { "$assetName: Expected $expected, but got $actual" }
        }
    }

    @Test
    fun `test matchAssetNameByPlatformArch`() {
        val parser = DistributionSuffixParserImpl()
        val assetNames = testData380Beta01.keys
        testData380Beta01.forEach { (assetName, expected) ->
            val actual = parser.matchAssetNameByPlatformArch(assetNames, expected)
            assert(actual == assetName) { "$expected: Expected $assetName, but got $actual" }
        }
    }
}