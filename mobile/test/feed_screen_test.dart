import 'dart:async';
import 'dart:math' as math;

import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/theme/theme_controller.dart';
import 'package:animal_locket/core/widgets/album/hanko_stamp.dart';
import 'package:animal_locket/core/widgets/album/washi_photo_card.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/features/feed/domain/post_repository.dart';
import 'package:animal_locket/features/feed/presentation/feed_controller.dart';
import 'package:animal_locket/features/feed/presentation/feed_screen.dart';
import 'package:animal_locket/features/feed/presentation/posts_grid.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

final _now = DateTime.now().toUtc();

Post _post(
  String id, {
  DateTime? at,
  String alias = 'Cáo Lém Lỉnh 042',
  String? caption = 'Chiều nay ở Hội An 🏮',
  bool liked = false,
}) => Post(
  id: id,
  author: PublicProfile(publicId: 'u$id', alias: alias, leadAnimal: 'fox'),
  mediaId: 'm$id',
  status: PostStatus.published,
  likeCount: 24,
  commentCount: 5,
  likedByMe: liked,
  createdAt: at ?? _now,
  publishedAt: at ?? _now,
  caption: caption,
);

class _FakeFeed extends FeedController {
  _FakeFeed(this.initial);
  final FeedState initial;
  final liked = <String>[];

  @override
  Future<FeedState> build() async => initial;

  @override
  Future<void> toggleLike(String postId) async => liked.add(postId);

  @override
  Future<void> refresh() async {}

  @override
  Future<void> loadMore() async {}
}

final _loading = Completer<String?>();

Future<_FakeFeed> _pump(
  WidgetTester tester,
  FeedState state, {
  double width = 400,
  double height = 2400,
}) async {
  // Tall enough that the lazy ListView builds every card.
  tester.view.physicalSize = Size(width, height);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final fake = _FakeFeed(state);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        themeStoreProvider.overrideWithValue(MemoryThemeStore()),
        feedControllerProvider.overrideWith(() => fake),
        mediaUrlProvider.overrideWith((ref, mediaId) {
          return switch (mediaId) {
            'm1' => Future<String?>.value(null),
            'm2' => Future<String?>.error(StateError('no url')),
            _ => _loading.future,
          };
        }),
      ],
      child: MaterialApp(
        theme: AppTheme.forPalette(AlbumPalette.of(AlbumThemeId.shu)),
        home: const FeedScreen(),
      ),
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 400));
  return fake;
}

/// Vuốt lên (hoặc xuống) một lần như ngón tay trên điện thoại.
Future<void> _swipe(WidgetTester tester, {bool back = false}) async {
  await tester.fling(find.byType(PageView), Offset(0, back ? 250 : -250), 800);
  await tester.pump();
  await tester.pump(const Duration(seconds: 1));
}

WashiPhotoCard _visibleCard(WidgetTester tester) =>
    tester.widget<WashiPhotoCard>(find.byType(WashiPhotoCard));

/// Trang đang hiện (số thực: 1.0 nghĩa là đã khớp đúng trang thứ hai).
double _page(WidgetTester tester) {
  final position = tester
      .state<ScrollableState>(
        find.descendant(
          of: find.byType(PageView),
          matching: find.byType(Scrollable),
        ),
      )
      .position;
  return position.pixels / position.viewportDimension;
}

