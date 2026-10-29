package com.amresalehin.emregallery.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amresalehin.emregallery.R

@Composable
fun WelcomeScreen(
    initialStep: Int = 0,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentStep by remember { mutableIntStateOf(initialStep) }
    var mediaGranted by remember { mutableStateOf(hasMediaPermission(context)) }
    var notificationGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var allFilesGranted by remember { mutableStateOf(hasAllFilesAccess()) }

    // Re-check permissions when coming back to the app (e.g. from system settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mediaGranted = hasMediaPermission(context)
                notificationGranted = hasNotificationPermission(context)
                allFilesGranted = hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        mediaGranted = hasMediaPermission(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationGranted = isGranted
    }

    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        allFilesGranted = hasAllFilesAccess()
    }

    fun requestMedia() {
        val permissions = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            Build.VERSION.SDK_INT >= 29 -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        mediaPermissionLauncher.launch(permissions)
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationGranted = true
        }
    }

    fun requestAllFiles() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = try {
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            } catch (_: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
            allFilesLauncher.launch(intent)
        } else {
            allFilesGranted = true
        }
    }

    // Atmospheric mist/fog gradient matching Emre style (#F3F5F7 to #161A20)
    val mistGradient = Brush.verticalGradient(
        0.00f to Color(0xFFF3F5F7),
        0.28f to Color(0xFFE2E7EC),
        0.58f to Color(0xFF8F9BA8),
        0.82f to Color(0xFF38414D),
        1.00f to Color(0xFF161A20)
    )

    val grantedCount = (if (mediaGranted) 1 else 0) +
        (if (notificationGranted) 1 else 0) +
        (if (allFilesGranted) 1 else 0)
    val allPermissionsGranted = grantedCount == 3

    val buttonText = when {
        allPermissionsGranted -> "Enter Emre"
        grantedCount in 1..2 -> "Growing Wings"
        else -> "Liberate Emre"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(mistGradient)
            .testTag("welcome_screen")
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                (fadeIn(animationSpec = tween(280)) + slideInHorizontally(animationSpec = tween(280)) { it / 3 })
                    .togetherWith(fadeOut(animationSpec = tween(220)) + slideOutHorizontally(animationSpec = tween(220)) { -it / 3 })
            },
            label = "welcome_flow"
        ) { step ->
            when (step) {
                0 -> {
                    // Screen 1: Minimalist Emre Splash for First Setup Run
                    // Centered logo, "Emre Is Waiting" message, and transparent bottom-right navigation chevron.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { currentStep = 1 }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_app_logo),
                                contentDescription = "Emre Gallery",
                                modifier = Modifier.size(76.dp),
                                tint = Color(0xFF1D2228)
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = "Emre Is Waiting",
                                fontFamily = FontFamily.Serif,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.5.sp,
                                color = Color(0xFF14171B),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("welcome_intro_title")
                            )
                        }

                        // Top-right navigation chevron: transparent button, only the arrow is visible
                        IconButton(
                            onClick = { currentStep = 1 },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color(0xFF161A20)
                            ),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(end = 20.dp, top = 16.dp)
                                .size(56.dp)
                                .testTag("welcome_intro_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Proceed to setup",
                                tint = Color(0xFF161A20),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                1 -> {
                    // Screen 2: "Let's set things up" Permissions screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(36.dp))

                        // Soft circular halo with Emre logo
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.50f),
                            modifier = Modifier.size(84.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_app_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(38.dp),
                                    tint = Color(0xFF161A20)
                                )
                            }
                        }

                        Spacer(Modifier.height(28.dp))

                        // Editorial Serif Headline
                        Text(
                            text = "Let’s set things up",
                            fontFamily = FontFamily.Serif,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF14171B)
                        )

                        Spacer(Modifier.height(32.dp))

                        // Card 1: Photos and videos
                        PermissionCard(
                            icon = Icons.Outlined.Photo,
                            title = "Photos and videos",
                            isGranted = mediaGranted,
                            onClick = { requestMedia() },
                            testTag = "welcome_permission_media"
                        )

                        Spacer(Modifier.height(14.dp))

                        // Card 2: Notifications
                        PermissionCard(
                            icon = Icons.Outlined.Notifications,
                            title = "Notifications",
                            isGranted = notificationGranted,
                            onClick = { requestNotifications() },
                            testTag = "welcome_permission_notifications"
                        )

                        Spacer(Modifier.height(14.dp))

                        // Card 3: All files access
                        PermissionCard(
                            icon = Icons.Outlined.FolderCopy,
                            title = "All files access",
                            isGranted = allFilesGranted,
                            onClick = { requestAllFiles() },
                            testTag = "welcome_permission_all_files"
                        )

                        Spacer(Modifier.weight(1f))

                        // Dynamic Action Button:
                        // "Liberate Emre" (0 granted) -> "Growing Wings" (1 or 2 granted) -> "Enter Emre" (all 3 granted)
                        // Arrow appears ONLY on "Enter Emre"
                        Button(
                            onClick = {
                                if (allPermissionsGranted) {
                                    onComplete()
                                } else if (!mediaGranted) {
                                    requestMedia()
                                } else if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    requestNotifications()
                                } else if (!allFilesGranted) {
                                    requestAllFiles()
                                }
                            },
                            shape = RoundedCornerShape(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (allPermissionsGranted) Color.White else Color(0x33485666),
                                contentColor = if (allPermissionsGranted) Color(0xFF14171B) else Color.White
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (allPermissionsGranted) Color.White else Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(bottom = 4.dp)
                                .testTag("welcome_continue_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = buttonText,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (allPermissionsGranted) Color(0xFF14171B) else Color.White
                                    )
                                )
                                if (allPermissionsGranted) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = Color(0xFF14171B)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0x75F5F7FA),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.60f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF181C22)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF14171A)
                ),
                modifier = Modifier.weight(1f)
            )

            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Permission granted",
                    modifier = Modifier.size(22.dp),
                    tint = Color(0xFF14171A)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Grant permission",
                    modifier = Modifier.size(22.dp),
                    tint = Color(0xFF38404A)
                )
            }
        }
    }
}
