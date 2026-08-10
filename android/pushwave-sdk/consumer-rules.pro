# The SDK is entered from the framework (service, activity) and from app code
# that may be obfuscated separately, so keep its public surface intact.
-keep class com.pushwave.sdk.PushWave { *; }
-keep class com.pushwave.sdk.PushWave$* { *; }
-keep class com.pushwave.sdk.PushWaveMessagingService { *; }
-keep class com.pushwave.sdk.PushWaveOpenActivity { *; }
