package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.util.CobaltCustomTabsLauncher
import com.example.util.GitHubReleaseRepository
import com.example.util.ReleaseApk
import com.example.util.formatFileSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** An APK action always revalidates the selected version/asset before opening its URL. */
@Composable
fun ReleaseApkActions(
    version: String, assets: List<ReleaseApk>, releaseUrl: String? = null,
    onDownloadOpened: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(version) { mutableStateOf(false) }
    var error by remember(version) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (assets.isEmpty()) Text("Download unavailable", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        assets.forEach { asset ->
            OutlinedButton(enabled = !busy, onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        val confirmed = GitHubReleaseRepository.verifyApk(context, version, asset)
                        CobaltCustomTabsLauncher.launch(context, confirmed.url)
                        onDownloadOpened()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        error = (failure as? IllegalStateException)?.message
                            ?: "Could not confirm this APK. Refresh the release and try again."
                    }
                    finally { busy = false }
                }
            }) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                else Icon(painterResource(R.drawable.ic_custom_download), null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(asset.label + " (" + formatFileSize(asset.sizeBytes) + ")")
            }
        }
        if (error != null) Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (!releaseUrl.isNullOrBlank()) TextButton(onClick = { CobaltCustomTabsLauncher.launch(context, releaseUrl) }) {
            Text("View GitHub release")
        }
    }
}
