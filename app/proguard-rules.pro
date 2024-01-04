-optimizationpasses 5
-optimizations !code/simplification/cast,!field/*,!class/merging/*
-keepattributes Exceptions,InnerClasses,Signature,SourceFile,LineNumberTable,*Annotation*

-keep class **.R { *; }
-keep class **.R$* { *; }

-dontwarn android.webkit.JavascriptInterface
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
