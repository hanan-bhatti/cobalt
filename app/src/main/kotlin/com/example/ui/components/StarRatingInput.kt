package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.example.R
import org.xmlpull.v1.XmlPullParser
import kotlin.math.roundToInt

/** Five half-step stars map exactly to Trakt's integer 1–10 scores. Reuses Cobalt's star geometry. */
@Composable
internal fun StarRatingInput(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, onCommit: ((Int) -> Unit)? = null) {
    val context = LocalContext.current
    val star = remember(context) {
        val xml = context.resources.getXml(R.drawable.ic_custom_star)
        try {
            var data: String? = null
            while (xml.eventType != XmlPullParser.END_DOCUMENT && data == null) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == "path")
                    data = xml.getAttributeValue("http://schemas.android.com/apk/res/android", "pathData")
                xml.next()
            }
            // The first subpath is the existing rounded star; the second cuts out its outline.
            requireNotNull(PathParser.createPathFromPathData(requireNotNull(data).substringBefore('Z') + "Z")).asComposePath()
        } finally { xml.close() }
    }
    val latestChange by rememberUpdatedState(onChange)
    val latestCommit by rememberUpdatedState(onCommit)
    val currentValue by rememberUpdatedState(value)
    val selected = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    fun pick(x: Float, width: Float): Int = kotlin.math.ceil(x / width * 10).toInt().coerceIn(1, 10).also(latestChange)
    Row(modifier.width(240.dp).testTag("media-rating-stars")
        .pointerInput(Unit) {
            var selectedValue = value
            var originalValue = value
            detectDragGestures(onDragStart = { originalValue = currentValue; selectedValue = pick(it.x, size.width.toFloat()) },
                onDrag = { change, _ -> change.consume(); selectedValue = pick(change.position.x, size.width.toFloat()) },
                onDragEnd = { latestCommit?.invoke(selectedValue) }, onDragCancel = { latestChange(originalValue) })
        }
        .semantics(mergeDescendants = true) {
        stateDescription = "${value / 2f} of 5 stars, $value of 10"
        progressBarRangeInfo = ProgressBarRangeInfo(value / 2f, 0f..5f, 9)
        setProgress { val selectedValue = (it * 2).roundToInt().coerceIn(1, 10); latestChange(selectedValue); latestCommit?.invoke(selectedValue); true }
    }, horizontalArrangement = Arrangement.Center) {
        repeat(5) { index ->
            Canvas(Modifier.size(48.dp).pointerInput(index) {
                detectTapGestures { position -> val selectedValue = index * 2 + if (position.x < size.width / 2f) 1 else 2; latestChange(selectedValue); latestCommit?.invoke(selectedValue) }
            }) {
                val padding = 6.dp.toPx()
                val side = size.width - padding * 2
                val fill = ((value - index * 2) / 2f).coerceIn(0f, 1f)
                translate(padding, padding) {
                    scale(side / 24f, side / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                        drawPath(star, empty)
                        clipRect(right = 24f * fill) { drawPath(star, selected) }
                    }
                }
            }
        }
    }
}
