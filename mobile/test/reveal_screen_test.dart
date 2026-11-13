import 'dart:async';

import 'package:animal_locket/core/auth/auth_controller.dart';
import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/core/network/error_messages.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/polaroid.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/animal_avatar.dart';
import 'package:animal_locket/features/auth/presentation/reveal_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'onboarding_test_harness.dart';

final _rabbit = MeProfile(
  publicId: 'abcdefghijklmnopqrst',
  alias: 'Mây Tinh Nghịch',
  leadAnimal: 'rabbit',
  avatar: '1:rabbit:6:bow',
  ageGroup: AgeGroup.adult,
  discoverable: true,
  plan: AccountPlan.free,
  createdAt: DateTime.utc(2026, 11, 9),
);

Future<FakeAuth> _pump(
  WidgetTester tester, {
  Future<MeProfile> Function()? register,
  double width = 390,
  double height = 844,
  double textScale = 1,
}) async {
  final auth = FakeAuth(
    const AuthState.needsProfile(),
    register: register ?? () async => _rabbit,
  );
  await pumpOnboarding(
    tester,
    const RevealScreen(birthDate: '2004-03-14'),
    auth,
    width: width,
    height: height,
    textScale: textScale,
  );
  return auth;
}

Polaroid _polaroid(WidgetTester tester) =>
    tester.widget<Polaroid>(find.byType(Polaroid));

void main() {
  setUp(loadAvatarLibrary);

  testWidgets('before the spin: an undeveloped polaroid and the alias line', (
    tester,
  ) async {
    await _pump(tester);
    expect(find.text('AVATAR CỦA RIÊNG BẠN'), findsOneWidget);
    expect(_polaroid(tester).caption, 'ảnh chưa rửa…');
    expect(find.text('?'), findsOneWidget);
    expect(find.text('Sẵn sàng nhận con vật?'), findsOneWidget);
    expect(
      find.text('Biệt danh của bạn không cần là tên thật.'),
      findsOneWidget,
    );
    expect(find.text('Mây Tinh Nghịch'), findsOneWidget);
    expect(find.text('Quay avatar'), findsOneWidget);
    expect(find.byType(Icon), findsNothing);
  });

  testWidgets('the dice offers another alias', (tester) async {
    await _pump(tester);
    final dice = find.bySemanticsLabel('Tạo biệt danh ngẫu nhiên');
    expect(dice, findsOneWidget);
    expect(
      tester
          .widgetList<AlbumIcon>(find.byType(AlbumIcon))
          .any((i) => i.icon == AlbumIcons.dice),
      isTrue,
    );
    final aliases = <String>{};
    for (var i = 0; i < 20; i++) {
      await tester.tap(dice);
      await tester.pump();
      aliases.add(
        tester.widget<EditableText>(find.byType(EditableText)).controller.text,
      );
    }
    expect(aliases.length, greaterThan(1));
  });

  testWidgets('spinning develops the polaroid into the square avatar', (
    tester,
  ) async {
    final auth = await _pump(tester);
    await tester.tap(find.text('Quay avatar'));
    await tester.pump();
    expect(auth.calls, ['register 2004-03-14 Mây Tinh Nghịch']);
    // Mid-spin the button is off.
    expect(
      tester.widget<PaperButton>(find.byType(PaperButton)).onPressed,
      isNull,
    );
    await tester.pump(const Duration(seconds: 2));
    final avatar = tester.widget<AnimalAvatar>(
      find.descendant(
        of: find.byType(Polaroid),
        matching: find.byType(AnimalAvatar),
      ),
    );
    expect(avatar.avatar, '1:rabbit:6:bow');
    expect(avatar.square, isTrue);
    expect(_polaroid(tester).caption, 'Thỏ');
    expect(find.text('Chào Mây Tinh Nghịch'), findsOneWidget);
    expect(find.text('Thỏ · không ai khác có avatar này'), findsOneWidget);
    await tester.tap(find.text('Vào app'));
    expect(auth.calls.last, 'finish');
  });

  testWidgets('reduced motion skips the spin', (tester) async {
    await _pump(tester);
    tester.binding.platformDispatcher.accessibilityFeaturesTestValue =
        const FakeAccessibilityFeatures(disableAnimations: true);
    addTearDown(
      tester.binding.platformDispatcher.clearAccessibilityFeaturesTestValue,
    );
    await tester.pump();
    await tester.tap(find.text('Quay avatar'));
    await tester.pump();
    await tester.pump();
    expect(find.text('Thỏ · không ai khác có avatar này'), findsOneWidget);
    expect(
      tester.widget<PaperButton>(find.byType(PaperButton)).onPressed,
      isNotNull,
    );
  });

  testWidgets('an API problem shows under the alias line', (tester) async {
    const problem = ApiProblem(status: 409, code: 'ALIAS_TAKEN');
    await _pump(tester, register: () => Future.error(problem));
    await tester.tap(find.text('Quay avatar'));
    await tester.pump();
    await tester.pump();
    expect(
      find.text(messageForApiCode(problem.code, status: problem.status)),
      findsOneWidget,
    );
    expect(_polaroid(tester).caption, 'ảnh chưa rửa…');
  });

  testWidgets('while registering the button says so and is off', (
    tester,
  ) async {
    final pending = Completer<MeProfile>();
    await _pump(tester, register: () => pending.future);
    await tester.tap(find.text('Quay avatar'));
    await tester.pump();
    expect(find.text('Đang đăng ký…'), findsOneWidget);
    expect(
      tester.widget<PaperButton>(find.byType(PaperButton)).onPressed,
      isNull,
    );
  });

  testWidgets('fits a narrow phone at 1.5x text', (tester) async {
    await _pump(tester, width: 360, height: 640, textScale: 1.5);
    expect(tester.takeException(), isNull);
  });
}
