# Keep WebSocket
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep serialization
-keep class kotlinx.serialization.** { *; }

# Keep our protocol classes
-keep class com.touchcontrol.gesture.GestureProtocol$** { *; }
