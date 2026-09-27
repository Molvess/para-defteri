import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:para_defteri_flutter/ledger.dart';
import 'package:para_defteri_flutter/store.dart';
import 'package:para_defteri_flutter/dialogs.dart';

void main() {
  test('Keep examples parse exact values and directions; invalid import is reported', () {
    final batch = parseKeep(
      'Mustafa - 200 TL - yemeks 05/07/2026 +\nRifat - 1150 TL - Telefon 09/03/2026 -\nYasin - 40 TL - içecek 20/07/2026 +',
    );
    expect(batch.errors, isEmpty);
    expect(batch.debts.map((d) => d.cents), [20000, 115000, 4000]);
    expect(batch.debts.map((d) => d.income), [true, false, true]);
    expect(batch.debts.every((d) => !d.paid), isTrue);
    expect(
      parseKeep('Ayşe - 1.250,50 TL - Borç - 05/07/2026 +').debts.single.cents,
      125050,
    );
    expect(parseKeep('Ayşe - 200 TL - Yemek 31/02/2026 +').errors, isNotEmpty);
    expect(parseKeep('Ayşe - 200 TL - Yemek 05/07/2026').errors, isNotEmpty);
  });
  test('Turkish name capitalization retains caret; valid IBAN checksum and normalization', () {
    final formatted = TurkishNameFormatter().formatEditUpdate(
      TextEditingValue.empty,
      const TextEditingValue(
        text: 'ışık',
        selection: TextSelection.collapsed(offset: 2),
      ),
    );
    expect(formatted.text, 'Işık');
    expect(formatted.selection.baseOffset, 2);
    expect(capitalizeName('ipek'), 'İpek');
    expect(personKey(' IŞIK '), personKey('ışık'));
    expect(validIban('tr33 0006 1005 1978 6457 8413 26'), isTrue);
    expect(validIban('TR330006100519786457841327'), isFalse);
    expect(() => parseCents('NaN'), throwsFormatException);
  });
  test(
    'CSV and JSON round trip Turkish text quotes newlines money and IBAN',
    () {
      const d = Debt(
        id: 'one',
        name: 'Işık',
        cents: 125050,
        description: 'Öğle, "yemek"\nikinci satır',
        date: '05/07/2026',
        income: false,
        paid: true,
        createdAt: 12,
      );
      const p = Person('Işık', [
        BankAccount('TR330006100519786457841326', 'Maaş'),
      ]);
      final csv = parseLegacyCsv(exportCsv([d], {p.key: p}));
      expect(csv.errors, isEmpty);
      expect(csv.debts.single.description, d.description);
      expect(csv.debts.single.cents, d.cents);
      expect(csv.people.single.accounts.single.iban, p.accounts.single.iban);
      final json = parseBackup(backupJson([d], [p]));
      expect(json.debts.single.toJson(), d.toJson());
      expect(json.people.single.toJson(), p.toJson());
    },
  );
  test('SQLite edit updates same id, preserves created time; restart and repeated import preserve records', () async {
    sqfliteFfiInit();
    final dir = await Directory.systemTemp.createTemp('ledger-test-');
    final path = '${dir.path}/ledger.db';
    var store = await LedgerStore.open(path: path, factory: databaseFactoryFfi);
    const d = Debt(
      id: 'one',
      name: 'Mustafa',
      cents: 20000,
      description: '',
      date: '05/07/2026',
      income: true,
      paid: false,
      createdAt: 123,
    );
    await store.saveDebt(d);
    await store.saveDebt(d.toggle(), editing: true);
    await store.savePerson(
      const Person('Mustafa', [
        BankAccount('TR330006100519786457841326', 'Banka'),
      ]),
    );
    final backup = backupJson(store.debts, store.people.values.toList());
    await store.db.close();
    store = await LedgerStore.open(path: path, factory: databaseFactoryFfi);
    expect(store.debts.single.id, 'one');
    expect(store.debts.single.paid, isTrue);
    expect(store.debts.single.createdAt, 123);
    expect(store.people.values.single.accounts, hasLength(1));
    expect(await store.importBatch(parseBackup(backup)), 0);
    expect(store.debts, hasLength(1));
    await expectLater(
      store.importBatch(parseKeep('bad row')),
      throwsFormatException,
    );
    expect(store.debts, hasLength(1));
    await store.db.close();
    await dir.delete(recursive: true);
  });
}
