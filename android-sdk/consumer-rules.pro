# Plumb5 Monitoring SDK - consumer ProGuard/R8 rules
# (Fixed: the old rules pointed at com.example.monitoring_sdk, which no longer
#  exists, so Gson-serialised models could be renamed in minified apps.)

# Keep Gson/Room model fields used for serialization
-keep class com.plumb5.monitor.network.EventRequest { *; }
-keep class com.plumb5.monitor.database.EventEntity { *; }
-keep class com.plumb5.monitor.database.TransactionEntity { *; }

# Keep the public SDK API
-keep public class com.plumb5.monitor.** { public *; }

# Keep the WebView JavaScript bridge methods
-keepclassmembers class com.plumb5.monitor.ui.P5DialogBox$P5JavaScriptInterface {
    @android.webkit.JavascriptInterface <methods>;
}
