package com.example.ui.screens

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import java.util.Locale
import com.example.data.model.SavedItemType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.components.bounceClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.SecondBrainViewModel
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: SecondBrainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToLegal: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val userEmail by viewModel.userEmail.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userPhotoUrl by viewModel.userPhotoUrl.collectAsState()
    val geminiApiKey by viewModel.settingsRepository.geminiApiKey.collectAsState()
    val selectedModel by viewModel.settingsRepository.selectedModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    
    var apiKeyInput by remember { mutableStateOf(geminiApiKey) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Profile", "Settings")

    LaunchedEffect(Unit) {
        if (availableModels.isEmpty()) {
            viewModel.fetchAvailableModels()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Profile & Settings", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.bounceClick().testTag("profile_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title, fontWeight = FontWeight.Medium) }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (selectedTabIndex == 0) {
                // User Profile Section
                val allItems by viewModel.allItems.collectAsState()
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // 1. Beautiful Avatar & Profile Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Circular Avatar with Gradient
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        ),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!userPhotoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = userPhotoUrl,
                                        contentDescription = "Profile Picture",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    val initials = if (userEmail != null) {
                                        val part = userEmail!!.substringBefore("@").lowercase()
                                        if (part.contains("hannanbhatti") || part.contains("hanan")) "HB"
                                        else part.take(2).uppercase()
                                    } else {
                                        "G"
                                    }
                                    Text(
                                        text = initials,
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            val displayName = userName ?: if (userEmail != null) {
                                val part = userEmail!!.substringBefore("@")
                                if (part.lowercase().contains("hannanbhatti") || part.lowercase().contains("hanan")) {
                                    "Hanan Bhatti"
                                } else {
                                    part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                                }
                            } else {
                                "Guest User"
                            }
                            
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Text(
                                text = userEmail ?: "Local/Offline Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // User status pill
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (userEmail != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(0.5.dp, if (userEmail != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (userEmail != null) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (userEmail != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (userEmail != null) "Cloud Synced" else "Offline Only",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (userEmail != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // 2. Statistics Grid
                    SectionCard(title = "Your Knowledge Metrics") {
                        val totalItems = allItems.size
                        val totalText = allItems.count { it.type == SavedItemType.TEXT }
                        val totalLinks = allItems.count { it.type == SavedItemType.LINK }
                        val totalImages = allItems.count { it.type == SavedItemType.IMAGE }
                        val totalVideos = allItems.count { it.type == SavedItemType.VIDEO }
                        val totalAudio = allItems.count { it.type == SavedItemType.AUDIO }
                        val totalCode = allItems.count { it.type == SavedItemType.CODE }
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Total captures row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderSpecial,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Total Captures",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "$totalItems",
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            // Types Grid (2 columns)
                            val statsList = listOf(
                                StatItem(title = "Texts", count = totalText, icon = Icons.Outlined.Description, tint = MaterialTheme.colorScheme.primary),
                                StatItem(title = "Links", count = totalLinks, icon = Icons.Outlined.Link, tint = MaterialTheme.colorScheme.secondary),
                                StatItem(title = "Voice Notes", count = totalAudio, icon = Icons.Outlined.Mic, tint = MaterialTheme.colorScheme.tertiary),
                                StatItem(title = "Images", count = totalImages + totalVideos, icon = Icons.Outlined.Image, tint = MaterialTheme.colorScheme.primary),
                                StatItem(title = "Code Snips", count = totalCode, icon = Icons.Outlined.Code, tint = MaterialTheme.colorScheme.secondary)
                            )
                            
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                statsList.chunked(2).forEach { chunk ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        chunk.forEach { stat ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = stat.icon,
                                                        contentDescription = null,
                                                        tint = stat.tint,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Column {
                                                        Text(stat.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text("${stat.count}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                                    }
                                                }
                                            }
                                        }
                                        if (chunk.size < 2) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Actions Card
                    if (userEmail != null) {
                        Button(
                            onClick = { viewModel.signOut() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Outlined.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out Account", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onNavigateToAuth,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Outlined.Login, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign In / Register", fontWeight = FontWeight.Bold)
                        }
                    }

                    // 4. Legal & Info Card
                    SectionCard(title = "Information & Legal") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { onNavigateToLegal("about") }) {
                                Text("About Second Brain", color = MaterialTheme.colorScheme.onSurface)
                            }
                            TextButton(onClick = { onNavigateToLegal("faq") }) {
                                Text("Frequently Asked Questions", color = MaterialTheme.colorScheme.onSurface)
                            }
                            TextButton(onClick = { onNavigateToLegal("privacy") }) {
                                Text("Privacy Policy", color = MaterialTheme.colorScheme.onSurface)
                            }
                            TextButton(onClick = { onNavigateToLegal("terms") }) {
                                Text("Terms & Conditions", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            } else {
                // Gemini settings section
                SectionCard(title = "Gemini AI Settings") {
                    Text(
                        text = "API Key",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = { Text("Enter Gemini API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        trailingIcon = {
                            IconButton(onClick = {
                                viewModel.settingsRepository.setGeminiApiKey(apiKeyInput)
                                viewModel.fetchAvailableModels()
                                Toast.makeText(context, "Gemini API Key saved", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Outlined.Save, contentDescription = "Save Key")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Your API key is stored locally on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "OCR Model",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = showModelDropdown,
                        onExpandedChange = { showModelDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModelDropdown) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(20.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = showModelDropdown,
                            onDismissRequest = { showModelDropdown = false }
                        ) {
                            val modelsToShow = availableModels.ifEmpty {
                                listOf(
                                    "gemini-flash-lite-latest",
                                    "gemini-2.5-flash",
                                    "gemini-1.5-flash",
                                    "gemini-1.5-pro",
                                    "gemini-2.0-flash-exp"
                                )
                            }
                            modelsToShow.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        viewModel.settingsRepository.setSelectedModel(model)
                                        showModelDropdown = false
                                        Toast.makeText(context, "OCR Model updated to $model", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            if (availableModels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("⚠️ Showing standard models. Save API key to fetch custom list.", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary) },
                                    onClick = { showModelDropdown = false }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.fetchAvailableModels(isUserTriggered = true) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("Refresh Model List")
                    }
                }

                SectionCard("OCR Settings") {
                    val ocrSensitivity by viewModel.settingsRepository.ocrSensitivity.collectAsState()
                    var showOcrDropdown by remember { mutableStateOf(false) }
                    val ocrOptions = listOf("Low", "Medium", "High")

                    Text(
                        text = "Extraction Sensitivity",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = showOcrDropdown,
                        onExpandedChange = { showOcrDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = ocrSensitivity,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showOcrDropdown) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(20.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = showOcrDropdown,
                            onDismissRequest = { showOcrDropdown = false }
                        ) {
                            ocrOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        viewModel.settingsRepository.setOcrSensitivity(option)
                                        showOcrDropdown = false
                                        Toast.makeText(context, "Extraction Sensitivity updated to $option", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                SectionCard("Appearance") {
                    val themeMode by viewModel.settingsRepository.themeMode.collectAsState()
                    var showThemeDropdown by remember { mutableStateOf(false) }
                    val themeOptions = listOf("System Default", "Light", "Dark")

                    Text(
                        text = "App Theme",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = showThemeDropdown,
                        onExpandedChange = { showThemeDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = themeMode,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showThemeDropdown) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(20.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = showThemeDropdown,
                            onDismissRequest = { showThemeDropdown = false }
                        ) {
                            themeOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        viewModel.settingsRepository.setThemeMode(option)
                                        showThemeDropdown = false
                                        Toast.makeText(context, "App Theme updated to $option", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                content = content
            )
        }
    }
}

private data class StatItem(
    val title: String,
    val count: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color
)
