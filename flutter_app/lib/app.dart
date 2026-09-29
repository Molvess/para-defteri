import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'ledger.dart';
import 'store.dart';
import 'dialogs.dart';
import 'person_avatar.dart';
import 'access_consent.dart';
import 'reminders.dart';

void notice(BuildContext context, String text) {
  ScaffoldMessenger.of(context).hideCurrentSnackBar();
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
}

String errorText(Object e) => e is FormatException
    ? e.message
    : e is PlatformException && e.message != null
    ? e.message!
    : 'İşlem tamamlanamadı. Kaydı/dosyayı kontrol edip tekrar deneyin.';
Color tone(BuildContext context, Color color) =>
    Theme.of(context).brightness == Brightness.dark
    ? color
    : color == green
    ? const Color(0xff236445)
    : color == red
    ? const Color(0xff9d3030)
    : color == amber
    ? const Color(0xff795a19)
    : color;

const green = Color(0xff84b59a),
    red = Color(0xffd89999),
    amber = Color(0xffd2b27d);
const platform = MethodChannel('com.molvess.ledger/files');

ThemeData darkTheme() => ThemeData(
  useMaterial3: true,
  brightness: Brightness.dark,
  colorScheme:
      ColorScheme.fromSeed(
        seedColor: const Color(0xff7894ae),
        brightness: Brightness.dark,
      ).copyWith(
        surface: const Color(0xff171e26),
        primary: const Color(0xffa4b8cd),
        surfaceContainerHighest: const Color(0xff222e3b),
        error: red,
      ),
  scaffoldBackgroundColor: const Color(0xff10161d),
  appBarTheme: const AppBarTheme(
    backgroundColor: Color(0xff10161d),
    scrolledUnderElevation: 0,
  ),
  cardTheme: const CardThemeData(
    elevation: 0,
    color: Color(0xff1a232d),
    margin: EdgeInsets.zero,
  ),
  inputDecorationTheme: const InputDecorationTheme(
    border: OutlineInputBorder(),
    filled: true,
    fillColor: Color(0xff111a23),
  ),
  outlinedButtonTheme: OutlinedButtonThemeData(
    style: OutlinedButton.styleFrom(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      side: const BorderSide(color: Color(0xff506174)),
      backgroundColor: const Color(0xff202c38),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
    ),
  ),
);

ThemeData lightTheme() => ThemeData(
  useMaterial3: true,
  brightness: Brightness.light,
  colorScheme:
      ColorScheme.fromSeed(
        seedColor: const Color(0xff0d9488),
        brightness: Brightness.light,
      ).copyWith(
        surface: const Color(0xffffffff),
        primary: const Color(0xff0d9488),
        surfaceContainerHighest: const Color(0xffedf2f7),
        error: const Color(0xffe53e3e),
      ),
  scaffoldBackgroundColor: const Color(0xfff5f7fa),
  appBarTheme: const AppBarTheme(
    backgroundColor: Color(0xfff5f7fa),
    scrolledUnderElevation: 0,
    foregroundColor: Color(0xff1a202c),
  ),
  cardTheme: const CardThemeData(
    elevation: 0.5,
    color: Color(0xffffffff),
    margin: EdgeInsets.zero,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.all(Radius.circular(12)),
      side: BorderSide(color: Color(0xffe2e8f0)),
    ),
  ),
  inputDecorationTheme: const InputDecorationTheme(
    border: OutlineInputBorder(),
    filled: true,
    fillColor: Color(0xfff8fafc),
  ),
  outlinedButtonTheme: OutlinedButtonThemeData(
    style: OutlinedButton.styleFrom(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      side: const BorderSide(color: Color(0xffcbd5e1)),
      backgroundColor: const Color(0xfff8fafc),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
    ),
  ),
);

class LedgerApp extends StatefulWidget {
  final LedgerStore? store;
  const LedgerApp({super.key, this.store});
  @override
  State<LedgerApp> createState() => _LedgerAppState();
}

