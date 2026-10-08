package com.deyu.share.ui.screens

import com.deyu.share4.protocol.VideoQuality
import com.deyu.share4.protocol.WeiboMblog

/** 未登录态的展示数据(字段与真实 cardlist 对齐,登录后无缝切换) */
val mockTimeline = listOf(
    WeiboMblog(
        mid = "1", userName = "微博小秘书", verified = true, verifiedType = 0,
        createdAt = "刚刚", source = "微博 weibo.com",
        text = "欢迎来到 <b>Share 4.0 社区版</b>!这是重写客户端的第一个时间线,当前为未登录演示数据。登录后这里将加载你的真实关注流。",
        reposts = 12, comments = 34, likes = 56,
    ),
    WeiboMblog(
        mid = "2", userName = "数码测评君",
        createdAt = "5分钟前", source = "Share 4.0 for Android",
        text = "协议层进展:guest/login 实测 200,登录身份校验(i+s+p)通过,短信发码格式验证完成,发博 multipart 规格就绪。端到端只差测试小号!",
        reposts = 3, comments = 8, likes = 21,
        retweetedUser = "余恒业",
        retweetedText = "Share 3.9.5 最终版发布,感谢大家多年的陪伴。",
    ),
    WeiboMblog(
        mid = "3", userName = "电影捞饭", verified = true, verifiedType = 0,
        createdAt = "12分钟前", source = "iPhone 16 Pro",
        text = "本周观影清单出炉!三部冷门佳作推荐,部部高分 🎬 🍿",
        pics = listOf(
            "https://picsum.photos/seed/weibo1/900/1200",
            "https://picsum.photos/seed/weibo2/1200/800",
            "https://picsum.photos/seed/weibo3/1000/1000",
        ),
        reposts = 45, comments = 130, likes = 892,
    ),
    WeiboMblog(
        mid = "4", userName = "深夜食堂主理人",
        createdAt = "30分钟前", source = "Android 17",
        text = "今日份深夜放毒:一碗热气腾腾的豚骨拉面,汤头熬了 18 小时。地址在评论区!",
        pics = listOf("https://picsum.photos/seed/ramen/1400/900"),
        reposts = 8, comments = 56, likes = 234,
    ),
    WeiboMblog(
        mid = "5", userName = "科技美学", verified = true, verifiedType = 1,
        createdAt = "1小时前", source = "微博视频号",
        text = "【一周科技盘点】这个季度最值得期待的新品都在这里了 👉 点开看视频,支持长按 3 倍速和清晰度切换!",
        videoCover = "https://picsum.photos/seed/tech/1280/720",
        videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        videoQualities = listOf(
            VideoQuality("1080P", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
            VideoQuality("720P", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"),
            VideoQuality("标清", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"),
        ),
        reposts = 120, comments = 340, likes = 2100,
    ),
)
