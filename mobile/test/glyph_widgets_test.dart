import 'package:animal_locket/features/chat/data/system_glyphs.dart';
import 'package:animal_locket/features/chat/domain/chat_repository.dart';
import 'package:animal_locket/features/chat/presentation/widgets/glyph_drawing_pad.dart';
import 'package:animal_locket/features/chat/presentation/widgets/glyph_message_bubble.dart';
import 'package:animal_locket/features/chat/presentation/widgets/glyph_token.dart';
import 'package:animal_locket/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets(
    'drawing pad exposes Vietnamese semantics and a 44px clear target',
    (tester) async {
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: GlyphDrawingPad(
              semanticsLabel: 'Khung vẽ ký hiệu một nét',
              hint: 'Vẽ một nét vào đây',
              clearLabel: 'Xóa nét vẽ',
              onStrokeComplete: (_) {},
            ),
          ),
        ),
      );

      expect(find.bySemanticsLabel('Khung vẽ ký hiệu một nét'), findsOneWidget);
      final button = find.byTooltip('Xóa nét vẽ');
      expect(button, findsOneWidget);
      expect(tester.getSize(button).width, greaterThanOrEqualTo(44));
      expect(tester.getSize(button).height, greaterThanOrEqualTo(44));
      semantics.dispose();
    },
  );

  testWidgets('glyph token keeps a minimum 44px tap target', (tester) async {
    final semantics = tester.ensureSemantics();
    final glyph = buildSystemGlyphs().first;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: GlyphToken(
            glyph: glyph,
            removeLabel: 'Xóa ký hiệu Đồng ý',
            onRemove: () {},
          ),
        ),
      ),
    );

    final remove = find.byTooltip('Xóa ký hiệu Đồng ý');
    expect(remove, findsOneWidget);
    expect(tester.getSize(remove).width, greaterThanOrEqualTo(44));
    expect(tester.getSize(remove).height, greaterThanOrEqualTo(44));
    semantics.dispose();
  });

  testWidgets('unknown glyph shows server fallback text', (tester) async {
    final message = ChatMessage(
      id: 'm-1',
      conversationId: 'c-1',
      senderId: 'u-1',
      type: ChatMessageType.glyph,
      text: 'Đồng ý',
      glyphIds: const ['missing-glyph'],
      createdAt: DateTime.utc(2026),
    );
    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('vi'),
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        home: Scaffold(
          body: GlyphMessageBubble(
            message: message,
            dictionary: const [],
            mine: false,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Đồng ý'), findsOneWidget);
    expect(find.textContaining('nội dung thay thế'), findsOneWidget);
  });

  testWidgets('eye button reveals the handwritten meaning', (tester) async {
    final glyphs = buildSystemGlyphs();
    final message = ChatMessage(
      id: 'm-2',
      conversationId: 'c-1',
      senderId: 'u-1',
      type: ChatMessageType.glyph,
      glyphIds: [glyphs.first.id],
      createdAt: DateTime.utc(2026),
    );
    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('vi'),
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        home: Scaffold(
          body: GlyphMessageBubble(
            message: message,
            dictionary: glyphs,
            mine: true,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(Icon), findsNothing);
    expect(find.text(glyphs.first.meaning), findsNothing);
    await tester.tap(find.byType(IconButton));
    await tester.pumpAndSettle();
    final meaning = tester.widget<Text>(find.text(glyphs.first.meaning));
    expect(meaning.style?.fontFamily, 'Patrick Hand');
    expect(meaning.style?.color, isNot(const Color(0xFFFFFFFF)));
  });
}
