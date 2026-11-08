package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.*
import androidx.annotation.Keep
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.media.YouTubeEmbed
import com.example.ui.screens.extractYouTubeVideoId
import kotlinx.coroutines.delay

/** Narrow, event-only bridge. It has no file, account, navigation or credential access. */
@Keep
internal class TrailerEvents(private val onReady: () -> Unit, private val onState: (Int) -> Unit, private val onError: (Int) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var active = true
    @JavascriptInterface fun ready() { main.post { if (active) onReady() } }
    @JavascriptInterface fun state(value: Int) { main.post { if (active) onState(value) } }
    @JavascriptInterface fun error(value: Int) { main.post { if (active) onError(value) } }
    fun release() { active = false; main.removeCallbacksAndMessages(null) }
}

private fun WebView.registerTrailerEvents(events: TrailerEvents) {
    addJavascriptInterface(events, "CobaltTrailer")
}

@Composable
fun InAppTrailerPlayer(trailerUrl: String, modifier: Modifier = Modifier) {
    val video = remember(trailerUrl) { extractYouTubeVideoId(trailerUrl) }
    val uri = remember(trailerUrl) { Uri.parse(trailerUrl) }
    if (video == null) {
        if (uri.scheme == "https" && uri.path.orEmpty().substringAfterLast('.').lowercase() in listOf("mp4", "webm", "m4v", "mov")) {
            VideoPlayer(videoUri = trailerUrl, modifier = modifier)
        } else {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This provider doesn’t offer an in-app trailer.", style = MaterialTheme.typography.bodySmall)
                if (uri.scheme == "https" && !uri.host.isNullOrBlank()) TrailerProviderButton(trailerUrl)
            }
        }
        return
    }
    var started by remember(video) { mutableStateOf(false) }
    var attempt by remember(video) { mutableIntStateOf(0) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!started) {
            Surface(onClick = { started = true }, shape = RoundedCornerShape(16.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    AsyncImage("https://img.youtube.com/vi/$video/hqdefault.jpg", null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Icon(painterResource(R.drawable.ic_custom_play), "Play trailer", Modifier.padding(16.dp).size(28.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        } else key(video, attempt) {
            EmbeddedYouTubePlayer(video, onRetry = { attempt++ })
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedYouTubePlayer(videoId: String, onRetry: () -> Unit) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var ready by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    var initialPlayStarted by remember { mutableStateOf(false) }
    var rendererGone by remember { mutableStateOf(false) }
    val events: TrailerEvents = remember {
        TrailerEvents(onReady = { ready = true }, onState = { playing = it == 1 }, onError = { code ->
            playing = false
            failure = when (code) {
                100 -> "This trailer has been removed or made private."
                101, 150 -> "The provider has restricted embedded playback for this trailer."
                153 -> "YouTube could not verify the player. Retry the trailer."
                else -> "This trailer couldn’t play. Check your connection and retry."
            }
        })
    }
    val web = remember {
        runCatching { WebView(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            registerTrailerEvents(events)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !YouTubeEmbed.allowNavigation(request.url, request.isForMainFrame)
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) failure = "Couldn’t load the trailer. Check your connection and retry."
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    rendererGone = true
                    events.release()
                    view.destroy()
                    failure = "The video player stopped. Retry the trailer."
                    return true
                }
            }
            webChromeClient = WebChromeClient()
            loadDataWithBaseURL(YouTubeEmbed.origin(BuildConfig.APPLICATION_ID) + "/", YouTubeEmbed.html(videoId, BuildConfig.APPLICATION_ID), "text/html", "UTF-8", null)
        } }.getOrNull()
    }
    if (web == null) {
        Text("The Android video player is unavailable.", style = MaterialTheme.typography.bodySmall)
        TrailerProviderButton("https://www.youtube.com/watch?v=$videoId")
        DisposableEffect(events) { onDispose { events.release() } }
        return
    }
    LaunchedEffect(ready, failure, visible) {
        if (rendererGone) return@LaunchedEffect
        if (failure != null || !visible) {
            web.evaluateJavascript("if(cobaltPlayer&&cobaltPlayer.pauseVideo)cobaltPlayer.pauseVideo();", null)
            if (failure != null) web.stopLoading()
        } else if (ready && !initialPlayStarted && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            web.evaluateJavascript("cobaltPlayer.playVideo();", null)
            initialPlayStarted = true
        }
    }
    DisposableEffect(web, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (rendererGone) return@LifecycleEventObserver
            if (event == Lifecycle.Event.ON_PAUSE) {
                web.evaluateJavascript("if(cobaltPlayer&&cobaltPlayer.pauseVideo)cobaltPlayer.pauseVideo();", null)
                web.onPause()
                playing = false
            } else if (event == Lifecycle.Event.ON_RESUME) web.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            events.release()
            if (!rendererGone) {
                web.stopLoading()
                web.removeJavascriptInterface("CobaltTrailer")
                web.destroy()
            }
        }
    }
    LaunchedEffect(web) {
        delay(20_000)
        if (!ready && failure == null) failure = "The trailer is taking too long to load. Check your connection and retry."
    }
    if (failure == null) {
        AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).heightIn(min = 200.dp).clip(RoundedCornerShape(16.dp))
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow(clipBounds = false)
                val viewport = androidx.compose.ui.geometry.Rect(0f, 0f, hostView.rootView.width.toFloat(), hostView.rootView.height.toFloat())
                val shown = bounds.intersect(viewport)
                visible = bounds.width > 0 && bounds.height > 0 && shown.width.coerceAtLeast(0f) * shown.height.coerceAtLeast(0f) >= bounds.width * bounds.height / 2f
            })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = {
                web.evaluateJavascript(if (playing) "cobaltPlayer.pauseVideo();" else "cobaltPlayer.playVideo();", null)
            }, enabled = ready) {
                Icon(painterResource(if (playing) R.drawable.ic_custom_pause else R.drawable.ic_custom_play), if (playing) "Pause trailer" else "Play trailer", Modifier.size(24.dp))
            }
            if (!ready) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Loading trailer…", style = MaterialTheme.typography.bodySmall)
            } else Text(if (playing) "Playing trailer" else "Trailer paused", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(failure!!, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onRetry) { Text("Retry trailer") }
                TrailerProviderButton("https://www.youtube.com/watch?v=$videoId")
            }
        }
    }
}

@Composable
private fun TrailerProviderButton(url: String) {
    val context = LocalContext.current
    TextButton(onClick = {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(context, "No app can open this trailer", android.widget.Toast.LENGTH_SHORT).show() }
    }) { Text("Open on provider") }
}
