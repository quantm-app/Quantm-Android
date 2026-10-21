# kotlinx.serialization ships its own keep rules; these cover the generated serializers of our models
-keepclassmembers @kotlinx.serialization.Serializable class app.quantm.android.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
