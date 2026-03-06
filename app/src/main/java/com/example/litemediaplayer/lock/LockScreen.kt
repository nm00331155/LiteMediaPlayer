package com.example.litemediaplayer.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    targetType: LockTargetType,
    targetId: Long?,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
    viewModel: LockViewModel = hiltViewModel(),
    biometricHelper: BiometricHelper = BiometricHelper()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lockConfig = viewModel.getLockConfig(targetType, targetId)

    var pinInput by remember { mutableStateOf("") }
    var patternInput by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ロック解除",
            style = MaterialTheme.typography.headlineSmall
        )

        if (lockConfig == null || !lockConfig.isEnabled) {
            Text(text = "このコンテンツはロックされていません")
            Button(onClick = onUnlocked) {
                Text(text = "続行")
            }
            return@Column
        }

        when (LockAuthMethod.valueOf(lockConfig.authMethod)) {
            LockAuthMethod.PIN -> {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter(Char::isDigit).take(8) },
                    label = { Text(text = "PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        scope.launch {
                            val ok = viewModel.unlockWithPin(targetType, targetId, pinInput)
                            if (ok) {
                                onUnlocked()
                            } else {
                                localError = "PIN が一致しません"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "PIN で解除")
                }
            }

            LockAuthMethod.PATTERN -> {
                OutlinedTextField(
                    value = patternInput,
                    onValueChange = { patternInput = it },
                    label = { Text(text = "パターン") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        scope.launch {
                            val ok = viewModel.unlockWithPattern(targetType, targetId, patternInput)
                            if (ok) {
                                onUnlocked()
                            } else {
                                localError = "パターンが一致しません"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "パターンで解除")
                }
            }

            LockAuthMethod.BIOMETRIC -> {
                Button(
                    onClick = {
                        if (activity == null || !biometricHelper.canUseBiometric(context)) {
                            localError = "生体認証が利用できません"
                            return@Button
                        }
                        biometricHelper.authenticate(
                            activity = activity,
                            title = "ロック解除",
                            subtitle = "生体認証でアクセスします"
                        ) { success ->
                            if (success) {
                                scope.launch {
                                    viewModel.unlockWithBiometric(targetType, targetId)
                                    onUnlocked()
                                }
                            } else {
                                localError = "生体認証に失敗しました"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "生体認証で解除")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onCancel) {
                Text(text = "キャンセル")
            }
        }

        val error = localError ?: uiState.errorMessage
        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
