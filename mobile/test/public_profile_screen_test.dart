import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/polaroid.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/animal_avatar.dart';
import 'package:animal_locket/features/feed/data/post_repository_impl.dart';
import 'package:animal_locket/features/feed/domain/post_repository.dart';
import 'package:animal_locket/features/feed/presentation/feed_controller.dart';
import 'package:animal_locket/features/profile/presentation/public_profile_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'onboarding_test_harness.dart' show loadAvatarLibrary;

const _id = 'foxfoxfoxfoxfoxfoxfo';
const _fox = PublicProfile(
  publicId: _id,
  alias: 'Cáo Lém Lỉnh',
  avatar: '1:fox:0:crown',
);

class _NoPosts implements PostRepository {
  @override
  Future<PagedResult<Post>> postsOf(
    String publicId, {
    String? before,
    int limit = 30,
  }) async => const PagedResult(items: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<void> _pump(
  WidgetTester tester, {
  bool friend = true,
  double width = 390,
  double height = 844,
  double textScale = 1,
}) async {
  tester.view.physicalSize = Size(width, height);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        publicProfileProvider.overrideWith((ref, id) async => _fox),
        isFriendProvider.overrideWith((ref, id) async => friend),
        postRepositoryProvider.overrideWithValue(_NoPosts()),
        mediaUrlProvider.overrideWith((ref, id) async => null),
      ],
      child: MaterialApp(
        theme: AppTheme.forPalette(AlbumPalette.of(AlbumThemeId.shu)),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: const PublicProfileScreen(publicId: _id),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  setUp(loadAvatarLibrary);

  testWidgets('taped polaroid avatar beside the alias, copy kept', (
    tester,
  ) async {
    await _pump(tester);
    expect(find.text('Hồ sơ'), findsOneWidget);
    expect(find.text('Cáo Lém Lỉnh'), findsWidgets);
    expect(find.text('Hồ sơ ẩn danh'), findsOneWidget);
    final avatar = tester.widget<AnimalAvatar>(
      find.descendant(
        of: find.byType(Polaroid),
        matching: find.byType(AnimalAvatar),
      ),
    );
    expect(avatar.avatar, '1:fox:0:crown');
    expect(avatar.square, isTrue);
    expect(find.text('Ảnh của Cáo Lém Lỉnh'), findsOneWidget);
    expect(find.text('Cáo Lém Lỉnh chưa đăng ảnh nào.'), findsOneWidget);
    expect(find.byType(Icon), findsNothing);
  });

  testWidgets('paper message button; unfriend label only for friends', (
    tester,
  ) async {
    await _pump(tester);
    final message = tester.widget<PaperButton>(
      find.widgetWithText(PaperButton, 'Nhắn tin'),
    );
    expect(message.icon, AlbumIcons.chat);
    expect(find.widgetWithText(PaperButton, 'Huỷ kết bạn'), findsOneWidget);
  });

  testWidgets('strangers do not see "Huỷ kết bạn"', (tester) async {
    await _pump(tester, friend: false);
    expect(find.text('Huỷ kết bạn'), findsNothing);
  });

  testWidgets('report and block are small links with album icons', (
    tester,
  ) async {
    await _pump(tester);
    final icons = tester
        .widgetList<AlbumIcon>(find.byType(AlbumIcon))
        .map((i) => i.icon)
        .toSet();
    expect(icons.containsAll({AlbumIcons.flag, AlbumIcons.block}), isTrue);
    expect(find.text('Báo cáo'), findsOneWidget);
    expect(find.text('Chặn'), findsOneWidget);
    await tester.tap(find.text('Chặn'));
    await tester.pumpAndSettle();
    expect(find.text('Chặn người này?'), findsOneWidget);
  });

  testWidgets('fits a narrow phone at 1.5x text', (tester) async {
    await _pump(tester, width: 360, height: 640, textScale: 1.5);
    expect(tester.takeException(), isNull);
  });
}
