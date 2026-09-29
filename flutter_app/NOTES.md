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

### 1.1.1 düzenlemeleri

Bekleyenler kartında fotoğraf, tüm kartın sağında dikey ortalıdır. Kişi penceresi içerik kadar yer kaplar; IBAN eklendikçe büyür ve ekran sınırında kayar. Ödeme butonu yeşil, geri alma kehribar tonundadır. Borç kartında yalnızca ödeme, IBAN kopyalama ve silme eylemleri bulunur; IBAN yoksa kopyalama pasiftir. Kişi yönetimine kart fotoğrafından, bekleyenler kartından veya üstteki kişiler ikonundan ulaşılır.

Fotoğraf seçimi Google Fotoğraflar'ın görsel seçme ekranını öncelikli açar. Yüklü/uygun değilse Android 13+ sistem fotoğraf seçicisi, eski cihazlarda galeri seçicisi kullanılır; fotoğraf için dosya seçici kullanılmaz. Cihaz/uygulama sürümüne göre bu ekranın görünümü değişebilir.

Üç nokta menüsü ikonlu ve açıklamalı bir alt paneldir. Borç kartı işlemleri yuvarlatılmış dikdörtgen, kenarlıklı butonlardır; mevcut renkler korunur.

Kişi / IBAN → **Fotoğraf ekle** ile Android dosya/galeri seçicisi açılır. Fotoğraf değiştirilebilir veya onayla kaldırılabilir. Seçim iptal edilirse mevcut fotoğraf korunur. Seçilen görselin yönü düzeltilir, ortadan kare kırpılır ve en fazla 256×256 JPEG olarak uygulamaya kopyalanır; orijinal galeri dosyası değişmez. Geniş depolama izni gerekmez. Fotoğraf kişi kartının sağında, borç kartında ve borç formunda adı eşleşen kişi için gösterilir.

Fotoğraf kişi JSON alanında SQLite'a kaydedilir ve **JSON yedeğine dahildir**; CSV fotoğraf içermez. Eski fotoğrafsız kayıtlar ve eski emoji bilgisi korunur; veritabanı sıfırlanmaz. Yedek birleştirme mevcut fotoğrafı değiştirmez, yalnızca eksik fotoğrafı doldurur. Galeriden fotoğrafın silinmesi uygulamadaki kopyayı etkilemez. Güncellemeyi aynı Flutter uygulamasının üzerine kurun, kaldırıp yeniden kurmayın.

Doğrulama: `dart analyze --fatal-infos`, Android release lint ve 11 Flutter testi. Fotoğrafın SQLite yeniden açılışında korunması, JSON yedeği, eski kayıt uyumluluğu, menü, seçici iptali/hatası, formdaki kişi eşleşmesi, kart merkez hizası ve 0/1/çoklu IBAN pencere boyutları test edilir. Widget testinde Android fotoğraf seçicisinin yanıtı taklit edilir; gerçek telefonun Google Fotoğraflar/galeri uygulaması bu ortamda denenmemiştir.

## Kapsam

### 1.1.4 — tema, kişiler ve hatırlatıcılar

