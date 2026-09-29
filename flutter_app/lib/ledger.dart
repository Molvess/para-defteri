import 'dart:convert';

String personKey(String name) => name
    .trim()
    .replaceAll(RegExp(r'\s+'), ' ')
    .replaceAll('I', 'ı')
    .replaceAll('İ', 'i')
    .toLowerCase();
String capitalizeName(String name) => name.replaceFirstMapped(
  RegExp(r'\S'),
  (m) => m[0] == 'i' ? 'İ' : m[0]!.toUpperCase(),
);
String normalizeIban(String text) =>
    text.replaceAll(RegExp(r'\s'), '').toUpperCase();
String formattedIban(String text) =>
    normalizeIban(text)
        .replaceAllMapped(RegExp(r'.{1,4}'), (m) => '${m[0]} ')
        .trim();
bool validIban(String text) {
  final value = normalizeIban(text);
  if (!RegExp(r'^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$').hasMatch(value)) {
    return false;
  }
  if (value.startsWith('TR') && !RegExp(r'^TR\d{24}$').hasMatch(value)) {
    return false;
  }
  var remainder = 0;
  for (final c in (value.substring(4) + value.substring(0, 4)).codeUnits) {
    final digits = c >= 65 ? (c - 55).toString() : String.fromCharCode(c);
    for (final digit in digits.codeUnits) {
      remainder = (remainder * 10 + digit - 48) % 97;
    }
  }
  return remainder == 1;
}

String maskedIban(String iban) =>
    '${iban.substring(0, 4)} •••• •••• ${iban.substring(iban.length - 4)}';
String dateText(DateTime d) =>
    '${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')}/${d.year}';
DateTime parseDate(String text) {
  final m = RegExp(r'^(\d{2})/(\d{2})/(\d{4})$').firstMatch(text);
  if (m == null) throw const FormatException('Tarih gg/aa/yyyy olmalı.');
  final day = int.parse(m[1]!),
      month = int.parse(m[2]!),
      year = int.parse(m[3]!);
  final date = DateTime(year, month, day);
  if (date.day != day || date.month != month || date.year != year) {
    throw const FormatException('Geçersiz tarih.');
  }
  return date;
}

int parseCents(String input) {
  var value = input.trim();
  if (value.contains(',')) {
    value = value.replaceAll('.', '').replaceAll(',', '.');
  } else if (RegExp(r'^\d{1,3}(\.\d{3})+$').hasMatch(value)) {
    value = value.replaceAll('.', '');
  }
  if (!RegExp(r'^\d+(\.\d{1,2})?$').hasMatch(value)) {
    throw const FormatException('Geçerli bir tutar girin (örn. 1250,50).');
  }
  final parts = value.split('.');
  final cents =
      int.parse(parts[0]) * 100 +
      (parts.length == 2 ? int.parse(parts[1].padRight(2, '0')) : 0);
  if (cents <= 0 || cents > 900000000000000) {
    throw const FormatException(
      'Tutar sıfırdan büyük ve desteklenen aralıkta olmalı.',
    );
  }
  return cents;
}

String money(int cents) {
  final whole = (cents.abs() ~/ 100).toString().replaceAllMapped(
    RegExp(r'(\d)(?=(\d{3})+$)'),
    (m) => '${m[1]}.',
  );
  return '${cents < 0 ? '-' : ''}$whole,${(cents.abs() % 100).toString().padLeft(2, '0')} ₺';
}

String newId() => DateTime.now().microsecondsSinceEpoch.toString();

