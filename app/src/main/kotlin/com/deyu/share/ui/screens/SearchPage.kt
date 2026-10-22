package com.deyu.share.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.components.EmptyState
import com.deyu.share.ui.components.FeedDivider
import com.deyu.share.ui.components.LoadingState
import com.deyu.share.ui.components.WeiboCard
import com.deyu.share.ui.components.cardNavOf
import com.deyu.share4.protocol.HotWord
import com.deyu.share4.protocol.WeiboMblog
import kotlinx.coroutines.launch

/**
 * 搜索页:顶栏(返回 + 搜索框 + 搜索键)+ 未搜索时的微博热搜榜
 * (weibo.com ajax 通道)+ 结果流。可带初始关键词直接出结果。
 */
@Composable
fun SearchPage(nav: NavController, initialQuery: String = "") {
    val scope = rememberCoroutineScope()
    val cardNav = remember { cardNavOf(nav) }
    val keyboard = LocalSoftwareKeyboardController.current
    var keyword by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<WeiboMblog>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(initialQuery.isNotBlank()) }
    var hotWords by remember { mutableStateOf<List<HotWord>>(emptyList()) }

    fun doSearch(word: String) {
        val w = word.trim()
        if (w.isBlank() || searching) return
        keyword = w
        searching = true; searched = true
        keyboard?.hide()
        scope.launch {
            results = runCatching { AppState.client.search(w) }.getOrDefault(emptyList())
            searching = false
        }
    }

    LaunchedEffect(Unit) {
        hotWords = runCatching { AppState.client.hotSearch() }.getOrDefault(emptyList())
        // 只带关键词进来时直接搜;从顶栏空手进来不抢焦点,先看热搜
        if (initialQuery.isNotBlank()) doSearch(initialQuery)
    }

    // 独立路由页:搜索框行即顶栏,须避开系统状态栏(edge-to-edge)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            OutlinedTextField(
                value = keyword, onValueChange = { keyword = it },
                placeholder = { Text("搜索微博…", fontSize = 14.sp) },
                singleLine = true,
                shape = CircleShape,
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = {
                    Icon(Icons.Filled.Search, null, Modifier.width(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (keyword.isNotEmpty()) {
                        IconButton(onClick = { keyword = "" }) {
                            Icon(Icons.Filled.Close, "清空", Modifier.width(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch(keyword) }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text("搜索", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (keyword.isNotBlank() && !searching)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    .clickable(enabled = keyword.isNotBlank() && !searching) { doSearch(keyword) })
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        when {
            searching -> LoadingState()
            searched && results.isEmpty() -> EmptyState(
                text = "没有找到「$keyword」相关微博",
                icon = Icons.Filled.Search,
                hint = "换个关键词试试,或检查是否拼写有误",
            )
            searched -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "result_title") {
                    Row(Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("搜索结果", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(8.dp))
                        Text("${results.size} 条", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(results, key = { it.mid }) { m ->
                    WeiboCard(m, cardNav)
                    FeedDivider()
                }
            }
            else -> HotSearchBoard(hotWords) { doSearch(it) }
        }
    }
}
