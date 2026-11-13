package com.parsfilo.contentapp.feature.audio.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ResumableAudioUriTest {
    @Test
    fun localAudioCacheCanBeRestoredButMissingFileCannot() {
        val filesDir = Files.createTempDirectory("audio-resume").toFile()
        try {
            val audio = File(File(filesDir, "audio_cache").apply { mkdirs() }, "amen.mp3")
            audio.writeText("sample")
            assertTrue(isResumableAudioUri(audio.toURI().toString(), filesDir))
            assertFalse(
                isResumableAudioUri(
                    File(filesDir, "audio_cache/missing.mp3").toURI().toString(),
                    filesDir,
                ),
            )
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun privateFilesOutsideAudioCacheAndNetworkStreamsAreNotColdResumed() {
        val filesDir = Files.createTempDirectory("audio-resume").toFile()
        try {
            val other = File(filesDir, "unrelated.mp3").apply { writeText("sample") }
            assertFalse(isResumableAudioUri(other.toURI().toString(), filesDir))
            assertFalse(isResumableAudioUri("https://example.org/audio.mp3", filesDir))
            assertFalse(isResumableAudioUri(null, filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun bundledAssetsOnlyAllowSimpleRelativeNames() {
        val filesDir = Files.createTempDirectory("audio-resume").toFile()
        try {
            assertTrue(isResumableAudioUri("asset:///audio.mp3", filesDir))
            assertFalse(isResumableAudioUri("asset:///../other.mp3", filesDir))
            assertFalse(isResumableAudioUri("asset:///", filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
