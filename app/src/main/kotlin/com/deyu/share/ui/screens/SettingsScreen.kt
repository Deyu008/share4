package com.deyu.share.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deyu.share.data.SettingsStore
import com.deyu.share.ui.Routes

/**
 * 设置页,结构仿原版 SettingActivity(g.xml 聚合分类)。
 * 原则:只列真实生效的项;原版其余分类(流量控制/音效/通知/隐私/
 * 阅读习惯/微博样式/布局样式)随功能实装逐步加入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    Scaffold(
        topBar = { SettingsTopBar("设置", nav) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsSection("常规设置")
            SettingsCard {
                SettingsEntry("基本设置", "夜间模式、首页显示与刷新") {
                    nav.navigate(Routes.SETTINGS_GENERAL)
                }
            }
            SettingsSection("其他")
            SettingsCard {
                SettingsEntry("Share 高级版", "已解锁") { nav.navigate(Routes.PREMIUM) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 16.dp))
                SettingsEntry("关于", null) { nav.navigate(Routes.ABOUT) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 基本设置:本轮真实生效的开关(对应原版 i.xml/n.xml 中的同名项) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(nav: NavController) {
    var darkMode by remember { mutableStateOf(SettingsStore.darkMode) }
    var hideHotTab by remember { mutableStateOf(SettingsStore.hideHotTab) }
    var tapRefresh by remember { mutableStateOf(SettingsStore.tapTabToRefresh) }

    Scaffold(
        topBar = { SettingsTopBar("基本设置", nav) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsSection("显示")
            SettingsCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("夜间模式", fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    // 三态单选:横向等分,不再挤成一排
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            SettingsStore.DarkMode.SYSTEM to "跟随系统",
                            SettingsStore.DarkMode.LIGHT to "浅色",
                            SettingsStore.DarkMode.DARK to "深色",
                        ).forEach { (mode, label) ->
                            val on = darkMode == mode
                            Row(Modifier.weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    darkMode = mode; SettingsStore.darkMode = mode
                                }
                                .padding(vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = on, onClick = {
                                    darkMode = mode; SettingsStore.darkMode = mode
                                }, modifier = Modifier.size(28.dp))
                                Text(label, fontSize = 13.sp,
                                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            SettingsSection("首页")
            SettingsCard {
                SwitchEntry("隐藏热门板块", "底栏不再显示「热门」tab(返回首页生效)",
                    hideHotTab) { hideHotTab = it; SettingsStore.hideHotTab = it }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 16.dp))
                SwitchEntry("重复点击首页 tab 时刷新", null, tapRefresh) {
                    tapRefresh = it; SettingsStore.tapTabToRefresh = it
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(title: String, nav: NavController) {
    androidx.compose.material3.TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        navigationIcon = {
            androidx.compose.material3.IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground),
    )
}

/** 分类小标题:主色 + 全大写观感,与卡片留白一起形成分组层次 */
@Composable
private fun SettingsSection(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 8.dp))
}

/** 设置分组卡:圆角白底,条目之间用细线分隔 */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surface)) {
        content()
    }
}

@Composable
private fun SettingsEntry(title: String, summary: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            summary?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun SwitchEntry(title: String, summary: String?, value: Boolean,
                        onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            summary?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
