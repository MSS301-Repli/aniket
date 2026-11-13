import 'package:animal_locket/core/auth/auth_controller.dart';
import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/core/network/error_messages.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/polaroid.dart';
import 'package:animal_locket/core/widgets/animal_avatar.dart';
import 'package:animal_locket/features/auth/presentation/sign_in_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'onboarding_test_harness.dart';

void main() {
  setUp(loadAvatarLibrary);

  testWidgets('album cover keeps the copy, with the corrected promise', (
    tester,
  ) async {
    await pumpOnboarding(
      tester,
      const SignInScreen(),
      FakeAuth(const AuthState.unauthenticated()),
    );
    expect(find.text('Gần nhau hơn,\nvẫn là chính mình.'), findsOneWidget);
    expect(
      find.text(
        'Chia sẻ khoảnh khắc với bạn bè. Mỗi người là một con vật duy nhất, không lộ tên thật.',
      ),
      findsOneWidget,
    );
    expect(find.textContaining('Khuôn mặt'), findsNothing);
    expect(
      find.text(
        'Chúng tôi không hiển thị tên thật, email hay vị trí chính xác của bạn.',
      ),
      findsOneWidget,
    );
    expect(find.byType(Icon), findsNothing);
  });

  testWidgets('two origami polaroids: fox and panda, captioned by name', (
    tester,
  ) async {
    await pumpOnboarding(
      tester,
      const SignInScreen(),
      FakeAuth(const AuthState.unauthenticated()),
    );
    final avatars = tester
        .widgetList<AnimalAvatar>(
          find.descendant(
            of: find.byType(Polaroid),
            matching: find.byType(AnimalAvatar),
          ),
        )
        .toList();
    expect(avatars.map((a) => a.avatar), ['1:fox:0:none', '1:panda:3:none']);
    expect(avatars.every((a) => a.square), isTrue);
    expect(find.text('Cáo'), findsOneWidget);
    expect(find.text('Gấu trúc'), findsOneWidget);
  });

  testWidgets('paper buttons sign in and open the demo', (tester) async {
    final auth = FakeAuth(const AuthState.unauthenticated());
    await pumpOnboarding(tester, const SignInScreen(), auth);
    expect(find.byType(PaperButton), findsNWidgets(2));
    await tester.tap(find.text('Đăng nhập bằng email'));
    await tester.tap(find.text('Mở tài khoản demo'));
    expect(auth.calls, ['signIn', 'enterDemo']);
  });

  testWidgets('while checking both buttons are off and say so', (tester) async {
    final auth = FakeAuth(const AuthState.checking());
    await pumpOnboarding(tester, const SignInScreen(), auth);
    expect(find.text('Đang mở đăng nhập…'), findsOneWidget);
    for (final b in tester.widgetList<PaperButton>(find.byType(PaperButton))) {
      expect(b.onPressed, isNull, reason: b.label);
    }
  });

  testWidgets('shows a generic sign-in error', (tester) async {
    await pumpOnboarding(
      tester,
      const SignInScreen(),
      FakeAuth(const AuthState.unauthenticated(error: 'timeout')),
    );
    expect(find.text('Đăng nhập thất bại: timeout'), findsOneWidget);
  });

  testWidgets('shows the friendly message for an API problem', (tester) async {
    const problem = ApiProblem(status: 503, code: 'UNAVAILABLE');
    await pumpOnboarding(
      tester,
      const SignInScreen(),
      FakeAuth(const AuthState.unauthenticated(error: problem)),
    );
    expect(find.textContaining('Đăng nhập thất bại'), findsNothing);
    expect(
      find.text(messageForApiCode(problem.code, status: problem.status)),
      findsOneWidget,
    );
  });

  testWidgets('tilted polaroids are not cut off by their box', (tester) async {
    await pumpOnboarding(
      tester,
      const SignInScreen(),
      FakeAuth(const AuthState.unauthenticated()),
    );
    final box = tester.widget<Stack>(
      find
          .ancestor(
            of: find.byType(Polaroid).first,
            matching: find.byType(Stack),
          )
          .first,
    );
    expect(box.clipBehavior, Clip.none);
  });

  testWidgets('fits a narrow phone at 1.5x text', (tester) async {
    await pumpOnboarding(
      tester,
      const SignInScreen(),
      FakeAuth(const AuthState.unauthenticated()),
      width: 360,
      height: 640,
      textScale: 1.5,
    );
    expect(tester.takeException(), isNull);
  });
}
