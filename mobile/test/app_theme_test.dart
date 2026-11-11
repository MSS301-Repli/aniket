import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  for (final id in AlbumThemeId.values) {
    group('AppTheme.forPalette(${id.name})', () {
      final palette = AlbumPalette.of(id);
      final theme = AppTheme.forPalette(palette);

      test('carries the palette as an extension', () {
        expect(theme.extension<AlbumPalette>(), palette);
      });

      test('leaves the scaffold transparent for AppBackground', () {
        expect(theme.scaffoldBackgroundColor, Colors.transparent);
      });

      test('uses the album fonts', () {
        expect(theme.textTheme.bodyMedium!.fontFamily, AppFonts.body);
        expect(theme.textTheme.titleMedium!.fontFamily, AppFonts.body);
        expect(theme.textTheme.headlineMedium!.fontFamily, AppFonts.title);
        expect(AppFonts.body, 'Be Vietnam Pro');
        expect(AppFonts.title, 'Lora');
        expect(AppFonts.hand, 'Patrick Hand');
      });

      test('maps the color scheme from palette tokens', () {
        expect(theme.colorScheme.primary, palette.accent);
        expect(theme.colorScheme.onPrimary, const Color(0xFFFFFFFF));
        expect(theme.colorScheme.surface, palette.paper);
        expect(theme.colorScheme.error, palette.danger);
      });

      testWidgets('filled buttons use the accent', (tester) async {
        await tester.pumpWidget(
          MaterialApp(
            theme: theme,
            home: Scaffold(
              body: FilledButton(
                onPressed: () {},
                child: const Text('Thử lại'),
              ),
            ),
          ),
        );
        final material = tester.widget<Material>(
          find.descendant(
            of: find.byType(FilledButton),
            matching: find.byType(Material),
          ),
        );
        expect(material.color, palette.accent);
      });
    });
  }

  test('AppTheme.light is the Shu theme', () {
    expect(
      AppTheme.light.extension<AlbumPalette>(),
      AlbumPalette.of(AlbumThemeId.shu),
    );
  });
}
