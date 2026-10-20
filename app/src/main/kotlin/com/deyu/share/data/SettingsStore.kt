package com.deyu.share.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 应用设置仓储(仿 Share 的 C5108oOo00O0o 设置仓库)。
 * 只登记真实生效的开关;未实装的功能不放假开关。
 * 深色模式为 Compose 可观察状态,切换即时生效;其余项按需读取。
 */
object SettingsStore {
    private const val FILE = "settings"

    private lateinit var sp: SharedPreferences

    /** 夜间模式 */
    enum class DarkMode { SYSTEM, LIGHT, DARK }

    private var darkModeState by mutableStateOf(DarkMode.SYSTEM)

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        darkModeState = when (sp.getString("darkMode", "system")) {
            "light" -> DarkMode.LIGHT
            "dark" -> DarkMode.DARK
            else -> DarkMode.SYSTEM
        }
    }

    var darkMode: DarkMode
        get() = darkModeState
        set(v) {
            darkModeState = v
            sp.edit().putString("darkMode", v.name.lowercase()).apply()
        }

    /** 隐藏底栏「热门」板块(对应原版设置“隐藏热门板块”,重启页面生效) */
    var hideHotTab: Boolean
        get() = sp.getBoolean("hideHotTab", false)
        set(v) = sp.edit().putBoolean("hideHotTab", v).apply()

    /** 重复点击当前 tab 时刷新列表(对应原版设置同名项) */
    var tapTabToRefresh: Boolean
        get() = sp.getBoolean("tapTabToRefresh", true)
        set(v) = sp.edit().putBoolean("tapTabToRefresh", v).apply()
}
