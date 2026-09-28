import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app.dart';
import 'ledger.dart';
import 'store.dart';
import 'person_avatar.dart';
import 'access_consent.dart';

class TurkishNameFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    if (!newValue.composing.isCollapsed) return newValue;
    final text = capitalizeName(newValue.text);
    return newValue.copyWith(
      text: text,
      selection: TextSelection(
        baseOffset: newValue.selection.baseOffset.clamp(0, text.length),
        extentOffset: newValue.selection.extentOffset.clamp(0, text.length),
      ),
    );
  }
}

class DebtEditor extends StatefulWidget {
  final LedgerStore store;
  final Debt? debt;
  const DebtEditor({super.key, required this.store, this.debt});
  @override
  State<DebtEditor> createState() => _DebtEditorState();
}

class _DebtEditorState extends State<DebtEditor> {
  final form = GlobalKey<FormState>();
  late final name = TextEditingController(text: widget.debt?.name ?? '');
  late final amount = TextEditingController(
    text: widget.debt == null
        ? ''
        : (widget.debt!.cents / 100).toStringAsFixed(2).replaceAll('.', ','),
  );
  late final description = TextEditingController(
    text: widget.debt?.description ?? '',
  );
  late DateTime date = widget.debt == null
      ? DateTime.now()
      : parseDate(widget.debt!.date);
  late bool income = widget.debt?.income ?? true,
      paid = widget.debt?.paid ?? false;
  bool busy = false;
  String? error;
  @override
  void dispose() {
    name.dispose();
    amount.dispose();
    description.dispose();
    super.dispose();
  }

