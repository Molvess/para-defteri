import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app.dart';
import 'ledger.dart';
import 'store.dart';

Future<void> syncAndroidReminders(List<Debt> debts) async {
  final items = debts
      .where((d) => d.reminder?.enabled == true)
      .map(
        (d) => {
          'id': d.id,
          'body':
              '${d.name} · ${money(d.cents)} · ${d.income ? 'Alacak' : 'Verecek'}${d.description.isEmpty ? '' : ' · ${d.description}'}',
          'reminder': d.reminder!.toJson(),
        },
      )
      .toList();
  final status = await platform.invokeMapMethod<String, dynamic>(
    'syncReminders',
    {'items': jsonEncode(items)},
  );
  if (items.isNotEmpty && status?['notifications'] != true) {
    throw PlatformException(
      code: 'NOTIFICATIONS',
      message: 'Bildirim izni kapalı.',
    );
  }
}

class ReminderCenter extends StatelessWidget {
  final LedgerStore store;
  const ReminderCenter({super.key, required this.store});

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Row(
      children: [
        Icon(Icons.notifications_active_outlined),
        SizedBox(width: 10),
        Expanded(child: Text('Hatırlatıcılar')),
      ],
    ),
    content: SizedBox(
      width: 560,
      height: MediaQuery.sizeOf(context).height * 0.62,
      child: ListenableBuilder(
        listenable: store,
        builder: (context, _) {
          final debts = store.debts
              .where((debt) => debt.reminder != null)
              .toList(growable: false);
          if (debts.isEmpty) {
            return const Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.notifications_none, size: 48),
                  SizedBox(height: 12),
                  Text(
                    'Henüz hatırlatıcı yok',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                  ),
                  SizedBox(height: 6),
                  Text(
                    'Borç kartındaki Hatırlatıcı düğmesinden yeni bir plan ekleyebilirsiniz.',
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            );
          }
          return ListView.separated(
            itemCount: debts.length,
            separatorBuilder: (_, _) => const SizedBox(height: 10),
            itemBuilder: (context, index) {
              final debt = debts[index];
              final reminder = debt.reminder!;
              final colors = Theme.of(context).colorScheme;
              return Material(
                color: colors.surfaceContainerHighest.withValues(alpha: 0.55),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                  side: BorderSide(color: colors.outlineVariant),
                ),
                clipBehavior: Clip.antiAlias,
                child: ListTile(
                  contentPadding: const EdgeInsets.fromLTRB(14, 8, 8, 8),
                  leading: CircleAvatar(
                    backgroundColor: reminder.enabled
                        ? colors.primaryContainer
                        : colors.surfaceContainerHighest,
                    child: Icon(
                      reminder.enabled
                          ? Icons.notifications_active_outlined
                          : Icons.notifications_paused_outlined,
                      color: reminder.enabled
                          ? colors.onPrimaryContainer
                          : colors.onSurfaceVariant,
                    ),
                  ),
                  title: Text(
                    debt.name,
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                  subtitle: Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(
                      '${money(debt.cents)} · ${debt.income ? 'Alacak' : 'Verecek'}\n${reminder.summary}',
                    ),
                  ),
                  isThreeLine: true,
                  trailing: OutlinedButton(
                    onPressed: () => showDialog<void>(
                      context: context,
                      builder: (_) => ReminderEditor(debt: debt, store: store),
                    ),
                    child: const Text('Düzenle'),
                  ),
                ),
              );
            },
          );
        },
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Kapat'),
      ),
    ],
  );
}

class ReminderEditor extends StatefulWidget {
  final Debt debt;
  final LedgerStore store;
  const ReminderEditor({super.key, required this.debt, required this.store});
  @override
  State<ReminderEditor> createState() => _ReminderEditorState();
}

