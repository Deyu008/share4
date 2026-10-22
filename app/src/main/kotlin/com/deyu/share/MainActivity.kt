package com.deyu.share

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.material3.LocalContentColor
import com.deyu.share.data.AppState
import com.deyu.share.data.SettingsStore
import com.deyu.share.ui.AppNav
import com.deyu.share.ui.theme.DiffuseBackground
import com.deyu.share.ui.theme.LocalHazeState
import com.deyu.share.ui.theme.Share4Theme
import com.deyu.share.ui.theme.rememberMaterialState
import dev.chrisbanes.haze.hazeSource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppState.init(applicationContext)
        SettingsStore.init(applicationContext)
        setContent {
            Share4Theme {
                // 背景单独作为一个 haze 源:顶栏玻璃模糊它。
                // 注意不能把 AppNav 也放进源里——haze 的效果节点会跳过"正在绘制的祖先源",
                // 那样底栏玻璃在源内部取不到样本,只剩色罩不模糊。
                val bgHazeState = rememberMaterialState()
                CompositionLocalProvider(
                    LocalHazeState provides bgHazeState,
                    // 全局默认文字色:卡片/顶栏都是自绘的(没有 Surface 提供 contentColor),
                    // 不给的话默认是黑色,深色模式下会看不见
                    LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().hazeSource(bgHazeState)) {
                            DiffuseBackground()
                        }
                        AppNav()
                    }
                }
            }
        }
    }
}
