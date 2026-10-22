package com.deyu.share.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.deyu.share.ui.components.EmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 草稿箱(仿 StatusDraftActivity):点击继续编辑,右侧删除(带确认) */
@Composable
fun DraftsScreen(nav: NavController) {
    val ctx = LocalContext.current
    var drafts by remember { mutableStateOf(DraftStore.list(ctx)) }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除草稿?") },
            text = { Text("删除后无法恢复") },
            confirmButton = {
                TextButton(onClick = {
                    DraftStore.remove(ctx, id)
                    drafts = DraftStore.list(ctx)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    if (drafts.isEmpty()) {
        EmptyState(
            text = "草稿箱是空的",
            icon = Icons.Outlined.EditNote,
            hint = "发博时关闭编辑页可以「存入草稿箱」",
        )
        return
    }
    LazyColumn {
        items(drafts, key = { it.id }) { d ->
            Row(Modifier.fillMaxWidth()
                .clickable { nav.navigate(Routes.composer(d.id)) }
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.text.ifBlank { "(空草稿)" }, fontSize = 15.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
                        .format(Date(d.updatedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { pendingDelete = d.id }) {
                    Icon(Icons.Outlined.DeleteOutline, "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 16.dp))
        }
    }
}