  Future<void> save() async {
    if (!form.currentState!.validate() || busy) return;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final d = Debt(
        id: widget.debt?.id ?? newId(),
        name: capitalizeName(name.text.trim()),
        cents: parseCents(amount.text),
        description: description.text.trim(),
        date: dateText(date),
        income: income,
        paid: paid,
        createdAt:
            widget.debt?.createdAt ?? DateTime.now().millisecondsSinceEpoch,
      );
      await widget.store.saveDebt(d, editing: widget.debt != null);
      if (mounted) {
        notice(
          context,
          widget.debt == null ? 'Borç eklendi.' : 'Borç güncellendi.',
        );
        Navigator.pop(context);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          error = errorText(e);
          busy = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: !busy,
    child: AlertDialog(
      title: Text(widget.debt == null ? 'Yeni borç' : 'Borcu düzenle'),
      content: SizedBox(
        width: 460,
        child: SingleChildScrollView(
          child: Form(
            key: form,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                ListenableBuilder(
                  listenable: Listenable.merge([name, widget.store]),
                  builder: (context, _) => Padding(
                    padding: const EdgeInsets.only(bottom: 16),
                    child: Row(
                      children: [
                        PersonAvatar(
                          person: widget.store.people[personKey(name.text)],
                          name: name.text,
                          size: 64,
                        ),
                        const SizedBox(width: 14),
                        const Expanded(
                          child: Text(
                            'Kişi fotoğrafı\nKayıtlı kişinin adını yazınca görünür.',
                            style: TextStyle(
                              fontSize: 12,
                              color: Colors.white60,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                TextFormField(
                  controller: name,
                  enabled: !busy,
                  inputFormatters: [TurkishNameFormatter()],
                  textCapitalization: TextCapitalization.words,
                  decoration: const InputDecoration(labelText: 'Kim?'),
                  validator: (v) => v == null || v.trim().isEmpty
                      ? 'Kişi adı zorunlu.'
                      : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: amount,
                  enabled: !busy,
                  keyboardType: const TextInputType.numberWithOptions(
                    decimal: true,
                  ),
                  decoration: const InputDecoration(
                    labelText: 'Tutar (₺)',
                    hintText: '1250,50',
                  ),
                  validator: (v) {
                    try {
                      parseCents(v ?? '');
                      return null;
                    } catch (e) {
                      return errorText(e);
                    }
                  },
                ),
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  onPressed: busy
                      ? null
                      : () async {
                          final picked = await showDatePicker(
                            context: context,
                            initialDate: date,
                            firstDate: DateTime(1900),
                            lastDate: DateTime(2200),
                          );
                          if (picked != null && mounted) {
                            setState(() {
                              date = picked;
                            });
                          }
                        },
                  icon: const Icon(Icons.calendar_month_outlined),
                  label: Text('Tarih: ${dateText(date)}'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: description,
                  enabled: !busy,
                  maxLines: 3,
                  decoration: const InputDecoration(
                    labelText: 'Açıklama (isteğe bağlı)',
                  ),
                ),
                const SizedBox(height: 12),
                SegmentedButton<bool>(
                  segments: const [
                    ButtonSegment(value: true, label: Text('Alacak')),
                    ButtonSegment(value: false, label: Text('Verecek')),
                  ],
                  selected: {income},
                  onSelectionChanged: busy
                      ? null
                      : (v) => setState(() {
                          income = v.first;
                        }),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(paid ? 'Ödendi' : 'Ödenecek'),
                  subtitle: const Text('Ödeme durumu'),
                  value: paid,
                  onChanged: busy
                      ? null
                      : (v) => setState(() {
                          paid = v;
                        }),
                ),
                if (error != null)
                  Text(error!, style: const TextStyle(color: red)),
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: busy ? null : () => Navigator.pop(context),
          child: const Text('İptal'),
        ),
        FilledButton(
          onPressed: busy ? null : save,
          child: Text(busy ? 'Kaydediliyor…' : 'Kaydet'),
        ),
      ],
    ),
  );
}

Future<void> copyIban(BuildContext context, String iban) async {
  try {
    await Clipboard.setData(ClipboardData(text: iban));
    if (context.mounted) notice(context, 'IBAN panoya kopyalandı.');
  } catch (_) {
    if (context.mounted) {
      await showDialog<void>(
        context: context,
        builder: (c) => AlertDialog(
          title: const Text('Kopyalama başarısız'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'IBAN’ı basılı tutarak elle seçip kopyalayabilirsiniz.',
              ),
              SelectableText(iban),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(c),
              child: const Text('Kapat'),
            ),
          ],
        ),
      );
    }
  }
}

class PeopleDialog extends StatefulWidget {
  final LedgerStore store;
  final String? selectedKey;
  const PeopleDialog({super.key, required this.store, this.selectedKey});
  @override
  State<PeopleDialog> createState() => _PeopleDialogState();
}

class _PeopleDialogState extends State<PeopleDialog> {
  late String? selected = widget.selectedKey;
  bool busy = false;
  String? error;
  final revealed = <String>{};
  Future<void> choosePhoto(Person p) async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      if (!await confirmFileAccess(context, FileAccess.photo) || !mounted) {
        return;
      }
      final photo = await platform.invokeMethod<String>('pickPhoto');
      if (photo == null || !mounted) return;
      final current = widget.store.people[p.key] ?? p;
      await widget.store.savePerson(current.withPhoto(photo));
      if (mounted) notice(context, 'Kişi fotoğrafı kaydedildi.');
    } catch (e) {
      if (mounted && isPermissionError(e)) {
        await showPermissionHelp(context, e as PlatformException);
      }
      if (mounted) {
        setState(() {
          error = errorText(e);
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          busy = false;
        });
      }
    }
  }

  Future<void> removePhoto(Person p) async {
    final yes = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Kişi fotoğrafı kaldırılsın mı?'),
        content: const Text('Galerideki asıl fotoğraf silinmez.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(c, false),
            child: const Text('Vazgeç'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(c, true),
            child: const Text('Kaldır'),
          ),
        ],
      ),
    );
    if (yes == true && mounted) {
      try {
        await write((widget.store.people[p.key] ?? p).withPhoto(''));
      } catch (_) {}
    }
  }

  Future<void> write(Person p) async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      await widget.store.savePerson(p);
    } catch (e) {
      if (mounted) {
        setState(() {
          error = errorText(e);
        });
      }
      rethrow;
    } finally {
      if (mounted) {
        setState(() {
          busy = false;
        });
      }
    }
  }

  Future<void> addPerson() async {
    final name = await showDialog<String>(
      context: context,
      builder: (_) => const NameDialog(),
    );
    if (name == null || !mounted) return;
    final key = personKey(name);
    if (!widget.store.people.containsKey(key)) {
      try {
        await write(Person(capitalizeName(name.trim()), const []));
      } catch (_) {
        return;
      }
    }
    if (mounted) {
      setState(() {
        selected = key;
      });
    }
  }

  Future<void> editAccount(Person p, [BankAccount? old]) => showDialog<void>(
    context: context,
    builder: (_) => IbanEditor(person: p, old: old, store: widget.store),
  );
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.store,
    builder: (context, _) {
      final people = widget.store.people.values.toList()
        ..sort((a, b) => a.key.compareTo(b.key));
      final p = widget.store.people[selected];
      return AlertDialog(
        scrollable: true,
        title: Text(p?.name ?? 'Kişiler ve IBAN'),
        content: SizedBox(
          width: 500,
          height: p == null ? MediaQuery.sizeOf(context).height * .58 : null,
          child: p == null
              ? Column(
                  children: [
                    const Text(
                      'Kişiler borçlardan oluşur. İsterseniz borç eklemeden kişi ve IBAN kaydedebilirsiniz.',
                    ),
                    const SizedBox(height: 12),
                    FilledButton.icon(
                      onPressed: addPerson,
                      icon: const Icon(Icons.person_add_alt),
                      label: const Text('Kişi ekle'),
                    ),
                    if (error != null)
                      Text(error!, style: const TextStyle(color: red)),
                    Expanded(
                      child: people.isEmpty
                          ? const Center(child: Text('Henüz kişi yok.'))
                          : ListView.builder(
                              itemCount: people.length,
                              itemBuilder: (c, i) => ListTile(
                                leading: PersonAvatar(
                                  person: people[i],
                                  name: people[i].name,
                                ),
                                title: Text(people[i].name),
                                subtitle: Text(
                                  '${people[i].accounts.length} IBAN',
                                ),
                                trailing: const Icon(Icons.chevron_right),
                                onTap: () => setState(() {
                                  selected = people[i].key;
                                }),
                              ),
                            ),
                    ),
                  ],
                )
              : Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (widget.selectedKey == null)
                      TextButton(
                        onPressed: busy
                            ? null
                            : () => setState(() {
                                selected = null;
                              }),
                        child: const Text('Tüm kişiler'),
                      ),
                    Row(
                      children: [
                        PersonAvatar(person: p, name: p.name, size: 64),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Wrap(
                            spacing: 8,
                            runSpacing: 6,
                            children: [
                              OutlinedButton.icon(
                                onPressed: busy ? null : () => choosePhoto(p),
                                icon: const Icon(
                                  Icons.add_photo_alternate_outlined,
                                  size: 18,
                                ),
                                label: Text(
                                  p.photo.isEmpty
                                      ? 'Fotoğraf ekle'
                                      : 'Fotoğrafı değiştir',
                                ),
                              ),
                              if (p.photo.isNotEmpty)
                                OutlinedButton(
                                  onPressed: busy ? null : () => removePhoto(p),
                                  child: const Text('Fotoğrafı kaldır'),
                                ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    if (busy) const LinearProgressIndicator(),
                    if (error != null)
                      Text(error!, style: const TextStyle(color: red)),
                    SizedBox(
                      child: p.accounts.isEmpty
                          ? const Padding(
                              padding: EdgeInsets.symmetric(vertical: 16),
                              child: Text('Bu kişiye kayıtlı IBAN yok.'),
                            )
                          : Column(
                              mainAxisSize: MainAxisSize.min,
                              children: List.generate(p.accounts.length, (i) {
                                final account = p.accounts[i];
                                return Card(
                                  margin: const EdgeInsets.only(bottom: 12),
                                  child: Padding(
                                    padding: const EdgeInsets.all(12),
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          account.label.isEmpty
                                              ? 'IBAN ${i + 1}'
                                              : account.label,
                                          style: const TextStyle(
                                            fontWeight: FontWeight.bold,
                                          ),
                                        ),
                                        const SizedBox(height: 8),
                                        SelectableText(
                                          revealed.contains(account.iban)
                                              ? account.iban
                                              : maskedIban(account.iban),
                                        ),
                                        Wrap(
                                          children: [
                                            TextButton(
                                              onPressed: () => copyIban(
                                                context,
                                                account.iban,
                                              ),
                                              child: const Text(
                                                'IBAN’ı kopyala',
                                              ),
                                            ),
                                            TextButton(
                                              onPressed: () => setState(() {
                                                if (!revealed.add(
                                                  account.iban,
                                                )) {
                                                  revealed.remove(account.iban);
                                                }
                                              }),
                                              child: Text(
                                                revealed.contains(account.iban)
                                                    ? 'Gizle'
                                                    : 'Göster',
                                              ),
                                            ),
                                            TextButton(
                                              onPressed: busy
                                                  ? null
                                                  : () =>
                                                        editAccount(p, account),
                                              child: const Text('Düzenle'),
                                            ),
                                            IconButton(
                                              tooltip: 'IBAN sil',
                                              onPressed: busy
                                                  ? null
                                                  : () async {
                                                      final yes = await showDialog<bool>(
                                                        context: context,
                                                        builder: (c) => AlertDialog(
                                                          title: const Text(
                                                            'IBAN silinsin mi?',
                                                          ),
                                                          content: Text(
                                                            maskedIban(
                                                              account.iban,
                                                            ),
                                                          ),
                                                          actions: [
                                                            TextButton(
                                                              onPressed: () =>
                                                                  Navigator.pop(
                                                                    c,
                                                                    false,
                                                                  ),
                                                              child: const Text(
                                                                'Vazgeç',
                                                              ),
                                                            ),
                                                            FilledButton(
                                                              onPressed: () =>
                                                                  Navigator.pop(
                                                                    c,
                                                                    true,
                                                                  ),
                                                              child: const Text(
                                                                'Sil',
                                                              ),
                                                            ),
                                                          ],
                                                        ),
                                                      );
                                                      if (yes == true &&
                                                          mounted) {
                                                        try {
                                                          await write(
                                                            Person(
                                                              p.name,
                                                              p.accounts
                                                                  .where(
                                                                    (a) =>
                                                                        a.iban !=
                                                                        account
                                                                            .iban,
                                                                  )
                                                                  .toList(),
                                                              avatar: p.avatar,
                                                              photo: p.photo,
                                                            ),
                                                          );
                                                        } catch (_) {}
                                                      }
                                                    },
                                              icon: const Icon(
                                                Icons.delete_outline,
                                                color: red,
                                              ),
                                            ),
                                          ],
                                        ),
                                      ],
                                    ),
                                  ),
                                );
                              }),
                            ),
                    ),
                    FilledButton.icon(
                      onPressed: busy ? null : () => editAccount(p),
                      icon: const Icon(Icons.add),
                      label: const Text('IBAN ekle'),
                    ),
                  ],
                ),
        ),
        actions: [
          TextButton(
            onPressed: busy ? null : () => Navigator.pop(context),
            child: const Text('Kapat'),
          ),
        ],
      );
    },
  );
}

