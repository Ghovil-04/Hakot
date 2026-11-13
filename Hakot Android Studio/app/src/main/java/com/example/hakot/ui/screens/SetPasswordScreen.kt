package com.example.hakot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hakot.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SetPasswordScreen(
    onPasswordUpdated: () -> Unit,
    onUpdatePassword: suspend (String) -> Pair<Boolean, String>
) {
    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, DarkBorder, RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SET YOUR ACCOUNT PASSWORD",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = EcoGreenLight
                )
                Text(
                    text = "Please set a secure password to complete your account setup or recovery.",
                    fontSize = 11.sp,
                    color = TextOnDarkSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it; errorMessage = null },
                    label = { Text("New Password") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", tint = TextOnDarkSecondary) },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Password",
                                tint = TextOnDarkSecondary
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EcoGreenPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextOnDarkPrimary,
                        unfocusedTextColor = TextOnDarkPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPasswordInput,
                    onValueChange = { confirmPasswordInput = it; errorMessage = null },
                    label = { Text("Confirm New Password") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", tint = TextOnDarkSecondary) },
                    trailingIcon = {
                        IconButton(onClick = { isConfirmVisible = !isConfirmVisible }) {
                            Icon(
                                imageVector = if (isConfirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Password",
                                tint = TextOnDarkSecondary
                            )
                        }
                    },
                    visualTransformation = if (isConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EcoGreenPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextOnDarkPrimary,
                        unfocusedTextColor = TextOnDarkPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = HazardousRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                successMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = EcoGreenLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (passwordInput.length < 6) {
                            errorMessage = "Password must be at least 6 characters long."
                        } else if (passwordInput != confirmPasswordInput) {
                            errorMessage = "Passwords do not match."
                        } else {
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val result = onUpdatePassword(passwordInput)
                                    if (result.first) {
                                        successMessage = "Password set successfully! Redirecting..."
                                        passwordInput = ""
                                        confirmPasswordInput = ""
                                        delay(1200)
                                        onPasswordUpdated()
                                    } else {
                                        errorMessage = result.second
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "An unexpected error occurred while updating password."
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Text("Save Password & Continue", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
