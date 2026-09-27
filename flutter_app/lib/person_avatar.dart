import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import 'ledger.dart';

/// Decodes only when this person's photo changes, never on every scroll frame.
class PersonAvatar extends StatefulWidget {
  final Person? person;
  final String name;
  final double size;
  const PersonAvatar({
    super.key,
    this.person,
    required this.name,
    this.size = 48,
  });
  @override
  State<PersonAvatar> createState() => _PersonAvatarState();
}

class _PersonAvatarState extends State<PersonAvatar> {
  Uint8List? bytes;
  void decode() {
    try {
      final photo = widget.person?.photo ?? '';
      bytes = photo.isEmpty ? null : base64Decode(photo);
    } catch (_) {
      bytes = null;
    }
  }

  @override
  void initState() {
    super.initState();
    decode();
  }

  @override
  void didUpdateWidget(PersonAvatar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.person?.photo != widget.person?.photo) decode();
  }

  @override
  Widget build(BuildContext context) {
    final name = widget.name.trim();
    final fallback = Center(
      child: Text(
        widget.person?.avatar.isNotEmpty == true
            ? widget.person!.avatar
            : name.isEmpty
            ? '?'
            : name.characters.first,
        style: TextStyle(
          fontSize: widget.size * .38,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
    return Semantics(
      label: '$name kişi fotoğrafı',
      image: true,
      child: Container(
        width: widget.size,
        height: widget.size,
        decoration: BoxDecoration(
          color: const Color(0xff2b3845),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xff4b5b6b)),
        ),
        clipBehavior: Clip.antiAlias,
        child: bytes == null
            ? fallback
            : Image.memory(
                bytes!,
                fit: BoxFit.cover,
                gaplessPlayback: true,
                cacheWidth: 256,
                cacheHeight: 256,
                errorBuilder: (_, error, stack) => fallback,
              ),
      ),
    );
  }
}
