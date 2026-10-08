# Share 4.0

从零重写的第三方微博客户端(社区版),接替已停更的 Share 3.9.5。

Kotlin + Jetpack Compose,双模块架构,原生协议栈。

## 架构

```
┌─────────────────────────────────────────┐
│  :app        Compose UI                 │
│  ├─ 登录(短信验证码 / 密码,含风控验证页) │
│  ├─ 时间线(X 风格卡片,玻璃顶/底栏)      │
│  ├─ 图片查看器(缩放/翻页)               │
│  ├─ 视频播放器(清晰度切换/长按倍速)      │
│  └─ 发博 / 个人页 / WebView 兜底         │
├─────────────────────────────────────────┤
│  :protocol   纯 Kotlin 协议层            │
│  ├─ 请求签名(s / i / p / mfp / cum)     │
│  ├─ guest/login 匿名设备注册             │
│  ├─ 短信登录(发码 → 验码)               │
│  ├─ alt 免密重登 / 会话续期              │
│  └─ cardlist 时间线解析                  │
├─────────────────────────────────────────┤
│  jniLibs     签名计算 native 桥          │
└─────────────────────────────────────────┘
```

## 模块说明

| 模块 | 内容 |
|---|---|
| `:protocol` | 微博 m-api 协议封装,纯 Kotlin/JVM,可独立单测(与 Python 参考实现对拍的金标准单测) |
| `:app` | Compose UI,Material 3,edge-to-edge,Haze 玻璃效果,Media3 播放器 |
| native 桥 | `libwbutil.so`(签名门恒真 patch,算法保留),通过同名类 JNI 绑定,零 NDK |

## 构建

```bash
./gradlew assembleDebug     # debug 包
./gradlew assembleRelease   # R8 优化,约 2.5MB
```

要求:JDK 17,Android SDK 35。`local.properties` 里配 `sdk.dir`。

## 协议层要点

- 基址 `api.weibo.cn/2/`,gsid 会话制;匿名接口(设备注册/短信)与已登录接口(时间线)签名要求不同
- 已登录请求签名(`cum`)由 native 桥计算,输入为与实际请求一致的完整 query 串
- 短信登录链路:`login_sendcode` → 风控时经 `errurl` WebView 验证页(JSBridge)→ 验码 → 会话
- 登录会话(gsid / alt / web cookie)持久化,token 过期自动续期,WebView 时间线为降级兜底

## 已验证

- guest/login 匿名注册(真实服务端)
- 短信登录端到端(真实账号,含风控验证页全流程)
- 会话持久化与免密重登
- release 构建 R8 + 资源收缩

## 声明

本项目仅供学习交流协议逆向与 Android 客户端开发。请勿用于违反微博服务条款的用途;使用产生的账号风险由使用者自行承担。
