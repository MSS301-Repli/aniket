import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/double_tap_like.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Future<void> _pump(
  WidgetTester tester, {
  required bool liked,
  bool enabled = true,
  required VoidCallback onLike,
}) => tester.pumpWidget(
  MaterialApp(
    theme: AppTheme.forPalette(AlbumPalette.of(AlbumThemeId.shu)),
    home: Scaffold(
      body: DoubleTapLike(
        liked: liked,
        enabled: enabled,
        onLike: onLike,
        child: const SizedBox(key: Key('photo'), width: 200, height: 200),
      ),
    ),
  ),
);

Future<void> _doubleTap(WidgetTester tester) async {
  await tester.tap(find.byKey(const Key('photo')));
  await tester.pump(const Duration(milliseconds: 60));
  await tester.tap(find.byKey(const Key('photo')));
  await tester.pump();
}

void main() {
  testWidgets('double tap likes a photo that is not liked yet', (tester) async {
    var likes = 0;
    await _pump(tester, liked: false, onLike: () => likes++);
    await _doubleTap(tester);
    expect(likes, 1);
    await tester.pumpAndSettle();
  });

  testWidgets('double tap never unlikes an already liked photo', (
    tester,
  ) async {
    var likes = 0;
    await _pump(tester, liked: true, onLike: () => likes++);
    await _doubleTap(tester);
    expect(likes, 0);
    await tester.pumpAndSettle();
  });

  testWidgets('a single tap does nothing', (tester) async {
    var likes = 0;
    await _pump(tester, liked: false, onLike: () => likes++);
    await tester.tap(find.byKey(const Key('photo')));
    await tester.pumpAndSettle();
    expect(likes, 0);
  });

  testWidgets('disabled: double tap is ignored', (tester) async {
    var likes = 0;
    await _pump(tester, liked: false, enabled: false, onLike: () => likes++);
    await _doubleTap(tester);
    expect(likes, 0);
  });
}
