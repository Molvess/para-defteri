# Fotoğraf/dosya erişimi ve APK kurulumu

## Erişim modeli

Fotoğraf ekleme, yedek içe aktarma ve dışa aktarma öncesinde uygulama içi açıklama/onay gösterilir. Vazgeçildiğinde Android seçicisi açılmaz. **1.1.3 itibarıyla** bu açıklamadan sonra native tarafta gerçek runtime izin denetimi ve gerekiyorsa `requestPermissions` çağrısı vardır. İçe aktarmada kayıt yazmadan önce ayrıca önizleme/onay bulunur.

Fotoğraf seçiminden önce Android 13'te `READ_MEDIA_IMAGES`, Android 14+ için bununla birlikte `READ_MEDIA_VISUAL_USER_SELECTED`, Android 7–12'de `READ_EXTERNAL_STORAGE` kontrol edilir/istenir. Android 14+ kısmi fotoğraf izni de yeterlidir; tam galeri iznine zorlanmaz. Önceden verilen izinler yeniden sorulmaz; her işlemde güncel izin kontrol edilir. Ret/iptal halinde fotoğraf seçici açılmaz. Tekrar sorulamayan izinler için uygulama ayarlarına bağlantı gösterilir; ayarlardan dönünce kullanıcı işlemi yeniden başlatır.

Dosya işlemlerinde Android 7–9'da okuma için `READ_EXTERNAL_STORAGE`, yazma için `WRITE_EXTERNAL_STORAGE` kontrol edilir/istenir. Android 10+ dosyalar için genel bir depolama izni istenmez: Storage Access Framework seçili dosyaya erişim verir. `MANAGE_EXTERNAL_STORAGE`, video, ses veya konum izni istenmez.

**Yetki ve fiili kullanım farklıdır:** Tam fotoğraf izni, eski Android'deki depolama izni daha geniş okuma/yazma yetkileri verir; bunlar sadece tek dosya izni olarak tanıtılmaz. Uygulama yine yalnızca seçilen fotoğraf/dosyayı işler; galeriyi taramaz veya bütün dosyaları listelemez. Bu izin kapısı ürün sahibinin isteğiyle eklenmiştir; sistem seçicileri teknik olarak geniş runtime izinleri gerektirmez. Google Play yayını öncesi geniş fotoğraf izninin politika uygunluğu ayrıca değerlendirilmeli; izin eklemek Play Protect güveni/uyarılarının kalkması garantisi değildir.

Fotoğrafın küçük kopyası özel SQLite kaydında tutulur. URI erişim reddi anlaşılır mesajla bildirilir. Borçlar, kişi/IBAN ve mevcut fotoğraflar izin reddiyle silinmez. İzin pencerelerinin gerçek cihaz davranışı bu ortamda denenmemiştir; sürüm matrisi Kotlin birim testlerinde, ret/ayar yönlendirmesi Flutter testinde denetlenir.

Doğrulama komutları: Flutter klasöründe `dart analyze --fatal-infos`, `flutter test`; Android klasöründe `./gradlew :app:testDebugUnitTest :app:lintRelease`. Kotlin izin politikası testleri Android 7–16 için tam/kısmi izin, ret ve dosya izni ayrımını kapsar.

Kaynak: [Android izin minimizasyonu](https://developer.android.com/privacy-and-security/minimize-permission-requests), [dosya seçicisi](https://developer.android.com/training/data-storage/shared/documents-files).

## Play Protect

“Google'a güvenlik kontrolü için gönder” mesajı, bilinmeyen APK için tarama isteğidir; tek başına zararlı yazılım tespiti değildir. Güvendiğiniz resmi kaynaktan indirdiğiniz APK için cihazdaki taramayı çalıştırabilirsiniz. Bu mesajı uygulama içinden kaldırmak veya bir manifest izniyle önlemek mümkün değildir. Yeni APK'larda tekrar tarama istenebilir. Koruma kapatılmamalıdır.

“Zararlı uygulama” veya “Kurulum engellendi” farklı uyarılardır; bu durumda tam uyarıyı incelemeden kuruluma devam etmeyin. Hatalı zararlı sınıflandırması için geliştirici itirazı yapılabilir; sırf “güvenlik kontrolüne gönder” mesajı için itiraz çözüm değildir.

Mevcut Flutter APK release/AOT çalışır fakat yerel debug anahtarıyla imzalanır. Üretim dağıtımı için kalıcı, özel yayın anahtarı ve uygun mağaza dağıtımı ayrıca hazırlanmalıdır. Bunlar da Play Protect uyarısının hiç görünmeyeceğini garanti etmez. Mevcut kullanıcıların güncelleyebilmesi için bu değişiklikte imza veya paket kimliği değiştirilmedi. İmza geçişi ayrıca planlanmalı; kayıtları korumak için uygulama silinmemeli ve önce JSON yedeği alınmalıdır. Anahtar/parolalar public repoya yüklenmemelidir.

Kaynak: [Google Play Protect geliştirici rehberi](https://developers.google.com/android/play-protect/warning-dev-guidance).
