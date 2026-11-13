import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/theme/theme_controller.dart';
import 'package:animal_locket/core/widgets/album/gold_badge.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/polaroid.dart';
import 'package:animal_locket/features/billing/domain/billing_repository.dart';
import 'package:animal_locket/features/billing/presentation/billing_controller.dart';
import 'package:animal_locket/features/billing/presentation/pro_screen.dart';
import 'package:animal_locket/features/feed/data/post_repository_impl.dart';
import 'package:animal_locket/features/feed/domain/post_repository.dart';
import 'package:animal_locket/features/feed/presentation/feed_controller.dart';
import 'package:animal_locket/features/friends/presentation/friends_controller.dart';
import 'package:animal_locket/features/profile/presentation/profile_controller.dart';
import 'package:animal_locket/features/profile/presentation/profile_screen.dart';
import 'package:animal_locket/features/profile/presentation/settings_screen.dart';
import 'package:animal_locket/features/profile/presentation/upgrade_pro_button.dart';
import 'package:animal_locket/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'onboarding_test_harness.dart' show loadAvatarLibrary;

const _fox = PublicProfile(
  publicId: 'foxfoxfoxfoxfoxfoxfo',
  alias: 'Cáo Lém Lỉnh',
  avatar: '1:fox:0:crown',
);

MeProfile _me({AccountPlan plan = AccountPlan.free}) => MeProfile(
  publicId: 'mememememememememem2',
  alias: 'Mèo Mơ Màng Rất Dài Tên',
  avatar: '1:fox:0:crown',
  ageGroup: AgeGroup.adult,
  discoverable: true,
  plan: plan,
  createdAt: DateTime.utc(2026),
);

class _Profile extends ProfileController {
  _Profile(this.plan);
  final AccountPlan plan;
  @override
  Future<ProfileData> build() async => ProfileData(
    profile: _me(plan: plan),
    avatars: const [
      OwnedAvatar(avatar: '1:fox:0:crown', source: 'REGISTER'),
      OwnedAvatar(avatar: '1:fox:0:crown', source: 'REGISTER'),
    ],
  );
}

class _Friends extends FriendsController {
  @override
  Future<FriendsState> build() async => const FriendsState(
    friends: [_fox],
    incoming: [],
    outgoing: [],
    online: {'foxfoxfoxfoxfoxfoxfo': true},
  );
}

class _Billing extends BillingController {
  @override
  Future<BillingState> build() async => const BillingState(
    enabled: true,
    me: BillingMe(plan: 'FREE', wallet: {'BOOST': 2}),
    products: [
      BillingProduct(
        sku: 'pro30',
        type: 'SUBSCRIPTION',
        item: 'PRO',
        quantity: 1,
        priceVnd: 49000,
        periodDays: 30,
      ),
    ],
  );
}

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
  WidgetTester tester,
  Widget home, {
  AccountPlan plan = AccountPlan.free,
  double width = 390,
  double textScale = 1,
}) async {
  tester.view.physicalSize = Size(width, 2400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        themeStoreProvider.overrideWithValue(MemoryThemeStore()),
        profileControllerProvider.overrideWith(() => _Profile(plan)),
        friendsControllerProvider.overrideWith(_Friends.new),
        billingControllerProvider.overrideWith(_Billing.new),
        blockedUsersProvider.overrideWith((ref) async => const [_fox]),
        postRepositoryProvider.overrideWithValue(_NoPosts()),
        mediaUrlProvider.overrideWith((ref, id) async => null),
      ],
      child: MaterialApp(
        theme: AppTheme.forPalette(AlbumPalette.of(AlbumThemeId.shu)),
        locale: const Locale('vi'),
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: home,
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  setUp(loadAvatarLibrary);

  testWidgets('my profile is an album page with paper controls', (
    tester,
  ) async {
    await _pump(tester, const ProfileScreen());
    expect(find.byType(Icon), findsNothing);
    for (final label in ['Cài đặt', 'Đổi biệt danh']) {
      expect(find.byTooltip(label), findsOneWidget);
    }
    // Avatar của tôi + 2 avatar trong bộ sưu tập.
    expect(find.byType(Polaroid), findsNWidgets(3));
    expect(find.bySemanticsLabel('Mở trang của Cáo Lém Lỉnh'), findsOneWidget);
    expect(
      find.widgetWithText(UpgradeProButton, 'Nâng cấp Pro'),
      findsOneWidget,
    );
  });

  testWidgets('pro members do not see the Pro upsell', (tester) async {
    await _pump(tester, const ProfileScreen(), plan: AccountPlan.pro);
    expect(find.byType(UpgradeProButton), findsNothing);
  });

  testWidgets('settings keeps logout, delete and unblock', (tester) async {
    await _pump(tester, const SettingsScreen());
    expect(find.byType(Icon), findsNothing);
    expect(find.widgetWithText(PaperButton, 'Bỏ chặn'), findsOneWidget);
    expect(find.widgetWithText(PaperButton, 'Đăng xuất'), findsOneWidget);
    await tester.tap(find.text('Xoá tài khoản'));
    await tester.pumpAndSettle();
    expect(find.text('Xoá tài khoản?'), findsOneWidget);
  });

  testWidgets('pro page has a gold badge and paper buy buttons', (
    tester,
  ) async {
    await _pump(tester, const ProScreen());
    expect(find.byType(Icon), findsNothing);
    expect(find.byType(GoldBadge), findsOneWidget);
    expect(find.text('Bạn đang dùng gói Free'), findsOneWidget);
    expect(
      find.widgetWithText(PaperButton, 'Gói Pro 30 ngày · 49.000 đ'),
      findsOneWidget,
    );
  });

  for (final (name, screen) in [
    ('profile', const ProfileScreen()),
    ('settings', const SettingsScreen()),
    ('pro', const ProScreen()),
  ]) {
    testWidgets('$name fits a narrow phone at 1.5x text', (tester) async {
      await _pump(tester, screen, width: 360, textScale: 1.5);
      expect(tester.takeException(), isNull);
    });
  }
}
