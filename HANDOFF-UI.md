# 交接文档：Share4 UI 优化 + 长文正文（2026-10-22）

## 一句话现状

UI 全面优化已**改完并逐页真机截图验收通过**（浅色/深色均正常）；长微博完整正文也已**收口**——
`m.weibo.cn/statuses/extend` 实测可用（1178 字全文），首页与详情页两条路径均已真机验证。
改动已 commit，**推送仍待用户开 7890 代理**。

---

## 1. 当前状态速查

| 项 | 状态 |
|---|---|
| 代码可编译 | ✅ `assembleDebug` 通过（exit=0） |
| 装机验证 | ✅ 已装 emulator-5554，长文正文真机验证通过 |
| 长文正文 | ✅ **已解决**（见 §3，端点为 m.weibo.cn/statuses/extend） |
| git | ✅ 本轮改动已 commit（拆 2 个 commit） |
| 推送 | ❌ 本地累计 13 个 commit 未推送（等用户开 7890 代理） |

工作区：`E:\Code\Share\share4`（包名 com.deyu.share），git 远端 Deyu008/share4。
构建命令：`JAVA_HOME=E:\Code\Share\tools\jdk17 .\gradlew.bat assembleDebug --offline`

---

## 2. 本轮改了什么（已验收）

### 2.1 主题与底栏
- `ui/theme/Theme.kt`：**补齐 `secondaryContainer`/`onSecondaryContainer`/`tertiary` 等色位**——之前 M3 底栏药丸指示器用的是默认紫，与微博蓝主题冲突（真机截图确认过）。
- `Typography.bodySmall` 去掉了写死的 `InkSecondary`（深色模式下对比度不足），改为继承 `onSurface`。
- `ui/screens/HomeScreen.kt`：**M3 NavigationBar → 自绘底栏**（图标+文案，选中整列变主题蓝，无药丸），并补 `navigationBarsPadding`。
- 重复点 tab 刷新的信号从布尔 `lastTab` 改成自增计数器 `refreshTick`（布尔方案连点两次第二次无效）。

### 2.2 卡片（`ui/components/WeiboCard.kt`，改动最大）
- **多图不再丢失**：原 `pics.take(3)` 静默丢弃第 4 张以后的图 → 改真九宫格（1 张大图 4:3、2/4 张两列、其余三列，超 9 张末格盖「+N」），末行补 `Spacer(weight)` 防止末张被拉宽。
- **操作栏重做**：删掉那个恒为 0、无任何功能的柱状图标（BarChart），改为 4 个等分动作（评论/转发/赞/收藏）+ 右侧「更多」下拉菜单（复制正文 / 复制链接 / 浏览器打开）。
- 收藏按钮只在收藏后才显示「已收藏」文字，未收藏时只留图标（原来恒显文字占位，五列宽度不齐）。
- **视频封面缺失时不再是一片空白灰块**：改渐变兜底 + 居中播放键 + 「N 档清晰度」角标 + 底部压暗。
- 长正文支持折叠/展开（`COLLAPSE_AT = 200` 字符，6 行封顶），并接入长文加载（见 §3）。
- 转发块补小头像；`stripHtml` 增强（还原 `&nbsp; &amp; &#39;` 等实体与 `&#10;` 换行）。
- 点赞初始态改为读服务端 `m.liked`（新增字段）。

### 2.3 状态组件抽取
- 新增 `ui/components/States.kt`：`LoadingState` / `ListFooter` / `EmptyState` / `ErrorState`。此前 8 个页面各写一套 Box+CircularProgressIndicator，间距与文案口径不一。

### 2.4 逐页优化（均已真机截图核对）
| 页面 | 改动 |
|---|---|
| 搜索页 | 补**返回键**（原来只能靠系统返回）、清空按钮、圆形搜索框；**取消自动聚焦**（原来自动弹键盘把热搜榜挡住一半）；热搜词不再被压成省略号（词条改 `weight(1f)` 撑满，热度右对齐） |
| 消息页 | 未读数从 5 个 AssistChip（窄屏挤成两行）改**五等分统计格**（图标+数字徽标+名称）；私信入口改圆形图标+副标题+箭头；`>` 字符换真图标 |
| 热门页 | 微博榜/热搜榜加下拉刷新；热搜榜头部加图标底板与「实时」标记；卡片分隔线对齐头像列 |
| 详情页 | 评论输入条补 `navigationBarsPadding`（原来贴在导航栏上）；空评论态改图标化；「来自 XX」来源行留白修正 |
| 主页 | 去掉开发用的 uid 行；性别做小徽标、地区用定位图标；计数改 `formatCount`；关注按钮改圆角胶囊 |
| 草稿箱 | 删除加**二次确认弹窗**；空态图标化；修掉重复的 `Spacer` import |
| 收藏页 | 补下拉刷新 + 空态引导文案 |
| 私信 | 会话列表未读改小红点角标；聊天气泡改四角不对称圆角；输入条补导航栏留白；**发送失败有顶部错误条**（原来静默失败） |
| 设置页 | 夜间模式三态从挤在一行改**三等分胶囊单选**；条目改圆角分组卡 + 真箭头图标 |
| 高级版 | 徽章头改蓝底白图标，功能项改圆角卡列表 |
| 发博页 | 补顶栏（关闭/标题/发布）；字数统计从悬浮层挪进底栏（原来会盖住内容）；补**图片缩略图预览**；配图按钮换图标 |

### 2.5 其他
- 切换流/刷新时 `scrollToItem(0)` 回顶部。
- 列表底部统一留 88dp，避免被 FAB 和底栏遮挡。
- `AppNav.kt` 统一 `Titled()` 顶栏字号。

