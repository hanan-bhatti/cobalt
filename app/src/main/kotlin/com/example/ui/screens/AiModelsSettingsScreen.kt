package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.remote.ai.*
import com.example.ui.components.*
import com.example.ui.viewmodel.CobaltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AiModelsSettingsScreen(viewModel: CobaltViewModel, onBack: () -> Unit, onUsage: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AiProviderStore(context) }
    val revision by AiProviderStore.changes.collectAsState()
    val provider = remember(revision) { store.provider() }
    val audioProvider = remember(revision) { store.provider(audio = true) }
    var page by rememberSaveable { mutableStateOf("main") }
    var providerSheet by remember { mutableStateOf<Boolean?>(null) }
    var modelSheet by remember { mutableStateOf<Boolean?>(null) }
    var returnToModels by remember { mutableStateOf(false) }
    BackHandler(page != "main") { page = if (page == "guide") "advanced" else "main" }
    when (page) {
        "advanced" -> AiAdvancedSettings(viewModel, store, { page = "main" }, { page = "guide" })
        "guide" -> AiPromptGuide { page = "advanced" }
        else -> AiSettingsPage("AI models", "Providers, text capture and voice notes", onBack) {
            item {
                SettingsGroupLabel("Provider")
                ExpressiveSettingsNavigationItem(
                    title = provider.label, subtitle = "Choose where image captures are processed",
                    leadingIcon = { SettingsIconBadge(R.drawable.ic_custom_ai_brain) },
                    onClick = { providerSheet = false }, compact = true
                )
                Spacer(Modifier.height(8.dp))
                key(provider) { AiApiKeyCard(provider, store) }
            }
            item {
                SettingsGroupLabel("Models")
                ExpressiveSettingsGroup {
                    ExpressiveSettingsNavigationItem(
                        title = "Image model", subtitle = "${provider.label} · ${store.model(provider).ifBlank { "Choose a model" }}",
                        onClick = { modelSheet = false }, shape = connectedPillShape(0, 2), compact = true
                    )
                    ExpressiveSettingsNavigationItem(
                        title = "Audio model", subtitle = "${audioProvider.label} · ${store.model(audioProvider, true).ifBlank { "Choose a model" }}",
                        onClick = { modelSheet = true }, shape = connectedPillShape(1, 2), compact = true
                    )
                }
            }
            item {
                SettingsGroupLabel("Activity")
                ExpressiveSettingsNavigationItem("Usage", onUsage, subtitle = "Requests and provider-reported tokens", compact = true,
                    leadingIcon = { SettingsIconBadge(R.drawable.ic_custom_history) })
            }
            item {
                ExpressiveSettingsNavigationItem("Advanced settings", { page = "advanced" },
                    subtitle = "Capture instructions, text detail and prompt guide", compact = true,
                    leadingIcon = { SettingsIconBadge(R.drawable.ic_custom_info) })
            }
        }
    }
    providerSheet?.let { audio ->
        AiProviderSheet(if (audio) audioProvider else provider, { providerSheet = null; if (returnToModels) { modelSheet = audio; returnToModels = false } }) { chosen ->
            store.select(chosen, audio)
            providerSheet = null
            if (returnToModels) { modelSheet = audio; returnToModels = false }
        }
    }
    modelSheet?.let { audio ->
        key(audio, if (audio) audioProvider else provider) {
            AiModelSheet(store, audio, { modelSheet = null }, {
                modelSheet = null
                returnToModels = true
                providerSheet = audio
            })
        }
    }
}

@Composable
internal fun AiSettingsPage(title: String, subtitle: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    val list = rememberLazyListState()
    val header = rememberCollapsibleTopBarState(list, maxHeight = 160.dp)
    val height = with(LocalDensity.current) { header.heightPx.toDp() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).nestedScroll(header.nestedScrollConnection)) {
        LazyColumn(
            state = list, modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = height + 12.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp), content = content
        )
        CollapsibleCommonTopBar(title = title, subtitle = subtitle, collapseFraction = header.collapseFraction,
            headerHeight = height, onBackClick = onBack)
    }
}

