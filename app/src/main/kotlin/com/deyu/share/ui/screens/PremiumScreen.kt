package com.deyu.share.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

/**
 * Share 高级版(仿原版 SharePurchaseActivity 的形态)。
 * 本复刻为个人内部版:全部高级功能直接解锁、永久生效,无付费门;
 * 清单即原版会员门(O00O0Oo()Z 调用点)逆向出的功能面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(nav: NavController) {
    val features = listOf(
        "多账号登录(最多 10 个)" to "原版免费版仅 1 个",
        "移除捐赠提示横幅" to "原版免费版 7 天后出现",
        "关键词屏蔽数量上限提升" to "年费会员档位上限",
        "发博自定义样式(尾巴/字体菜单)" to "原版发布页高级菜单",
        "个人主页高级功能" to "原版主页菜单解锁项",
    )
    Scaffold(
        topBar = { SettingsTopBar("Share 高级版", nav) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())) {
            // 徽章头
            Row(Modifier.fillMaxWidth()
                .padding(top = 12.dp, bottom = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.WorkspacePremium, null,
                        tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("高级版 · 已解锁", style = MaterialTheme.typography.titleLarge)
                    Text("内部版本,永久生效", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("高级功能", fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 6.dp))
            features.forEachIndexed { i, (name, note) ->
                Row(Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 15.sp)
                        Text(note, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (i < features.lastIndex) Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("功能随版本逐步实装;解锁状态对所有已实装与后续功能立即生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 关于页(仿 SettingAboutActivity) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(nav: NavController) {
    Scaffold(
        topBar = { SettingsTopBar("关于", nav) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(28.dp))
            Text("Share", style = MaterialTheme.typography.titleLarge)
            Text("1.0.0(复刻版)", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Text("忠实复刻自已停更的第三方微博客户端 Share 3.9.5(com.hengye.share),\n" +
                "协议层为纯 Kotlin 逆向实现,仅供个人学习使用。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
