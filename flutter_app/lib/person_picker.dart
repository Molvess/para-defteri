import 'package:flutter/material.dart';

import 'ledger.dart';
import 'person_avatar.dart';

class PersonPicker extends StatefulWidget {
  final List<Person> people;
  const PersonPicker({super.key, required this.people});
  @override
  State<PersonPicker> createState() => _PersonPickerState();
}

class _PersonPickerState extends State<PersonPicker> {
  String query = '';
  @override
  Widget build(BuildContext context) {
    final people =
        widget.people.where((p) => p.key.contains(personKey(query))).toList()
          ..sort((a, b) => a.key.compareTo(b.key));
    return AlertDialog(
      title: const Text('Kayıtlı kişi seç'),
      content: SizedBox(
        width: 450,
        height: MediaQuery.sizeOf(context).height * .45,
        child: Column(
          children: [
            TextField(
              autofocus: false,
              decoration: const InputDecoration(
                labelText: 'Kişi ara',
                prefixIcon: Icon(Icons.search),
              ),
              onChanged: (v) => setState(() => query = v),
            ),
            const SizedBox(height: 12),
            Expanded(
              child: people.isEmpty
                  ? const Center(
                      child: Text(
                        'Kayıtlı kişi bulunamadı. Yeni adı Kim? alanına yazabilirsiniz.',
                      ),
                    )
                  : ListView.builder(
                      itemCount: people.length,
                      itemBuilder: (c, i) => ListTile(
                        leading: PersonAvatar(
                          person: people[i],
                          name: people[i].name,
                        ),
                        title: Text(people[i].name),
                        onTap: () => Navigator.pop(context, people[i]),
                      ),
                    ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Vazgeç'),
        ),
      ],
    );
  }
}
