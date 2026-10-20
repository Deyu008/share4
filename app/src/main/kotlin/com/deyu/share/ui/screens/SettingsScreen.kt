package com.deyu.share.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            SettingsSection("常规设置")
            SettingsEntry("基本设置") { nav.navigate(Routes.SETTINGS_GENERAL) }
            SettingsSection("其他")
            SettingsEntry("Share 高级版") { nav.navigate(Routes.PREMIUM) }
            SettingsEntry("关于") { nav.navigate(Routes.ABOUT) }
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            SettingsSection("显示")
            Text("夜间模式", fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                listOf(
                    SettingsStore.DarkMode.SYSTEM to "跟随系统",
                    SettingsStore.DarkMode.LIGHT to "浅色",
                    SettingsStore.DarkMode.DARK to "深色",
                ).forEach { (mode, label) ->
                    Row(Modifier.clickable {
                        darkMode = mode; SettingsStore.darkMode = mode
                    }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = darkMode == mode, onClick = {
                            darkMode = mode; SettingsStore.darkMode = mode
                        })
                        Text(label, fontSize = 14.sp)
                    }
                }
            }
            SettingsSection("首页")
            SwitchEntry("隐藏热门板块", "底栏不再显示「热门」tab(重启页面生效)", hideHotTab) {
                hideHotTab = it; SettingsStore.hideHotTab = it
            }
            SwitchEntry("当重复点击首页的tab时刷新", null, tapRefresh) {
                tapRefresh = it; SettingsStore.tapTabToRefresh = it
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(title: String, nav: NavController) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground),
    )
}

@Composable
private fun SettingsSection(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp,
            vertical = 10.dp))
}

@Composable
private fun SettingsEntry(title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchEntry(title: String, summary: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}