class _ReminderEditorState extends State<ReminderEditor> {
  late DateTime start = widget.debt.reminder == null
      ? DateTime.now().add(const Duration(minutes: 5))
      : parseDate(widget.debt.reminder!.startDate);
  late TimeOfDay time = widget.debt.reminder == null
      ? TimeOfDay.fromDateTime(start)
      : TimeOfDay(
          hour: widget.debt.reminder!.hour,
          minute: widget.debt.reminder!.minute,
        );
  late String repeat = widget.debt.reminder?.repeat ?? 'monthly';
  late DateTime? end = widget.debt.reminder?.endDate == null
      ? null
      : parseDate(widget.debt.reminder!.endDate!);
  late bool enabled = widget.debt.reminder?.enabled ?? true;
  late final interval = TextEditingController(
    text: '${widget.debt.reminder?.interval ?? 1}',
  );
  bool busy = false;
  String? error;
  Map<String, dynamic>? status;
  @override
  void initState() {
    super.initState();
    refresh();
  }

  @override
  void dispose() {
    interval.dispose();
    super.dispose();
  }

  Future<void> refresh() async {
    try {
      final s = await platform.invokeMapMethod<String, dynamic>(
        'reminderStatus',
      );
      if (mounted) setState(() => status = s);
    } catch (_) {
      if (mounted) {
        setState(() => error = 'Bildirim durumu okunamadı. Tekrar deneyin.');
      }
    }
  }

  Future<void> settings(String method) async {
    try {
      await platform.invokeMethod<void>(method);
    } catch (_) {
      if (mounted) {
        setState(
          () => error = 'Android ayarları açılamadı. Telefon ayarlarından izinleri kontrol edin.',
        );
      }
    }
  }

