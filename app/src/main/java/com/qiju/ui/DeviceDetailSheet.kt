package com.qiju.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiju.data.HomeViewModel

/** 设备详情弹层（对齐参考截图 4/6：玄关灯 已开启 + 电源钮 + 能力提示） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailSheet(vm: HomeViewModel) {
    val deviceId = vm.selectedDeviceId ?: return
    val device = vm.plan.devices.find { it.id == deviceId } ?: return
    val room = vm.plan.rooms.find { it.id == device.roomId }
    val st = vm.state(deviceId)
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = { vm.selectedDeviceId = null },
        sheetState = sheetState,
        containerColor = Color(0xFF121A32)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 30.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "${room?.name ?: "未分配"} / ${com.qiju.data.Device.TYPE_NAMES[device.type] ?: "设备"}",
                        style = MaterialTheme.typography.labelMedium, color = TextSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(device.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkCard)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { vm.selectedDeviceId = null },
                    contentAlignment = Alignment.Center
                ) { Text("×", color = TextSecondary, fontSize = 18.sp) }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (st.on && !st.unavailable) Color(0xFF4A4028) else DarkCard),
                        contentAlignment = Alignment.Center
                    ) { DeviceIcon(device.type, st.on && !st.unavailable) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            when {
                                st.unavailable -> "设备不可用"
                                device.type == "sensor" && st.value.isNotBlank() -> st.value
                                st.on -> "已开启"
                                else -> "已关闭"
                            },
                            fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                        )
                        if (st.on && device.type == "light" && !st.unavailable) {
                            Text("亮度 ${st.brightness}%", style = MaterialTheme.typography.labelMedium, color = Warm)
                        }
                        if (st.on && device.type == "ac" && !st.unavailable) {
                            Text("目标 ${st.temperature.toInt()}°C · 制冷", style = MaterialTheme.typography.labelMedium, color = Accent)
                        }
                        if (device.type == "sensor" && !st.unavailable && st.value.isNotBlank()) {
                            Text("实时读数 · Home Assistant", style = MaterialTheme.typography.labelMedium, color = Accent)
                        }
                    }
                }
                // 电源按钮
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(if (st.on) Accent else DarkCardHi)
                        .clickable { vm.toggleDevice(deviceId) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⏻", color = Color.White, fontSize = 24.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            if ("brightness" in device.capabilities) {
                Text("亮度", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Slider(
                    value = st.brightness.toFloat(),
                    onValueChange = { vm.setBrightness(deviceId, it.toInt()) },
                    valueRange = 1f..100f,
                    colors = SliderDefaults.colors(thumbColor = Warm, activeTrackColor = Warm)
                )
            }
            if ("temperature" in device.capabilities) {
                Text("目标温度 ${st.temperature.toInt()}°C", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Slider(
                    value = st.temperature,
                    onValueChange = { vm.setTemperature(deviceId, it) },
                    valueRange = 16f..30f,
                    steps = 13,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("可用控制项由设备能力决定。", style = MaterialTheme.typography.bodySmall, color = TextDim)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("更多控制与历史", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text("↗", color = TextSecondary)
            }
        }
    }
}
