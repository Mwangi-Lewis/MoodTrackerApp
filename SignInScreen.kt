package com.example.moodtrackerapp

import android.util.Patterns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// ----- Shared styles -----
private val ScreenBg = Color(0xFFFAFAFC) // light iOS-like background
private val PrimaryBlack = Color(0xFF000000)

/* --------------------------------- SIGN IN --------------------------------- */

@Composable
fun SignInScreen(
    onCreateAccountClick: () -> Unit,
    onSuccess: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    var email by remember { mutableStateOf(TextFieldValue("")) }
    var password by remember { mutableStateOf(TextFieldValue("")) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    // Important: contentWindowInsets = WindowInsets(0) to avoid auto "safe area" padding
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
        containerColor = ScreenBg,
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBg)
                .padding(padding)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Mood Tracker App", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("Welcome back", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Sign in to continue tracking your moods",
                    color = Color.Gray, fontSize = 14.sp
                )
                Spacer(Modifier.height(20.dp))

                // Email
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        loading = true
                        auth.signInWithEmailAndPassword(email.text.trim(), password.text)
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { e -> error = e.localizedMessage }
                            .addOnCompleteListener { loading = false }
                    },
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (loading) "Signing in..." else "Sign In", color = Color.White)
                }

                TextButton(onClick = onCreateAccountClick) {
                    Text("New here? Create an account", color = Color(0xFF4A3AFF))
                }

                error?.let {
                    Text(it, color = Color.Red)
                }
            }
        }
    }
}

/* ------------------------------- CREATE ACCOUNT ---------------------------- */

@Composable
fun CreateAccountScreen(
    onSignInClick: () -> Unit,
    onAccountCreated: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showPass by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    fun validate(): String? {
        if (username.isBlank()) return "Please enter a username."
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Enter a valid email."
        if (password.length < 6) return "Password must be at least 6 characters."
        if (password != confirm) return "Passwords do not match."
        return null
    }

    fun createAccount() {
        errorMsg = validate()
        if (errorMsg != null) return

        isLoading = true
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    isLoading = false
                    errorMsg = "Could not create user. Try again."
                    return@addOnSuccessListener
                }

                // Update FirebaseAuth display name
                val profile = UserProfileChangeRequest.Builder()
                    .setDisplayName(username.trim())
                    .build()
                user.updateProfile(profile)

                // Save profile in Firestore: users/{uid}
                val data = mapOf(
                    "username" to username.trim(),
                    "email" to email.trim(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("users").document(user.uid)
                    .set(data)
                    .addOnSuccessListener {
                        isLoading = false
                        onAccountCreated()
                    }
                    .addOnFailureListener { e ->
                        isLoading = false
                        errorMsg = e.localizedMessage ?: "Failed to save profile."
                    }
            }
            .addOnFailureListener { e ->
                isLoading = false
                errorMsg = e.localizedMessage ?: "Account creation failed."
            }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
        containerColor = ScreenBg,
        contentWindowInsets = WindowInsets(0)
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBg)
                .padding(pad)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Create an account", style = MaterialTheme.typography.headlineSmall)
            Text("Enter your details to sign up", style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Text(
                        if (showPass) "Hide" else "Show",
                        modifier = Modifier
                            .clickable { showPass = !showPass }
                            .padding(8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("Confirm password") },
                singleLine = true,
                visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Text(
                        if (showConfirm) "Hide" else "Show",
                        modifier = Modifier
                            .clickable { showConfirm = !showConfirm }
                            .padding(8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            if (errorMsg != null) {
                Text(
                    errorMsg!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = { if (!isLoading) createAccount() },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text("Create account")
            }

            TextButton(onClick = onSignInClick) {
                Text("Already have an account? Sign in")
            }
        }
    }
}