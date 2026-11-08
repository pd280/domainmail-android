# Rust cdylib 通过 JNI 反射调用，保留 native 方法不被混淆/裁剪
-keep class com.ifwqu.domainmail.RustBridge { *; }
-keepclasseswithmembernames class * { native <methods>; }
