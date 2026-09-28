# Fotoğraf/dosya erişimi ve APK kurulumu

## Erişim modeli

Fotoğraf ekleme, yedek içe aktarma ve dışa aktarma öncesinde uygulama içi açıklama/onay gösterilir. Vazgeçildiğinde Android seçicisi açılmaz. Bu onay bir Android runtime izni değildir; işletim sistemi seçicisi yalnızca kullanıcının seçtiği URI için erişim verir. İçe aktarmada kayıt yazmadan önce ayrıca önizleme/onay bulunur.

Fotoğraf ve içe aktarma işlemi seçili URI'yi okur; dışa aktarma yalnızca seçilen hedefe yazar. URI izinleri açıkça talep edilir. Erişim reddi anlaşılır mesajla bildirilir. Fotoğrafın küçük kopyası uygulamanın özel SQLite kaydında saklandığından galeriye kalıcı erişim gerekmez. Geniş galeri/tüm dosyalar izinleri (`READ_MEDIA_IMAGES`, `MANAGE_EXTERNAL_STORAGE`) istenmez. Bu nedenle telefonun uygulama izinleri sayfasında genel bir Dosyalar/Galeri izni görünmeyebilir; bu, seçicinin çalışmadığı anlamına gelmez.

Kaynak: [Android izin minimizasyonu](https://developer.android.com/privacy-and-security/minimize-permission-requests), [dosya seçicisi](https://developer.android.com/training/data-storage/shared/documents-files).

## Play Protect

“Google'a güvenlik kontrolü için gönder” mesajı, bilinmeyen APK için tarama isteğidir; tek başına zararlı yazılım tespiti değildir. Güvendiğiniz resmi kaynaktan indirdiğiniz APK için cihazdaki taramayı çalıştırabilirsiniz. Bu mesajı uygulama içinden kaldırmak veya bir manifest izniyle önlemek mümkün değildir. Yeni APK'larda tekrar tarama istenebilir. Koruma kapatılmamalıdır.

“Zararlı uygulama” veya “Kurulum engellendi” farklı uyarılardır; bu durumda tam uyarıyı incelemeden kuruluma devam etmeyin. Hatalı zararlı sınıflandırması için geliştirici itirazı yapılabilir; sırf “güvenlik kontrolüne gönder” mesajı için itiraz çözüm değildir.

Mevcut Flutter APK release/AOT çalışır fakat yerel debug anahtarıyla imzalanır. Üretim dağıtımı için kalıcı, özel yayın anahtarı ve uygun mağaza dağıtımı ayrıca hazırlanmalıdır. Bunlar da Play Protect uyarısının hiç görünmeyeceğini garanti etmez. Mevcut kullanıcıların güncelleyebilmesi için bu değişiklikte imza veya paket kimliği değiştirilmedi. İmza geçişi ayrıca planlanmalı; kayıtları korumak için uygulama silinmemeli ve önce JSON yedeği alınmalıdır. Anahtar/parolalar public repoya yüklenmemelidir.

Kaynak: [Google Play Protect geliştirici rehberi](https://developers.google.com/android/play-protect/warning-dev-guidance).
