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
 * (weibo.com ajax 通道)。可带初始关键词直接出结果。
 */
@Composable
fun SearchPage(nav: NavController, initialQuery: String = "") {
    val scope = rememberCoroutineScope()
    val cardNav = remember { cardNavOf(nav) }
    var keyword by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<WeiboMblog>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(initialQuery.isNotBlank()) }
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
        if (initialQuery.isNotBlank()) doSearch(initialQuery)
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
                LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
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