class Debt {
  final String id, name, description, date;
  final int cents, createdAt;
  final bool income, paid;
  final Reminder? reminder;
  const Debt({
    required this.id,
    required this.name,
    required this.cents,
    required this.description,
    required this.date,
    required this.income,
    required this.paid,
    required this.createdAt,
    this.reminder,
  });
  String get key => personKey(name);
  Debt toggle() => Debt(
    id: id,
    name: name,
    cents: cents,
    description: description,
    date: date,
    income: income,
    paid: !paid,
    createdAt: createdAt,
    reminder: reminder,
  );
  Debt withReminder(Reminder? value) => Debt(
    id: id,
    name: name,
    cents: cents,
    description: description,
    date: date,
    income: income,
    paid: paid,
    createdAt: createdAt,
    reminder: value,
  );
  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'cents': cents,
    'description': description,
    'date': date,
    'income': income,
    'paid': paid,
    'createdAt': createdAt,
    if (reminder != null) 'reminder': reminder!.toJson(),
  };
  factory Debt.fromJson(Map<String, dynamic> j) {
    final d = Debt(
      id: j['id'] as String,
      name: j['name'] as String,
      cents: j['cents'] as int,
      description: j['description'] as String,
      date: j['date'] as String,
      income: j['income'] as bool,
      paid: j['paid'] as bool? ?? false,
      createdAt: j['createdAt'] as int,
      reminder: j['reminder'] == null
          ? null
          : Reminder.fromJson(Map<String, dynamic>.from(j['reminder'])),
    );
    parseDate(d.date);
    if (d.id.isEmpty ||
        d.name.trim().isEmpty ||
        d.cents <= 0 ||
        d.cents > 900000000000000) {
      throw const FormatException('Geçersiz borç kaydı.');
    }
    return d;
  }
}

class Reminder {
  final bool enabled;
  final String startDate, repeat;
  final String? endDate;
  final int hour, minute, interval;
  const Reminder({
    required this.startDate,
    required this.hour,
    required this.minute,
    this.repeat = 'once',
    this.interval = 1,
    this.endDate,
    this.enabled = true,
  });
  String get time =>
      '${hour.toString().padLeft(2, '0')}:${minute.toString().padLeft(2, '0')}';
  String get summary =>
      '${enabled ? '' : 'Duraklatıldı · '}$startDate $time · ${repeat == 'once' ? 'Tek sefer' : 'Her $interval ${const {'daily': 'gün', 'weekly': 'hafta', 'monthly': 'ay'}[repeat]}'}${endDate == null ? '' : ' · Bitiş $endDate'}';
  Map<String, dynamic> toJson() => {
    'enabled': enabled,
    'startDate': startDate,
    'hour': hour,
    'minute': minute,
    'repeat': repeat,
    'interval': interval,
    'endDate': endDate,
  };
  factory Reminder.fromJson(Map<String, dynamic> j) {
    final r = Reminder(
      startDate: j['startDate'] as String,
      hour: j['hour'] as int,
      minute: j['minute'] as int,
      repeat: j['repeat'] as String? ?? 'once',
      interval: j['interval'] as int? ?? 1,
      endDate: j['endDate'] as String?,
      enabled: j['enabled'] as bool? ?? true,
    );
    final start = parseDate(r.startDate);
    if (start.year < 1900 ||
        start.year > 2200 ||
        r.hour < 0 ||
        r.hour > 23 ||
        r.minute < 0 ||
        r.minute > 59 ||
        !['once', 'daily', 'weekly', 'monthly'].contains(r.repeat) ||
        r.interval < 1 ||
        r.interval > 365 ||
        (r.endDate != null && parseDate(r.endDate!).isBefore(start))) {
      throw const FormatException(
        'Hatırlatıcı tarih/saat veya tekrar aralığı geçersiz.',
      );
    }
    return r;
  }
}

class BankAccount {
  final String iban, label;
  const BankAccount(this.iban, this.label);
  Map<String, dynamic> toJson() => {'iban': iban, 'label': label};
  factory BankAccount.fromJson(Map<String, dynamic> j) {
    final iban = normalizeIban(j['iban'] as String);
    if (!validIban(iban)) {
      throw const FormatException('Yedekte geçersiz IBAN var.');
    }
    return BankAccount(iban, j['label'] as String);
  }
}

