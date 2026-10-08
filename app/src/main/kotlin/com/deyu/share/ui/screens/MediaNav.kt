package com.deyu.share.ui.screens

import com.deyu.share4.protocol.VideoQuality

/** 媒体查看的跨页传递(避免 nav 参数序列化大列表) */
object MediaNav {
    var pics: List<String> = emptyList()
    var startIndex: Int = 0
    var videoUrl: String = ""
    var videoCover: String? = null
    var qualities: List<VideoQuality> = emptyList()

    fun openPics(list: List<String>, index: Int) {
        pics = list; startIndex = index; videoUrl = ""; qualities = emptyList()
    }

    fun openVideo(url: String, cover: String?, qualities: List<VideoQuality> = emptyList()) {
        videoUrl = url; videoCover = cover; this.qualities = qualities; pics = emptyList()
    }
}
