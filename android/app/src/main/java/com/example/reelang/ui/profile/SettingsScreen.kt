package com.example.reelang.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reelang.auth.UserSession
import com.example.reelang.network.ApiClient
import com.example.reelang.network.models.ProfileUpdateRequest
import com.example.reelang.ui.SharedState
import com.example.reelang.ui.onboarding.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val googleSignInClient = remember(context) {
        GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN)
    }
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching {
            ApiClient.api.getMyProfile(UserSession.userId)
        }.onSuccess {
            username = it.username
            bio = it.bio ?: ""
        }
    }

    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("Bio") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching {
                            ApiClient.api.updateMyProfile(
                                userId = UserSession.userId,
                                body = ProfileUpdateRequest(username = username, bio = bio)
                            )
                        }.onSuccess {
                            SharedState.triggerProfileRefresh()
                            showEditProfileDialog = false
                        }
                    }
                }) {
                    Text("Save", color = ReelangRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel", color = ReelangTextSecondary)
                }
            },
            containerColor = ReelangSurface
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign out", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        googleSignInClient.signOut().addOnCompleteListener {
                            FirebaseAuth.getInstance().signOut()
                            showLogoutDialog = false
                            onLogout()
                        }
                    }
                ) {
                    Text("Sign out", color = ReelangRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = ReelangTextSecondary)
                }
            },
            containerColor = ReelangSurface
        )
    }

    Scaffold(
        containerColor = ReelangCream,
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ReelangSurface)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ReelangTextPrimary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Settings",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = ReelangTextPrimary
                    )
                }
                HorizontalDivider(color = ReelangBorder)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Account",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ReelangTextSecondary,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            val user = FirebaseAuth.getInstance().currentUser
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = ReelangSurface,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = ReelangTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = user?.displayName ?: "User",
                            fontWeight = FontWeight.SemiBold,
                            color = ReelangTextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = user?.email ?: "",
                            fontSize = 12.sp,
                            color = ReelangTextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Filled.Edit,
                label = "Edit Profile",
                onClick = { showEditProfileDialog = true }
            )

            SettingsItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = "Sign out",
                tint = ReelangRed,
                onClick = { showLogoutDialog = true }
            )
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    label: String,
    tint: Color = ReelangTextPrimary,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = ReelangSurface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = tint
            )
        }
    }
}
