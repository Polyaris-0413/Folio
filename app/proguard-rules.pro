# Folio 的 R8/ProGuard 规则。
# 目前应用无反射/序列化自定义逻辑;第三方库(Compose、aboutlibraries、materialkolor)
# 自带 consumer rules,无需额外 keep。

# 统一日志出口 AppLog:release 剥离调试级日志(d),消息字符串拼接随调用点一并消除;
# 警告/错误(w/e)保留供线上排障
-assumenosideeffects class com.folio.read.util.AppLog {
    public void d(java.lang.String, java.lang.String);
}