class NameDialog extends StatefulWidget {
  const NameDialog({super.key});
  @override
  State<NameDialog> createState() => _NameDialogState();
}

class _NameDialogState extends State<NameDialog> {
  final controller = TextEditingController();
  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Kişi ekle'),
    content: TextField(
      controller: controller,
      autofocus: true,
      inputFormatters: [TurkishNameFormatter()],
      decoration: const InputDecoration(labelText: 'Ad soyad'),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('İptal'),
      ),
      FilledButton(
        onPressed: () {
          if (controller.text.trim().isNotEmpty) {
            Navigator.pop(context, controller.text);
          }
        },
        child: const Text('Ekle'),
      ),
    ],
  );
}

class IbanEditor extends StatefulWidget {
  final Person person;
  final BankAccount? old;
  final LedgerStore store;
  const IbanEditor({
    super.key,
    required this.person,
    this.old,
    required this.store,
  });
  @override
  State<IbanEditor> createState() => _IbanEditorState();
}

class _IbanEditorState extends State<IbanEditor> {
  late final iban = TextEditingController(text: widget.old?.iban ?? '');
  late final label = TextEditingController(text: widget.old?.label ?? '');
  bool busy = false;
  String? error;
  @override
  void dispose() {
    iban.dispose();
    label.dispose();
    super.dispose();
  }

