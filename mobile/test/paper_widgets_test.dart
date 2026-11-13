import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/polaroid.dart';
import 'package:animal_locket/core/widgets/album/washi_photo_card.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

final _palette = AlbumPalette.of(AlbumThemeId.shu);

Widget _app(Widget child, {double textScale = 1}) => MaterialApp(
  theme: AppTheme.forPalette(_palette),
  home: MediaQuery(
    data: MediaQueryData(
      size: const Size(360, 720),
      textScaler: TextScaler.linear(textScale),
    ),
    child: Scaffold(
      body: Padding(padding: const EdgeInsets.all(20), child: child),
    ),
  ),
);

void main() {
  group('PaperButton', () {
    testWidgets('tape label shrink-wraps its text even with room to spare', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(
          SizedBox(
            width: 300,
            child: Wrap(
              children: [PaperButton.tape(label: 'Huỷ', onPressed: () {})],
            ),
          ),
        ),
      );
      expect(tester.getSize(find.byType(PaperButton)).width, lessThan(200));
    });

    testWidgets('shows its label and icon and reports taps', (tester) async {
      var taps = 0;
      await tester.pumpWidget(
        _app(
          PaperButton(
            label: 'Đăng nhập bằng email',
            icon: AlbumIcons.mail,
            onPressed: () => taps++,
          ),
        ),
      );
      expect(find.text('Đăng nhập bằng email'), findsOneWidget);
      expect(
        tester.widget<AlbumIcon>(find.byType(AlbumIcon)).icon,
        AlbumIcons.mail,
      );
      await tester.tap(find.byType(PaperButton));
      expect(taps, 1);
    });

    testWidgets('is a torn paper strip with a folded corner, ≥ 52pt tall', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(PaperButton(label: 'Tiếp tục', onPressed: () {})),
      );
      expect(
        find.descendant(
          of: find.byType(PaperButton),
          matching: find.byWidgetPredicate(
            (w) => w is ClipPath && w.clipper is TornPaperClipper,
          ),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: find.byType(PaperButton),
          matching: find.byWidgetPredicate(
            (w) => w is CustomPaint && w.painter is FoldedCornerPainter,
          ),
        ),
        findsOneWidget,
      );
      expect(
        tester.getSize(find.byType(PaperButton)).height,
        greaterThanOrEqualTo(52),
      );
    });

    testWidgets('disabled: ignores taps and says so to screen readers', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(const PaperButton(label: 'Tiếp tục', onPressed: null)),
      );
      await tester.tap(find.byType(PaperButton), warnIfMissed: false);
      expect(
        tester.getSemantics(find.byType(PaperButton)),
        isSemantics(
          isButton: true,
          hasEnabledState: true,
          isEnabled: false,
          label: 'Tiếp tục',
        ),
      );
    });

    testWidgets('tape variant is a plain paper label without washi', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(PaperButton.tape(label: 'Mở tài khoản demo', onPressed: () {})),
      );
      expect(
        find.descendant(
          of: find.byType(PaperButton),
          matching: find.byWidgetPredicate(
            (w) => w is CustomPaint && w.painter is WashiTapePainter,
          ),
        ),
        findsNothing,
      );
    });

    testWidgets('long label at 1.5x fits a narrow phone', (tester) async {
      await tester.pumpWidget(
        _app(
          PaperButton(
            label: 'Đăng nhập bằng email rất rất dài để thử xuống dòng',
            icon: AlbumIcons.mail,
            trailingIcon: AlbumIcons.arrowRight,
            onPressed: () {},
          ),
          textScale: 1.5,
        ),
      );
      expect(tester.takeException(), isNull);
    });
  });

  group('Polaroid', () {
    testWidgets('frames a square picture with a handwritten caption', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(
          const Center(
            child: Polaroid(
              width: 180,
              caption: 'Thỏ',
              child: ColoredBox(key: Key('pic'), color: Colors.pink),
            ),
          ),
        ),
      );
      final pic = tester.getSize(find.byKey(const Key('pic')));
      expect(pic.width, pic.height);
      final caption = tester.widget<Text>(find.text('Thỏ'));
      expect(caption.style?.fontFamily, AppFonts.hand);
      expect(
        find.byWidgetPredicate(
          (w) => w is CustomPaint && w.painter is WashiTapePainter,
        ),
        findsOneWidget,
      );
    });
  });
}
