import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/hanko_stamp.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/animal_avatar.dart';
import 'package:animal_locket/features/friends/domain/friends_repository.dart';
import 'package:animal_locket/features/friends/presentation/friends_controller.dart';
import 'package:animal_locket/features/friends/presentation/friends_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'onboarding_test_harness.dart' show loadAvatarLibrary;

const _fox = PublicProfile(
  publicId: 'foxfoxfoxfoxfoxfoxfo',
  alias: 'Cáo Lém Lỉnh',
  avatar: '1:fox:0:none',
);
const _panda = PublicProfile(
  publicId: 'pandapandapandapanda',
  alias: 'Gấu Trúc Mơ Màng',
  avatar: '1:panda:3:glasses',
);
final _request = FriendRequest(
  id: 'r1',
  user: const PublicProfile(publicId: 'owlowlowlowlowlowlow', alias: 'Cú Đêm'),
  createdAt: DateTime.utc(2026, 11, 1),
);
final _sent = FriendRequest(
  id: 'r2',
  user: const PublicProfile(publicId: 'catcatcatcatcatcatca', alias: 'Mèo'),
  createdAt: DateTime.utc(2026, 11, 1),
);

FriendsState _state({String? busyId}) => FriendsState(
  friends: const [_fox, _panda],
  incoming: [_request],
  outgoing: [_sent],
  online: const {'foxfoxfoxfoxfoxfoxfo': true},
  busyId: busyId,
);

class _FakeFriends extends FriendsController {
  _FakeFriends(this.initial);
  final Object initial;
  final calls = <String>[];

  @override
  Future<FriendsState> build() async {
    final i = initial;
    if (i is FriendsState) return i;
    throw i;
  }

  @override
  Future<void> refresh() async => calls.add('refresh');
  @override
  Future<void> accept(String id) async => calls.add('accept $id');
  @override
  Future<void> decline(String id) async => calls.add('decline $id');
  @override
  Future<void> cancel(String id) async => calls.add('cancel $id');
  @override
  Future<void> remove(String id) async => calls.add('remove $id');
}

Future<_FakeFriends> _pump(
  WidgetTester tester,
  Object state, {
  double width = 390,
  double height = 844,
  double textScale = 1,
}) async {
  tester.view.physicalSize = Size(width, height);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final fake = _FakeFriends(state);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [friendsControllerProvider.overrideWith(() => fake)],
      child: MaterialApp(
        theme: AppTheme.forPalette(AlbumPalette.of(AlbumThemeId.shu)),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: const FriendsScreen(),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return fake;
}

Future<void> _openTab(WidgetTester tester, String label) async {
  await tester.tap(find.text(label));
  await tester.pumpAndSettle();
}

void main() {
  setUp(loadAvatarLibrary);

  testWidgets('paper tabs and an address-book page of friends', (tester) async {
    await _pump(tester, _state());
    expect(find.text('Bạn bè và lời mời'), findsOneWidget);
    for (final t in ['Bạn bè 2', 'Lời mời 1', 'Đã gửi 1']) {
      expect(find.text(t), findsOneWidget, reason: t);
    }
    expect(find.text('Cáo Lém Lỉnh'), findsOneWidget);
    expect(find.text('Đang online'), findsOneWidget);
    expect(find.text('Ngoại tuyến'), findsOneWidget);
    final avatars = tester.widgetList<AnimalAvatar>(find.byType(AnimalAvatar));
    expect(avatars.every((a) => a.square), isTrue);
    expect(find.byTooltip('Xem hồ sơ'), findsNWidgets(2));
    expect(find.byType(Icon), findsNothing);
  });

  testWidgets('incoming: check-mark stamp accepts, plain × declines', (
    tester,
  ) async {
    final fake = await _pump(tester, _state());
    await _openTab(tester, 'Lời mời 1');
    expect(
      find.text('Không hiển thị khoảng cách để bảo vệ riêng tư'),
      findsOneWidget,
    );
    final stamp = tester.widget<HankoStamp>(find.byType(HankoStamp));
    expect(stamp.mark, AlbumIcons.check);
    expect(
      tester.getSemantics(find.byType(HankoStamp)),
      isSemantics(isButton: true, label: 'Chấp nhận', hasToggledState: false),
    );
    await tester.tap(find.byType(HankoStamp));
    await tester.tap(find.byTooltip('Từ chối'));
    expect(fake.calls, ['accept r1', 'decline r1']);
  });

  testWidgets('outgoing: a plain "Huỷ" text button cancels', (tester) async {
    final fake = await _pump(tester, _state());
    await _openTab(tester, 'Đã gửi 1');
    await tester.tap(find.widgetWithText(TextButton, 'Huỷ'));
    expect(fake.calls, ['cancel r2']);
  });

  testWidgets('friend menu offers message, profile and a confirmed unfriend', (
    tester,
  ) async {
    final fake = await _pump(tester, _state());
    await tester.tap(find.byTooltip('Xem hồ sơ').first);
    await tester.pumpAndSettle();
    for (final label in ['Nhắn tin', 'Xem hồ sơ', 'Huỷ kết bạn']) {
      expect(find.text(label), findsOneWidget);
    }
    await tester.tap(find.text('Huỷ kết bạn'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Huỷ kết bạn'));
    await tester.pumpAndSettle();
    expect(fake.calls.where((c) => c.startsWith('remove ')), hasLength(1));
  });

  testWidgets('a busy request shows progress instead of buttons', (
    tester,
  ) async {
    await _pump(tester, _state(busyId: 'r1'));
    // The spinner never settles: wait a fixed time for the tab switch.
    await tester.tap(find.text('Lời mời 1'));
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(find.byType(HankoStamp), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('empty lists keep their copy', (tester) async {
    await _pump(
      tester,
      const FriendsState(friends: [], incoming: [], outgoing: [], online: {}),
    );
    expect(find.text('Chưa có bạn bè.'), findsOneWidget);
    await _openTab(tester, 'Lời mời 0');
    expect(find.text('Không có lời mời mới.'), findsOneWidget);
    await _openTab(tester, 'Đã gửi 0');
    expect(find.text('Bạn chưa gửi lời mời nào.'), findsOneWidget);
  });

  testWidgets('load error offers a paper retry', (tester) async {
    final fake = await _pump(tester, StateError('offline'));
    await tester.tap(
      find.widgetWithText(PaperButton, 'Không tải được · Thử lại'),
    );
    expect(fake.calls, ['refresh']);
  });

  testWidgets('fits a narrow phone at 1.5x text', (tester) async {
    // Tab labels must show their counts in full, not fade them out.
    await _pump(tester, _state(), width: 360, height: 640, textScale: 1.5);
    for (final label in ['Bạn bè 2', 'Lời mời 1', 'Đã gửi 1']) {
      final text = tester.renderObject<RenderParagraph>(find.text(label));
      expect(
        text.size.width,
        greaterThanOrEqualTo(text.getMaxIntrinsicWidth(double.infinity) - .5),
        reason: label,
      );
    }
    expect(tester.takeException(), isNull);
    await _openTab(tester, 'Lời mời 1');
    expect(tester.takeException(), isNull);
  });
}
