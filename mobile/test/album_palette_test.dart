import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('AlbumPalette.of', () {
    test('Shu uses the spec values', () {
      final p = AlbumPalette.of(AlbumThemeId.shu);
      expect(p.background, const Color(0xFFFFF8EF));
      expect(p.accent, const Color(0xFFB8402A));
    });

    test('each theme has its own accent', () {
      expect(
        AlbumPalette.of(AlbumThemeId.yuzu).accent,
        const Color(0xFFA35C00),
      );
      expect(
        AlbumPalette.of(AlbumThemeId.sakura).accent,
        const Color(0xFFB23A5B),
      );
      expect(
        AlbumPalette.of(AlbumThemeId.sora).accent,
        const Color(0xFF2F5F8A),
      );
    });
  });

  group('contrastRatio', () {
    test('black on white is 21:1', () {
      expect(
        contrastRatio(const Color(0xFF000000), const Color(0xFFFFFFFF)),
        closeTo(21.0, 0.01),
      );
    });

    test('a color against itself is 1:1', () {
      const c = Color(0xFFB8402A);
      expect(contrastRatio(c, c), closeTo(1.0, 0.0001));
    });
  });

  for (final id in AlbumThemeId.values) {
    test('${id.name} text pairs meet WCAG AA (4.5:1)', () {
      final p = AlbumPalette.of(id);
      final pairs = <String, (Color, Color)>{
        'ink/background': (p.ink, p.background),
        'ink/paper': (p.ink, p.paper),
        'muted/background': (p.muted, p.background),
        'muted/paper': (p.muted, p.paper),
        'accent/paper': (p.accent, p.paper),
        'accent/accentSoft': (p.accent, p.accentSoft),
        'danger/paper': (p.danger, p.paper),
        'white/accent': (const Color(0xFFFFFFFF), p.accent),
      };
      for (final entry in pairs.entries) {
        final (fg, bg) = entry.value;
        expect(
          contrastRatio(fg, bg),
          greaterThanOrEqualTo(4.5),
          reason: '${id.name} ${entry.key}',
        );
      }
    });
  }
}