class Person {
  final String name, avatar, photo;
  final List<BankAccount> accounts;
  const Person(this.name, this.accounts, {this.avatar = '', this.photo = ''});
  Person withPhoto(String value) =>
      Person(name, accounts, avatar: avatar, photo: value);
  String get key => personKey(name);
  Map<String, dynamic> toJson() => {
    'name': name,
    'accounts': accounts.map((a) => a.toJson()).toList(),
    'avatar': avatar,
    'photo': photo,
  };
  factory Person.fromJson(Map<String, dynamic> j) {
    final photo = j['photo'] as String? ?? '';
    if (photo.length > 350000) {
      throw const FormatException('Profil fotoğrafı çok büyük.');
    }
    if (photo.isNotEmpty) base64Decode(photo);
    return Person(
      j['name'] as String,
      (j['accounts'] as List)
          .map((a) => BankAccount.fromJson(Map<String, dynamic>.from(a)))
          .toList(),
      avatar: j['avatar'] as String? ?? '',
      photo: photo,
    );
  }
}

class ImportBatch {
  final List<Debt> debts;
  final List<Person> people;
  final List<String> errors;
  const ImportBatch(this.debts, this.people, [this.errors = const []]);
}

ImportBatch parseKeep(String text) {
  final debts = <Debt>[], errors = <String>[];
  final pattern = RegExp(
    r'^\s*(.+?)\s+-\s+([\d.,]+)\s*(?:TL|₺)?\s+-\s+(.*?)\s+(?:-\s*)?(\d{2}/\d{2}/\d{4})\s*([+-])(?:\s*(✅|❌|Ödendi|Ödenecek))?\s*$',
    caseSensitive: false,
  );
  final now = DateTime.now().millisecondsSinceEpoch;
  final lines = text.split('\n');
  for (var i = 0; i < lines.length; i++) {
    final line = lines[i].trim();
    if (line.isEmpty ||
        personKey(line).replaceAll(' ', '') == 'alacaklar-verecekler') {
      continue;
    }
    try {
      final m = pattern.firstMatch(line);
      if (m == null) {
        throw const FormatException(
          'Ad - Tutar TL - Açıklama Tarih +/- bekleniyor.',
        );
      }
      parseDate(m[4]!);
      debts.add(
        Debt(
          id: '${newId()}-$i',
          name: capitalizeName(m[1]!.trim()),
          cents: parseCents(m[2]!),
          description: m[3]!.trim(),
          date: m[4]!,
          income: m[5] == '+',
          paid: m[6] == '✅' || personKey(m[6] ?? '') == 'ödendi',
          createdAt: now + i,
        ),
      );
    } catch (e) {
      errors.add('Satır ${i + 1}: $e');
    }
  }
  return ImportBatch(debts, const [], errors);
}

String backupJson(List<Debt> debts, List<Person> people) =>
    const JsonEncoder.withIndent('  ').convert({
      'version': 1,
      'debts': debts.map((d) => d.toJson()).toList(),
      'people': people.map((p) => p.toJson()).toList(),
    });
ImportBatch parseBackup(String text) {
  final j = jsonDecode(text.replaceFirst('\ufeff', '')) as Map<String, dynamic>;
  if (j['version'] != 1) {
    throw const FormatException('Desteklenmeyen yedek sürümü.');
  }
  return ImportBatch(
    (j['debts'] as List)
        .map((d) => Debt.fromJson(Map<String, dynamic>.from(d)))
        .toList(),
    (j['people'] as List)
        .map((p) => Person.fromJson(Map<String, dynamic>.from(p)))
        .toList(),
  );
}

