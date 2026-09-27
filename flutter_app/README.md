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
