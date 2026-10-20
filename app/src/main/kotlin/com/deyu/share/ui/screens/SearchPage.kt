package com.deyu.share.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.components.WeiboCard
import com.deyu.share.ui.components.cardNavOf
import com.deyu.share.ui.components.formatCount
import com.deyu.share4.protocol.HotWord
import com.deyu.share4.protocol.WeiboMblog
import kotlinx.coroutines.launch

/**
 * 搜索页:顶部搜索框(100303type=1 实测端点)+ 未搜索时的微博热搜榜
 * (weibo.com ajax 通道)。热搜词点击即发起搜索。
 */
@Composable
fun SearchPage(nav: NavController) {
    val scope = rememberCoroutineScope()
    val cardNav = remember { cardNavOf(nav) }
    var keyword by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeiboMblog>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var hotWords by remember { mutableStateOf<List<HotWord>>(emptyList()) }

    fun doSearch(word: String) {
        if (word.isBlank() || searching) return
        keyword = word
        searching = true; searched = true
        scope.launch {
            results = runCatching { AppState.client.search(word.trim()) }
                .getOrDefault(emptyList())
            searching = false
        }
    }

    LaunchedEffect(Unit) {
        hotWords = runCatching { AppState.client.hotSearch() }.getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = keyword, onValueChange = { keyword = it },
                placeholder = { Text("搜索微博…") }, singleLine = true,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { doSearch(keyword) },
                enabled = keyword.isNotBlank() && !searching) {
                Icon(Icons.Filled.Search, "搜索")
            }
        }
        if (searching) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.5.dp)
            }
        } else if (searched) {
            if (results.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("没有找到相关微博", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(results, key = { it.mid }) { m ->
                        WeiboCard(m, cardNav, Modifier.padding(horizontal = 6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(start = 76.dp))
                    }
                }
            }
        } else {
            HotSearchBoard(hotWords) { doSearch(it) }
        }
    }
}

/** 热搜榜:名次 + 角标 + 词 + 热度,前三名标红 */
@Composable
private fun HotSearchBoard(words: List<HotWord>, onClick: (String) -> Unit) {
    if (words.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("热搜不可用(旧会话缺少网页票据,重新登录即可)",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item(key = "hot_title") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalFireDepartment, null,
                    tint = Color(0xFFE2413E))
                Spacer(Modifier.width(6.dp))
                Text("微博热搜", style = MaterialTheme.typography.titleMedium)
            }
        }
        items(words.size) { i ->
            val w = words[i]
            Row(Modifier.fillMaxWidth()
                .clickable { onClick(w.word) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (i < 3) Color(0xFFE2413E)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(30.dp))
                Text(w.word, fontSize = 15.sp,
                    fontWeight = if (i < 3) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1)
                w.label?.let { l ->
                    Spacer(Modifier.width(6.dp))
                    Text(l, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2413E),
                        modifier = Modifier
                            .background(Color(0x1AE2413E), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp))
                }
                Spacer(Modifier.weight(1f))
                if (w.heat > 0) {
                    Text(formatCount(w.heat), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (i < words.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(start = 46.dp))
            }
        }
    }
}
