package com.example.media.recommendation

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.SavedItemEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.data.model.TvShowProgress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run once with screeningTastePhase=seed, force-stop, then run with screeningTastePhase=verify. */
@RunWith(AndroidJUnit4::class)
class ScreeningLearningDeviceTest {
    @Test fun learningSurvivesProcessDeathAndCorrectionsKeepArchiveFacts() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("screeningTastePhase")
        assumeTrue("This persistence check needs separate seed and verify processes", phase in setOf("seed", "verify"))
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = IsolatedLearningContext(base)
        val marker = context.getSharedPreferences("fixture", Context.MODE_PRIVATE)
        val store = ScreeningStore(context)
        val archive = Room.databaseBuilder(base, AppDatabase::class.java, "algorithm_device_archive.db").build()
        val dao = archive.savedItemDao()
        try {
            if (phase == "seed") {
                store.resetLearning()
                val now = System.currentTimeMillis()
                val local = title(now)
                store.observe(listOf(local), now)
                val oldKey = store.canonicalKey(local)
                store.toggleReaction(local.id, oldKey, ScreeningPreference.LIKED, now = now)
                store.ignoreEvidence("rating:$oldKey", true, now)
                val enriched = local.copy(tmdbId = 98_765_431)
                val duplicate = enriched.copy(id = "algorithm-device-duplicate", updatedAt = now + 1)
                val items = listOf(enriched, duplicate)
                val signals = store.observe(items, now)
                val pick = ScreeningRecommendationEngine.choose(
                    ScreeningRecommendationEngine.rank(items, signals, now, includeCooldowns = false), "device-persistence")!!
                assertEquals(1, ScreeningTaste.profile(items, signals, now).titleCount)
                assertTrue(store.publish(ScreeningDecisionSnapshot("device-validation-publication", "Widget validation", "ALL",
                    pick.key, pick.item.id, pick.item.title, now, ScreeningScoreTrace.from(pick), reason = pick.reason)))
                val question = store.createDelivery(pick, now, ScreeningSchedule.day(now), false)!!
                dao.insertItem(SavedItemEntity.fromDomainModel(enriched))
                dao.insertItem(SavedItemEntity.fromDomainModel(duplicate))
                dao.insertItem(SavedItemEntity.fromDomainModel(SavedItem("algorithm-device-note", SavedItemType.TEXT,
                    "Preserved note fixture", "This body must survive recommendation corrections.")))
                assertTrue(marker.edit().putLong("at", now).putInt("pid", Process.myPid())
                    .putString("question", question.token).commit())
            } else {
                val now = marker.getLong("at", 0)
                assertTrue("Run the seed phase first", now > 0)
                assertNotEquals("Force-stop between phases to verify a new process", marker.getInt("pid", -1), Process.myPid())
                val beforeTitle = dao.getItemById("algorithm-device-title")!!
                val beforeNote = dao.getItemById("algorithm-device-note")!!
                val items = listOf(beforeTitle.toDomainModel(), dao.getItemById("algorithm-device-duplicate")!!.toDomainModel())
                val key = store.canonicalKey(items.first())
                val initial = store.learningSnapshot(now)
                assertEquals(1, initial.signals.reactions.size)
                assertEquals(ScreeningPreference.LIKED, store.reaction("algorithm-device-title")!!.value)
                assertTrue("rating:$key" in initial.signals.ignoredEvidence)
                val profile = ScreeningTaste.profile(items, initial.signals, now)
                assertEquals(60, profile.genres.size)
                assertEquals(5, ScreeningGenres.overview(profile.genres).visible.size)
                assertEquals(55, ScreeningGenres.overview(profile.genres).others.size)
                assertEquals(1, profile.titleCount)
                assertNull(profile.evidence.single { it.kind == ScreeningEvidenceKind.RATING }.at)
                val publication = initial.publications.single()
                assertNotNull(publication.reason)
                val trace = publication.trace!!
                assertEquals(trace.rawPoints, trace.features.values.sum(), .000001)
                assertEquals(trace.selectionPoints, trace.rawPoints + trace.varietyAdjustment, .000001)
                assertTrue(trace.shortlist.isNotEmpty())
                repeat(3) { assertEquals(initial, store.learningSnapshot(now)) }
                assertFalse(store.publish(publication))
                assertEquals(1, store.snapshot(now).items.getValue(key).shownCount)

                store.clearReaction("tv:local:algorithm-device-title", now)
                store.ignoreEvidence("rating:tv:local:algorithm-device-title", false, now)
                val corrected = ScreeningTaste.profile(items, store.snapshot(now), now)
                assertFalse(corrected.evidence.single { it.kind == ScreeningEvidenceKind.RATING }.ignored)
                assertTrue(corrected.evidence.none { it.kind == ScreeningEvidenceKind.LIKE && it.contributes })
                val token = marker.getString("question", null)!!
                val questionItem = items.single { it.id == store.delivery(token)!!.itemId }
                store.resetLearning()
                assertTrue(store.delivery(token)!!.answered)
                assertFalse(store.answer(token, questionItem, ScreeningFeedback.USEFUL))
                val reset = store.snapshot(now)
                assertTrue(reset.reactions.isEmpty())
                assertTrue(reset.feedback.isEmpty())
                assertTrue(ScreeningTaste.profile(items, reset, now).genres.all { (it.weight ?: 0.0) > 0.0 })
                assertEquals(beforeTitle, dao.getItemById("algorithm-device-title"))
                assertEquals(beforeNote, dao.getItemById("algorithm-device-note"))
                assertEquals(3, dao.getItemById("algorithm-device-title")!!.toDomainModel().showProgress!!.totalWatched)
                marker.edit().clear().commit()
            }
            Unit
        } finally { archive.close() }
    }

    private fun title(now: Long) = SavedItem("algorithm-device-title", SavedItemType.MEDIA, "Persistence fixture", "",
        timestamp = now - 45 * SCREENING_DAY, updatedAt = now, mediaType = "tv", userRating = 9,
        watchStatus = "Watching", genres = (1..60).map { "Genre ${it.toString().padStart(2, '0')}" },
        showProgress = TvShowProgress(totalAired = 5, totalWatched = 3))
}

/** Give persistence fixtures their own database and preferences, away from the emulator user's app data. */
private class IsolatedLearningContext(base: Context) : ContextWrapper(base) {
    private fun fixtureName(value: String) = "algorithm_device_$value"
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences(fixtureName(name), mode)
    override fun getDatabasePath(name: String): File = super.getDatabasePath(fixtureName(name))
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        super.openOrCreateDatabase(fixtureName(name), mode, factory)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
        errorHandler: DatabaseErrorHandler?): SQLiteDatabase = super.openOrCreateDatabase(fixtureName(name), mode, factory, errorHandler)
}