class _LedgerAppState extends State<LedgerApp> {
  late Future<LedgerStore> future = widget.store != null
      ? Future.value(widget.store!)
      : LedgerStore.open().then((store) async {
          store.syncAlarms = syncAndroidReminders;
          await store.syncReminders();
          return store;
        });

  @override
  Widget build(BuildContext context) => FutureBuilder<LedgerStore>(
    future: future,
    builder: (context, snapshot) {
      final store = snapshot.data ?? widget.store;
      if (store == null) {
        return MaterialApp(
          debugShowCheckedModeBanner: false,
          home: Scaffold(
            body: Center(
              child: snapshot.hasError
                  ? Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.error_outline, color: red),
                        const SizedBox(height: 12),
                        const Text(
                          'Kayıtlar açılamadı. Verileriniz sıfırlanmadı.',
                        ),
                        const SizedBox(height: 12),
                        FilledButton(
                          onPressed: () => setState(() {
                            future = LedgerStore.open().then((store) async {
                              store.syncAlarms = syncAndroidReminders;
                              await store.syncReminders();
                              return store;
                            });
                          }),
                          child: const Text('Tekrar dene'),
                        ),
                      ],
                    )
                  : const CircularProgressIndicator(),
            ),
          ),
        );
      }
      return ListenableBuilder(
        listenable: store,
        builder: (context, _) => MaterialApp(
          debugShowCheckedModeBanner: false,
          title: 'Para Defteri',
          locale: const Locale('tr'),
          supportedLocales: const [Locale('tr')],
          localizationsDelegates: GlobalMaterialLocalizations.delegates,
          theme: lightTheme(),
          darkTheme: darkTheme(),
          themeMode: store.isDark ? ThemeMode.dark : ThemeMode.light,
          home: LedgerHome(store: store),
        ),
      );
    },
  );
}

class Startup extends StatelessWidget {
  const Startup({super.key});
  @override
  Widget build(BuildContext context) => const LedgerApp();
}

class LedgerHome extends StatefulWidget {
  final LedgerStore store;
  const LedgerHome({super.key, required this.store});
  @override
  State<LedgerHome> createState() => _LedgerHomeState();
}

