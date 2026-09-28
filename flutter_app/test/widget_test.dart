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

  testWidgets('Person dialog fits zero and one IBAN; many accounts scroll', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final store = (await tester.runAsync(database))!;
    addTearDown(store.db.close);
    await tester.runAsync(() => store.savePerson(const Person('Işık', [])));
    await tester.pumpWidget(
      shell(
        Scaffold(
          body: PeopleDialog(store: store, selectedKey: personKey('Işık')),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final dialogSurface = find
        .descendant(
          of: find.byType(AlertDialog),
          matching: find.byWidgetPredicate(
            (w) => w is Material && w.type == MaterialType.card,
          ),
        )
        .first;
    final emptyHeight = tester.getSize(dialogSurface).height;
    expect(emptyHeight, lessThan(400));
    const accounts = [
      BankAccount('TR330006100519786457841326', 'Banka'),
      BankAccount('DE89370400440532013000', 'İkinci banka'),
      BankAccount('GB82WEST12345698765432', 'Üçüncü banka'),
    ];
    await tester.runAsync(
      () => store.savePerson(Person('Işık', accounts.take(1).toList())),
    );
    await tester.pumpAndSettle();
    final oneHeight = tester.getSize(dialogSurface).height;
    expect(oneHeight, greaterThan(emptyHeight));
    expect(oneHeight, lessThan(600));
    await tester.runAsync(
      () => store.savePerson(const Person('Işık', accounts)),
    );
    await tester.pumpAndSettle();
    expect(tester.getSize(dialogSurface).height, lessThan(844));
    await tester.drag(
      find.byType(SingleChildScrollView).first,
      const Offset(0, -400),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'Pending avatar is centered in whole card; debt actions are simplified',
    (tester) async {
      final store = (await tester.runAsync(database))!;
      addTearDown(store.db.close);
      const d = Debt(
        id: 'center',
        name: 'Işık',
        cents: 20000,
        description: '',
        date: '05/07/2026',
        income: true,
        paid: false,
        createdAt: 1,
      );
      await tester.runAsync(() => store.saveDebt(d));
      await tester.pumpWidget(shell(LedgerHome(store: store)));
      await tester.pumpAndSettle();
      final group = find.byKey(ValueKey('person-${d.key}'));
      final avatar = find.descendant(
        of: group,
        matching: find.byType(PersonAvatar),
      );
      final card = find.descendant(of: group, matching: find.byType(Card));
      expect(
        tester.getCenter(avatar).dy,
        closeTo(tester.getCenter(card).dy, .1),
      );
      await tester.pumpWidget(
        shell(
          Scaffold(
            body: DebtCard(
              debt: d,
              person: store.people[d.key],
              busy: false,
              edit: () {},
              remove: () {},
              toggle: () {},
              manage: () {},
              copy: () {},
            ),
          ),
        ),
      );
      expect(find.text('Kişi / IBAN'), findsNothing);
      expect(find.text('IBAN ekle'), findsNothing);
      final copyButton = find.widgetWithText(OutlinedButton, 'IBAN’ı kopyala');
      expect(tester.widget<OutlinedButton>(copyButton).onPressed, isNull);
      final payment = tester.widget<OutlinedButton>(
        find.widgetWithText(OutlinedButton, 'Ödendi yap'),
      );
      expect(payment.style?.foregroundColor?.resolve({}), green);
      expect(tester.takeException(), isNull);
    },
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
    var pickerCalls = 0;
    messenger.setMockMethodCallHandler(platform, (call) async {
      pickerCalls++;
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
    await tester.tap(find.text('Fotoğraf ekle'));
    await tester.pump(const Duration(milliseconds: 350));
    expect(find.text('Fotoğraf erişimi'), findsOneWidget);
    expect(pickerCalls, 0);
    await tester.tap(find.text('Vazgeç'));
    await tester.pumpAndSettle();
    expect(store.people.values.single.photo, isEmpty);
    expect(pickerCalls, 0);
    await tester.tap(find.text('Fotoğraf ekle'));
    await tester.pump(const Duration(milliseconds: 350));
    await tester.tap(find.text('Fotoğraf seç'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 350));
    await tester.runAsync(() async {
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
    await tester.pump(const Duration(milliseconds: 350));
    await tester.tap(find.text('Fotoğraf seç'));
    await tester.pumpAndSettle();
    expect(store.people.values.single.photo, photo);
    fail = true;
    await tester.tap(find.text('Fotoğrafı değiştir'));
    await tester.pump(const Duration(milliseconds: 350));
    await tester.tap(find.text('Fotoğraf seç'));
    await tester.pumpAndSettle();
    expect(find.text('Fotoğraf açılamadı.'), findsOneWidget);
    expect(store.people.values.single.photo, photo);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'File access is never requested before consent; cancellation preserves data',
    (tester) async {
      final store = (await tester.runAsync(database))!;
      addTearDown(store.db.close);
      final calls = <String>[];
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(platform, (call) async {
        calls.add(call.method);
        return null; // User closes the Android picker.
      });
      addTearDown(() => messenger.setMockMethodCallHandler(platform, null));
      await tester.pumpWidget(shell(LedgerHome(store: store)));
      for (final scenario in [
        ('CSV / JSON içe aktar', 'Dosya seç', 'open'),
        ('JSON yedek kaydet', 'Kaydetme yeri seç', 'save'),
      ]) {
        await tester.tap(find.byTooltip('Yedekleme ve aktarım'));
        await tester.pumpAndSettle();
        await tester.tap(find.text(scenario.$1));
        await tester.pump(const Duration(milliseconds: 350));
        final before = calls.length;
        await tester.tap(find.text('Vazgeç'));
        await tester.pumpAndSettle();
        expect(calls.length, before);
        await tester.tap(find.byTooltip('Yedekleme ve aktarım'));
        await tester.pumpAndSettle();
        await tester.tap(find.text(scenario.$1));
        await tester.pump(const Duration(milliseconds: 350));
        await tester.tap(find.text(scenario.$2));
        await tester.pumpAndSettle();
        expect(calls.last, scenario.$3);
        expect(calls.length, before + 1);
        expect(store.debts, isEmpty);
        expect(find.text('İçe aktar'), findsNothing);
      }
      expect(tester.takeException(), isNull);
    },
  );

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
