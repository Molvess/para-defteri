import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:para_defteri_flutter/app.dart';
import 'package:para_defteri_flutter/dialogs.dart';
import 'package:para_defteri_flutter/ledger.dart';
import 'package:para_defteri_flutter/store.dart';

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
    await tester.enterText(find.byType(TextFormField).first, 'Başka kişi');
    await tester.tap(find.text('İptal'));
    await tester.pumpAndSettle();
    expect(store.debts.single.name, 'Işık');
    expect(tester.takeException(), isNull);
  });
}
