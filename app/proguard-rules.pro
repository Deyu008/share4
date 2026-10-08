# kotlinx-serialization(协议模型 LoginSession 反射用)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.deyu.share4.protocol.**$$serializer { *; }
-keepclassmembers class com.deyu.share4.protocol.** { *** Companion; }
-keepclasseswithmembers class com.deyu.share4.protocol.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp/Coil 自带 consumer 规则,无需手写
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# JNI 绑定占位类:so 的 JNI_OnLoad 按类名/方法名精确查找,禁止混淆与裁剪
-keep class com.sina.weibo.** { *; }
