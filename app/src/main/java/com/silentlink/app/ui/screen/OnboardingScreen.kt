package com.silentlink.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentlink.app.ui.MainViewModel
import com.silentlink.app.ui.theme.AccentBlue
import com.silentlink.app.ui.theme.DarkColors
import com.silentlink.app.ui.theme.SuccessGreen

@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    val clipboardManager = LocalClipboardManager.current

    var termsAccepted by remember { mutableStateOf(false) }
    var privacyAccepted by remember { mutableStateOf(false) }
    var partnerCodeInput by remember { mutableStateOf("") }
    var myCode by remember { mutableStateOf("") }
    var showConnectSection by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        myCode = viewModel.generateMyCode()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // 앱 아이콘 영역
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AccentBlue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "SilentLink",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground
        )
        Text(
            text = "상대방이 무음이라 연락이 닿지 않을 때\n동의 기반으로 소리를 켜주는 앱",
            fontSize = 14.sp,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(36.dp))

        // 내 초대 코드
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "내 초대 코드",
                    fontSize = 12.sp,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = myCode.chunked(3).joinToString(" - "),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = AccentBlue,
                        letterSpacing = 2.sp
                    )
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(myCode))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "복사",
                            tint = colors.onSurface
                        )
                    }
                }
                Text(
                    text = "이 코드를 상대방에게 공유하세요",
                    fontSize = 12.sp,
                    color = colors.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 상대방 코드 입력
        OutlinedTextField(
            value = partnerCodeInput,
            onValueChange = { partnerCodeInput = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
            label = { Text("상대방 초대 코드 입력") },
            placeholder = { Text("6자리 코드") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = colors.outline,
                focusedLabelColor = AccentBlue,
                cursorColor = AccentBlue
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                letterSpacing = 2.sp
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 서비스 고지 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "서비스 이용 안내",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "SilentLink는 연인·가족 등 신뢰 관계에서 상대방의 무음으로 인한 연락 불편을 해소하기 위한 동의 기반 커뮤니케이션 보조 앱입니다.",
                    fontSize = 13.sp,
                    color = colors.onSurface,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                listOf(
                    "상대방의 명시적 동의(앱 설치 및 권한 허용) 없이는 어떠한 기능도 작동하지 않습니다.",
                    "동의 없이 타인의 기기에 설치하거나 스토킹·감시 목적으로 사용하는 행위는 정보통신망법에 의해 처벌받을 수 있습니다.",
                    "앱 기능 악용으로 발생한 모든 민·형사상 책임은 사용자 본인에게 있습니다."
                ).forEach { notice ->
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Text("• ", fontSize = 13.sp, color = AccentBlue)
                        Text(notice, fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.8f), lineHeight = 18.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 약관 동의 체크박스
        CheckboxRow(
            checked = termsAccepted,
            onCheckedChange = { termsAccepted = it },
            label = "이용약관에 동의합니다 (필수)"
        )
        Spacer(modifier = Modifier.height(8.dp))
        CheckboxRow(
            checked = privacyAccepted,
            onCheckedChange = { privacyAccepted = it },
            label = "개인정보처리방침에 동의합니다 (필수)"
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 시작 버튼
        val canStart = termsAccepted && privacyAccepted
        Button(
            onClick = {
                if (canStart) {
                    viewModel.onboard(true)
                    if (partnerCodeInput.length == 6) {
                        viewModel.connectWithPartnerCode(partnerCodeInput)
                    }
                }
            },
            enabled = canStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentBlue,
                disabledContainerColor = AccentBlue.copy(alpha = 0.3f)
            )
        ) {
            Text(
                text = "시작하기",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        uiState.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