@Composable
private fun AiApiKeyCard(provider: AiProvider, store: AiProviderStore) {
    val context = LocalContext.current
    var keyText by remember(provider) { mutableStateOf(store.key(provider)) }
    var endpoint by remember(provider) { mutableStateOf(store.endpoint(provider)) }
    var keyPage by remember(provider) { mutableStateOf(store.keyPage(provider).orEmpty()) }
    var visible by remember(provider) { mutableStateOf(false) }
    var saved by remember(provider) { mutableStateOf(false) }
    var error by remember(provider) { mutableStateOf<String?>(null) }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiInfoLabel("API key", "Saving stores the key on this device; it does not validate it with the provider. Some local servers accept requests without a key. Hosted providers and authenticated local servers need one.")
            Text("${provider.label} receives the images or recordings you send for processing.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextField(
                value = keyText, onValueChange = { keyText = it; saved = false; error = null },
                placeholder = { Text("Paste API key") },
                singleLine = true, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { IconButton(onClick = { visible = !visible }) {
                    Icon(painterResource(if (visible) R.drawable.ic_custom_eye_off else R.drawable.ic_custom_eye), if (visible) "Hide API key" else "Show API key")
                } },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${provider.label} API key" }
            )
            if (provider == AiProvider.CUSTOM) {
                Text("API key is optional only if your server allows requests without one.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AiFilledField(endpoint, { endpoint = it; saved = false }, "API endpoint · required",
                    "https://your-provider.example/v1/", keyboardType = KeyboardType.Uri,
                    info = "The API base URL from your provider’s documentation. This is required even when no key is needed. For a local server, use its network address; localhost means this phone.")
                AiFilledField(keyPage, { keyPage = it; saved = false }, "API key page · optional",
                    "https://your-provider.example/settings/keys", keyboardType = KeyboardType.Uri,
                    info = "An optional shortcut to your provider’s API-key page. This does not change the API endpoint used for requests.")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (saved) Text("Settings saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    try { store.saveConnection(provider, keyText, endpoint, keyPage); saved = true; error = null }
                    catch (e: IllegalArgumentException) { error = e.message ?: "Check the URLs and try again." }
                    catch (_: Exception) { error = "Could not protect the API key. The previous saved key was retained. Try saving again." }
                }) { Text("Save") }
                val link = store.keyPage(provider)
                if (link != null) TextButton(onClick = { com.example.util.CobaltCustomTabsLauncher.launch(context, link) }, modifier = Modifier.weight(1f)) {
                    Text(if (provider == AiProvider.GEMINI) "Get key in AI Studio" else "Get ${provider.label} API key")
                }
            }
            if (provider == AiProvider.CUSTOM && store.keyPage(provider) == null)
                Text("Get an API key from your provider. Add its key-page URL above to keep a shortcut here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AiProviderSheet(selected: AiProvider, onDismiss: () -> Unit, onSelect: (AiProvider) -> Unit) {
    SettingsBottomSheet("Choose provider", "Each provider keeps its own API key and model selections.", onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProvider.entries.forEach { provider ->
                Surface(onClick = { onSelect(provider) }, shape = RoundedCornerShape(20.dp),
                    color = if (provider == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(provider.label, style = MaterialTheme.typography.titleMedium)
                            Text(if (provider == AiProvider.CUSTOM) "Use your own compatible API endpoint" else "Use your own ${provider.label} API key",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RadioButton(selected = provider == selected, onClick = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiModelSheet(store: AiProviderStore, audio: Boolean, onDismiss: () -> Unit, onProvider: () -> Unit) {
    val context = LocalContext.current
    val configurationRevision by AiProviderStore.changes.collectAsState()
    val provider = store.provider(audio)
    val scope = rememberCoroutineScope()
    var models by remember(provider) { mutableStateOf(store.cachedModels(provider)) }
    var query by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(store.model(provider, audio)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var setup by remember { mutableStateOf(false) }
    suspend fun refresh() {
        loading = true; error = null
        try { models = AiGateway(context).discover(store.connection(audio)) }
        catch (cancel: CancellationException) { throw cancel }
        catch (e: Exception) { error = e.message ?: "Could not load models. Try again." }
        finally { loading = false }
    }
    LaunchedEffect(provider, configurationRevision) { refresh() }
    val candidate = manual.trim().ifBlank { selected }
    val filtered = models.filter { it.id.contains(query, true) || it.label.contains(query, true) }
    SettingsBottomSheet(if (audio) "Audio model" else "Image model", "Choose from ${provider.label}, or add a custom model below.", onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().heightIn(max = 600.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(query, { query = it }, placeholder = { Text("Search models") }, singleLine = true,
                leadingIcon = { Icon(painterResource(R.drawable.ic_custom_search), null) },
                trailingIcon = {
                    IconButton(enabled = !loading, onClick = { scope.launch { refresh() } }) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(painterResource(R.drawable.ic_custom_sync), "Refresh model list")
                    }
                }, shape = CircleShape, colors = aiFieldColors(), modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(provider.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onProvider) { Text("Change provider") }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(0.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                item {
                    if (audio && provider == AiProvider.OPENROUTER) Text("Choose another provider for voice notes. Cobalt does not support audio uploads here yet.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp)) }
                    if (filtered.isEmpty()) Text(if (loading) "Loading available models…" else if (query.isNotBlank()) "No models match this search." else "No models available. Check provider setup or add a custom ID below.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
                items(filtered, key = { it.id }) { model ->
                    val chosen = candidate == model.id
                    Surface(onClick = { selected = model.id; manual = "" }, enabled = audio || model.support != "No image input advertised",
                        color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(model.label.ifBlank { model.id }, style = MaterialTheme.typography.titleMedium)
                                if (model.label != model.id) Text(model.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!audio && model.support == "No image input advertised") Text(model.support, style = MaterialTheme.typography.labelSmall)
                            }
                            RadioButton(chosen, onClick = null)
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(20.dp))
                    AiFilledField(manual, { manual = it }, "Custom model ID · optional", "Enter an exact model ID")
                    Text("Only needed when your model is missing from the list. Model task support is not verified by listing it.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = { setup = !setup }) { Text(if (setup) "Close provider setup" else "API key and setup link") }
                    if (setup) AiApiKeyCard(provider, store)
                }
            }
            if (candidate.isNotBlank()) Text(candidate, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2)
            Button(enabled = candidate.isNotBlank() && candidate.length <= 256 && !(audio && provider == AiProvider.OPENROUTER),
                onClick = { store.saveModel(provider, candidate, audio); onDismiss() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Use model") }
        }
    }
}

@Composable
internal fun aiFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent
)

@Composable
internal fun AiFilledField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String,
    minLines: Int = 1, maxLines: Int = 1, keyboardType: KeyboardType = KeyboardType.Text, info: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (info != null) AiInfoLabel(label, info)
        else Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp))
        TextField(value, onValueChange, placeholder = { Text(placeholder) },
            singleLine = maxLines == 1, minLines = minLines, maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(20.dp), colors = aiFieldColors(),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
    }
}

/** Tap-to-open explanation anchored to its label; dismisses on outside tap or Back. */
@Composable
internal fun AiInfoLabel(label: String, explanation: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(40.dp)) {
                Icon(painterResource(R.drawable.ic_custom_info), contentDescription = "About $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            if (expanded) RoundedInfoPopup("About $label", explanation) { expanded = false }
        }
    }
}
