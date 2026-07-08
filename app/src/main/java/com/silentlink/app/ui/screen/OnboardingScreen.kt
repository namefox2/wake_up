package com.silentlink.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentlink.app.ui.MainViewModel
import com.silentlink.app.ui.theme.AccentBlue

@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme

    val context = LocalContext.current
    var termsAccepted    by remember { mutableStateOf(false) }
    var privacyAccepted  by remember { mutableStateOf(false) }
    var ageVerified      by remember { mutableStateOf(false) }
    var adConsentAccepted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(72.dp))

        // 앱 아이콘
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(AccentBlue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "깨워줘",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground
        )
        Text(
            "상대방이 무음이어도\n동의 기반으로 소리를 켜주는 앱",
            fontSize = 15.sp,
            color = colors.onSurface.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(top = 10.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        // 서비스 안내
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "서비스 이용 안내",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    "상대방이 앱을 설치하고 권한을 허용해야만 기능이 작동합니다.",
                    "상대방의 상황(회의, 수업, 운전 등)을 충분히 고려하여 신중하게 사용하세요.",
                    "동의 없이 타인의 기기에 설치하거나 감시 목적으로 사용하면 정보통신망법에 의해 처벌받을 수 있습니다.",
                    "기능 악용으로 발생한 법적 책임은 사용자 본인에게 있습니다.",
                    "개발자는 고의 또는 중과실이 없는 한 서비스 이용으로 발생한 피해에 대해 책임을 지지 않습니다."
                ).forEach { notice ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("• ", fontSize = 13.sp, color = AccentBlue)
                        Text(
                            notice,
                            fontSize = 12.sp,
                            color = colors.onSurface.copy(alpha = 0.75f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 동의 체크박스
        AgreementRow(
            checked = termsAccepted,
            onCheckedChange = { termsAccepted = it },
            label = "이용약관에 동의합니다 (필수)",
            onViewDetail = {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://namefox2.github.io/wake_up/terms.html")))
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        AgreementRow(
            checked = privacyAccepted,
            onCheckedChange = { privacyAccepted = it },
            label = "개인정보처리방침에 동의합니다 (필수)",
            onViewDetail = {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://namefox2.github.io/wake_up/privacy.html")))
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        AgreementRow(
            checked = ageVerified,
            onCheckedChange = { ageVerified = it },
            label = "만 14세 이상입니다 (필수)"
        )
        Spacer(modifier = Modifier.height(4.dp))
        AgreementRow(
            checked = adConsentAccepted,
            onCheckedChange = { adConsentAccepted = it },
            label = "맞춤 광고를 위한 광고 식별자 수집에 동의합니다 (선택, 미동의 시 일반 광고 표시)"
        )

        Spacer(modifier = Modifier.height(28.dp))

        val canStart = termsAccepted && privacyAccepted && ageVerified

        Button(
            onClick = { if (canStart && !uiState.isOnboarding) viewModel.onboard(adConsentAccepted) },
            enabled = canStart && !uiState.isOnboarding,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentBlue,
                disabledContainerColor = AccentBlue.copy(alpha = 0.3f)
            )
        ) {
            if (uiState.isOnboarding) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = androidx.compose.ui.graphics.Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("저장 중...", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text("시작하기", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        uiState.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(14.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun AgreementRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    onViewDetail: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = AccentBlue,
                uncheckedColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (onViewDetail != null) {
            TextButton(
                onClick = onViewDetail,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("전문 보기", fontSize = 12.sp, color = AccentBlue)
            }
        }
    }
}
