import 'package:flutter/material.dart';

enum FileAccess { photo, importBackup, exportBackup }

/// App-level consent, not a replacement or imitation of Android permissions.
/// The OS picker grants access only to the file the user subsequently selects.
Future<bool> confirmFileAccess(BuildContext context, FileAccess access) async {
  final (title, description, button) = switch (access) {
    FileAccess.photo => (
      'Fotoğraf erişimi',
      'Yalnızca seçeceğiniz fotoğraf okunur. Küçük bir kopyası bu kişinin profiline ve JSON yedeğine kaydedilir. Galerinin tamamına erişilmez; orijinal fotoğraf değiştirilmez.',
      'Fotoğraf seç',
    ),
    FileAccess.importBackup => (
      'Dosya okuma erişimi',
      'Android dosya seçicisinde yalnızca seçeceğiniz CSV veya JSON dosyası okunur. Diğer dosyalarınıza erişilmez. Kayıtlar aktarılmadan önce ayrıca onayınız istenir.',
      'Dosya seç',
    ),
    FileAccess.exportBackup => (
      'Dosya kaydetme erişimi',
      'Yedek yalnızca Android seçicisinde belirleyeceğiniz konuma yazılır. Tam IBAN ve borç bilgileri, JSON yedeğinde ayrıca kişi fotoğrafları bulunur. Güvenli bir konum seçin.',
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
                  'Bu, uygulama içi bilgilendirme ve onaydır. Dosyaya erişimi sonraki Android seçim ekranında verirsiniz.',
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
