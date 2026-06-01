package com.silentlink.app.ui.screen

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.silentlink.app.billing.BillingManager
import com.silentlink.app.billing.SUPPORT_TIERS
import com.silentlink.app.model.AppTheme
import com.silentlink.app.ui.MainViewModel
import com.silentlink.app.ui.theme.AccentBlue
import com.silentlink.app.ui.theme.DangerRed
import com.silentlink.app.ui.theme.toSilentLinkColors

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    val billingManager = remember { BillingManager(context) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(Unit) { billingManager.connect() }
    DisposableEffect(Unit) { onDispose { billingManager.disconnect() } }

    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "설정",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground
        )
        Spacer(modifier = Modifier.height(20.dp))

        // 연결 정보
        if (uiState.isConnected) {
            SettingSection(title = "연결 정보") {
                SettingItem(
                    icon = Icons.Default.QrCode,
                    title = "내 초대 코드",
                    subtitle = uiState.myCode.chunked(3).joinToString(" - "),
                    iconTint = AccentBlue,
                    onClick = { clipboard.setText(AnnotatedString(uiState.myCode)) }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 권한 설정
        SettingSection(title = "권한 설정") {
            SettingItem(
                icon = Icons.Default.VolumeUp,
                title = "시스템 설정 변경 권한",
                subtitle = "무음/볼륨 제어에 필요 — 탭하여 허용",
                iconTint = AccentBlue,
                onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 테마 선택
        SettingSection(title = "앱 테마") {
            SettingItem(
                icon = Icons.Default.Palette,
                title = "테마 선택",
                subtitle = uiState.theme.displayName,
                iconTint = AccentBlue,
                onClick = { showThemeDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 개발자 후원
        SettingSection(title = "개발자 후원") {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "앱 개발을 응원해주세요 ❤️",
                    fontSize = 13.sp,
                    color = colors.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUPPORT_TIERS.chunked(2).forEach { rowTiers ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowTiers.forEach { tier ->
                                OutlinedButton(
                                    onClick = {
                                        billingManager.launchBillingFlow(context as Activity, tier.productId)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = tier.displayName, fontSize = 11.sp, color = AccentBlue.copy(alpha = 0.7f))
                                        Text(text = tier.price, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 기타
        SettingSection(title = "기타") {
            SettingItem(
                icon = Icons.Default.Article,
                title = "이용약관",
                iconTint = colors.onSurface.copy(alpha = 0.6f),
                onClick = { showPrivacyDialog = true }
            )
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            SettingItem(
                icon = Icons.Default.PrivacyTip,
                title = "개인정보처리방침",
                iconTint = colors.onSurface.copy(alpha = 0.6f),
                onClick = { showPrivacyDialog = true }
            )
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            SettingItem(
                icon = Icons.Default.Info,
                title = "앱 버전",
                subtitle = "1.0.0",
                iconTint = colors.onSurface.copy(alpha = 0.6f)
            )
        }

        if (uiState.isConnected) {
            Spacer(modifier = Modifier.height(16.dp))
            SettingSection(title = "연결") {
                SettingItem(
                    icon = Icons.Default.LinkOff,
                    title = "연결 해제",
                    subtitle = "상대방과의 연결을 끊습니다",
                    iconTint = DangerRed,
                    titleColor = DangerRed,
                    onClick = { showDisconnectDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("연결 해제") },
            text = { Text("상대방과의 연결을 해제하시겠습니까?\n연결 해제 후에는 상호 제어가 불가능합니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnect()
                        showDisconnectDialog = false
                    }
                ) { Text("해제", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("취소")
                }
            },
            containerColor = colors.surfaceVariant
        )
    }

    if (showThemeDialog) {
        ThemePickerDialog(
            currentTheme = uiState.theme,
            onSelect = {
                viewModel.setTheme(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showPrivacyDialog) {
        PrivacyDialog(onDismiss = { showPrivacyDialog = false })
    }
}

@Composable
private fun SettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = AccentBlue,
    titleColor: Color = MaterialTheme.colorScheme.onBackground,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, color = titleColor, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = colors.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = colors.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ThemePickerDialog(
    currentTheme: AppTheme,
    onSelect: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "테마 선택",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                AppTheme.entries.forEach { theme ->
                    val themeColors = theme.toSilentLinkColors()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(theme) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                            listOf(themeColors.background, themeColors.accent, themeColors.surface).forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = theme.displayName,
                            fontSize = 15.sp,
                            fontWeight = if (theme == currentTheme) FontWeight.Bold else FontWeight.Normal,
                            color = if (theme == currentTheme) themeColors.accent else colors.onSurface
                        )
                        if (theme == currentTheme) {
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = themeColors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("이용약관 및 개인정보처리방침", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = """
【이용약관】

제1조 (목적)
본 약관은 SilentLink 앱 서비스 이용에 관한 조건과 절차를 규정합니다.

제2조 (서비스 이용 조건)
• 상대방의 명시적 동의 없이 기능이 작동하지 않습니다.
• 동의 없이 타인의 기기에 설치하는 행위는 금지됩니다.
• 스토킹·감시 목적의 사용은 정보통신망법에 의해 처벌받을 수 있습니다.

제3조 (면책 조항)
앱 기능 악용으로 발생한 모든 민·형사상 책임은 사용자 본인에게 있으며, 개발자는 법적 책임을 지지 않습니다.

【개인정보처리방침】

수집 항목: Firebase 익명 인증 UID, 기기 상태 정보
수집 목적: 기기 간 실시간 상태 동기화
보유 기간: 연결 해제 시 즉시 삭제
제3자 제공: 없음
                    """.trimIndent(),
                    fontSize = 13.sp,
                    color = colors.onSurface,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("확인")
                }
            }
        }
    }
}
