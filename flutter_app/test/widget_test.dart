import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:para_defteri_flutter/app.dart';
import 'package:para_defteri_flutter/dialogs.dart';
import 'package:para_defteri_flutter/ledger.dart';
import 'package:para_defteri_flutter/store.dart';
import 'package:para_defteri_flutter/person_avatar.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  sqfliteFfiInit();
  Future<LedgerStore> database() =>
      LedgerStore.open(path: inMemoryDatabasePath, factory: databaseFactoryFfi);
  Widget shell(Widget child) => MaterialApp(
    locale: const Locale('tr'),
    supportedLocales: const [Locale('tr')],
    localizationsDelegates: GlobalMaterialLocalizations.delegates,
    home: child,
  );

  testWidgets('Tools menu uses a bottom sheet without refresh-rate item', (
    tester,
  ) async {
    final store = (await tester.runAsync(database))!;
    addTearDown(store.db.close);
    await tester.pumpWidget(shell(LedgerHome(store: store)));
    await tester.tap(find.byTooltip('Yedekleme ve aktarım'));
    await tester.pumpAndSettle();
    expect(find.byType(BottomSheet), findsOneWidget);
    expect(find.text('Defter araçları'), findsOneWidget);
    expect(find.text('Ekran yenileme hızı'), findsNothing);
    expect(find.text('JSON yedek kaydet'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Photo picker saves, cancels safely and reports errors', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    const photo =
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=';
    final store = (await tester.runAsync(database))!;
    addTearDown(store.db.close);
    await tester.runAsync(() => store.savePerson(const Person('Işık', [])));
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    String? result = photo;
    bool fail = false;
    messenger.setMockMethodCallHandler(platform, (call) async {
      expect(call.method, 'pickPhoto');
      if (fail) {
        throw PlatformException(code: 'FILES', message: 'Fotoğraf açılamadı.');
      }
      return result;
    });
    addTearDown(() => messenger.setMockMethodCallHandler(platform, null));
    await tester.pumpWidget(
      shell(
        Scaffold(
          body: PeopleDialog(store: store, selectedKey: personKey('Işık')),
        ),
      ),
    );
    await tester.runAsync(() async {
      await tester.tap(find.text('Fotoğraf ekle'));
      // Wait for the actual SQLite operation, outside the test fake clock.
      for (
        var i = 0;
        i < 100 && store.people.values.single.photo.isEmpty;
        i++
      ) {
        await Future<void>.delayed(const Duration(milliseconds: 10));
      }
    });
    await tester.pumpAndSettle();
    expect(store.people.values.single.photo, photo);
    expect(find.byType(PersonAvatar), findsOneWidget);
    result = null;
    await tester.tap(find.text('Fotoğrafı değiştir'));
    await tester.pumpAndSettle();
    expect(store.people.values.single.photo, photo);
    fail = true;
    await tester.tap(find.text('Fotoğrafı değiştir'));
    await tester.pumpAndSettle();
    expect(find.text('Fotoğraf açılamadı.'), findsOneWidget);
    expect(store.people.values.single.photo, photo);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'Long ledger builds only visible rows; filters keep direction and status separate',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final store = (await tester.runAsync(database))!;
      addTearDown(store.db.close);
      await tester.runAsync(
        () => store.importBatch(
          ImportBatch(
            List.generate(
              1000,
              (i) => Debt(
                id: '$i',
                name: 'Kişi $i',
                cents: 10000,
                description: '',
                date: '28/09/2026',
                income: i.isEven,
                paid: i % 3 == 0,
                createdAt: i,
              ),
            ),
            const [],
          ),
        ),
      );
      await tester.pumpWidget(shell(LedgerHome(store: store)));
      await tester.pumpAndSettle();
      expect(find.text('Kişi 998'), findsNothing);
      expect(find.byType(Metric).evaluate().length, lessThan(30));
      await tester.tap(find.text('Verecekler'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Ödendi').first);
      await tester.pumpAndSettle();
      expect(find.textContaining('Kişilerde bekleyenler'), findsNothing);
      await tester.drag(find.byType(CustomScrollView), const Offset(0, -550));
      await tester.pumpAndSettle();
      expect(find.byType(DebtCard).evaluate().length, lessThan(12));
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets('Cancel populated debt editor leaves stored row unchanged', (
    tester,
  ) async {
    final store = (await tester.runAsync(database))!;
    addTearDown(store.db.close);
    final debt = Debt(
      id: 'test',
      name: 'Işık',
      cents: 20000,
      description: 'Yemek',
      date: '05/07/2026',
      income: true,
      paid: false,
      createdAt: 1,
    );
    await tester.runAsync(() => store.saveDebt(debt));
    await tester.runAsync(
      () => store.savePerson(const Person('Işık', [], avatar: '🌿')),
    );
    await tester.pumpWidget(
      shell(
        Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () => showDialog<void>(
                context: context,
                builder: (_) => DebtEditor(store: store, debt: debt),
              ),
              child: const Text('Aç'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Aç'));
    await tester.pumpAndSettle();
    expect(find.text('Işık'), findsOneWidget);
    expect(
      tester.widget<PersonAvatar>(find.byType(PersonAvatar)).person?.avatar,
      '🌿',
    );
    await tester.enterText(find.byType(TextFormField).first, 'Başka kişi');
    await tester.pump();
    expect(
      tester.widget<PersonAvatar>(find.byType(PersonAvatar)).person,
      isNull,
    );
    await tester.tap(find.text('İptal'));
    await tester.pumpAndSettle();
    expect(store.debts.single.name, 'Işık');
    expect(tester.takeException(), isNull);
  });
}
