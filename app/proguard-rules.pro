# REMNANT — regras R8 para o build de release (minify + shrinkResources).
# Mantém só o que reflexão/serialização realmente precisa. Room, Retrofit, OkHttp, Moshi,
# CameraX, ML Kit e Compose já embarcam as suas próprias regras via META-INF/proguard.

# --- Diagnóstico: preserva número de linha para stack traces legíveis (sem nomes de ficheiro).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Assinaturas genéricas e anotações são necessárias ao Retrofit/Moshi para tipos parametrizados.
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# --- Moshi (codegen): os adapters gerados são referenciados por nome de classe.
-keep class **JsonAdapter { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-dontwarn com.squareup.moshi.**

# --- Retrofit: interfaces de serviço e tipos de retorno suspend (Continuation).
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# --- Room: entidades/DAOs anotados são gerados em compile-time; só os nomes de coluna importam.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- ML Kit / Play Services: modelos e binders são carregados por reflexão.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# --- Health Connect (classes de permissão referenciadas por string no manifesto).
-keep class androidx.health.connect.client.** { *; }
-dontwarn androidx.health.connect.**

# --- Receivers e Activities declarados no manifesto (o R8 já os mantém, mas fica explícito).
-keep class com.liferpg.notif.DescansoReceiver { *; }
-keep class com.liferpg.notif.LembreteReceiver { *; }

# --- Remove logs verbosos do binário final. Sem android.util.Log.v/d/i no release.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# O catálogo empacotado usa os nomes destes enums no JSON. Mantê-los estáveis é
# obrigatório: se o R8 os renomear, a release abre sem exercícios nem treinos.
-keep enum com.liferpg.domain.Musculo { *; }
-keep enum com.liferpg.domain.ModoMedida { *; }
-keep enum com.liferpg.domain.LocalTreino { *; }

# --- Kotlin metadata / coroutines
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
