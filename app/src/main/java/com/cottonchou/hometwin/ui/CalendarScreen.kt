package com.cottonchou.hometwin.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cottonchou.hometwin.data.HomeViewModel
import java.time.LocalDate
import java.time.YearMonth

private data class HomeEvent(val date: LocalDate, val time: String, val title: String)

/** 日历 Tab：月视图 + 简单日程（本地示例数据，可扩展接入系统日历） */
@Composable
fun CalendarScreen(vm: HomeViewModel) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }

    val events = remember {
        val today = LocalDate.now()
        listOf(
            HomeEvent(today, "07:30", "窗帘自动开启"),
            HomeEvent(today, "18:00", "归家模式 · 预热客厅空调"),
            HomeEvent(today.plusDays(2), "10:00", "空气检测仪滤芯更换"),
            HomeEvent(today.plusDays(5), "22:00", "全屋巡检 · 门窗传感器")
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text("日历", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkCard)
                .padding(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${month.year} 年 ${month.monthValue} 月", style = MaterialTheme.typography.titleMedium)
                Row {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DarkCardHi)
                            .clickable { month = month.minusMonths(1) },
                        contentAlignment = Alignment.Center
                    ) { Text("‹", color = TextPrimary) }
                    Spacer(Modifier.size(8.dp))
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DarkCardHi)
                            .clickable { month = month.plusMonths(1) },
                        contentAlignment = Alignment.Center
                    ) { Text("›", color = TextPrimary) }
                }
            }
            Spacer(Modifier.height(10.dp))
            val first = month.atDay(1)
            val leading = first.dayOfWeek.value % 7 // 周日开头
            val days = month.lengthOfMonth()
            val cells = List(leading) { null } + (1..days).map { month.atDay(it) }
            val week = listOf("日", "一", "二", "三", "四", "五", "六")
            Row(Modifier.fillMaxWidth()) {
                week.forEach {
                    Text(it, textAlign = TextAlign.Center, color = TextDim,
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            cells.chunked(7).forEach { weekCells ->
                Row(Modifier.fillMaxWidth()) {
                    weekCells.forEach { day ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (day != null) {
                                val isSel = day == selected
                                val hasEvent = events.any { it.date == day }
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (isSel) Accent else androidx.compose.ui.graphics.Color.Transparent)
                                        .clickable { selected = day },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        day.dayOfMonth.toString(),
                                        color = if (isSel) androidx.compose.ui.graphics.Color.White else TextPrimary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (hasEvent && !isSel) {
                                    Box(
                                        Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(Warm)
                                            .align(Alignment.BottomCenter)
                                    )
                                }
                            }
                        }
                    }
                    repeat(7 - weekCells.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("${selected.monthValue} 月 ${selected.dayOfMonth} 日 日程",
            style = MaterialTheme.typography.titleSmall, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        val dayEvents = events.filter { it.date == selected }
        if (dayEvents.isEmpty()) {
            Text("无日程", color = TextDim, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            dayEvents.forEach { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkCard)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(e.time, color = Accent, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.size(12.dp))
                    Text(e.title, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
