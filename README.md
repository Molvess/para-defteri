<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Para Defteri

Jetpack Compose ve Room tabanlı Android para defteri uygulaması.

View your app in AI Studio: https://ai.studio/apps/2e08094b-ed22-4fea-8410-9edefd100be9

## APK derleme

Proje Gradle wrapper içerir; Android Studio zorunlu değildir. JDK 21 ve Android SDK bulunan bir ortamda:

```bash
./gradlew assembleDebug
```

Oluşan APK: `app/build/outputs/apk/debug/app-debug.apk`

Google AI Studio'ya yukarıdaki bağlantıdan veya private GitHub deposunu içe aktararak açabilirsiniz. Private repo erişimi için GitHub hesabını yetkilendirmeniz gerekir.

Release APK/AAB için imza anahtarı ve `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` ortam değişkenleri ayrıca yapılandırılmalıdır. Bu gizli bilgiler repoya eklenmemelidir.
