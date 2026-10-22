package com.deyu.share.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.screens.AboutScreen
import com.deyu.share.ui.screens.ComposerScreen
import com.deyu.share.ui.screens.DmChatScreen
import com.deyu.share.ui.screens.DmUsersScreen
import com.deyu.share.ui.screens.DraftsScreen
import com.deyu.share.ui.screens.FavoritesScreen
import com.deyu.share.ui.screens.GeneralSettingsScreen
import com.deyu.share.ui.screens.HomeScreen
import com.deyu.share.ui.screens.LoginScreen
import com.deyu.share.ui.screens.PremiumScreen
import com.deyu.share.ui.screens.ProfileScreen
import com.deyu.share.ui.screens.ProtocolScreen
import com.deyu.share.ui.screens.SearchPage
import com.deyu.share.ui.screens.SettingsScreen
import com.deyu.share.ui.screens.StatusDetailScreen
import com.deyu.share.ui.screens.VerifyScreen
import com.deyu.share.ui.screens.VideoScreen
import com.deyu.share.ui.screens.ViewerScreen
import com.deyu.share.ui.theme.edgeLineColor
import com.deyu.share.ui.theme.glassPanel
import com.deyu.share.ui.theme.topEdgeLine

object Routes {
    const val LOGIN = "login"
    const val TIMELINE = "home"                    // 主壳(仿 StatusActivity)
    const val COMPOSER = "composer"
    const val PROTOCOL = "protocol"
    const val VIEWER = "viewer"
    const val VIDEO = "video"
    const val VERIFY = "verify"
    const val PROFILE = "profile/{uid}"
    const val DETAIL = "detail/{mid}"
    const val SEARCH = "search?q={q}"
    const val SETTINGS = "settings"
    const val SETTINGS_GENERAL = "settings/general"
    const val PREMIUM = "premium"
    const val ABOUT = "about"
    const val FAVORITES = "favorites"
    const val DRAFTS = "drafts"
    const val DM_USERS = "dm/users"
    const val DM_CHAT = "dm/chat/{uid}?name={name}"
    const val COMPOSER_DRAFT = "composer?draftId={draftId}"

    fun profile(uid: String) = "profile/$uid"
    fun detail(mid: String) = "detail/$mid"
    fun search(query: String = "") = "search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
    fun dmChat(uid: String, name: String) =
        "dm/chat/$uid?name=" + java.net.URLEncoder.encode(name, "UTF-8")
    fun composer(draftId: Long = 0L) =
        if (draftId == 0L) COMPOSER else "composer?draftId=$draftId"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    // 已有会话则直达主壳(会话恢复免登录)
    val start = remember { if (AppState.session != null) Routes.TIMELINE else Routes.LOGIN }

    NavHost(
        navController = nav, startDestination = start,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(260)) + fadeIn(tween(260))
        },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(220)) + fadeOut(tween(220))
        },
    ) {
        composable(Routes.LOGIN) { LoginScreen(nav) }
        composable(Routes.TIMELINE) { HomeScreen(nav) }
        composable(Routes.COMPOSER) { ComposerScreen(nav) }
        composable(Routes.PROTOCOL) { ProtocolScreen(nav) }
        composable(Routes.SEARCH) { entry ->
            SearchPage(nav, entry.arguments?.getString("q").orEmpty())
        }
        composable(Routes.VIEWER, enterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(220)) }) { ViewerScreen(nav) }
        composable(Routes.VIDEO, enterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(220)) }) { VideoScreen(nav) }
        composable(Routes.VERIFY) { VerifyScreen(nav) }
        composable(Routes.PROFILE) { entry ->
            ProfileScreen(entry.arguments?.getString("uid").orEmpty(), nav)
        }
        composable(Routes.DETAIL) { entry ->
            StatusDetailScreen(entry.arguments?.getString("mid").orEmpty(), nav)
        }
        composable(Routes.SETTINGS) { SettingsScreen(nav) }
        composable(Routes.SETTINGS_GENERAL) { GeneralSettingsScreen(nav) }
        composable(Routes.PREMIUM) { PremiumScreen(nav) }
        composable(Routes.ABOUT) { AboutScreen(nav) }
        composable(Routes.FAVORITES) { FavoriteRoute(nav) }
        composable(Routes.DRAFTS) { DraftsRoute(nav) }
        composable(Routes.DM_USERS) { DmUsersRoute(nav) }
        composable(Routes.DM_CHAT) { entry ->
            DmChatRoute(
                uid = entry.arguments?.getString("uid").orEmpty(),
                name = entry.arguments?.getString("name").orEmpty(), nav = nav)
        }
        composable(Routes.COMPOSER_DRAFT) { entry ->
            ComposerScreen(nav, entry.arguments?.getString("draftId")?.toLongOrNull() ?: 0L)
        }
    }
}

/** 收藏/草稿/私信路由页:统一带玻璃顶栏的脚手架包装 */
@Composable
private fun Titled(title: String, nav: NavController, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        // 玻璃顶栏(延伸进状态栏后面)
        Box(Modifier.fillMaxWidth()
            .glassPanel()
            .topEdgeLine(edgeLineColor())) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(52.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回",
                        tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(title, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
            }
        }
        Box(Modifier.weight(1f).navigationBarsPadding()) { content() }
    }
}

@Composable
private fun FavoriteRoute(nav: NavController) = Titled("我的收藏", nav) { FavoritesScreen(nav) }

@Composable
private fun DraftsRoute(nav: NavController) = Titled("草稿箱", nav) { DraftsScreen(nav) }

@Composable
private fun DmUsersRoute(nav: NavController) = Titled("私信", nav) { DmUsersScreen(nav) }

@Composable
private fun DmChatRoute(uid: String, name: String, nav: NavController) =
    Titled(name.ifBlank { "私信" }, nav) { DmChatScreen(uid, name, nav) }
