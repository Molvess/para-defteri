import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:para_defteri_flutter/app.dart';
import 'package:para_defteri_flutter/dialogs.dart';
import 'package:para_defteri_flutter/ledger.dart';
import 'package:para_defteri_flutter/person_picker.dart';
import 'package:para_defteri_flutter/reminders.dart';
import 'package:para_defteri_flutter/store.dart';

const sample = Debt(
  id: 'legacy',
  name: 'Işık',
  cents: 20000,
  description: 'Kira',
  date: '15/10/2026',
  income: false,
  paid: false,
  createdAt: 12,
);
const iban = 'TR330006100519786457841326';
const reminder = Reminder(
  startDate: '15/10/2090',
  hour: 9,
  minute: 30,
  repeat: 'monthly',
  endDate: '15/10/2091',
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  sqfliteFfiInit();
  Future<LedgerStore> open() =>
      LedgerStore.open(path: inMemoryDatabasePath, factory: databaseFactoryFfi);
  Widget shell(Widget child) => MaterialApp(
    theme: lightTheme(),
    locale: const Locale('tr'),
    supportedLocales: const [Locale('tr')],
    localizationsDelegates: GlobalMaterialLocalizations.delegates,
    home: Scaffold(body: child),
  );

  test('IBAN groups are display-only and old debts have no reminder', () {
    expect(formattedIban(iban), 'TR33 0006 1005 1978 6457 8413 26');
    expect(normalizeIban(formattedIban(iban)), iban);
    expect(Debt.fromJson(sample.toJson()).reminder, isNull);
    final d = sample.withReminder(reminder);
    expect(d.toggle().reminder!.toJson(), reminder.toJson());
    expect(
      parseBackup(backupJson([d], [])).debts.single.reminder!.toJson(),
      reminder.toJson(),
    );
    expect(
      () => Reminder.fromJson({...reminder.toJson(), 'hour': 25}),
      throwsFormatException,
    );
    expect(
      () => Reminder.fromJson({...reminder.toJson(), 'endDate': '14/10/2090'}),
      throwsFormatException,
    );
  });

  test('v1 SQLite migration retains people IBAN and debts; theme and reminders persist', () async {
    final dir = await Directory.systemTemp.createTemp('ledger-v1-');
    final path = '${dir.path}/test.db';
    final old = await databaseFactoryFfi.openDatabase(
      path,
      options: OpenDatabaseOptions(
        version: 1,
        onCreate: (db, _) async {
          await db.execute(
            'CREATE TABLE debts (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)',
          );
          await db.execute(
            'CREATE TABLE people (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)',
          );
        },
      ),
    );
    await old.insert('debts', {
      'id': sample.id,
      'payload': jsonEncode(sample.toJson()),
    });
    await old.insert('people', {
      'id': sample.key,
      'payload': jsonEncode(
        const Person('Işık', [
          BankAccount(iban, 'Banka'),
        ], avatar: '🌿').toJson(),
      ),
    });
    await old.close();
    var store = await LedgerStore.open(path: path, factory: databaseFactoryFfi);
    expect(store.isDark, isTrue);
    expect(store.debts.single.toJson(), sample.toJson());
    await store.toggleTheme();
    await store.saveDebt(sample.withReminder(reminder), editing: true);
    await store.db.close();
    store = await LedgerStore.open(path: path, factory: databaseFactoryFfi);
    expect(store.isDark, isFalse);
    expect(store.people.values.single.accounts.single.iban, iban);
    expect(store.people.values.single.avatar, '🌿');
    expect(store.debts.single.reminder!.toJson(), reminder.toJson());
    expect(store.debts.single.createdAt, 12);
    await store.db.close();
    await dir.delete(recursive: true);
  });

  test('Debt edits deletion and restore resync alarms; failed sync preserves records', () async {
    final store = await open();
    addTearDown(store.db.close);
    var synced = <Debt>[];
    store.syncAlarms = (items) async => synced = items;
    await store.saveDebt(sample.withReminder(reminder));
    expect(synced.single.reminder, isNotNull);
    await store.saveDebt(store.debts.single.toggle(), editing: true);
    expect(synced.single.reminder, isNotNull);
    await store.deleteDebt(store.debts.single);
    expect(synced, isEmpty);
    await store.importBatch(ImportBatch([sample.withReminder(reminder)], []));
    expect(synced.single.id, sample.id);
    store.syncAlarms = (_) async => throw StateError('offline');
    await store.saveDebt(store.debts.single.withReminder(null), editing: true);
    expect(store.debts.single.reminder, isNull);
    expect(store.reminderError, isNotNull);
  });

  testWidgets(
    'Registered person chooser fills Kim without creating another person',
    (tester) async {
      final store = (await tester.runAsync(open))!;
      addTearDown(store.db.close);
      await tester.runAsync(() => store.savePerson(const Person('Işık', [])));
      await tester.pumpWidget(shell(DebtEditor(store: store)));
      await tester.tap(find.byTooltip('Kayıtlı kişi seç'));
      await tester.pumpAndSettle();
      expect(find.byType(PersonPicker), findsOneWidget);
      await tester.tap(find.text('Işık'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<TextFormField>(find.byType(TextFormField).first)
            .controller!
            .text,
        'Işık',
      );
      expect(store.people.length, 1);
      expect(store.debts, isEmpty);
    },
  );

  testWidgets(
    'IBAN box stays present when toggling visibility in light theme',
    (tester) async {
      final store = (await tester.runAsync(open))!;
      addTearDown(store.db.close);
      await tester.runAsync(
        () => store.savePerson(
          const Person('Işık', [BankAccount(iban, 'Banka')]),
        ),
      );
      await tester.pumpWidget(
        shell(PeopleDialog(store: store, selectedKey: personKey('Işık'))),
      );
      final box = find.byKey(const ValueKey('iban-box-$iban'));
      expect(box, findsOneWidget);
      final decoration = tester.widget<Container>(box).decoration;
      await tester.tap(find.text('Göster'));
      await tester.pumpAndSettle();
      expect(find.text('TR33 0006 1005 1978 6457 8413 26'), findsOneWidget);
      expect(tester.widget<Container>(box).decoration, decoration);
      await tester.tap(find.text('Gizle'));
      await tester.pumpAndSettle();
      expect(box, findsOneWidget);
      expect(find.text(maskedIban(iban)), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('Theme toggles and header has no Flutter subtitle', (
    tester,
  ) async {
    final store = (await tester.runAsync(open))!;
    addTearDown(store.db.close);
    await tester.pumpWidget(LedgerApp(store: store));
    await tester.pumpAndSettle();
    expect(find.text('Flutter · Yerel kayıt'), findsNothing);
    expect(find.text('v1.1.4'), findsOneWidget);
    await tester.runAsync(() => store.toggleTheme());
    await tester.pumpAndSettle();
    expect(
      tester.widget<MaterialApp>(find.byType(MaterialApp)).themeMode,
      ThemeMode.light,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('Tools contain reminder center but no theme option', (
    tester,
  ) async {
    final store = (await tester.runAsync(open))!;
    addTearDown(store.db.close);
    await tester.runAsync(() => store.saveDebt(sample.withReminder(reminder)));
    await tester.pumpWidget(LedgerApp(store: store));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Yedekleme ve aktarım'));
    await tester.pumpAndSettle();
    expect(find.text('Hatırlatıcılar'), findsOneWidget);
    expect(find.textContaining('temaya geç'), findsNothing);
    await tester.tap(find.text('Hatırlatıcılar'));
    await tester.pumpAndSettle();
    expect(find.text('Işık'), findsWidgets);
    expect(find.text('Düzenle'), findsOneWidget);
    expect(find.textContaining('15/10/2090'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'Reminder notification denial leaves debt unchanged; allowed save persists',
    (tester) async {
      final store = (await tester.runAsync(open))!;
      addTearDown(store.db.close);
      await tester.runAsync(() => store.saveDebt(sample));
      var allowed = false;
      var requests = 0;
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(platform, (call) async {
        if (call.method == 'requestNotifications') requests++;
        return {'notifications': allowed, 'exact': false};
      });
      addTearDown(() => messenger.setMockMethodCallHandler(platform, null));
      await tester.pumpWidget(
        shell(
          Builder(
            builder: (c) => TextButton(
              onPressed: () => showDialog<void>(
                context: c,
                builder: (_) => ReminderEditor(debt: sample, store: store),
              ),
              child: const Text('Aç'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Aç'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Kaydet'));
      await tester.pumpAndSettle();
      expect(requests, 1);
      expect(store.debts.single.reminder, isNull);
      expect(find.textContaining('Bildirim izni verilmedi'), findsOneWidget);
      allowed = true;
      await tester.runAsync(() async {
        await tester.tap(find.text('Kaydet'));
        for (var i = 0; i < 100 && store.debts.single.reminder == null; i++) {
          await Future<void>.delayed(const Duration(milliseconds: 10));
        }
      });
      await tester.pumpAndSettle();
      expect(store.debts.single.reminder!.repeat, 'monthly');
      expect(requests, 2);
      expect(tester.takeException(), isNull);
    },
  );
}
