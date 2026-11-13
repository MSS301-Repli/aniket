import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/hanko_stamp.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/paper_tile_button.dart';
import 'package:animal_locket/core/widgets/album/polaroid.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/animal_avatar.dart';
import 'package:animal_locket/features/discovery/domain/discovery_repository.dart';
import 'package:animal_locket/features/discovery/presentation/discovery_controller.dart';
import 'package:animal_locket/features/discovery/presentation/discovery_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'onboarding_test_harness.dart' show loadAvatarLibrary;

const _lion = DiscoveryCard(
  publicId: 'lionlionlionlionlion',
  alias: 'Sư Tử Nắng Sớm',
  avatar: '1:lion:4:none',
  distanceBand: '1_TO_3KM',
  online: true,
  boosted: true,
);

class _FakeDiscovery extends DiscoveryController {
  _FakeDiscovery(this.initial);
  final DiscoveryState initial;
  final calls = <String>[];

  @override
  Future<DiscoveryState> build() async => initial;
  @override
  void setActive(bool active) {}
  @override
  Future<String?> swipe(bool right) async {
    calls.add('swipe $right');
    return null;
  }

  @override
  Future<void> boost() async => calls.add('boost');
  @override
  Future<void> refresh() async => calls.add('refresh');
  @override
  Future<void> enable() async => calls.add('enable');
  @override
  Future<void> requestLocation() async => calls.add('location');
}

Future<_FakeDiscovery> _pump(
  WidgetTester tester,
  DiscoveryState state, {
  double width = 390,
  double height = 844,
  double textScale = 1,
}) async {
  tester.view.physicalSize = Size(width, height);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final fake = _FakeDiscovery(state);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [discoveryControllerProvider.overrideWith(() => fake)],
      child: MaterialApp(
        theme: AppTheme.forPalette(AlbumPalette.of(AlbumThemeId.shu)),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: const DiscoveryScreen(),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return fake;
}

const _deck = DiscoveryState(
  enabled: true,
  cards: [_lion],
  swipesLeftToday: 18,
);

void main() {
  setUp(loadAvatarLibrary);

  testWidgets('the card is a big polaroid with paper labels and a Boost seal', (
    tester,
  ) async {
    await _pump(tester, _deck);
    expect(find.text('Khám phá'), findsOneWidget);
    expect(find.text('Còn 18 lượt quẹt phải hôm nay'), findsOneWidget);
    final polaroid = tester.widget<Polaroid>(find.byType(Polaroid));
    expect(polaroid.caption, 'Sư Tử Nắng Sớm');
    expect(
      tester
          .widget<AnimalAvatar>(
            find.descendant(
              of: find.byType(Polaroid),
              matching: find.byType(AnimalAvatar),
            ),
          )
          .square,
      isTrue,
    );
    for (final t in ['Đang online', '1–3 km', 'Con vật ẩn danh', 'Boost']) {
      expect(find.text(t), findsOneWidget, reason: t);
    }
    expect(find.byType(Icon), findsNothing);
  });

  testWidgets('skip and boost are paper tiles, befriend is a heart stamp', (
    tester,
  ) async {
    final fake = await _pump(tester, _deck);
    final tiles = tester
        .widgetList<PaperTileButton>(find.byType(PaperTileButton))
        .map((t) => (t.label, t.icon))
        .toList();
    expect(tiles, [
      ('Bỏ qua', AlbumIcons.close),
      ('Dùng Boost', AlbumIcons.bolt),
    ]);
    final like = find.byType(HankoStamp);
    expect(tester.widget<HankoStamp>(like).mark, AlbumIcons.heart);
    expect(
      tester.getSemantics(like),
      isSemantics(isButton: true, label: 'Kết bạn', hasToggledState: false),
    );
    await tester.tap(find.bySemanticsLabel('Bỏ qua'));
    await tester.tap(like);
    await tester.tap(find.bySemanticsLabel('Dùng Boost'));
    await tester.pump();
    expect(fake.calls, ['swipe false', 'swipe true', 'boost']);
  });

  testWidgets('no card: skip and befriend are off; reload is offered', (
    tester,
  ) async {
    final fake = await _pump(
      tester,
      const DiscoveryState(enabled: true, swipesLeftToday: 3),
    );
    expect(find.text('Hết thẻ mới rồi'), findsOneWidget);
    expect(
      tester
          .widgetList<PaperTileButton>(find.byType(PaperTileButton))
          .first
          .onPressed,
      isNull,
    );
    expect(
      tester.widget<HankoStamp>(find.byType(HankoStamp)).onPressed,
      isNull,
    );
    await tester.tap(find.text('Tải lại'));
    expect(fake.calls, ['refresh']);
  });

  testWidgets('busy: all three actions are off', (tester) async {
    await _pump(
      tester,
      const DiscoveryState(enabled: true, cards: [_lion], busy: true),
    );
    for (final t in tester.widgetList<PaperTileButton>(
      find.byType(PaperTileButton),
    )) {
      expect(t.onPressed, isNull, reason: t.label);
    }
    expect(
      tester.widget<HankoStamp>(find.byType(HankoStamp)).onPressed,
      isNull,
    );
  });

  testWidgets('location off and degraded notes keep their copy', (
    tester,
  ) async {
    final fake = await _pump(
      tester,
      const DiscoveryState(
        enabled: true,
        cards: [_lion],
        locationOff: true,
        degraded: true,
      ),
    );
    expect(
      find.text(
        'Bật vị trí để tìm người ở gần. Hiện chỉ thấy người đang online.',
      ),
      findsOneWidget,
    );
    expect(
      find.text('Dịch vụ đang chậm, bộ thẻ có thể chưa đầy đủ.'),
      findsOneWidget,
    );
    await tester.tap(find.text('Bật vị trí'));
    expect(fake.calls, ['location']);
  });

  testWidgets('discovery off: a paper page with a paper button', (
    tester,
  ) async {
    final fake = await _pump(tester, const DiscoveryState(enabled: false));
    expect(find.text('Tìm người bạn mới'), findsOneWidget);
    await tester.tap(find.widgetWithText(PaperButton, 'Bật khám phá'));
    expect(fake.calls, ['enable']);
    expect(find.byType(Icon), findsNothing);
  });

  testWidgets('fits a short narrow phone at 1.5x text', (tester) async {
    await _pump(
      tester,
      const DiscoveryState(
        enabled: true,
        cards: [_lion],
        locationOff: true,
        degraded: true,
      ),
      width: 360,
      height: 640,
      textScale: 1.5,
    );
    expect(tester.takeException(), isNull);
  });
}
