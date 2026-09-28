# Para Defteri

Herkesin faydalanabilmesi için geliştirilen, MIT lisanslı açık kaynak borç/alacak takip uygulaması. Kayıtlar cihazda yerel olarak saklanır.

## Sürümler

- **Flutter / Dart:** [flutter_app](flutter_app/) — kişi fotoğrafları, çoklu IBAN, borç düzenleme, filtreler, kişi toplamları, Keep metni aktarımı ve CSV/JSON yedekleme. [Kurulum ve veri taşıma notları](flutter_app/NOTES.md).
- **Kotlin / Jetpack Compose:** `app/` — Room tabanlı ilk Android sürümü. Flutter sürümünden bağımsızdır; iki uygulama birlikte kurulabilir.

## Flutter APK derleme

Flutter SDK, JDK 21 ve Android SDK bulunan bir ortamda:

```bash
cd flutter_app
flutter pub get
flutter test
flutter build apk --release
```

APK: `flutter_app/build/app/outputs/flutter-apk/app-release.apk` (depo köküne göre).

Mevcut Flutter yapılandırması yerel kurulum için debug anahtarıyla imzalanmış release/AOT APK üretir. Mağaza dağıtımı için kendi kalıcı yayın anahtarınızı yapılandırın. Güncellemelerde aynı imzayı kullanın; mevcut kayıtları korumak için uygulamayı kaldırmayın.

## Kotlin APK derleme

Proje Gradle wrapper içerir; Android Studio zorunlu değildir. JDK 21 ve Android SDK bulunan bir ortamda:

```bash
./gradlew assembleDebug
```

Oluşan APK: `app/build/outputs/apk/debug/app-debug.apk`

Release APK/AAB için imza anahtarı ve `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` ortam değişkenleri ayrıca yapılandırılmalıdır. Bu gizli bilgiler repoya eklenmemelidir.

## Katkı ve gizlilik

Hata bildirimleri, öneriler ve pull request'ler memnuniyetle karşılanır. Issue, ekran görüntüsü veya örnek yedek paylaşırken gerçek kişi adı, IBAN, fotoğraf ve borç bilgilerini gizleyin. Veritabanlarını, kişisel yedekleri ve imza anahtarlarını repoya eklemeyin.

## Lisans

Projenin özgün kodu [MIT Lisansı](LICENSE) ile sunulur. Lisans ve telif bildirimlerini koruyarak kişisel veya ticari amaçla kullanabilir, değiştirebilir ve paylaşabilirsiniz. Yazılım garanti verilmeden sunulur. Üçüncü taraf bağımlılıklar kendi lisanslarına tabidir.
