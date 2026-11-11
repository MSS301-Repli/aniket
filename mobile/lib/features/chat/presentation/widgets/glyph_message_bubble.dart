import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/features/chat/domain/chat_repository.dart';
import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:animal_locket/features/chat/presentation/widgets/glyph_drawing_pad.dart';
import 'package:animal_locket/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';

class GlyphMessageBubble extends StatefulWidget {
  const GlyphMessageBubble({
    super.key,
    required this.message,
    required this.dictionary,
    required this.mine,
  });

  final ChatMessage message;
  final List<GlyphDefinition> dictionary;
  final bool mine;

  @override
  State<GlyphMessageBubble> createState() => _GlyphMessageBubbleState();
}

class _GlyphMessageBubbleState extends State<GlyphMessageBubble> {
  var _reveal = false;

  void _toggle() => setState(() => _reveal = !_reveal);

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final byId = {for (final glyph in widget.dictionary) glyph.id: glyph};
    final resolved = widget.message.glyphIds
        .map((id) => byId[id])
        .whereType<GlyphDefinition>()
        .toList(growable: false);
    if (resolved.length != widget.message.glyphIds.length) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(widget.message.text ?? l10n.glyphUnknownFallback),
          const SizedBox(height: 4),
          Text(
            l10n.glyphUnknownFallback,
            style: Theme.of(context).textTheme.labelSmall,
          ),
        ],
      );
    }
    return Column(
      crossAxisAlignment: widget.mine
          ? CrossAxisAlignment.end
          : CrossAxisAlignment.start,
      children: [
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: resolved
              .map((glyph) => GlyphIcon(glyph: glyph, size: 42))
              .toList(growable: false),
        ),
        if (_reveal) ...[
          const SizedBox(height: 6),
          // Nghĩa viết tay dưới các ký hiệu.
          Text(
            resolved.map((glyph) => glyph.meaning).join(' · '),
            style: TextStyle(
              fontFamily: AppFonts.hand,
              fontSize: 17,
              color: context.album.ink,
            ),
          ),
        ],
        Semantics(
          button: true,
          label: _reveal ? l10n.glyphHideMeaning : l10n.glyphShowMeaning,
          excludeSemantics: true,
          onTap: _toggle,
          child: IconButton(
            constraints: const BoxConstraints.tightFor(width: 48, height: 48),
            visualDensity: VisualDensity.compact,
            tooltip: _reveal ? l10n.glyphHideMeaning : l10n.glyphShowMeaning,
            onPressed: _toggle,
            icon: AlbumIcon(
              _reveal ? AlbumIcons.eyeOff : AlbumIcons.eye,
              size: 20,
              color: context.album.muted,
            ),
          ),
        ),
      ],
    );
  }
}
