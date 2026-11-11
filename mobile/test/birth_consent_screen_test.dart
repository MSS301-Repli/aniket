import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/hanko_stamp.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/features/auth/presentation/birth_consent_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

const _terms = 'Tôi đồng ý Điều khoản sử dụng và Chính sách riêng tư.';

Future<GoRouter> _pump(
  WidgetTester tester, {
  double width = 390,
  double height = 844,
  double textScale = 1,
}) async {
  tester.view.physicalSize = Size(width, height);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final router = GoRouter(
    routes: [
      GoRoute(path: '/', builder: (_, _) => const BirthConsentScreen()),
      GoRoute(
        path: '/reveal',
        builder: (_, state) => Text('reveal ${state.uri.query}'),
      ),
    ],
  );
  await tester.pumpWidget(
    MaterialApp.router(
      theme: AppTheme.forPalette(AlbumPalette.of(AlbumThemeId.shu)),
      routerConfig: router,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context)
            .copyWith(textScaler: TextScaler.linear(textScale)),
        child: child!,
      ),
    ),
  );
  await tester.pumpAndSettle();
  return router;
}

PaperButton _continue(WidgetTester tester) =>
    tester.widget<PaperButton>(find.byType(PaperButton));

Future<void> _pickDefaultDate(WidgetTester tester) async {
  await tester.tap(find.text('Chạm để chọn ngày sinh'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('OK'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('taped slip keeps the copy and adds the tap hint', (
    tester,
  ) async {
    await _pump(tester);
    for (final text in [
      'Thiết lập an toàn',
      'NGÀY SINH',
      'Mỗi độ tuổi có một không gian phù hợp.',
      'Ngày sinh chỉ dùng để xác định nhóm tuổi. Thông tin này không xuất hiện trên hồ sơ.',
      'Chạm để chọn ngày sinh',
      _terms,
      'Tiếp tục',
    ]) {
      expect(find.text(text), findsOneWidget, reason: text);
    }
    expect(find.byType(Icon), findsNothing);
    expect(find.byType(CheckboxListTile), findsNothing);
  });

  testWidgets('three handwritten boxes fill in after picking a date', (
    tester,
  ) async {
    await _pump(tester);
    expect(find.text('--'), findsNWidgets(2));
    expect(find.text('----'), findsOneWidget);
    expect(
      tester.getSemantics(find.byKey(const Key('birth-date'))),
      isSemantics(isButton: true, label: 'Chọn ngày sinh'),
    );
    await _pickDefaultDate(tester);
    final year = DateTime.now().year - 18;
    expect(find.text('01'), findsNWidgets(2));
    expect(find.text('$year'), findsOneWidget);
    expect(
      tester.getSemantics(find.byKey(const Key('birth-date'))),
      isSemantics(isButton: true, label: '01/01/$year'),
    );
  });

  testWidgets('consent is a check-mark stamp named by the terms', (
    tester,
  ) async {
    await _pump(tester);
    final stamp = find.byType(HankoStamp);
    expect(tester.widget<HankoStamp>(stamp).mark, AlbumIcons.check);
    expect(tester.widget<HankoStamp>(stamp).stamped, isFalse);
    expect(
      tester.getSemantics(stamp),
      isSemantics(isButton: true, hasToggledState: true, label: _terms),
    );
    // The sentence is part of the tap target too.
    await tester.tap(find.text(_terms));
    await tester.pumpAndSettle();
    expect(tester.widget<HankoStamp>(stamp).stamped, isTrue);
  });

  testWidgets('continue waits for both, then goes to reveal with the date', (
    tester,
  ) async {
    final router = await _pump(tester);
    expect(_continue(tester).onPressed, isNull);
    await _pickDefaultDate(tester);
    expect(_continue(tester).onPressed, isNull);
    await tester.tap(find.byType(HankoStamp));
    await tester.pumpAndSettle();
    expect(_continue(tester).onPressed, isNotNull);
    await tester.tap(find.byType(PaperButton));
    await tester.pumpAndSettle();
    expect(router.state.uri.path, '/reveal');
    expect(
      router.state.uri.queryParameters['birthDate'],
      '${DateTime.now().year - 18}-01-01',
    );
  });

  testWidgets('screen readers find no unnamed tap target', (tester) async {
    final handle = tester.ensureSemantics();
    await _pump(tester);
    expect(
      find.semantics.byPredicate((node) {
        final d = node.getSemanticsData();
        return d.hasAction(SemanticsAction.tap) &&
            d.label.isEmpty &&
            d.tooltip.isEmpty;
      }),
      findsNothing,
    );
    handle.dispose();
  });

  testWidgets('fits a narrow phone at 1.5x text', (tester) async {
    await _pump(tester, width: 360, height: 640, textScale: 1.5);
    expect(tester.takeException(), isNull);
  });
}
