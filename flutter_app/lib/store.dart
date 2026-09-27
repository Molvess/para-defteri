import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:sqflite/sqflite.dart';

import 'ledger.dart';

class LedgerStore extends ChangeNotifier {
  final Database db;
  List<Debt> debts = const [];
  Map<String, Person> people = const {};
  LedgerStore(this.db);
  static Future<LedgerStore> open({
    String? path,
    DatabaseFactory? factory,
  }) async {
    final f = factory ?? databaseFactory;
    final location =
        path ?? '${await f.getDatabasesPath()}/para_defteri_flutter.db';
    final db = await f.openDatabase(
      location,
      options: OpenDatabaseOptions(
        version: 1,
        onCreate: (db, version) async {
          await db.execute(
            'CREATE TABLE debts (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)',
          );
          await db.execute(
            'CREATE TABLE people (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)',
          );
        },
      ),
    );
    final store = LedgerStore(db);
    await store.reload();
    return store;
  }

  Future<void> reload() async {
    final ds = (await db.query('debts'))
        .map((r) => Debt.fromJson(jsonDecode(r['payload'] as String)))
        .toList();
    ds.sort((a, b) {
      final time = b.createdAt.compareTo(a.createdAt);
      return time != 0 ? time : b.id.compareTo(a.id);
    });
    final ps = (await db.query('people'))
        .map((r) => Person.fromJson(jsonDecode(r['payload'] as String)));
    debts = List.unmodifiable(ds);
    people = Map.unmodifiable({for (final p in ps) p.key: p});
    notifyListeners();
  }

  Future<void> saveDebt(Debt debt, {bool editing = false}) async {
    Debt.fromJson(debt.toJson());
    await db.transaction((tx) async {
      final value = {'id': debt.id, 'payload': jsonEncode(debt.toJson())};
      if (editing) {
        if (await tx.update(
              'debts',
              value,
              where: 'id = ?',
              whereArgs: [debt.id],
            ) !=
            1) {
          throw StateError('Borç artık mevcut değil.');
        }
      } else {
        await tx.insert('debts', value);
      }
      await tx.insert('people', {
        'id': debt.key,
        'payload': jsonEncode(Person(debt.name, const []).toJson()),
      }, conflictAlgorithm: ConflictAlgorithm.ignore);
    });
    await reload();
  }

  Future<void> deleteDebt(Debt debt) async {
    await db.delete('debts', where: 'id = ?', whereArgs: [debt.id]);
    await reload();
  }

  Future<void> savePerson(Person person) async {
    Person.fromJson(person.toJson());
    if (person.key.isEmpty) throw const FormatException('Kişi adı zorunlu.');
    if (person.accounts.map((a) => a.iban).toSet().length !=
        person.accounts.length) {
      throw const FormatException('Bu IBAN zaten kayıtlı.');
    }
    await db.insert('people', {
      'id': person.key,
      'payload': jsonEncode(person.toJson()),
    }, conflictAlgorithm: ConflictAlgorithm.replace);
    await reload();
  }

  Future<int> importBatch(ImportBatch batch) async {
    if (batch.errors.isNotEmpty) {
      throw const FormatException(
        'Hatalı satırları düzeltin; hiçbir kayıt aktarılmadı.',
      );
    }
    var count = 0;
    String fingerprint(Debt d) =>
        '${d.key}|${d.cents}|${d.date}|${d.description}|${d.income}|${d.paid}|${d.createdAt}';
    await db.transaction((tx) async {
      final current = (await tx.query('debts'))
          .map((r) => Debt.fromJson(jsonDecode(r['payload'] as String)))
          .toList();
      final ids = current.map((d) => d.id).toSet(),
          fingerprints = current.map(fingerprint).toSet();
      for (final d in batch.debts) {
        Debt.fromJson(d.toJson());
        if (ids.contains(d.id) || fingerprints.contains(fingerprint(d))) {
          continue;
        }
        await tx.insert('debts', {
          'id': d.id,
          'payload': jsonEncode(d.toJson()),
        });
        await tx.insert('people', {
          'id': d.key,
          'payload': jsonEncode(Person(d.name, const []).toJson()),
        }, conflictAlgorithm: ConflictAlgorithm.ignore);
        ids.add(d.id);
        fingerprints.add(fingerprint(d));
        count++;
      }
      for (final p in batch.people) {
        Person.fromJson(p.toJson());
        final rows = await tx.query(
          'people',
          where: 'id = ?',
          whereArgs: [p.key],
        );
        final old = rows.isEmpty
            ? Person(p.name, const [])
            : Person.fromJson(jsonDecode(rows.first['payload'] as String));
        final accounts = {
          for (final a in p.accounts) a.iban: a,
          for (final a in old.accounts) a.iban: a,
        };
        final merged = Person(
          old.name,
          accounts.values.toList(),
          avatar: old.avatar.isEmpty ? p.avatar : old.avatar,
        );
        await tx.insert('people', {
          'id': p.key,
          'payload': jsonEncode(merged.toJson()),
        }, conflictAlgorithm: ConflictAlgorithm.replace);
      }
    });
    await reload();
    return count;
  }
}
