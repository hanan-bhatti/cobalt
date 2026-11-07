package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.model.TvShowProgress
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaRatingsRowTest {
    @Test fun unavailableAndInvalidScoresAreNotInvented() {
        assertTrue(externalRatingBadges(null).isEmpty())
        assertTrue(externalRatingBadges(TvShowProgress(imdbRating = "N/A",
            rottenTomatoesScore = "101%", popcornScore = "NaN")).isEmpty())
    }

    @Test fun cachedScoresKeepTheirOwnSourceAndScale() {
        val badges = externalRatingBadges(TvShowProgress(imdbRating = "8.6",
            rottenTomatoesScore = " 93% ", popcornScore = "0"))
        assertEquals(listOf("IMDb", "Rotten Tomatoes critics", "Popcornmeter audience"), badges.map { it.source })
        assertEquals(listOf("8.6/10", "93%", "0%"), badges.map { it.score })
    }

    @Test fun suppliedVectorsRenderInBrandColorsAtBadgeSize() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val logos = listOf(R.drawable.ic_rating_imdb to 0xFFF6C700.toInt(),
            R.drawable.ic_rating_rotten_tomatoes to 0xFFF93208.toInt(),
            R.drawable.ic_rating_popcornmeter to 0xFFDB382A.toInt())
        logos.forEach { (id, expectedColor) ->
            val drawable = requireNotNull(ResourcesCompat.getDrawable(context.resources, id, context.theme))
            val width = if (id == R.drawable.ic_rating_imdb) 108 else 57
            val bitmap = Bitmap.createBitmap(width, 54, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, width, 54)
            drawable.draw(Canvas(bitmap))
            val pixels = IntArray(width * 54)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, 54)
            assertTrue("Logo must remain visible: $id", pixels.count { it ushr 24 > 0 } > width * 5)
            assertTrue("Logo must preserve supplied color: $id", pixels.any { it == expectedColor })
            val output = java.io.File("build/reports/rating-logos", context.resources.getResourceEntryName(id) + ".png")
            output.parentFile.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
