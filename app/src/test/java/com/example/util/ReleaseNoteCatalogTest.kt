package com.example.util

import org.junit.Assert.*
import org.junit.Test

class ReleaseNoteCatalogTest {
    private val installed = ChangelogRelease("2.3.0", "2026-11-11", "Bundled 2.3.0 notes", isCurrent = true)
    private val previous = ChangelogRelease("2.2.2", "2026-10-27", "Published previous release")
    private val bundled = listOf(installed, previous)

    @Test fun olderCacheCannotHideInstalledRelease() {
        val result = mergeBundledReleaseNotes(bundled, listOf(previous))
        assertEquals(listOf("2.3.0", "2.2.2"), result.map { it.versionName })
        assertEquals(installed, result.first())
    }

    @Test fun githubTextCannotReplaceBundledTextButAssetMetadataIsRetained() {
        val remote = installed.copy(markdownContent = "Outdated GitHub notes", releaseDate = "Unreleased",
            htmlUrl = "https://github.com/releases/v2.3.0", downloadUrl = "https://github.com/cobalt.apk", downloadSizeBytes = 42)
        val result = mergeBundledReleaseNotes(bundled, listOf(remote, previous)).first()
        assertEquals(installed.markdownContent, result.markdownContent)
        assertEquals(installed.releaseDate, result.releaseDate)
        assertEquals(remote.htmlUrl, result.htmlUrl)
        assertEquals(remote.downloadUrl, result.downloadUrl)
        assertEquals(remote.downloadSizeBytes, result.downloadSizeBytes)
    }

    @Test fun obsoleteUnpublishedVersionIsRemovedAndFutureUpdatesRemain() {
        val future = ChangelogRelease("2.4.0", "Unreleased", "Future changes", isNewer = true)
        val obsolete = ChangelogRelease("2.2.3", "Unreleased", "Never published")
        val result = mergeBundledReleaseNotes(bundled, listOf(future, obsolete, previous))
        assertEquals(listOf("2.4.0", "2.3.0", "2.2.2"), result.map { it.versionName })
        assertEquals(installed, result.single { it.isCurrent })
    }

    @Test fun offlineEmptyCacheUsesCompleteBundledHistory() {
        assertEquals(bundled, mergeBundledReleaseNotes(bundled, emptyList()))
        assertEquals(bundled, mergeBundledReleaseNotes(bundled,
            listOf(ChangelogRelease("2.2.3", "Unreleased", "Obsolete draft"))))
    }

    @Test fun repeatedLoadsDoNotDuplicateInstalledRelease() {
        val first = mergeBundledReleaseNotes(bundled, listOf(previous))
        assertEquals(first, mergeBundledReleaseNotes(bundled, first))
        assertEquals(1, first.count { it.isCurrent })
    }

    @Test fun malformedBundleDoesNotDiscardUsableDownloadedHistory() {
        assertEquals(listOf(previous), mergeBundledReleaseNotes(emptyList(), listOf(previous)))
    }
}