  Future<void> save({bool remove = false}) async {
    if (busy) return;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      Reminder? r;
      if (!remove) {
        r = Reminder.fromJson(
          Reminder(
            startDate: dateText(start),
            hour: time.hour,
            minute: time.minute,
            repeat: repeat,
            interval: int.tryParse(interval.text) ?? 0,
            endDate: end == null ? null : dateText(end!),
            enabled: enabled,
          ).toJson(),
        );
        final at = DateTime(
          start.year,
          start.month,
          start.day,
          time.hour,
          time.minute,
        );
        if (enabled && repeat == 'once' && !at.isAfter(DateTime.now())) {
          throw const FormatException(
            'Tek seferlik hatırlatma gelecekte olmalı.',
          );
        }
        if (enabled &&
            end != null &&
            DateTime(
              end!.year,
              end!.month,
              end!.day,
              23,
              59,
              59,
            ).isBefore(DateTime.now())) {
          throw const FormatException('Bitiş tarihi geçmişte olamaz.');
        }
        if (enabled) {
          final s = await platform.invokeMapMethod<String, dynamic>(
            'requestNotifications',
          );
          if (mounted) setState(() => status = s);
          if (s?['notifications'] != true) {
            throw const FormatException(
              'Bildirim izni verilmedi veya kanal kapalı. Hatırlatıcı kaydedilmedi; aşağıdan bildirim ayarlarını açabilirsiniz.',
            );
          }
        }
      }
      final current = widget.store.debts.firstWhere(
        (d) => d.id == widget.debt.id,
      );
      await widget.store.saveDebt(current.withReminder(r), editing: true);
      if (widget.store.reminderError != null) {
        throw FormatException(widget.store.reminderError!);
      }
      if (mounted) {
        notice(
          context,
          remove
              ? 'Hatırlatıcı kaldırıldı.'
              : enabled
              ? 'Hatırlatıcı kaydedildi.${status?['exact'] == true ? '' : ' Kesin alarm izni yok; Android bildirimi geciktirebilir.'}'
              : 'Hatırlatıcı duraklatıldı.',
        );
        Navigator.pop(context);
      }
    } catch (e) {
      if (mounted) setState(() => error = errorText(e));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: !busy,
    child: AlertDialog(
      title: const Text('Hatırlatıcı'),
      content: SizedBox(
        width: 480,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                '${widget.debt.name} · ${money(widget.debt.cents)}',
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Hatırlatıcı açık'),
                value: enabled,
                onChanged: busy ? null : (v) => setState(() => enabled = v),
              ),
              OutlinedButton.icon(
                onPressed: busy
                    ? null
                    : () async {
                        final d = await showDatePicker(
                          context: context,
                          initialDate: start,
                          firstDate: DateTime(1900),
                          lastDate: DateTime(2200),
                        );
                        if (d != null && mounted) setState(() => start = d);
                      },
                icon: const Icon(Icons.calendar_month),
                label: Text('Başlangıç: ${dateText(start)}'),
              ),
              OutlinedButton.icon(
                onPressed: busy
                    ? null
                    : () async {
                        final t = await showTimePicker(
                          context: context,
                          initialTime: time,
                          builder: (c, child) => MediaQuery(
                            data: MediaQuery.of(c)
                                .copyWith(alwaysUse24HourFormat: true),
                            child: child!,
                          ),
                        );
                        if (t != null && mounted) setState(() => time = t);
                      },
                icon: const Icon(Icons.schedule),
                label: Text(
                  'Saat: ${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}',
                ),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                initialValue: repeat,
                decoration: const InputDecoration(labelText: 'Tekrar'),
                items: const [
                  DropdownMenuItem(value: 'once', child: Text('Tek sefer')),
                  DropdownMenuItem(value: 'daily', child: Text('Günlük')),
                  DropdownMenuItem(value: 'weekly', child: Text('Haftalık')),
                  DropdownMenuItem(value: 'monthly', child: Text('Aylık')),
                ],
                onChanged: busy ? null : (v) => setState(() => repeat = v!),
              ),
              if (repeat != 'once') ...[
                const SizedBox(height: 12),
                TextField(
                  controller: interval,
                  enabled: !busy,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    labelText: 'Kaç aralıkta bir? (1–365)',
                  ),
                ),
                Text(
                  repeat == 'monthly'
                      ? 'Seçtiğiniz ay gününde tekrarlanır. O gün yoksa ayın son günü kullanılır.'
                      : 'Başlangıç tarihine göre seçilen aralıkta tekrarlanır.',
                ),
              ],
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Bitiş tarihi belirle'),
                value: end != null,
                onChanged: busy
                    ? null
                    : (v) => setState(
                        () => end = v
                            ? start.add(const Duration(days: 365))
                            : null,
                      ),
              ),
              if (end != null)
                OutlinedButton(
                  onPressed: busy
                      ? null
                      : () async {
                          final d = await showDatePicker(
                            context: context,
                            initialDate: end!,
                            firstDate: DateTime(1900),
                            lastDate: DateTime(2201),
                          );
                          if (d != null && mounted) setState(() => end = d);
                        },
                  child: Text('Bitiş: ${dateText(end!)}'),
                ),
              const SizedBox(height: 8),
              const Text(
                'Saatler telefonun yerel saatidir. Ödendi yapmak tekrarları kapatmaz; buradan duraklatın veya kaldırın. Borç silinince hatırlatıcı da iptal edilir.',
                style: TextStyle(fontSize: 12),
              ),
              const SizedBox(height: 12),
              Text(
                status == null
                    ? 'İzin durumu kontrol ediliyor…'
                    : 'Bildirim: ${status!['notifications'] == true ? 'açık' : 'kapalı'} · Kesin zamanlama: ${status!['exact'] == true ? 'açık' : 'kapalı (gecikebilir)'}',
              ),
              Wrap(
                spacing: 8,
                children: [
                  TextButton(
                    onPressed: busy
                        ? null
                        : () => settings('openNotificationSettings'),
                    child: const Text('Bildirim ayarları'),
                  ),
                  TextButton(
                    onPressed: busy
                        ? null
                        : () => settings('openAlarmSettings'),
                    child: const Text('Kesin alarm izni'),
                  ),
                  TextButton(
                    onPressed: busy ? null : refresh,
                    child: const Text('Durumu yenile'),
                  ),
                ],
              ),
              if (error != null)
                Text(
                  error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              if (busy) const LinearProgressIndicator(),
            ],
          ),
        ),
      ),
      actions: [
        if (widget.debt.reminder != null)
          TextButton(
            onPressed: busy ? null : () => save(remove: true),
            child: const Text('Kaldır'),
          ),
        TextButton(
          onPressed: busy ? null : () => Navigator.pop(context),
          child: const Text('İptal'),
        ),
        FilledButton(
          onPressed: busy ? null : save,
          child: const Text('Kaydet'),
        ),
      ],
    ),
  );
}