- Fotoğraf seçiminde Google Fotoğraflar'a zorunlu yönlendirme kaldırıldı. Android'in varsayılan fotoğraf seçme uygulaması kullanılır; varsayılan yoksa Android uygun uygulamaları sunar. Fotoğraf görüntüleyicisinin seçim intent'ini desteklemesi gerekir. Uygun galeri yoksa sistem seçicisine dönülür. Gerçek fotoğraf izinleri korunur.
- IBAN gizli ve açıkken aynı kenarlıklı kutudadır; açık değer dört karakterlik gruplarla gösterilir. Kopyalama tam normalize IBAN'ı verir.
- Kim? alanının kişi arama butonu mevcut kişileri arayıp seçtirir; yeni ad yazmak hâlâ mümkündür.
- Açık/koyu tema tercihi SQLite ayarlar tablosunda saklanır. Şema v1→v2 geçişi yalnızca ayarlar tablosu ekler; borç, fotoğraf ve IBAN kayıtları korunur. Başlık kalın, alt satır sürüm numarasıdır.
- Uygulama simgesi sade beyaz **₺** işareti ve turkuaz zemin olarak vektör tabanlıdır; Android 8+ adaptif, Android 7 için katmanlı ikon kullanılır. Mevcut çalışma alanındaki bozuk WebP ikon taslakları değiştirilmedi; yeni manifest onlara bağlı değildir.
- Her borç kartındaki **Hatırlatıcı** eylemi tarih, saat, tek sefer/günlük/haftalık/aylık tekrar, 1–365 tekrar aralığı ve isteğe bağlı bitiş günü seçtirir. Örneğin başlangıcı ayın 15'i seçip aylık/1 yapabilirsiniz. Ayda ilgili gün yoksa son gün kullanılır; sonraki ay asıl güne geri dönülür.
- Üç nokta menüsündeki **Hatırlatıcılar** merkezi bütün aktif ve duraklatılmış planları kişi, tutar, yön ve zaman bilgisiyle tek listede gösterir. Buradan mevcut plan açılıp düzenlenebilir veya kaldırılabilir. Tema seçeneği üç nokta menüsünden kaldırılmıştır; açık/koyu geçişi başlıktaki güneş/ay düğmesindedir.
- Android 13+ bildirim izni kullanıcı kaydederken istenir. Kanal/bildirim kapalıysa uyarı gösterilir. Android 12+ kesin zamanlama özel izni ayrı butondan açılır; verilmezse alarm yaklaşık çalışır ve gecikebileceği açıkça belirtilir. Ayarlardan dönünce **Durumu yenile** ile kontrol edilebilir.
- Android AlarmManager ve manifest alıcısı uygulama ekranı kapalıyken çalışır. Yeniden başlatma, paket güncelleme, saat/saat dilimi değişikliği sonrası alarmlar yeniden kurulur. Saatler telefonun yerel saatidir. Kaçırılmış eski bildirimler topluca tekrar oynatılmaz. Telefonun zorla durdurma/pil politikaları teslimatı engelleyebilir; kesin teslimat garantisi yoktur.
- Hatırlatıcı borç JSON'una eklenir, eski kayıtlarda yok kabul edilir. JSON yedeği tekrar planını taşır; CSV fotoğraf ve hatırlatıcı planını içermez. İçe aktarma/yeniden açılışta planlar eşitlenir. Borç silmek alarmı iptal eder; geri alma tekrar kurar. **Ödendi** durumu tekrarları durdurmaz: hatırlatıcıyı ayrıca duraklatın/kaldırın.
- Alarm tanımları Android'in uygulamaya özel kayıt alanına da yansıtılır; eşitleme hatası varsa borç kaydı korunur ve ana ekranda tekrar deneme uyarısı çıkar. Kilit ekranında bildirim içeriği özel olarak işaretlenir; görünürlük kullanıcının Android ayarlarına bağlıdır.

Doğrulama: 20 Flutter testi; 5 izin + 6 tarih/tekrar Kotlin testi; statik analiz, release derlemesi ve Android lint. v1 verisinin korunması, tema kalıcılığı, hatırlatıcı merkezi, IBAN kutusu, kişi seçimi, bildirim reddi, alarm eşitleme, ay sonu/artık yıl ve yaz saati geçişleri test edilir. Gerçek cihazda bildirim/yeniden başlatma/galeri akışı bu ortamda denenmemiştir.

### 1.1.3 — gerçek Android izin kapısı

Fotoğraf seçicisinden önce Android sürümüne uygun runtime izinleri istenir; ret durumunda seçici açılmaz. Android 14+ kısmi fotoğraf erişimi kabul edilir. Android 7–9 dosya okuma/yazma izinleri de kontrol edilir; yeni Android sürümlerinde dosyalar SAF üzerinden seçilir. Önceden verilmiş izin yeniden sorulmaz; izinler Ayarlar'dan yönetilebilir. Önceki sürüm açıklamalarından farklı olarak tam fotoğraf izni artık manifestte vardır; ayrıntılar ve erişimin gerçek kapsamı [güvenlik notlarında](SECURITY.md) açıklanmıştır.

### 1.1.2 — seçili dosya erişimi

Fotoğraf seçme, CSV/JSON okuma ve yedek yazma öncesinde uygulama içi bilgilendirme/onay eklenmiştir. Bu, genel Android depolama izni değildir: gerçek erişim sistem seçicisinde yalnızca seçilen dosya için verilir. URI erişim istekleri açıkça belirtilir, erişim reddi ayrı mesajla açıklanır. Onay iptalinde seçici açılmaz. Play Protect tarama mesajları ve mevcut debug imzasının sınırları [güvenlik notlarında](SECURITY.md) açıklanır. Mevcut kayıtlar, paket kimliği ve imza korunur.

- Borç ekleme, aynı kimlikle düzenleme, iptal, silme/geri alma, ödeme durumu.
- Yön + durum filtreleri; filtreye uygun genel toplamlar ve kişi altında yalnızca bekleyen alacak/verecek.
- Türkçe ad baş harfi ve imleç koruması, takvim, anlaşılır doğrulama.
- Kişiler, çoklu IBAN, banka etiketi, MOD-97 doğrulama, maskeleme, kopyalama ve elle seçme yedeği.
- Son eklenenden başlayan sıralama, loş tema ve doğal kaydırma.
- Keep metni, mevcut Android CSV'si ve JSON içe aktarma; CSV/JSON dışa aktarma.
- SQLite kalıcılığı; kuruş cinsinden tam sayı tutarlar; işlemsel toplu aktarım.
- Muhasebe ekranı yoktur; mevcut Kotlin uygulamasındaki muhasebe kodu ve verisi korunur.
