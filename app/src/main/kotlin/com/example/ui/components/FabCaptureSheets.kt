package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.ui.viewmodel.CobaltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FabActionSheet(title: String, items: List<RadialMenuItem>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
            items.forEach { item ->
                Surface(onClick = { onDismiss(); item.action() }, shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(painterResource(item.iconResId), null, tint = item.color, modifier = Modifier.size(22.dp))
                        Text(item.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** System pickers return into the normal reviewable Capture draft; never auto-save. */
@Composable
internal fun rememberFabImageAction(viewModel: CobaltViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.handleSharedIntent("image/*", null, uri)
    }
    val cameraContract = remember { object : ActivityResultContracts.TakePicture() {
        override fun createIntent(context: android.content.Context, input: android.net.Uri): android.content.Intent =
            super.createIntent(context, input).apply {
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("Camera capture", input)
            }
    } }
    val camera = rememberLauncherForActivityResult(cameraContract) { success ->
        val path = cameraPath
        cameraPath = null
        if (path != null) {
            if (success) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
                viewModel.handleSharedIntent("image/jpeg", null, uri)
                // The source is kept until staging finishes; existing aged-share cache cleanup reclaims it.
            } else scope.launch(Dispatchers.IO) { File(path).delete() }
        }
    }
    if (visible) FabActionSheet("Add image", listOf(
        RadialMenuItem(R.drawable.ic_custom_image, "Choose from gallery", MaterialTheme.colorScheme.primary) {
            try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            catch (_: Exception) { Toast.makeText(context, "The image picker is unavailable. Try sharing an image to Cobalt.", Toast.LENGTH_LONG).show() }
        },
        RadialMenuItem(R.drawable.ic_capture_camera, "Take photo", MaterialTheme.colorScheme.primary) {
            scope.launch {
                try {
                    val uri = withContext(Dispatchers.IO) {
                        val dir = File(context.cacheDir, "shares/capture").apply { check(mkdirs() || isDirectory) }
                        val file = File.createTempFile("capture-", ".jpg", dir)
                        cameraPath = file.path
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    }
                    camera.launch(uri)
                } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (_: Exception) {
                    cameraPath?.let { path -> withContext(Dispatchers.IO) { File(path).delete() } }
                    cameraPath = null
                    Toast.makeText(context, "The camera is unavailable. You can choose an image from your gallery.", Toast.LENGTH_LONG).show()
                }
            }
        }
    ), onDismiss = { visible = false })
    return { visible = true }
}