void main() {
  final yesterday = _now.subtract(const Duration(days: 1));
  final three = FeedState(
    items: [
      _post('1', liked: true),
      _post('2'),
      _post('3', at: yesterday),
    ],
  );

  testWidgets('keeps the existing copy and day labels', (tester) async {
    await _pump(tester, three);
    expect(find.text('Khoảnh khắc'), findsOneWidget);
    expect(find.text(formatPostDay(_now.toLocal())), findsOneWidget);
    expect(find.text('Chiều nay ở Hội An 🏮'), findsOneWidget);
    expect(find.text('24'), findsOneWidget);
    expect(find.text('5'), findsOneWidget);
    await _swipe(tester);
    await _swipe(tester);
    expect(find.text(formatPostDay(yesterday.toLocal())), findsOneWidget);
  });

  testWidgets('uses only album widgets, no glass or Material icons', (
    tester,
  ) async {
    await _pump(tester, three);
    final feed = find.byType(FeedScreen);
    expect(
      find.descendant(of: feed, matching: find.byType(Icon)),
      findsNothing,
    );
  });

  testWidgets('cards sit centred, with no decoration gutter', (tester) async {
    await _pump(tester, three);
    final card = tester.getRect(find.byType(WashiPhotoCard));
    expect(card.left, closeTo(400 - card.right, 1));
    expect(find.byType(DecoSvg), findsNothing);
  });

  testWidgets('tilts cards alternately', (tester) async {
    await _pump(tester, three);
    final tilts = [_visibleCard(tester).tiltDegrees];
    for (var i = 0; i < 2; i++) {
      await _swipe(tester);
      tilts.add(_visibleCard(tester).tiltDegrees);
    }
    expect(tilts, [-1.4, 1.2, -1.4]);
  });

  testWidgets('pressing the hanko likes that post', (tester) async {
    final fake = await _pump(tester, three);
    HankoStamp stamp() => tester.widget<HankoStamp>(find.byType(HankoStamp));
    expect(stamp().stamped, isTrue);
    await tester.tap(find.byType(HankoStamp));
    expect(fake.liked, ['1']);
    await _swipe(tester);
    expect(stamp().stamped, isFalse);
  });

  testWidgets('photo frames keep 4:5 while empty, failed or loading', (
    tester,
  ) async {
    await _pump(tester, three);
    final frame = find.byWidgetPredicate(
      (w) => w is AspectRatio && w.aspectRatio == 0.8,
    );
    bool hasIcon(AlbumIcons icon) => tester
        .widgetList<AlbumIcon>(find.byType(AlbumIcon))
        .any((w) => w.icon == icon);
    expect(frame, findsOneWidget);
    expect(hasIcon(AlbumIcons.image), isTrue);
    await _swipe(tester);
    expect(frame, findsOneWidget);
    expect(hasIcon(AlbumIcons.imageBroken), isTrue);
    await _swipe(tester);
    expect(frame, findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('long alias and multi-line caption do not overflow', (
    tester,
  ) async {
    await _pump(
      tester,
      FeedState(
        items: [
          _post(
            '1',
            alias: 'Cáo ${'Lém Lỉnh ' * 7}',
            caption: 'Dòng một 🌸\nDòng hai\nDòng ba ${'rất dài ' * 12}\n4\n5',
          ),
        ],
      ),
      width: 360,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('empty state keeps its copy', (tester) async {
    await _pump(tester, const FeedState());
    expect(
      find.text(
        'Chưa có khoảnh khắc nào\nHãy chụp ảnh hoặc kết bạn để bắt đầu.',
      ),
      findsOneWidget,
    );
    final feed = find.byType(FeedScreen);
    expect(
      find.descendant(of: feed, matching: find.byType(Icon)),
      findsNothing,
    );
  });

  testWidgets('degraded banner keeps its copy', (tester) async {
    await _pump(tester, FeedState(items: [_post('1')], degraded: true));
    expect(
      find.text('Một phần dịch vụ đang chậm. Feed có thể chưa đầy đủ.'),
      findsOneWidget,
    );
  });

  test('tilt angles in degrees convert as expected', () {
    expect(WashiPhotoCard.tiltFor(1) * math.pi / 180, closeTo(.0209, .0001));
  });

  testWidgets('author row is at least 48pt tall to tap', (tester) async {
    await _pump(tester, three);
    final row = find.ancestor(
      of: find.text('Cáo Lém Lỉnh 042').first,
      matching: find.byType(InkWell),
    );
    expect(tester.getSize(row.first).height, greaterThanOrEqualTo(48));
  });

  testWidgets('cards do not replay their entrance after scrolling back', (
    tester,
  ) async {
    // Only posts whose photo resolves (no endless loading spinner).
    final five = FeedState(
      items: [for (var i = 0; i < 5; i++) _post(i.isEven ? '1' : '2')],
    );
    await _pump(tester, five, height: 844);
    await _swipe(tester);
    await _swipe(tester);
    // Jump straight back to the first post (rebuilt from scratch) and look at
    // the very next frame: it is already at its tilt, not restarting its
    // slide-and-tilt entrance from 0.
    tester
        .state<ScrollableState>(
          find.descendant(
            of: find.byType(PageView),
            matching: find.byType(Scrollable),
          ),
        )
        .position
        .jumpTo(0);
    await tester.pump();
    expect(_page(tester), 0);
    expect(_visibleCard(tester).tiltDegrees, WashiPhotoCard.tiltFor(0));
  });

  testWidgets('one swipe shows exactly one whole post', (tester) async {
    await _pump(tester, three, width: 390, height: 844);
    Rect card() => tester.getRect(find.byType(WashiPhotoCard));
    // The whole card sits above the floating tab bar (~100pt at the bottom).
    expect(card().top, greaterThanOrEqualTo(0));
    expect(card().bottom, lessThanOrEqualTo(844 - 100));
    await _swipe(tester);
    expect(_page(tester), 1);
    expect(find.byType(WashiPhotoCard), findsOneWidget);
    expect(_visibleCard(tester).tiltDegrees, WashiPhotoCard.tiltFor(1));
    expect(card().top, greaterThanOrEqualTo(0));
    expect(card().bottom, lessThanOrEqualTo(844 - 100));
  });

  testWidgets('a whole post with a 3-line caption fits a short phone', (
    tester,
  ) async {
    await _pump(
      tester,
      FeedState(items: [_post('1', caption: 'Một\nHai\nBa')]),
      width: 360,
      height: 640,
    );
    expect(tester.takeException(), isNull);
    expect(
      tester.getRect(find.byType(WashiPhotoCard)).bottom,
      lessThanOrEqualTo(640 - 80), // thanh tab 68pt sát đáy
    );
  });

  testWidgets(
    'hides the scrollbar',
    (tester) async {
      await _pump(tester, three, width: 390, height: 844);
      expect(
        find.descendant(
          of: find.byType(FeedScreen),
          matching: find.byWidgetPredicate((w) => w is RawScrollbar),
        ),
        findsNothing,
      );
    },
    // Web and desktop are where Flutter adds a scrollbar on its own.
    variant: TargetPlatformVariant.only(TargetPlatform.windows),
  );
}
