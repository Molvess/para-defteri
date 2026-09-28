import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

enum FileAccess { photo, importBackup, exportBackup }

/// App-level consent, not a replacement or imitation of Android permissions.
/// Native code subsequently checks real runtime permissions where applicable.
Future<bool> confirmFileAccess(BuildContext context, FileAccess access) async {
  final (title, description, button) = switch (access) {
    FileAccess.photo => (
      'Fotoğraf erişimi',
      'Devam ettiğinizde fotoğraf izni verilmemişse Android izin penceresi açılır. Android 14 ve üzerinde yalnızca seçtiğiniz fotoğraflara izin verebilirsiniz. Tam erişim seçeneği daha geniş fotoğraf okuma yetkisi verir. Uygulama profil için yalnızca sonrasında seçtiğiniz fotoğrafı işler ve küçük kopyasını JSON yedeğine dahil eder.',
      'Fotoğraf seç',
    ),
    FileAccess.importBackup => (
      'Dosya okuma erişimi',
      'Android 9 ve öncesinde depolama okuma izni istenir. Yeni Android sürümlerinde dosya erişimini sistem seçicisinde verirsiniz; ayrı bir genel depolama izni yoktur. Uygulama yalnızca seçeceğiniz CSV/JSON dosyasını okur, aktarmadan önce ayrıca onayınızı ister.',
      'Dosya seç',
    ),
    FileAccess.exportBackup => (
      'Dosya kaydetme erişimi',
      'Android 9 ve öncesinde depolama yazma izni istenir. Yeni sürümlerde yazma erişimini sistem dosya seçicisinde verirsiniz. Yedek yalnızca seçtiğiniz hedefe yazılır; tam IBAN, borç bilgileri ve JSON yedeğinde kişi fotoğrafları bulunur. Güvenli bir konum seçin.',
      'Kaydetme yeri seç',
    ),
  };
  return await showDialog<bool>(
        context: context,
        builder: (c) => AlertDialog(
          title: Text(title),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(description),
                const SizedBox(height: 12),
                const Text(
                  'Bu ekran bilgilendirmedir. Sistem izinlerini Android yönetir; önceden verdiğiniz izinler her seferinde tekrar sorulmaz. İzinleri telefonun uygulama ayarlarından kaldırabilirsiniz.',
                  style: TextStyle(fontSize: 12),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(c, false),
              child: const Text('Vazgeç'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(c, true),
              child: Text(button),
            ),
          ],
        ),
      ) ??
      false;
}

Future<void> showPermissionHelp(
  BuildContext context,
  PlatformException error,
) async {
  final settings = await showDialog<bool>(
    context: context,
    builder: (c) => AlertDialog(
      title: const Text('Erişim izni verilmedi'),
      content: Text(
        error.code == 'PERMISSION_BLOCKED'
            ? 'Android izin penceresini tekrar göstermeyebilir. İsterseniz uygulama ayarlarındaki İzinler bölümünden erişim verebilirsiniz. Mevcut borç ve kişi kayıtlarınız değişmedi.'
            : 'Fotoğraf veya dosya seçicisi açılmadı. İşlemi yeniden başlatarak izin verebilir veya uygulama ayarlarından izinleri yönetebilirsiniz.',
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(c, false),
          child: const Text('Şimdi değil'),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(c, true),
          child: const Text('Uygulama ayarları'),
        ),
      ],
    ),
  );
  if (settings == true && context.mounted) {
    try {
      await const MethodChannel('com.molvess.ledger/files')
          .invokeMethod<void>('openSettings');
    } on PlatformException {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Ayarlar açılamadı. Telefon ayarlarından Para Defteri Flutter → İzinler bölümünü açın.',
            ),
          ),
        );
      }
    }
  }
}

bool isPermissionError(Object error) =>
    error is PlatformException &&
    (error.code == 'PERMISSION_DENIED' || error.code == 'PERMISSION_BLOCKED');
