import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:animal_locket/features/chat/presentation/widgets/glyph_drawing_pad.dart';
import 'package:flutter/material.dart';

/// Ký hiệu đã chọn: mẩu giấy nhỏ chứa glyph và nút × (spec §7.6).
class GlyphToken extends StatelessWidget {
  const GlyphToken({
    super.key,
    required this.glyph,
    required this.removeLabel,
    this.onRemove,
  });

  final GlyphDefinition glyph;
  final String removeLabel;
  final VoidCallback? onRemove;

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    return Semantics(
      label: glyph.meaning,
      child: Container(
        constraints: const BoxConstraints(minHeight: 48),
        decoration: BoxDecoration(
          color: p.paper,
          borderRadius: BorderRadius.circular(3),
          border: Border.all(color: p.fold1),
          boxShadow: const [
            BoxShadow(
              color: Color(0x1F5A3C1E),
              blurRadius: 5,
              offset: Offset(0, 2),
            ),
          ],
        ),
        padding: const EdgeInsets.only(left: 8),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            GlyphIcon(glyph: glyph, size: 30, strokeWidth: 3.5),
            if (onRemove != null)
              Semantics(
                button: true,
                label: removeLabel,
                excludeSemantics: true,
                onTap: onRemove,
                child: IconButton(
                  constraints: const BoxConstraints.tightFor(
                    width: 48,
                    height: 48,
                  ),
                  tooltip: removeLabel,
                  onPressed: onRemove,
                  icon: AlbumIcon(AlbumIcons.close, size: 18, color: p.muted),
                ),
              )
            else
              const SizedBox(width: 8),
          ],
        ),
      ),
    );
  }
}
