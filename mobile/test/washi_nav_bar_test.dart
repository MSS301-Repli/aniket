import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/features/shell/presentation/app_shell.dart';
import 'package:animal_locket/features/shell/presentation/washi_nav_bar.dart';
import 'package:animal_locket/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

final _palette = AlbumPalette.of(AlbumThemeId.shu);

/// Router giống app thật: 5 nhánh của AppShell, mỗi nhánh một màn giả.
GoRouter _router() => GoRouter(
  initialLocation: '/feed',
  routes: [
    StatefulShellRoute.indexedStack(
      builder: (_, _, shell) => AppShell(navigationShell: shell),
      branches: [
        for (final path in [
          '/camera',
          '/feed',
          '/discover',
          '/chat',
          '/profile',
        ])
          StatefulShellBranch(
            routes: [
              GoRoute(path: path, builder: (_, _) => Text('screen $path')),
            ],
          ),
      ],
    ),
  ],
);

Widget _material(Widget home, {MediaQueryData? media}) => MaterialApp(
  theme: AppTheme.forPalette(_palette),
  locale: const Locale('vi'),
  supportedLocales: AppLocalizations.supportedLocales,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  home: media == null ? home : MediaQuery(data: media, child: home),
);

const _items = [
  WashiNavItem(icon: AlbumIcons.photos, label: 'Ảnh'),
  WashiNavItem(icon: AlbumIcons.feed, label: 'Bảng tin'),
  WashiNavItem(icon: AlbumIcons.discover, label: 'Khám phá'),
  WashiNavItem(icon: AlbumIcons.chat, label: 'Chat'),
  WashiNavItem(icon: AlbumIcons.profile, label: 'Hồ sơ'),
];

Widget _bar(int index, {ValueChanged<int>? onSelected}) => Scaffold(
  bottomNavigationBar: WashiNavBar(
    items: _items,
    currentIndex: index,
    onSelected: onSelected ?? (_) {},
  ),
);

void main() {
  testWidgets('AppShell puts Ảnh in the middle and routes every tab', (
    tester,
  ) async {
    final router = _router();
    await tester.pumpWidget(
      MaterialApp.router(
        theme: AppTheme.forPalette(_palette),
        locale: const Locale('vi'),
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        routerConfig: router,
      ),
    );
    await tester.pumpAndSettle();

    const labels = ['Bảng tin', 'Khám phá', 'Ảnh', 'Chat', 'Hồ sơ'];
    final xs = [for (final l in labels) tester.getCenter(find.text(l)).dx];
    for (var i = 1; i < xs.length; i++) {
      expect(xs[i], greaterThan(xs[i - 1]), reason: labels[i]);
    }
    expect(find.byType(NavigationBar), findsNothing);

    // Tấm polaroid đi theo tab Ảnh, ở giữa thanh.
    final polaroid = find.byWidgetPredicate(
      (w) => w is AlbumIcon && w.icon == AlbumIcons.photos,
    );
    expect(polaroid, findsOneWidget);
    expect(tester.getCenter(polaroid).dx, closeTo(xs[2], 4));

    // Mở ở /feed: tab Bảng tin (vị trí đầu) đang được chọn.
    expect(
      tester.getSemantics(find.text('Bảng tin')),
      isSemantics(isButton: true, isSelected: true),
    );

    const routes = {
      'Bảng tin': '/feed',
      'Khám phá': '/discover',
      'Ảnh': '/camera',
      'Chat': '/chat',
      'Hồ sơ': '/profile',
    };
    for (final MapEntry(key: label, value: path) in routes.entries) {
      await tester.tap(find.text(label));
      await tester.pumpAndSettle();
      expect(router.state.uri.path, path, reason: label);
      expect(find.text('screen $path'), findsOneWidget);
      expect(
        tester.getSemantics(find.text(label)),
        isSemantics(isButton: true, isSelected: true),
        reason: label,
      );
    }
  });

  testWidgets('marks only the current tab as selected, in the accent', (
    tester,
  ) async {
    await tester.pumpWidget(_material(_bar(2)));
    for (var i = 0; i < _items.length; i++) {
      expect(
        tester.getSemantics(find.text(_items[i].label)),
        isSemantics(isButton: true, isSelected: i == 2),
        reason: _items[i].label,
      );
    }
    final label = tester.widget<Text>(find.text('Khám phá'));
    expect(label.style?.color, _palette.accent);
  });

  testWidgets('reports taps with the tab index', (tester) async {
    int? picked;
    await tester.pumpWidget(_material(_bar(1, onSelected: (i) => picked = i)));
    await tester.tap(find.text('Chat'));
    expect(picked, 3);
  });

  testWidgets('labels fit at 1.5x text scale on a narrow phone', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 720);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      _material(
        _bar(2),
        media: const MediaQueryData(
          size: Size(360, 720),
          textScaler: TextScaler.linear(1.5),
        ),
      ),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('navigation labels keep text scaling at 2x', (tester) async {
    tester.view.physicalSize = const Size(360, 720);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      _material(
        _bar(2),
        media: const MediaQueryData(
          size: Size(360, 720),
          textScaler: TextScaler.linear(2),
        ),
      ),
    );

    expect(tester.getSize(find.byType(WashiNavBar)).height, greaterThan(68));
    expect(tester.getSize(find.text('Khám phá')).height, greaterThan(20));
    expect(
      tester.getSemantics(find.text('Khám phá')),
      isSemantics(label: 'Khám phá', isButton: true, isSelected: true),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('slides the washi to the new tab by default', (tester) async {
    const media = MediaQueryData(size: Size(400, 800));
    await tester.pumpWidget(_material(_bar(1), media: media));
    await tester.pumpWidget(_material(_bar(3), media: media));
    await tester.pump();
    expect(tester.hasRunningAnimations, isTrue);
    await tester.pumpAndSettle();
  });

  testWidgets('moves the washi without animating when motion is reduced', (
    tester,
  ) async {
    const media = MediaQueryData(size: Size(400, 800), disableAnimations: true);
    await tester.pumpWidget(_material(_bar(1), media: media));
    await tester.pumpWidget(_material(_bar(3), media: media));
    await tester.pump();
    expect(tester.hasRunningAnimations, isFalse);
  });

  testWidgets('bar surface is a paper Material so tab taps show ink', (
    tester,
  ) async {
    await tester.pumpWidget(_material(_bar(1)));
    expect(
      find.descendant(
        of: find.byType(WashiNavBar),
        matching: find.byWidgetPredicate(
          (w) => w is Material && w.color == _palette.paper,
        ),
      ),
      findsOneWidget,
    );
  });
}