  Future<void> save() async {
    final value = normalizeIban(iban.text);
    if (!validIban(value)) {
      setState(() {
        error = 'IBAN biçimi veya kontrol basamakları geçersiz.';
      });
      return;
    }
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final p = widget.store.people[widget.person.key] ?? widget.person;
      final accounts = p.accounts
          .where((a) => a.iban != widget.old?.iban)
          .toList();
      if (accounts.any((a) => a.iban == value)) {
        throw const FormatException('Bu IBAN zaten kayıtlı.');
      }
      accounts.add(BankAccount(value, label.text.trim()));
      await widget.store.savePerson(
        Person(p.name, accounts, avatar: p.avatar, photo: p.photo),
      );
      if (mounted) {
        notice(context, 'IBAN kaydedildi.');
        Navigator.pop(context);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          error = errorText(e);
          busy = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: !busy,
    child: AlertDialog(
      title: Text(widget.old == null ? 'IBAN ekle' : 'IBAN düzenle'),
      content: SizedBox(
        width: 460,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: iban,
                enabled: !busy,
                autocorrect: false,
                textCapitalization: TextCapitalization.characters,
                decoration: const InputDecoration(
                  labelText: 'IBAN',
                  hintText: 'TR33 0006 …',
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: label,
                enabled: !busy,
                decoration: const InputDecoration(
                  labelText: 'Banka / açıklama (isteğe bağlı)',
                ),
              ),
              if (error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 12),
                  child: Text(error!, style: const TextStyle(color: red)),
                ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: busy ? null : () => Navigator.pop(context),
          child: const Text('İptal'),
        ),
        FilledButton(
          onPressed: busy ? null : save,
          child: Text(busy ? 'Kaydediliyor…' : 'Kaydet'),
        ),
      ],
    ),
  );
}

class KeepDialog extends StatefulWidget {
  const KeepDialog({super.key});
  @override
  State<KeepDialog> createState() => _KeepDialogState();
}

class _KeepDialogState extends State<KeepDialog> {
  final text = TextEditingController();
  @override
  void dispose() {
    text.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Keep listesini yapıştır'),
    content: SizedBox(
      width: 520,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Her satır: Ad - Tutar TL - Açıklama Tarih +/-\n+ Alacak, − Verecek. Durum yoksa Ödenecek.',
            ),
            const SizedBox(height: 12),
            const SelectableText(
              'Mustafa - 200 TL - yemeks 05/07/2026 +\nRifat - 1150 TL - Telefon 09/03/2026 -\nYasin - 40 TL - içecek 20/07/2026 +',
              style: TextStyle(fontSize: 12, color: Colors.white60),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: text,
              minLines: 5,
              maxLines: 12,
              decoration: const InputDecoration(labelText: 'Keep metni'),
            ),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('İptal'),
      ),
      FilledButton(
        onPressed: () => Navigator.pop(context, text.text),
        child: const Text('Kontrol et'),
      ),
    ],
  );
}