---

## 3. 长微博完整正文（已收口）

### 3.1 现象
真机关注流里比亚迪那条长微博，正文停在「元UP想做的，就是在质价比和真」被硬截断，**详情页同样截断**。

### 3.2 已排除的路径（都实测过，别再重复试）
| 端点 | 实测结果 |
|---|---|
| m-api `statuses/friends_timeline` / `statuses/show` | text 截断（140 字 vs 完整 268 字） |
| mblog 内 `longText.longTextContent` | **2026 服务端根本不下发**该字段（原版 Share 的 mblog 模型里有） |
| m-api `statuses/extend?id={mid}` | 返回的是**详情页附加信息**（favorited / reads_count / comment_privilege / allow_comment / comment_manage_info），**正文不在其中**（共 67 个顶层键，无 text / longText） |
| `weibo.com/ajax/statuses/longText?id=` | `{"ok":0,"message":"你访问的地址不存在"}` |
| `weibo.com/ajax/statuses/show?id={数字id}` | 返回完整 mblog，但 **text 末尾带 `...<span class="expand">展开</span>` 截断标记**（len=268），仍不是全文 |

### 3.3 可用端点（唯一）
**`GET https://m.weibo.cn/statuses/extend?id={mid}`**，用 `.weibo.cn` 的 SUB（`LoginSession.webSub`，与 `.weibo.com` 的 `webSubCom` 是两套票据）。
响应：`{"data":{"longTextContent":"..."}}`。

实测同一条比亚迪长微博：服务端只给 140 字，该端点返回 **1178 字**（原始 HTML），尾部是完整结尾
（`#元UP荣膺全品类销量冠军##2027款元UP飞驰版焕新上市#`），不再是截断摘要。

> 坑：它与 m-api 的 `statuses/extend` **路径相似但主机不同**，极易混。

### 3.4 代码收口
`WeiboClient.longText(mid, id)` 两级候选：
1. **首选** m.weibo.cn/statuses/extend（`.weibo.cn` SUB，Referer 指向 `m.weibo.cn/detail/{mid}`）→ 全文；
2. 失败退回 weibo.com/ajax/statuses/show（`.weibo.com` SUB）→ 降级，内容本身仍截断，
   已剥掉尾部 `<span class="expand">展开</span>` 标记与结尾省略号，避免被当成全文展示。

临时 `println("Share4LongText …")` 调试行已删除（2 处）。

### 3.5 App 侧行为
卡片正文取值优先级：**已拉取的完整版 → mblog.longText → 时间线 text**。
服务端标记 `isLongText` 且正文缺失时显示「查看全文」，点击拉取并自动展开，展开后可「收起」。
首页时间线与详情页共用 `WeiboCard`，**两条路径均已真机验证全文正常显示**。

### 3.6 遗留清理项
- `ProtocolScreen.kt` 的「长文」探测按钮（mid 输入框 + 调 `client.longText`）保留——协议验证页本来就是工作台。
- `shots/` 目录是过程产物，已加进 `.gitignore`，不入库。

---

## 4. 提交与推送

已提交（2 个 commit）：
```bash
cd E:\Code\Share\share4
git log --oneline -3
```
推送需要用户先开代理（7890 Clash）：
```bash
git config --global http.https://github.com.proxy http://127.0.0.1:7890
git push -u origin main     # 远端用户名必须是 Deyu008
```

`E:\Code\Share\phase0\PROTOCOL.md` §13.1 **已按实测结论订正**：原文把
`weibo.com/ajax/statuses/show` 记为「可用端点」是错的（其 text 仍带截断标记），
已改为记录 m.weibo.cn/statuses/extend 为唯一全文端点，并补上「与 m-api 同名不同主机」的坑。

---

## 5. 环境与红线（必须继续遵守）

- 构建：`JAVA_HOME=E:\Code\Share\tools\jdk17`，`--offline`。
- adb：`C:\Users\DeYu\AppData\Local\Android\Sdk\platform-tools\adb.exe`（不在 PATH 里）。
- 模拟器 AVD `share_test`，serial `emulator-5554`。
- **安全红线（逐字保留）**：不用用户主微博账号做风控实验（测试用小号：uid 8417868306「质朴乌贼爱旅行」、5655585082「纸面师」，手机号 18128533280 只用于签名参数不落盘）；微博通讯协议忠实复刻不做「改进」；测试博文/点赞/评论须即时清理（无痕回归）；发私信只发给自己（uid 8417868306）且验证后 destroy_batch 清理；收藏测试后即时取消；测试草稿删除；手机上原版 Share(com.hengye.share) 是唯一能登录的参照物别动它；mmx API key 在 ~/.mmx/config.json 勿外泄。
- **截图坐标坑**：截图存的是 1080×2400 原始像素，但显示时会被缩放。**按显示图坐标点击会偏 1.2 倍**，必须用 `uiautomator dump` 拿真实 bounds 再点。
- 验证纪律：UI 判定必须以截图/像素证据为准，不能凭「应该开了」下结论。

---

## 6. 本轮验证过的页面（截图在 shots/）

浅色：首页、消息页、搜索页（热搜榜）、热门榜、热搜榜、详情页、抽屉、设置页、基本设置（浅/深）、高级版、草稿箱空态、发博页、存草稿弹窗、更多菜单、深色时间线。
长文：首页长微博「查看全文」入口出现 ✓、点击后正文变长并出现「收起」✓；
**2026-10-22 复核：正文已为全文（1178 字），尾部到「#2027款元UP飞驰版焕新上市#」自然结尾**；
详情页同一路径复核通过 ✓。
