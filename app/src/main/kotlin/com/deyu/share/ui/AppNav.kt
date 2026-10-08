package com.deyu.share.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.deyu.share.data.AppState
import com.deyu.share.ui.screens.ComposerScreen
import com.deyu.share.ui.screens.LoginScreen
import com.deyu.share.ui.screens.ProtocolScreen
import com.deyu.share.ui.screens.TimelineScreen
import com.deyu.share.ui.screens.VerifyScreen
import com.deyu.share.ui.screens.VideoScreen
import com.deyu.share.ui.screens.ViewerScreen

object Routes {
    const val LOGIN = "login"
    const val TIMELINE = "timeline"
    const val COMPOSER = "composer"
    const val PROTOCOL = "protocol"
    const val VIEWER = "viewer"
    const val VIDEO = "video"
    const val VERIFY = "verify"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    // 已有会话则直达时间线(会话恢复免登录)
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
        composable(Routes.TIMELINE) { TimelineScreen(nav) }
        composable(Routes.COMPOSER) { ComposerScreen(nav) }
        composable(Routes.PROTOCOL) { ProtocolScreen(nav) }
        composable(Routes.VIEWER, enterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(220)) }) { ViewerScreen(nav) }
        composable(Routes.VIDEO, enterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(220)) }) { VideoScreen(nav) }
        composable(Routes.VERIFY) { VerifyScreen(nav) }
    }
}
