# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
# Add specific rules for your app’s dependencies (if any)
# Example: Keep classes used by libraries like Gson or Retrofit
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