String exportCsv(List<Debt> debts, Map<String, Person> people) {
  String cell(String s) => '"${s.replaceAll('"', '""')}"';
  return '\ufeff${[
    ['Kişi', 'Tutar', 'Tarih', 'Yön', 'Ödeme Durumu', 'Açıklama', 'IBAN', 'Oluşturulma Zamanı'],
    ...debts.map((d) => [d.name, '${d.cents ~/ 100}.${(d.cents % 100).toString().padLeft(2, '0')}', d.date, d.income ? 'Alacak' : 'Verecek', d.paid ? 'Ödendi' : 'Ödenecek', d.description, (people[d.key]?.accounts ?? []).map((a) => a.label.isEmpty ? a.iban : '${a.label}: ${a.iban}').join(' | '), '${d.createdAt}']),
  ].map((r) => r.map(cell).join(',')).join('\r\n')}';
}

// RFC4180 quoting: embedded commas, newlines and doubled quotes are preserved.
List<List<String>> csvRows(String text) {
  final rows = <List<String>>[];
  var row = <String>[], field = StringBuffer(), quoted = false;
  final input = text.replaceFirst('\ufeff', '');
  for (var i = 0; i < input.length; i++) {
    final c = input[i];
    if (c == '"') {
      if (quoted && i + 1 < input.length && input[i + 1] == '"') {
        field.write('"');
        i++;
      } else {
        quoted = !quoted;
      }
    } else if (c == ',' && !quoted) {
      row.add(field.toString());
      field = StringBuffer();
    } else if ((c == '\n' || c == '\r') && !quoted) {
      if (c == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++;
      row.add(field.toString());
      rows.add(row);
      row = [];
      field = StringBuffer();
    } else {
      field.write(c);
    }
  }
  if (quoted) throw const FormatException('CSV tırnakları kapanmamış.');
  if (row.isNotEmpty || field.isNotEmpty) {
    row.add(field.toString());
    rows.add(row);
  }
  return rows;
}

ImportBatch parseLegacyCsv(String text) {
  final rows = csvRows(text);
  if (rows.isEmpty || !['Kişi', 'Kisi'].contains(rows.first.first)) {
    throw const FormatException('Para Defteri CSV dosyası seçin.');
  }
  final debts = <Debt>[], people = <String, Person>{}, errors = <String>[];
  for (var i = 1; i < rows.length; i++) {
    final r = rows[i];
    try {
      if (r.length != 8) throw const FormatException('8 alan bekleniyor.');
      parseDate(r[2]);
      if (!['Alacak', 'Verecek'].contains(r[3]) ||
          !['Ödendi', 'Ödenecek', 'Odendi', 'Odenecek'].contains(r[4])) {
        throw const FormatException('Geçersiz yön/durum.');
      }
      final amount = num.tryParse(r[1]);
      if (amount == null ||
          !amount.isFinite ||
          amount <= 0 ||
          amount > 9000000000000 ||
          r[0].trim().isEmpty) {
        throw const FormatException('Geçersiz tutar/kişi.');
      }
      final accounts = <BankAccount>[];
      for (final value in r[6].split(' | ').where((v) => v.trim().isNotEmpty)) {
        final colon = value.lastIndexOf(':');
        final iban = normalizeIban(
          colon < 0 ? value : value.substring(colon + 1),
        );
        if (!validIban(iban)) throw const FormatException('Geçersiz IBAN.');
        accounts.add(
          BankAccount(iban, colon < 0 ? '' : value.substring(0, colon).trim()),
        );
      }
      final key = personKey(r[0]);
      final merged = {
        for (final a in people[key]?.accounts ?? <BankAccount>[]) a.iban: a,
        for (final a in accounts) a.iban: a,
      };
      people[key] = Person(r[0], merged.values.toList());
      debts.add(
        Debt(
          id: '${newId()}-$i',
          name: r[0],
          cents: (amount * 100).round(),
          description: r[5],
          date: r[2],
          income: r[3] == 'Alacak',
          paid: ['Ödendi', 'Odendi'].contains(r[4]),
          createdAt: int.tryParse(r[7]) ?? i,
        ),
      );
    } catch (e) {
      errors.add('Satır ${i + 1}: $e');
    }
  }
  return ImportBatch(debts, people.values.toList(), errors);
}
