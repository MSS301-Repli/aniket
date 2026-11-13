import 'package:animal_locket/core/config/backend_mode.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/theme/theme_controller.dart';
import 'package:animal_locket/features/profile/presentation/settings_screen.dart';
import 'package:animal_locket/features/profile/presentation/theme_picker.dart';
import 'package:animal_locket/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

const _names = [
  'Shu · Son đỏ',
  'Yuzu · Vàng thanh yên',
  'Sakura · Hồng anh đào',
  'Sora · Trời xanh',
];

/// App tối giản giống AnimalLocketApp: ThemeData đi theo theme đang chọn.
class _Host extends ConsumerWidget {
  const _Host(this.home);
  final Widget home;

  @override
  Widget build(BuildContext context, WidgetRef ref) => MaterialApp(
    theme: AppTheme.forPalette(AlbumPalette.of(ref.watch(albumThemeProvider))),
    locale: const Locale('vi'),
    supportedLocales: AppLocalizations.supportedLocales,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    home: home,
  );
}

Future<(ProviderContainer, MemoryThemeStore)> _pump(
  WidgetTester tester,
  Widget home,
) async {
  tester.view.physicalSize = const Size(400, 1600);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final store = MemoryThemeStore();
  final container = ProviderContainer(
    overrides: [themeStoreProvider.overrideWithValue(store)],
  );
  addTearDown(container.dispose);
  container.read(backendModeProvider.notifier).useDemo();
  await tester.pumpWidget(
    UncontrolledProviderScope(container: container, child: _Host(home)),
  );
  await tester.pump(const Duration(milliseconds: 500));
  return (container, store);
}

void main() {
  testWidgets('lists the four themes under "Màu giao diện"', (tester) async {
    await _pump(tester, const Scaffold(body: ThemePicker()));
    expect(find.text('MÀU GIAO DIỆN'), findsOneWidget);
    for (final name in _names) {
      expect(find.text(name), findsOneWidget, reason: name);
    }
  });

  testWidgets('Shu is selected by default', (tester) async {
    await _pump(tester, const Scaffold(body: ThemePicker()));
    for (final name in _names) {
      expect(
        tester.getSemantics(find.text(name)),
        isSemantics(isButton: true, isSelected: name == _names.first),
        reason: name,
      );
    }
  });

  testWidgets('choosing Sakura applies and saves it', (tester) async {
    final (container, store) = await _pump(
      tester,
      const Scaffold(body: ThemePicker()),
    );
    await tester.tap(find.text('Sakura · Hồng anh đào'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(container.read(albumThemeProvider), AlbumThemeId.sakura);
    expect(await store.read(), AlbumThemeId.sakura);
    final context = tester.element(find.text('Sakura · Hồng anh đào'));
    expect(
      Theme.of(context).extension<AlbumPalette>()!.accent,
      AlbumPalette.of(AlbumThemeId.sakura).accent,
    );
  });

  testWidgets('settings shows the picker above the discovery switch', (
    tester,
  ) async {
    await _pump(tester, const SettingsScreen());
    expect(find.byType(ThemePicker), findsOneWidget);
    expect(
      tester.getTopLeft(find.byType(ThemePicker)).dy,
      lessThan(tester.getTopLeft(find.text('Cho phép khám phá')).dy),
    );
  });

  testWidgets('theme names are never cut off on a 400pt phone', (tester) async {
    await _pump(tester, const SettingsScreen());
    for (final name in _names) {
      final paragraph = tester.renderObject<RenderParagraph>(find.text(name));
      expect(paragraph.didExceedMaxLines, isFalse, reason: name);
    }
    // Tiles grow with the text instead of clipping it.
    expect(tester.takeException(), isNull);
  });

  testWidgets('theme tiles paint with Ink so taps show a splash', (
    tester,
  ) async {
    await _pump(tester, const Scaffold(body: ThemePicker()));
    expect(
      find.descendant(of: find.byType(ThemePicker), matching: find.byType(Ink)),
      findsNWidgets(4),
    );
  });
}
