package com.cottonchou.qiju.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cottonchou.qiju.data.HomeViewModel

/** 我的 Tab：户型编辑入口、Home Assistant 连接、设备绑定、导入导出 */
@Composable
fun ProfileScreen(vm: HomeViewModel, onOpenEditor: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var message by remember { mutableStateOf<String?>(null) }
    var showBindings by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text("我的", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))

        // ---- 户型编辑器入口 ----
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkCard)
                .clickable { onOpenEditor() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🏠", modifier = Modifier.width(32.dp))
            Column {
                Text("户型编辑器", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text("添加房间、拖动墙体、摆放门窗与设备", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- Home Assistant 连接 ----
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkCard)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏠 Home Assistant", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (vm.haConnected) Color(0xFF1E3A2A) else Color(0xFF2A2338))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (vm.haConnected) "● ${vm.haChannel}" else "○ ${vm.haStatus}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (vm.haConnected) Color(0xFF6FD598) else TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = vm.haUrl,
                onValueChange = { vm.haUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("局域网地址（如 http://192.168.1.2:8123）") },
                colors = haFieldColors()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.haRemoteUrl,
                onValueChange = { vm.haRemoteUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("远程地址（https://xxx.ui.nabu.casa 或隧道域名）") },
                colors = haFieldColors()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.haToken,
                onValueChange = { vm.haToken = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("长期访问令牌（Long-Lived Access Token）") },
                visualTransformation = PasswordVisualTransformation(),
                colors = haFieldColors()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(if (vm.haConnected) "断开" else "连接 HA", Modifier.weight(1f)) {
                    if (vm.haConnected) vm.disconnectHa() else vm.connectHa()
                    message = vm.haStatus
                }
                ActionButton("自动匹配设备", Modifier.weight(1f)) {
                    val n = vm.autoAssignEntities()
                    message = if (n > 0) "已自动绑定 $n 个设备的实体" else "没有匹配到实体，可在下方手动填写"
                }
                ActionButton("绑定列表", Modifier.weight(1f)) { showBindings = !showBindings }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "连接策略：局域网优先，不可达时自动走远程，断线 5 秒重连。\n" +
                    "远程接入三选一：\n" +
                    "① Nabu Casa：HA 设置面板一键开通，付费，得到 https://xxx.ui.nabu.casa\n" +
                    "② Cloudflare Tunnel / frp：自建隧道，免费，把 HA 8123 端口映射到自己的 https 域名\n" +
                    "③ Tailscale / WireGuard：手机装 VPN 组网，远程地址留空即可（仍走虚拟局域网）",
                style = MaterialTheme.typography.labelSmall, color = TextDim
            )

            // 绑定列表
            if (showBindings) {
                Spacer(Modifier.height(10.dp))
                Text("设备 ↔ 实体绑定（entity_id）", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                val drafts = remember(vm.plan) {
                    mutableStateMapOf<String, String>().apply {
                        vm.plan.devices.forEach { put(it.id, it.entityId ?: "") }
                    }
                }
                vm.plan.devices.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(
                            d.name, style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary, modifier = Modifier.weight(0.32f), maxLines = 1
                        )
                        OutlinedTextField(
                            value = drafts[d.id] ?: "",
                            onValueChange = { drafts[d.id] = it },
                            modifier = Modifier.weight(0.68f),
                            singleLine = true,
                            placeholder = { Text("light.xxx / switch.xxx", color = TextDim, style = MaterialTheme.typography.labelSmall) },
                            textStyle = MaterialTheme.typography.labelMedium,
                            colors = haFieldColors(compact = true)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("保存绑定", Modifier.weight(1f)) {
                        vm.plan.devices.forEach { d ->
                            vm.updateDeviceEntity(d.id, drafts[d.id] ?: "")
                        }
                        message = "绑定已保存，控制将直接下发到 HA"
                    }
                }
            }
        }

        message?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Accent, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(14.dp))
        Text("户型数据 (plan.json)", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("导出到剪贴板", Modifier.weight(1f)) {
                clipboard.setText(AnnotatedString(vm.exportPlan()))
                message = "户型 JSON 已复制到剪贴板"
            }
            ActionButton("从剪贴板导入", Modifier.weight(1f)) {
                val text = clipboard.getText()?.text ?: ""
                if (vm.importPlan(text)) message = "导入成功"
                else message = "导入失败：剪贴板内容不是有效户型"
            }
            ActionButton("恢复默认", Modifier.weight(1f)) {
                vm.resetPlan()
                message = "已恢复默认示例户型"
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("栖居 Qiju v0.3.0", style = MaterialTheme.typography.labelSmall, color = TextDim)
        Text("户型参考：house-control · 数据源：Home Assistant REST + WebSocket",
            style = MaterialTheme.typography.labelSmall, color = TextDim)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun haFieldColors(compact: Boolean = false) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Color(0xFF2A3558),
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = if (compact) androidx.compose.ui.graphics.Color.Transparent else androidx.compose.ui.graphics.Color.Transparent,
    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
)

@Composable
private fun ActionButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCardHi)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextPrimary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}
