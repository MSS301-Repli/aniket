import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/hanko_stamp.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/features/feed/data/post_repository_impl.dart';
import 'package:animal_locket/features/feed/domain/post_repository.dart';
import 'package:animal_locket/features/feed/presentation/feed_controller.dart';
import 'package:animal_locket/features/feed/presentation/posts_grid.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

Post _post(String id, PostStatus status) => Post(
  id: id,
  author: const PublicProfile(publicId: 'u1', alias: 'Cáo'),
  mediaId: 'm$id',
  status: status,
  likeCount: 3,
  commentCount: 1,
  likedByMe: false,
  createdAt: DateTime.utc(2026, 11, 1),
);

class _FakePosts implements PostRepository {
  _FakePosts(this.result);
  final Object result;

  @override
  Future<PagedResult<Post>> postsOf(
    String publicId, {
    String? before,
    int limit = 30,
  }) async {
    final r = result;
    if (r is PagedResult<Post>) return r;
    throw r;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<void> _pump(
  WidgetTester tester,
  Object result, {
  bool mine = false,
  double textScale = 1,
}) async {
  tester.view.physicalSize = const Size(360, 900);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        postRepositoryProvider.overrideWithValue(_FakePosts(result)),
        mediaUrlProvider.overrideWith((ref, id) async => null),
      ],
      child: MaterialApp(
        theme: AppTheme.forPalette(AlbumPalette.of(AlbumThemeId.shu)),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: Scaffold(
          body: SingleChildScrollView(
            padding: const EdgeInsets.all(20),
            child: PostsGrid(publicId: 'u1', mine: mine),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

final _three = PagedResult<Post>(
  items: [
    _post('1', PostStatus.published),
    _post('2', PostStatus.pendingMedia),
    _post('3', PostStatus.rejected),
    _post('4', PostStatus.deleted),
  ],
  nextBefore: 'c1',
);

void main() {
  testWidgets('small tilted prints, with paper labels for pending/rejected', (
    tester,
  ) async {
    await _pump(tester, _three, mine: true);
    final prints = tester.widgetList<Transform>(
      find.byKey(const ValueKey('post-print'), skipOffstage: false),
    );
    expect(prints, hasLength(3)); // deleted is hidden
    final angles = prints.map((t) => t.transform.getRotation().entry(1, 0));
    expect(angles.toSet().length, greaterThan(1)); // alternating tilt
    expect(find.text('Đang xử lý'), findsOneWidget);
    expect(find.text('Bị từ chối'), findsOneWidget);
    expect(find.text('Xem thêm ảnh'), findsOneWidget);
    expect(find.byType(Icon), findsNothing);
  });

  testWidgets('strangers get the friend hint; other errors offer a retry', (
    tester,
  ) async {
    await _pump(tester, const ApiProblem(status: 404));
    expect(find.text('Kết bạn để xem ảnh của người này.'), findsOneWidget);
  });

  testWidgets('other errors keep the retry text', (tester) async {
    await _pump(tester, const ApiProblem(status: 500));
    expect(find.text('Không tải được ảnh · Thử lại'), findsOneWidget);
  });

  testWidgets('opening a print shows a heart stamp and album icons', (
    tester,
  ) async {
    await _pump(tester, _three, mine: true);
    await tester.tap(find.byKey(const ValueKey('post-print')).first);
    await tester.pumpAndSettle();
    expect(find.byType(HankoStamp), findsOneWidget);
    expect(
      tester
          .widgetList<AlbumIcon>(find.byType(AlbumIcon))
          .map((i) => i.icon)
          .toSet()
          .containsAll({AlbumIcons.comment, AlbumIcons.trash}),
      isTrue,
    );
    expect(find.bySemanticsLabel('Xoá ảnh'), findsOneWidget);
    expect(find.byType(Icon), findsNothing);
  });

  testWidgets('fits a narrow phone at 1.5x text', (tester) async {
    await _pump(tester, _three, mine: true, textScale: 1.5);
    expect(tester.takeException(), isNull);
  });
}
