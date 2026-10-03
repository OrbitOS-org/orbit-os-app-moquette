# ProGuard rules for moquette

# ============================================
# PRESERVE MAIN CLASS
# ============================================

-keep public class org.orbitos.apps.moquette.App {
    public static void main(java.lang.String[]);
}

-keep class org.orbitos.apps.moquette.** { *; }
-keep class org.orbitos.sdk.** { *; }
-keep class arch.ipc.** { *; }

# ============================================
# PRESERVE gRPC
# ============================================

-keep class io.grpc.** { *; }
-keep interface io.grpc.** { *; }
-keep enum io.grpc.** { *; }
-keep class * extends io.grpc.stub.AbstractStub { *; }

# ============================================
# PRESERVE OKHTTP
# ============================================

-keep class okhttp3.OkHttpClient { *; }
-keep class okhttp3.Request { *; }
-keep class okhttp3.Response { *; }
-keep class okhttp3.Call { *; }
-keep class okhttp3.Callback { *; }
-keep class okhttp3.HttpUrl { *; }
-keep class okhttp3.Headers { *; }
-keep class okhttp3.MediaType { *; }
-keep class okhttp3.RequestBody { *; }
-keep class okhttp3.ResponseBody { *; }
-keep class okhttp3.internal.** { *; }
-keep class okio.** { *; }

# ============================================
# PRESERVE PROTOBUF
# ============================================

-keep class com.google.protobuf.GeneratedMessageLite { *; }
-keep class com.google.protobuf.MessageLite { *; }
-keep class com.google.protobuf.MessageLiteOrBuilder { *; }
-keep class com.google.protobuf.Internal { *; }
-keep class com.google.protobuf.LazyStringArrayList { *; }
-keep class com.google.protobuf.ByteString { *; }
-keep class com.google.protobuf.CodedInputStream { *; }
-keep class com.google.protobuf.CodedOutputStream { *; }
-keep class com.google.protobuf.ExtensionRegistryLite { *; }
-keep class com.google.protobuf.Parser { *; }
-keep class com.google.protobuf.InvalidProtocolBufferException { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
    <methods>;
}

# ============================================
# PRESERVE JUNIXSOCKET
# ============================================

-keep class org.newsclub.** { *; }

# ============================================
# CONFIGURATION
# ============================================

-dontoptimize
-dontobfuscate
-keepnames class org.orbitos.apps.moquette.App
-keepnames class org.orbitos.sdk.**
-dontwarn **
