import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/paper_tile_button.dart';
import 'package:animal_locket/features/chat/data/system_glyphs.dart';
import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:animal_locket/features/chat/presentation/glyph_controllers.dart';
import 'package:animal_locket/features/chat/presentation/widgets/glyph_composer_panel.dart';
import 'package:animal_locket/features/chat/presentation/widgets/glyph_token.dart';
import 'package:animal_locket/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

class _FakeGlyphs extends ConversationGlyphsController {
  _FakeGlyphs(super.conversationId);
  @override
  Future<List<GlyphDefinition>> build() async => buildSystemGlyphs();
}

Future<void> _pump(
  WidgetTester tester, {
  bool enabled = true,
  double width = 390,
  double textScale = 1,
}) async {
  tester.view.physicalSize = Size(width, 900);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [conversationGlyphsProvider.overrideWith2(_FakeGlyphs.new)],
      child: MaterialApp(
        theme: AppTheme.forPalette(AlbumPalette.of(AlbumThemeId.shu)),
        locale: const Locale('vi'),
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: Scaffold(
          body: Align(
            alignment: Alignment.bottomCenter,
            child: GlyphComposerPanel(conversationId: 'c1', enabled: enabled),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

PaperButton _send(WidgetTester tester) =>
    tester.widget<PaperButton>(find.widgetWithText(PaperButton, 'Gửi ký hiệu'));

PaperTileButton _tile(WidgetTester tester, String label) => tester
    .widgetList<PaperTileButton>(find.byType(PaperTileButton))
    .firstWhere((t) => t.label == label);

void main() {
  testWidgets('quick pick adds a paper token and enables send', (tester) async {
    await _pump(tester);
    final first = buildSystemGlyphs().first;
    expect(find.byType(Icon), findsNothing);
    expect(_send(tester).onPressed, isNull);

    await tester.tap(find.byWidget(_tile(tester, first.meaning)));
    await tester.pumpAndSettle();
    expect(find.byType(GlyphToken), findsOneWidget);
    expect(_send(tester).onPressed, isNotNull);
  });

  testWidgets('closed conversation disables send, create and quick pick', (
    tester,
  ) async {
    await _pump(tester, enabled: false);
    final first = buildSystemGlyphs().first;
    expect(_send(tester).onPressed, isNull);
    expect(_tile(tester, 'Tạo ký hiệu').onPressed, isNull);
    expect(_tile(tester, first.meaning).onPressed, isNull);
  });

  testWidgets('fits a narrow phone at 1.5x text', (tester) async {
    await _pump(tester, width: 360, textScale: 1.5);
    expect(tester.takeException(), isNull);
  });
}
