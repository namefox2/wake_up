package com.silentlink.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentlink.app.ads.BannerAdView
import com.silentlink.app.model.VolumeLevel
import com.silentlink.app.ui.MainViewModel
import com.silentlink.app.ui.theme.AccentBlue
import com.silentlink.app.ui.theme.DangerRed
import com.silentlink.app.ui.theme.SuccessGreen
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        // AdMob 배너 (상단)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            BannerAdView()
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            // 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SilentLink",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground
                )
                if (uiState.isConnected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "연결됨",
                            fontSize = 13.sp,
                            color = SuccessGreen
                        )
                    }
                } else {
                    Text(
                        text = "연결 없음",
                        fontSize = 13.sp,
                        color = colors.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!uiState.isConnected) {
                // 연결 안내 카드
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = colors.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "아직 연결되지 않았습니다",
                            fontSize = 16.sp,
                            color = colors.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "설정에서 상대방 초대 코드를 입력하여\n연결하세요",
                            fontSize = 13.sp,
                            color = colors.onSurface.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            } else {
                // 상대방 상태 카드
                StatusCard(uiState.partnerStatus.isMuted, uiState.partnerStatus.volumeLevel)

                Spacer(modifier = Modifier.height(16.dp))

                // 무음 제어 카드
                MuteControlCard(
                    isMuted = uiState.partnerStatus.isMuted,
                    isAccessAllowed = uiState.partnerStatus.isAccessAllowed,
                    onMuteToggle = { viewModel.sendMuteCommand(!uiState.partnerStatus.isMuted)  },
                    onVolumeSelect = { viewModel.sendVolumeCommand(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 방해금지 요약 카드
                DndSummaryCard(
                    schedulesCount = uiState.dndSchedules.count { it.isEnabled }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 접근 허용 토글 카드
                AccessToggleCard(
                    isAllowed = uiState.myStatus.isAccessAllowed,
                    onToggle = { viewModel.toggleMyAccess(it) }
                )
            }
        }
    }
}

@Composable
private fun StatusCard(isMuted: Boolean, volumeLevel: VolumeLevel) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "상대방 현재 상태",
                    fontSize = 12.sp,
                    color = colors.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isMuted) "무음 모드" else "${volumeLevel.icon} ${volumeLevel.label}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isMuted) DangerRed else colors.onBackground
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isMuted) DangerRed.copy(alpha = 0.15f)
                        else AccentBlue.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isMuted) "🔇" else volumeLevel.icon,
                    fontSize = 24.sp
                )
            }
        }
    }
}

@Composable
private fun MuteControlCard(
    isMuted: Boolean,
    isAccessAllowed: Boolean,
    onMuteToggle: () -> Unit,
    onVolumeSelect: (VolumeLevel) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "무음 제어",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground
                )
                Switch(
                    checked = isMuted,
                    onCheckedChange = { if (isAccessAllowed) onMuteToggle() },
                    enabled = isAccessAllowed,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = DangerRed,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = colors.outline
                    )
                )
            }

            if (!isAccessAllowed) {
                Text(
                    text = "상대방이 접근을 차단했습니다",
                    fontSize = 12.sp,
                    color = DangerRed,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "볼륨 조절",
                fontSize = 12.sp,
                color = colors.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VolumeLevel.entries.forEach { level ->
                    VolumeButton(
                        level = level,
                        enabled = isAccessAllowed,
                        onClick = { onVolumeSelect(level) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeButton(
    level: VolumeLevel,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = AccentBlue,
            disabledContentColor = colors.onSurface.copy(alpha = 0.3f)
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = 1.dp
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = level.icon, fontSize = 16.sp)
            Text(text = level.label, fontSize = 9.sp)
        }
    }
}

@Composable
private fun DndSummaryCard(schedulesCount: Int) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = if (schedulesCount > 0) DangerRed else colors.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "방해금지 스케줄",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground
                )
                Text(
                    text = if (schedulesCount > 0) "활성 스케줄 $schedulesCount개" else "설정된 스케줄 없음",
                    fontSize = 12.sp,
                    color = if (schedulesCount > 0) DangerRed else colors.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun AccessToggleCard(isAllowed: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAllowed) AccentBlue.copy(alpha = 0.1f)
            else DangerRed.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "내 기기 접근 허용",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isAllowed) AccentBlue else DangerRed
                )
                Text(
                    text = if (isAllowed) "상대방이 내 기기를 제어할 수 있습니다"
                    else "모든 원격 제어가 차단됩니다",
                    fontSize = 12.sp,
                    color = if (isAllowed) AccentBlue.copy(alpha = 0.7f) else DangerRed.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = isAllowed,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentBlue,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = DangerRed.copy(alpha = 0.5f)
                )
            )
        }
    }
}
