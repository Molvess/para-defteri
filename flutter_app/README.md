# Para Defteri Flutter

Mevcut Kotlin/Compose uygulamasından bağımsız, Dart/Flutter ile hazırlanmış Android sürümü. Ayrı uygulama kimliğiyle birlikte kurulabilir; mevcut uygulamanın verilerini değiştirmez.

Kurulum, eski CSV kayıtlarını taşıma, yedekleme, derleme ve performans sınırları için [uygulama notlarına](NOTES.md) bakın.

```sh
flutter pub get
dart analyze --fatal-infos
flutter test
flutter build apk --release
```

APK: `build/app/outputs/flutter-apk/app-release.apk`.

Fotoğraf/dosya erişim onayları ve Play Protect mesajları için [güvenlik notları](SECURITY.md).

## Lisans

Bu sürüm de depo kökündeki [MIT Lisansı](../LICENSE) kapsamındadır. Kullanım, değişiklik ve paylaşım serbesttir; lisans ve telif bildirimleri korunmalıdır. Üçüncü taraf bağımlılıklar kendi lisanslarına tabidir.
