# ===== Xposed エントリーポイント =====
-keep class com.kyomu.tools.MainHook { *; }

# ===== KyomuTools コアクラス（難読化禁止） =====
-keep class com.kyomu.tools.core.** { *; }
-keep class com.kyomu.tools.call.** { *; }
-keep class com.kyomu.tools.persist.** { *; }
-keep class com.kyomu.tools.ui.** { *; }
-keep class com.kyomu.tools.ui.pages.** { *; }
-keep class com.kyomu.tools.** { *; }

# ===== Xposed API =====
-keep class de.robv.android.xposed.** { *; }
-keep interface de.robv.android.xposed.** { *; }

# ===== JSON パース =====
-keep class org.json.** { *; }

# ===== リフレクション用 =====
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===== 最適化 =====
-optimizationpasses 3
-allowaccessmodification
