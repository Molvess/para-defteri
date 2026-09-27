# Para Defteri — bağımsız Flutter sürümü

Bu klasör mevcut Kotlin/Compose uygulamasından bağımsızdır. Android uygulama kimliği `com.molvess.para_defteri_flutter`, görünen adı **Para Defteri Flutter**. İki uygulama aynı telefona birlikte kurulabilir.

## Eski kayıtları taşıma

1. Eski Para Defteri uygulamasındaki CSV butonuyla borçları dışa aktarın.
2. Flutter uygulamasında sağ üst menü → **CSV / JSON içe aktar** ile dosyayı seçin.
3. Okunan kayıt sayısını kontrol edip aktarımı onaylayın.

Eski CSV'deki kişiler, borçlar, tutarlar, tarihler, yönler, ödeme durumları, oluşturulma zamanları ve çoklu IBAN'lar taşınır. CSV fotoğraf içermediğinden profil fotoğrafları taşınmaz; Flutter sürümünde kişi yönetiminden galerideki fotoğraf seçilebilir. Eski uygulamanın veritabanı değiştirilmez. Aynı adlı kişiler Türkçe büyük/küçük harf ve boşluk normalizasyonuyla eşleştirilir.

Flutter sürümünün **JSON yedek kaydet** menüsü borçları ve borcu bulunmayan kişiler dahil tüm kişi/IBAN kayıtlarını içerir. İçe aktarma mevcut veriyi silmez; aynı kimlikli kayıtları atlar ve eksik IBAN'ları birleştirir. Geçersiz satırlar varsa hiçbir kayıt yazılmaz. Eski CSV'yi tekrar içe aktarırken aynı içerik ve oluşturulma zamanına sahip kayıtlar atlanır. Keep yapıştırma ise yeni kayıt ekleme işlemidir.

## Derleme ve doğrulama

Flutter 3.47.5 / Dart 3.13.4, JDK 21 ve Android SDK kullanıldı. Bağımlılık sürümleri `pubspec.lock` içindedir.

```sh
flutter pub get
dart analyze --fatal-infos
flutter test
flutter build apk --release
```

APK: `build/app/outputs/flutter-apk/app-release.apk`. Yerel kurulum için Android debug anahtarıyla imzalanır; Dart kodu **release/AOT** olarak derlenir, debug çalışma modu değildir. Mağaza yayını için ayrı kalıcı yayın anahtarı gerekir. APK'yı güncellerken aynı imza anahtarını kullanın; kayıtları korumak için eski uygulamayı kaldırmayın.

Bu ortamda `flutter analyze` komutu Türkçe klasör adını LSP'ye aktarırken SDK iletişim hatası verdi; aynı analiz `dart analyze --fatal-infos` ile tamamlandı.

## Kaydırma ve yüksek yenileme hızı

Kişi ve borç kartları `SliverList.builder` ile yalnızca görünür bölgede oluşturulur. Tek doğal kaydırma alanı, sınırlı genişlik, gölgesiz kartlar kullanılır. Kaydırma konumunu izleyen `setState`, her karede toplam hesabı veya özel kaydırma fiziği yoktur. Toplamlar veri/filtre değişimlerinde hesaplanır; SQLite işlemleri asenkrondur. Dosya okuma/yazma Android tarafında arka plan iş parçacığındadır.

Android penceresi desteklenen aynı çözünürlükteki en yüksek yenileme hızını tercih eder; sistem/pil politikası bunu sınırlayabilir. Yenileme hızı menü seçeneği kaldırıldı; yüksek hız tercihi korunuyor. Bağlı fiziksel cihaz olmadığı için 120 Hz cihazda kare zamanları doğrulanmadı.

## 1.1.0 — fotoğraflar ve arayüz

Üç nokta menüsü ikonlu ve açıklamalı bir alt paneldir. Borç kartı işlemleri yuvarlatılmış dikdörtgen, kenarlıklı butonlardır; mevcut renkler korunur.

Kişi / IBAN → **Fotoğraf ekle** ile Android dosya/galeri seçicisi açılır. Fotoğraf değiştirilebilir veya onayla kaldırılabilir. Seçim iptal edilirse mevcut fotoğraf korunur. Seçilen görselin yönü düzeltilir, ortadan kare kırpılır ve en fazla 256×256 JPEG olarak uygulamaya kopyalanır; orijinal galeri dosyası değişmez. Geniş depolama izni gerekmez. Fotoğraf kişi kartının sağında, borç kartında ve borç formunda adı eşleşen kişi için gösterilir.

Fotoğraf kişi JSON alanında SQLite'a kaydedilir ve **JSON yedeğine dahildir**; CSV fotoğraf içermez. Eski fotoğrafsız kayıtlar ve eski emoji bilgisi korunur; veritabanı sıfırlanmaz. Yedek birleştirme mevcut fotoğrafı değiştirmez, yalnızca eksik fotoğrafı doldurur. Galeriden fotoğrafın silinmesi uygulamadaki kopyayı etkilemez. Güncellemeyi aynı Flutter uygulamasının üzerine kurun, kaldırıp yeniden kurmayın.

Doğrulama: `dart analyze --fatal-infos` ve 9 Flutter testi. Fotoğrafın SQLite yeniden açılışında korunması, JSON yedeği, eski kayıt uyumluluğu, menü, seçici iptali/hatası ve formdaki kişi eşleşmesi test edilir. Widget testinde Android fotoğraf seçicisinin yanıtı taklit edilir; gerçek telefonun galeri uygulaması bu ortamda denenmemiştir.

## Kapsam

- Borç ekleme, aynı kimlikle düzenleme, iptal, silme/geri alma, ödeme durumu.
- Yön + durum filtreleri; filtreye uygun genel toplamlar ve kişi altında yalnızca bekleyen alacak/verecek.
- Türkçe ad baş harfi ve imleç koruması, takvim, anlaşılır doğrulama.
- Kişiler, çoklu IBAN, banka etiketi, MOD-97 doğrulama, maskeleme, kopyalama ve elle seçme yedeği.
- Son eklenenden başlayan sıralama, loş tema ve doğal kaydırma.
- Keep metni, mevcut Android CSV'si ve JSON içe aktarma; CSV/JSON dışa aktarma.
- SQLite kalıcılığı; kuruş cinsinden tam sayı tutarlar; işlemsel toplu aktarım.
- Muhasebe ekranı yoktur; mevcut Kotlin uygulamasındaki muhasebe kodu ve verisi korunur.
