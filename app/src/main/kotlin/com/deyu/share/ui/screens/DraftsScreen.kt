package com.deyu.share.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.deyu.share.data.DraftStore
import com.deyu.share.ui.Routes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 草稿箱(仿 StatusDraftActivity):点击继续编辑,右滑/按钮删除 */
@Composable
fun DraftsScreen(nav: NavController) {
    val ctx = LocalContext.current
    var drafts by remember { mutableStateOf(DraftStore.list(ctx)) }

    Column(Modifier.fillMaxSize()) {
        if (drafts.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("草稿箱是空的", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn {
                items(drafts, key = { it.id }) { d ->
                    Row(Modifier.fillMaxWidth()
                        .clickable { nav.navigate(Routes.composer(d.id)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.text, fontSize = 15.sp, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            Text(SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
                                .format(Date(d.updatedAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            DraftStore.remove(ctx, d.id)
                            drafts = DraftStore.list(ctx)
                        }) {
                            Icon(Icons.Outlined.DeleteOutline, "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
