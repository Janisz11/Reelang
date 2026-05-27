package com.example.reelang.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reelang.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.example.reelang.ui.onboarding.ReelangBorder
import com.example.reelang.ui.onboarding.ReelangCream
import com.example.reelang.ui.onboarding.ReelangRed
import com.example.reelang.ui.onboarding.ReelangSurface
import com.example.reelang.ui.onboarding.ReelangTextPrimary
import com.example.reelang.ui.onboarding.ReelangTextSecondary

// ─── Screen ───────────────────────────────────────────────────────────────────

private val tabs = listOf("Sign In", "Register")

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel,
    onNavigateToOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context     = LocalContext.current
    val focusMgr    = LocalFocusManager.current
    val authState   by authViewModel.authState.collectAsState()

    var selectedTab     by remember { mutableIntStateOf(0) }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val isLoading    = authState is AuthState.Loading
    val errorMessage = (authState as? AuthState.Error)?.message


    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onNavigateToOnboarding()
        }
    }

    // Clear error when the user switches tabs or starts typing
    LaunchedEffect(selectedTab, email, password) { authViewModel.clearError() }

    // ── Google Sign-In plumbing ───────────────────────────────────────────────
    // R.string.default_web_client_id is generated automatically by the
    // google-services plugin from google-services.json (placed in app/).
    val googleSignInClient = remember(context) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn
                    .getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                account.idToken
                    ?.let { authViewModel.signInWithGoogle(it) }
                    ?: authViewModel.reportError("Google sign-in: missing ID token")
            } catch (e: ApiException) {
                authViewModel.reportError("Google sign-in failed (code ${e.statusCode})")
            }
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A0A0A), Color(0xFF3D1515))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            // ── Logo ──────────────────────────────────────────────────────────
            Text(text = "🌎", fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "ReeLang",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = "Learn languages by watching reels",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            // ── Form card ─────────────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ReelangSurface,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Tabs: Sign In / Register
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = ReelangSurface,
                        contentColor = ReelangRed,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = ReelangRed
                            )
                        },
                        divider = {
                            HorizontalDivider(color = ReelangBorder, thickness = 1.dp)
                        }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val selected = selectedTab == index
                            Tab(
                                selected = selected,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (selected) FontWeight.SemiBold
                                                     else FontWeight.Normal,
                                        color = if (selected) ReelangRed
                                                else ReelangTextSecondary
                                    )
                                }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 20.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {

                        // Email field
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusMgr.moveFocus(FocusDirection.Down) }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = authTextFieldColors()
                        )

                        // Password field
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusMgr.clearFocus(); submitForm(selectedTab, email, password, authViewModel) }
                            ),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Filled.Visibility
                                                      else Icons.Filled.VisibilityOff,
                                        contentDescription = "Toggle password",
                                        tint = ReelangTextSecondary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = authTextFieldColors()
                        )

                        // Inline error
                        AnimatedVisibility(visible = errorMessage != null) {
                            Text(
                                text = errorMessage ?: "",
                                color = ReelangRed,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Submit button
                        Button(
                            onClick = {
                                focusMgr.clearFocus()
                                submitForm(selectedTab, email, password, authViewModel)
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ReelangRed,
                                contentColor = Color.White,
                                disabledContainerColor = ReelangRed.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (selectedTab == 0) "Sign In" else "Create Account",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        // ── Divider ───────────────────────────────────────────
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = ReelangBorder
                            )
                            Text(
                                text = "  or  ",
                                fontSize = 12.sp,
                                color = ReelangTextSecondary
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = ReelangBorder
                            )
                        }

                        // ── Google Sign-In ────────────────────────────────────
                        OutlinedButton(
                            onClick = { googleLauncher.launch(googleSignInClient.signInIntent) },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            border = BorderStroke(1.dp, ReelangBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = ReelangCream,
                                contentColor = ReelangTextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "G",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color(0xFF4285F4)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Continue with Google",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Toggle sign-in / register hint
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { selectedTab = if (selectedTab == 0) 1 else 0 }) {
                Text(
                    text = if (selectedTab == 0)
                        "Don't have an account?  Register"
                    else
                        "Already have an account?  Sign In",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun submitForm(tab: Int, email: String, password: String, vm: AuthViewModel) {
    if (tab == 0) vm.signInWithEmail(email, password)
    else          vm.createAccount(email, password)
}

@Composable
private fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor       = ReelangRed,
    unfocusedBorderColor     = ReelangBorder,
    focusedLabelColor        = ReelangRed,
    unfocusedLabelColor      = ReelangTextSecondary,
    cursorColor              = ReelangRed,
    focusedTextColor         = ReelangTextPrimary,
    unfocusedTextColor       = ReelangTextPrimary
)
