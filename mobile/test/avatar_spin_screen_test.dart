import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/features/profile/presentation/avatar_spin_screen.dart';
import 'package:animal_locket/features/profile/presentation/profile_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'onboarding_test_harness.dart' show loadAvatarLibrary;

const _mine = '1:fox:0:crown';
const _won = '1:panda:3:bow';

MeProfile _me(int spins) => MeProfile(
  publicId: 'mememememememememem2',
  alias: 'Mèo Mơ Màng',
  avatar: _mine,
  leadAnimal: 'fox',
  ageGroup: AgeGroup.adult,
  discoverable: true,
  plan: AccountPlan.pro,
  createdAt: DateTime.utc(2026),
  avatarSpins: spins,
);

/// Giả lập controller: đếm số lần gọi để kiểm tra màn hình không tự đổi avatar.
class _FakeProfile extends ProfileController {
  _FakeProfile({this.spins = 1, this.failWith});

  final int spins;
  final ApiProblem? failWith;
  int spinCalls = 0;
  final List<String> applied = [];

  @override
  Future<ProfileData> build() async => ProfileData(
    profile: _me(spins),
    avatars: const [OwnedAvatar(avatar: _mine, source: 'REGISTER')],
  );

  @override
  Future<AvatarSpinResult> spinAvatar() async {
    spinCalls++;
    final problem = failWith;
    if (problem != null) throw problem;
    state = AsyncData(
      ProfileData(
        profile: _me(spins - 1),
        avatars: const [
          OwnedAvatar(avatar: _mine, source: 'REGISTER'),
          OwnedAvatar(avatar: _won, source: 'PURCHASE'),
        ],
      ),
    );
    return const AvatarSpinResult(avatar: _won, remaining: 0);
  }

  @override
  Future<void> resync() async {}

  @override
  Future<void> setAvatar(String avatar) async {
    applied.add(avatar);
    state = AsyncData(
      ProfileData(
        profile: _me(spins - 1),
        avatars: const [
          OwnedAvatar(avatar: _mine, source: 'REGISTER'),
          OwnedAvatar(avatar: _won, source: 'PURCHASE'),
        ],
      ),
    );
  }
}

Future<void> _pump(WidgetTester tester, _FakeProfile fake) async {
  tester.view.physicalSize = const Size(390, 2400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [profileControllerProvider.overrideWith(() => fake)],
      child: MaterialApp(
        theme: AppTheme.forPalette(AlbumPalette.of(AlbumThemeId.shu)),
        // Tắt chuyển động: hiệu ứng quay được bỏ qua, màn hình hiện ngay kết quả server trả.
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: true),
          child: child!,
        ),
        home: const AvatarSpinScreen(),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  setUp(loadAvatarLibrary);

  testWidgets('spinning adds to the collection without changing the avatar', (
    tester,
  ) async {
    final fake = _FakeProfile(spins: 1);
    await _pump(tester, fake);

    expect(find.widgetWithText(PaperButton, 'Quay (còn 1 lượt)'), findsOneWidget);
    await tester.tap(find.widgetWithText(PaperButton, 'Quay (còn 1 lượt)'));
    await tester.pumpAndSettle();

    expect(fake.spinCalls, 1);
    expect(find.text('Của bạn rồi!'), findsOneWidget);
    expect(find.textContaining('Hết lượt quay.'), findsOneWidget);
    // Điểm quan trọng: avatar đang dùng chỉ đổi khi người dùng bấm, không tự đổi.
    expect(fake.applied, isEmpty);
    expect(find.widgetWithText(PaperButton, 'Dùng avatar này'), findsOneWidget);
  });

  testWidgets('applying the won avatar calls setAvatar once', (tester) async {
    final fake = _FakeProfile(spins: 1);
    await _pump(tester, fake);
    await tester.tap(find.widgetWithText(PaperButton, 'Quay (còn 1 lượt)'));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(PaperButton, 'Dùng avatar này'));
    await tester.pumpAndSettle();

    expect(fake.applied, [_won]);
    expect(find.text('Đã đổi avatar'), findsOneWidget);
    expect(find.widgetWithText(PaperButton, 'Dùng avatar này'), findsNothing);
  });

  testWidgets('without credits the screen points at Pro', (tester) async {
    await _pump(tester, _FakeProfile(spins: 0));
    expect(find.text('Bạn chưa có lượt quay'), findsOneWidget);
    expect(
      find.widgetWithText(PaperButton, 'Nhận lượt quay với gói Pro'),
      findsOneWidget,
    );
    expect(find.textContaining('Quay (còn'), findsNothing);
  });

  testWidgets('server error shows the mapped message and keeps the button', (
    tester,
  ) async {
    final fake = _FakeProfile(
      spins: 1,
      failWith: const ApiProblem(status: 503, code: 'AVATAR_POOL_EXHAUSTED'),
    );
    await _pump(tester, fake);
    await tester.tap(find.widgetWithText(PaperButton, 'Quay (còn 1 lượt)'));
    await tester.pumpAndSettle();

    expect(find.textContaining('Lượt quay của bạn vẫn còn'), findsOneWidget);
    expect(fake.applied, isEmpty);
    // Vẫn còn lượt nên nút quay phải còn đó để thử lại.
    expect(find.widgetWithText(PaperButton, 'Quay (còn 1 lượt)'), findsOneWidget);
  });
}
