import 'package:animal_locket/core/l10n/locale_controller.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/features/profile/presentation/language_picker.dart';
import 'package:animal_locket/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// App tối giản giống AnimalLocketApp: ngôn ngữ đi theo lựa chọn đã lưu.
class _Host extends ConsumerWidget {
  const _Host();
  @override
  Widget build(BuildContext context, WidgetRef ref) => MaterialApp(
    theme: AppTheme.forPalette(AlbumPalette.of(AlbumThemeId.shu)),
    locale: ref.watch(appLocaleProvider),
    supportedLocales: AppLocalizations.supportedLocales,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    home: const Scaffold(body: LanguagePicker()),
  );
}

void main() {
  test('parseLanguage accepts only vi and en', () {
    expect(parseLanguage('en'), const Locale('en'));
    expect(parseLanguage('vi'), const Locale('vi'));
    expect(parseLanguage('fr'), isNull);
    expect(parseLanguage(null), isNull);
  });

  testWidgets('defaults to Vietnamese; picking English switches and saves', (
    tester,
  ) async {
    final store = MemoryLocaleStore();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [localeStoreProvider.overrideWithValue(store)],
        child: const _Host(),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('NGÔN NGỮ'), findsOneWidget);

    await tester.tap(find.text('English'));
    await tester.pumpAndSettle();
    expect(find.text('LANGUAGE'), findsOneWidget);
    expect(await store.read(), const Locale('en'));

    await tester.tap(find.text('Tiếng Việt'));
    await tester.pumpAndSettle();
    expect(find.text('NGÔN NGỮ'), findsOneWidget);
    expect(await store.read(), const Locale('vi'));
  });

  testWidgets('restores the saved language on start', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          localeStoreProvider.overrideWithValue(
            MemoryLocaleStore(const Locale('en')),
          ),
        ],
        child: const _Host(),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('LANGUAGE'), findsOneWidget);
  });
}
