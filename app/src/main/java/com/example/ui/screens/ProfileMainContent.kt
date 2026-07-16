package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.SavedItemType
import androidx.compose.ui.res.painterResource
import java.util.Locale
import com.example.ui.viewmodel.SecondBrainViewModel
import androidx.compose.runtime.collectAsState

@Composable
fun ProfileMainContent(
    viewModel: SecondBrainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToManageStorage: () -> Unit
) {
    val userEmail by viewModel.userEmail.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userPhotoUrl by viewModel.userPhotoUrl.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val usedStorageBytes by viewModel.usedStorageBytes.collectAsState()
    
    val totalItems = allItems.size
    val totalLinks = allItems.count { it.type == SavedItemType.LINK }
    val totalImages = allItems.count { it.type == SavedItemType.IMAGE }
    val totalVideos = allItems.count { it.type == SavedItemType.VIDEO }
    val totalText = allItems.count { it.type == SavedItemType.TEXT }
    val totalCode = allItems.count { it.type == SavedItemType.CODE }
    
    val isSyncing by viewModel.isSyncing.collectAsState()
    
    @OptIn(ExperimentalMaterial3Api::class)
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = { viewModel.syncData() },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // HEADER
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (userEmail == null) {
                    // Signed out
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_custom_profile),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Text(
                            text = "Guest",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Sign in to sync captures across all your devices",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onNavigateToAuth,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Sign in to backup", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    // Signed in
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.onSurface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!userPhotoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = userPhotoUrl,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                val initials = userEmail!!.substringBefore("@").take(1).uppercase()
                                Text(text = initials, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userName ?: userEmail!!.substringBefore("@").replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = userEmail ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFF34A853), CircleShape))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Synced just now", style = MaterialTheme.typography.bodySmall, color = Color(0xFF34A853), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            
            // YOUR ARCHIVE
            SectionContainer(title = "YOUR ARCHIVE") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.Bottom) {
                        Text(text = "$totalItems", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "items captured", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArchiveStatRow(
                            iconResId = R.drawable.ic_custom_link, count = totalLinks, label = "Links",
                            baseColor = Color(0xFF42A5F5)
                        )
                        ArchiveStatRow(
                            iconResId = R.drawable.ic_custom_image, count = totalImages, label = "Images",
                            baseColor = Color(0xFFAB47BC)
                        )
                        ArchiveStatRow(
                            iconResId = R.drawable.ic_custom_video, count = totalVideos, label = "Videos",
                            baseColor = Color(0xFFEF5350)
                        )
                        ArchiveStatRow(
                            iconResId = R.drawable.ic_custom_text, count = totalText, label = "Text",
                            baseColor = Color(0xFFFFA726)
                        )
                        ArchiveStatRow(
                            iconResId = R.drawable.ic_custom_code, count = totalCode, label = "Code",
                            baseColor = Color(0xFF66BB6A)
                        )
                    }
                }
            }
            
            // APP
            SectionContainer(title = "APP") {
                ClickableRow(title = "Settings", onClick = onNavigateToSettings)
            }
            
            // STORAGE SECTION
            SectionContainer(title = "STORAGE") {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    val usedMb = usedStorageBytes / (1024f * 1024f)
                    val cloudUsedStorageBytes by viewModel.cloudUsedStorageBytes.collectAsState()
                    
                    Text(text = "Storage Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (userEmail == null) {
                        Text(text = String.format(Locale.US, "%.1f MB Used locally", usedMb), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Sign in to back up your content to the cloud.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        Text(text = String.format(Locale.US, "%.1f MB Used locally", usedMb), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Cloud Backup Progress
                        val maxMb = 512f
                        val cloudUsedMb = cloudUsedStorageBytes / (1024f * 1024f)
                        val cloudProgress = (cloudUsedMb / maxMb).coerceIn(0f, 1f)

                        LinearProgressIndicator(
                            progress = { cloudProgress },
                            modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = String.format(Locale.US, "%.1f MB / 512 MB backed up", cloudUsedMb), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // Pill-shaped button with tactile spinning animation
                    var clicked by remember { mutableStateOf(false) }
                    val rotation by animateFloatAsState(
                        targetValue = if (clicked) 360f else 0f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
                        label = "rotate"
                    )
                    
                    Button(
                        onClick = { 
                            clicked = !clicked
                            onNavigateToManageStorage()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { rotationZ = rotation }
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text("Manage Storage", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            
            // INFORMATION & LEGAL
            SectionContainer(title = "INFORMATION & LEGAL") {
                Column {
                    ClickableRow(title = "About Second Brain", onClick = { onNavigateToLegal("about") })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 20.dp))
                    ClickableRow(title = "Frequently Asked Questions", onClick = { onNavigateToLegal("faq") })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 20.dp))
                    ClickableRow(title = "Privacy Policy", onClick = { onNavigateToLegal("privacy") })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 20.dp))
                    ClickableRow(title = "Terms & Conditions", onClick = { onNavigateToLegal("terms") })
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun SectionContainer(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

@Composable
fun ArchiveStatRow(
    iconResId: Int, 
    count: Int, 
    label: String, 
    baseColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(baseColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(baseColor.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = baseColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label, 
            fontSize = 16.sp, 
            color = MaterialTheme.colorScheme.onSurface, 
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count", 
            fontSize = 18.sp, 
            fontWeight = FontWeight.Bold, 
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ClickableRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Icon(
            painter = painterResource(id = R.drawable.ic_custom_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