class _LedgerHomeState extends State<LedgerHome> with WidgetsBindingObserver {
  int direction = 0, payment = 0;
  bool busy = false, summaries = true;
  LedgerStore get store => widget.store;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !busy) store.syncReminders();
  }

  Future<void> action(Future<void> Function() work, [String? success]) async {
    if (busy) return;
    setState(() {
      busy = true;
    });
    try {
      await work();
      if (mounted && success != null) notice(context, success);
    } catch (e) {
      if (mounted) {
        if (isPermissionError(e)) {
          await showPermissionHelp(context, e as PlatformException);
        } else {
          notice(context, errorText(e));
        }
      }
    } finally {
      if (mounted) {
        setState(() {
          busy = false;
        });
      }
    }
  }

  Future<void> edit([Debt? debt]) => showDialog<void>(
    context: context,
    builder: (_) => DebtEditor(store: store, debt: debt),
  );
  Future<void> remove(Debt debt) async {
    final yes = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Borç silinsin mi?'),
        content: Text('${debt.name} · ${money(debt.cents)}'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(c, false),
            child: const Text('Vazgeç'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(c, true),
            child: const Text('Sil'),
          ),
        ],
      ),
    );
    if (yes != true || !mounted) return;
    await action(() => store.deleteDebt(debt));
    if (mounted && !store.debts.any((d) => d.id == debt.id)) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Borç silindi.'),
          duration: const Duration(seconds: 8),
          action: SnackBarAction(
            label: 'Geri al',
            onPressed: () => action(() => store.saveDebt(debt)),
          ),
        ),
      );
    }
  }

  Future<void> people([String? key]) => showDialog<void>(
    context: context,
    builder: (_) => PeopleDialog(store: store, selectedKey: key),
  );
  Future<void> copyAccounts(Person? person) async {
    if (person == null || person.accounts.length != 1) {
      await people(person?.key);
      return;
    }
    await copyIban(context, person.accounts.single.iban);
  }

  Future<void> export(bool json) => action(() async {
    if (!await confirmFileAccess(context, FileAccess.exportBackup) ||
        !mounted) {
      return;
    }
    final content = json
        ? backupJson(store.debts, store.people.values.toList())
        : exportCsv(store.debts, store.people);
    final saved = await platform.invokeMethod<bool>('save', {
      'name':
          'para-defteri-${DateTime.now().millisecondsSinceEpoch}.${json ? 'json' : 'csv'}',
      'mime': json ? 'application/json' : 'text/csv',
      'text': content,
    });
    if (mounted && saved == true) {
      notice(context, 'Dosya kaydedildi. Tam IBAN içerir; güvenli saklayın.');
    }
  });
  Future<void> importFile() => action(() async {
    if (!await confirmFileAccess(context, FileAccess.importBackup) ||
        !mounted) {
      return;
    }
    final text = await platform.invokeMethod<String>('open');
    if (text == null || !mounted) return;
    final batch = text.replaceFirst('\ufeff', '').trimLeft().startsWith('{')
        ? parseBackup(text)
        : parseLegacyCsv(text);
    await reviewImport(batch);
  });
  Future<void> reviewImport(ImportBatch batch) async {
    final yes = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Aktarımı kontrol et'),
        content: SingleChildScrollView(
          child: Text(
            batch.errors.isNotEmpty
                ? '${batch.errors.length} hatalı satır var. Hiçbir kayıt aktarılmadı.\n\n${batch.errors.take(12).join('\n')}'
                : '${batch.debts.length} borç ve ${batch.people.length} kişi okunabildi. Mevcut kayıtlar korunacak. Aynı kimlikli yedek kayıtları atlanacak.',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(c, false),
            child: const Text('Vazgeç'),
          ),
          if (batch.errors.isEmpty &&
              (batch.debts.isNotEmpty || batch.people.isNotEmpty))
            FilledButton(
              onPressed: () => Navigator.pop(c, true),
              child: const Text('İçe aktar'),
            ),
        ],
      ),
    );
    if (yes == true) {
      final count = await store.importBatch(batch);
      if (mounted) {
        notice(
          context,
          '$count yeni borç aktarıldı; kişi ve IBAN bilgileri birleştirildi.',
        );
      }
    }
  }

  Future<void> keepImport() async {
    final text = await showDialog<String>(
      context: context,
      builder: (_) => const KeepDialog(),
    );
    if (text != null && mounted) {
      await action(() => reviewImport(parseKeep(text)));
    }
  }

  Future<void> showTools() async {
    final choice = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      builder: (c) => SafeArea(
        child: SingleChildScrollView(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Defter araçları',
                  style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 6),
                Text(
                  'Kayıtlarını taşı, yedekle ve güvende tut.',
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onSurface
                        .withValues(alpha: 0.6),
                  ),
                ),
                const SizedBox(height: 20),
                for (final item in [
                  (
                    'reminders',
                    Icons.notifications_active_outlined,
                    'Hatırlatıcılar',
                    'Tüm planları görüntüle ve düzenle',
                  ),
                  (
                    'keep',
                    Icons.note_alt_outlined,
                    'Google Keep listesini yapıştır',
                    'Notlarından yeni borçlar ekle',
                  ),
                  (
                    'open',
                    Icons.file_open_outlined,
                    'CSV / JSON içe aktar',
                    'Mevcut kayıtların korunarak birleştirilir',
                  ),
                  (
                    'json',
                    Icons.backup_outlined,
                    'JSON yedek kaydet',
                    'Kişiler, fotoğraflar, IBAN ve tüm borçlar',
                  ),
                  (
                    'csv',
                    Icons.table_chart_outlined,
                    'CSV dışa aktar',
                    'Borçlarını tablo olarak kaydet',
                  ),
                ])
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: Material(
                      color: Theme.of(context)
                          .colorScheme
                          .surfaceContainerHighest
                          .withValues(alpha: 0.5),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                        side: BorderSide(
                          color: Theme.of(context).dividerColor
                              .withValues(alpha: 0.2),
                        ),
                      ),
                      clipBehavior: Clip.antiAlias,
                      child: ListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 14,
                          vertical: 8,
                        ),
                        leading: Icon(item.$2, color: green),
                        title: Text(
                          item.$3,
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                        subtitle: Text(
                          item.$4,
                          style: TextStyle(
                            fontSize: 12,
                            color: Theme.of(context).colorScheme.onSurface
                                .withValues(alpha: 0.6),
                          ),
                        ),
                        trailing: const Icon(Icons.chevron_right, size: 20),
                        onTap: () => Navigator.pop(c, item.$1),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
    if (!mounted) return;
    switch (choice) {
      case 'reminders':
        await showDialog<void>(
          context: context,
          builder: (_) => ReminderCenter(store: store),
        );
      case 'keep':
        await keepImport();
      case 'open':
        await importFile();
      case 'json':
        await export(true);
      case 'csv':
        await export(false);
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: store,
    builder: (context, _) {
      // Runs on data/filter changes, never on scroll offsets.
      final visible = store.debts
          .where(
            (d) =>
                (direction == 0 || d.income == (direction == 1)) &&
                (payment == 0 || d.paid == (payment == 2)),
          )
          .toList();
      final groups = <String, PendingTotal>{};
      var pendingA = 0, pendingV = 0, paidA = 0, paidV = 0;
      for (final d in visible) {
        if (d.paid) {
          if (d.income) {
            paidA += d.cents;
          } else {
            paidV += d.cents;
          }
        } else {
          if (d.income) {
            pendingA += d.cents;
          } else {
            pendingV += d.cents;
          }
          final summary = groups.putIfAbsent(
            d.key,
            () => PendingTotal(d.key, d.name),
          );
          if (d.income) {
            summary.income += d.cents;
          } else {
            summary.expense += d.cents;
          }
        }
      }
      final totals = groups.values.toList()
        ..sort((a, b) => a.key.compareTo(b.key));
      return Scaffold(
        appBar: AppBar(
          title: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 32,
                height: 32,
                margin: const EdgeInsets.only(right: 10),
                decoration: BoxDecoration(
                  color: const Color(0xff0d9488),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Center(
                  child: Text(
                    '₺',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Text(
                      'Para Defteri',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontWeight: FontWeight.w900,
                        fontSize: 18,
                        letterSpacing: -0.3,
                      ),
                    ),
                    Text(
                      'v1.1.4',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w500,
                        color: Theme.of(context).colorScheme.onSurface
                            .withValues(alpha: 0.55),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          actions: [
            IconButton(
              tooltip: widget.store.isDark
                  ? 'Açık temaya geç'
                  : 'Koyu temaya geç',
              onPressed: busy ? null : () => action(widget.store.toggleTheme),
              icon: Icon(
                widget.store.isDark
                    ? Icons.light_mode_outlined
                    : Icons.dark_mode_outlined,
              ),
            ),
            IconButton(
              tooltip: 'Kişiler ve IBAN',
              onPressed: busy ? null : () => people(),
              icon: const Icon(Icons.people_outline),
            ),
            IconButton(
              tooltip: 'Yedekleme ve aktarım',
              onPressed: busy ? null : showTools,
              icon: const Icon(Icons.more_vert),
            ),
          ],
        ),
        body: SafeArea(
          top: false,
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 900),
              child: Column(
                children: [
                  if (busy) const LinearProgressIndicator(minHeight: 2),
                  if (store.reminderError != null)
                    MaterialBanner(
                      content: Text(store.reminderError!),
                      actions: [
                        TextButton(
                          onPressed: busy
                              ? null
                              : () => action(store.syncReminders),
                          child: const Text('Tekrar dene'),
                        ),
                      ],
                    ),
                  Expanded(
                    child: CustomScrollView(
                      key: const PageStorageKey('ledger-scroll'),
                      slivers: [
                        SliverPadding(
                          padding: const EdgeInsets.all(16),
                          sliver: SliverToBoxAdapter(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Card(
                                  child: Padding(
                                    padding: const EdgeInsets.all(16),
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        const Text(
                                          'GÖRÜNEN KAYITLARIN ÖZETİ',
                                          style: TextStyle(
                                            fontSize: 11,
                                            letterSpacing: 1.2,
                                          ),
                                        ),
                                        const SizedBox(height: 12),
                                        Wrap(
                                          spacing: 24,
                                          runSpacing: 12,
                                          children: [
                                            Metric(
                                              'Bekleyen alacak',
                                              pendingA,
                                              green,
                                            ),
                                            Metric(
                                              'Bekleyen verecek',
                                              pendingV,
                                              red,
                                            ),
                                            Metric(
                                              'Ödenen alacak',
                                              paidA,
                                              green,
                                            ),
                                            Metric(
                                              'Ödenen verecek',
                                              paidV,
                                              red,
                                            ),
                                          ],
                                        ),
                                        const SizedBox(height: 12),
                                        Text(
                                          'Toplamlar seçili yön ve ödeme filtresine aittir.',
                                          style: TextStyle(
                                            fontSize: 11,
                                            color: Theme.of(context)
                                                .colorScheme
                                                .onSurface
                                                .withValues(alpha: 0.6),
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                ),
                                const SizedBox(height: 16),
                                FilterBar(
                                  labels: const [
                                    'Tümü',
                                    'Alacaklar',
                                    'Verecekler',
                                  ],
                                  value: direction,
                                  change: (v) => setState(() {
                                    direction = v;
                                  }),
                                ),
                                const SizedBox(height: 8),
                                FilterBar(
                                  labels: const [
                                    'Tüm durumlar',
                                    'Ödenecek',
                                    'Ödendi',
                                  ],
                                  value: payment,
                                  change: (v) => setState(() {
                                    payment = v;
                                  }),
                                ),
                                if (totals.isNotEmpty)
                                  TextButton.icon(
                                    onPressed: () => setState(() {
                                      summaries = !summaries;
                                    }),
                                    icon: Icon(
                                      summaries
                                          ? Icons.expand_less
                                          : Icons.expand_more,
                                    ),
                                    label: Text(
                                      'Kişilerde bekleyenler (${totals.length})',
                                    ),
                                  ),
                              ],
                            ),
                          ),
                        ),
                        if (summaries)
                          SliverList.builder(
                            itemCount: totals.length,
                            itemBuilder: (context, i) {
                              final t = totals[i];
                              return Padding(
                                key: ValueKey('person-${t.key}'),
                                padding: const EdgeInsets.fromLTRB(
                                  16,
                                  0,
                                  16,
                                  8,
                                ),
                                child: Card(
                                  child: InkWell(
                                    borderRadius: BorderRadius.circular(12),
                                    onTap: () => people(t.key),
                                    child: Padding(
                                      padding: const EdgeInsets.all(14),
                                      child: Row(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.center,
                                        children: [
                                          Expanded(
                                            child: Column(
                                              crossAxisAlignment:
                                                  CrossAxisAlignment.start,
                                              children: [
                                                Text(
                                                  t.name,
                                                  style: const TextStyle(
                                                    fontWeight: FontWeight.w800,
                                                  ),
                                                ),
                                                const SizedBox(height: 8),
                                                Wrap(
                                                  spacing: 24,
                                                  runSpacing: 8,
                                                  children: [
                                                    Metric(
                                                      'Bekleyen alacak',
                                                      t.income,
                                                      green,
                                                    ),
                                                    Metric(
                                                      'Bekleyen verecek',
                                                      t.expense,
                                                      red,
                                                    ),
                                                  ],
                                                ),
                                              ],
                                            ),
                                          ),
                                          const SizedBox(width: 12),
                                          PersonAvatar(
                                            person: store.people[t.key],
                                            name: t.name,
                                            size: 64,
                                          ),
                                        ],
                                      ),
                                    ),
                                  ),
                                ),
                              );
                            },
                          ),
                        SliverToBoxAdapter(
                          child: Padding(
                            padding: const EdgeInsets.all(16),
                            child: Text(
                              '${visible.length} borç · En son eklenen en üstte',
                              style: TextStyle(
                                color: Theme.of(context).colorScheme.onSurface
                                    .withValues(alpha: 0.6),
                              ),
                            ),
                          ),
                        ),
                        if (visible.isEmpty)
                          const SliverToBoxAdapter(
                            child: Padding(
                              padding: EdgeInsets.all(32),
                              child: Column(
                                children: [
                                  Icon(Icons.receipt_long_outlined, size: 48),
                                  SizedBox(height: 16),
                                  Text('Bu görünümde borç kaydı yok.'),
                                  Text(
                                    'Yeni borç ekleyin veya filtreleri değiştirin.',
                                    textAlign: TextAlign.center,
                                  ),
                                ],
                              ),
                            ),
                          ),
                        SliverList.builder(
                          itemCount: visible.length,
                          itemBuilder: (context, i) {
                            final d = visible[i],
                                p = store.people[visible[i].key];
                            return Padding(
                              key: ValueKey(d.id),
                              padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
                              child: DebtCard(
                                debt: d,
                                person: p,
                                busy: busy,
                                edit: () => edit(d),
                                remove: () => remove(d),
                                toggle: () => action(
                                  () =>
                                      store.saveDebt(d.toggle(), editing: true),
                                  'Ödeme durumu güncellendi.',
                                ),
                                manage: () => people(d.key),
                                copy: () => copyAccounts(p),
                                remind: () => showDialog<void>(
                                  context: context,
                                  builder: (_) =>
                                      ReminderEditor(debt: d, store: store),
                                ),
                              ),
                            );
                          },
                        ),
                        const SliverToBoxAdapter(child: SizedBox(height: 100)),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        floatingActionButton: FloatingActionButton.extended(
          onPressed: busy ? null : () => edit(),
          icon: const Icon(Icons.add),
          label: const Text('Borç ekle'),
        ),
      );
    },
  );
}

class PendingTotal {
  final String key, name;
  int income = 0, expense = 0;
  PendingTotal(this.key, this.name);
}

class Metric extends StatelessWidget {
  final String label;
  final int cents;
  final Color color;
  const Metric(this.label, this.cents, this.color, {super.key});
  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    mainAxisSize: MainAxisSize.min,
    children: [
      Text(
        label,
        style: TextStyle(
          color: tone(context, color),
          fontStyle: FontStyle.italic,
          fontWeight: FontWeight.bold,
          fontSize: 12,
        ),
      ),
      Text(
        money(cents),
        style: TextStyle(
          color: tone(context, color),
          fontWeight: FontWeight.w800,
          fontSize: 18,
        ),
      ),
    ],
  );
}

class FilterBar extends StatelessWidget {
  final List<String> labels;
  final int value;
  final ValueChanged<int> change;
  const FilterBar({
    super.key,
    required this.labels,
    required this.value,
    required this.change,
  });
  @override
  Widget build(BuildContext context) => Wrap(
    spacing: 8,
    runSpacing: 4,
    children: [
      for (var i = 0; i < labels.length; i++)
        ChoiceChip(
          label: Text(labels[i]),
          selected: value == i,
          onSelected: (_) => change(i),
        ),
    ],
  );
}

class DebtCard extends StatelessWidget {
  final Debt debt;
  final Person? person;
  final bool busy;
  final VoidCallback edit, remove, toggle, manage, copy;
  final VoidCallback? remind;
  const DebtCard({
    super.key,
    required this.debt,
    required this.person,
    required this.busy,
    required this.edit,
    required this.remove,
    required this.toggle,
    required this.manage,
    required this.copy,
    this.remind,
  });
  @override
  Widget build(BuildContext context) {
    final color = tone(context, debt.income ? green : red);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                InkWell(
                  onTap: manage,
                  child: PersonAvatar(person: person, name: debt.name),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    debt.name,
                    style: const TextStyle(
                      fontWeight: FontWeight.w800,
                      fontSize: 17,
                    ),
                  ),
                ),
                IconButton(
                  tooltip: 'Borcu düzenle',
                  onPressed: busy ? null : edit,
                  icon: const Icon(Icons.edit_outlined),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              '${debt.income ? '+' : '−'} ${money(debt.cents)}',
              style: TextStyle(
                color: color,
                fontWeight: FontWeight.w800,
                fontSize: 24,
              ),
            ),
            if (debt.description.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(debt.description),
              ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 4,
              children: [
                Text(
                  debt.income ? 'ALACAK' : 'VERECEK',
                  style: TextStyle(
                    color: color,
                    fontWeight: FontWeight.bold,
                    fontStyle: FontStyle.italic,
                  ),
                ),
                Text(
                  debt.paid ? 'ÖDENDİ' : 'ÖDENECEK',
                  style: TextStyle(
                    color: debt.paid
                        ? (Theme.of(context).brightness == Brightness.dark
                              ? Colors.white60
                              : Colors.black54)
                        : amber,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                Text(
                  debt.date,
                  style: TextStyle(
                    color: Theme.of(context).brightness == Brightness.dark
                        ? Colors.white60
                        : Colors.black54,
                  ),
                ),
                if (debt.reminder != null)
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 6,
                      vertical: 2,
                    ),
                    decoration: BoxDecoration(
                      color: const Color(0xff0d9488).withValues(alpha: 0.18),
                      borderRadius: BorderRadius.circular(6),
                      border: Border.all(
                        color: const Color(0xff0d9488),
                        width: 0.8,
                      ),
                    ),
                    child: Text(
                      debt.reminder!.summary,
                      style: const TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: Color(0xff0d9488),
                      ),
                    ),
                  ),
              ],
            ),
            if (person?.accounts.isNotEmpty == true)
              Padding(
                padding: const EdgeInsets.only(top: 10),
                child: Text(
                  '${maskedIban(person!.accounts.first.iban)}${person!.accounts.length > 1 ? ' · ${person!.accounts.length} IBAN' : ''}',
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onSurface
                        .withValues(alpha: 0.6),
                  ),
                ),
              ),
            const Divider(height: 24),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                OutlinedButton.icon(
                  onPressed: busy ? null : toggle,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: debt.paid ? amber : green,
                    backgroundColor: debt.paid
                        ? const Color(0xff352e23)
                        : const Color(0xff203c31),
                    side: BorderSide(color: debt.paid ? amber : green),
                  ),
                  icon: Icon(
                    debt.paid ? Icons.undo : Icons.check_circle_outline,
                    size: 18,
                  ),
                  label: Text(debt.paid ? 'Ödenecek yap' : 'Ödendi yap'),
                ),
                OutlinedButton.icon(
                  onPressed: busy
                      ? null
                      : (person?.accounts.isNotEmpty == true ? copy : null),
                  icon: const Icon(Icons.copy_outlined, size: 18),
                  label: const Text('IBAN’ı kopyala'),
                ),
                OutlinedButton.icon(
                  onPressed: busy ? null : remind,
                  icon: const Icon(
                    Icons.notifications_active_outlined,
                    size: 18,
                  ),
                  label: const Text('Hatırlatıcı'),
                ),
                IconButton(
                  tooltip: 'Borcu sil',
                  onPressed: busy ? null : remove,
                  icon: const Icon(Icons.delete_outline, color: red),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
