import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/core/network/error_messages.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/washi_photo_card.dart';
import 'package:animal_locket/features/camera/presentation/send_status_pill.dart';
import 'package:animal_locket/features/camera/presentation/upload_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

final _palette = AlbumPalette.of(AlbumThemeId.shu);

class _FakeUpload extends UploadController {
  _FakeUpload(this.initial);
  final UploadFlowState initial;
  final calls = <String>[];

  @override
  UploadFlowState build() => initial;

  @override
  Future<void> retry() async => calls.add('retry');

  @override
  void dismiss() => calls.add('dismiss');
}

Future<(_FakeUpload, GoRouter)> _pump(
  WidgetTester tester,
  UploadFlowState state,
) async {
  final fake = _FakeUpload(state);
  final router = GoRouter(
    routes: [
      GoRoute(
        path: '/',
        builder: (_, _) => const Scaffold(
          body: ColoredBox(
            // A dark photo underneath: the label must stay readable on it.
            color: Colors.black,
            child: Center(child: SendStatusPill()),
          ),
        ),
      ),
      GoRoute(path: '/feed', builder: (_, _) => const Text('feed page')),
    ],
  );
  await tester.pumpWidget(
    ProviderScope(
      overrides: [uploadControllerProvider.overrideWith(() => fake)],
      child: MaterialApp.router(
        theme: AppTheme.forPalette(_palette),
        routerConfig: router,
      ),
    ),
  );
  await tester.pumpAndSettle();
  return (fake, router);
}

Finder get _paper => find.byWidgetPredicate(
  (w) =>
      w is DecoratedBox &&
      w.decoration is BoxDecoration &&
      (w.decoration as BoxDecoration).color == _palette.paper,
);

void main() {
  testWidgets('idle shows nothing', (tester) async {
    await _pump(tester, const UploadFlowState());
    expect(_paper, findsNothing);
  });

  testWidgets('sending: an opaque taped paper label, read out live', (
    tester,
  ) async {
    await tester.pumpWidget(const SizedBox());
    final fake = _FakeUpload(const UploadFlowState(stage: UploadStage.sending));
    await tester.pumpWidget(
      ProviderScope(
        overrides: [uploadControllerProvider.overrideWith(() => fake)],
        child: MaterialApp(
          theme: AppTheme.forPalette(_palette),
          home: const Scaffold(body: Center(child: SendStatusPill())),
        ),
      ),
    );
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Đang gửi…'), findsOneWidget);
    expect(_paper, findsOneWidget);
    expect(
      find.byWidgetPredicate(
        (w) => w is CustomPaint && w.painter is WashiTapePainter,
      ),
      findsOneWidget,
    );
    expect(
      find.byWidgetPredicate(
        (w) => w is Semantics && w.properties.liveRegion == true,
      ),
      findsOneWidget,
    );
    expect(find.byType(Icon), findsNothing);
  });

  testWidgets('sent: "Xem" dismisses and opens the feed', (tester) async {
    final (fake, router) = await _pump(
      tester,
      const UploadFlowState(stage: UploadStage.sent),
    );
    expect(find.text('Đã gửi'), findsOneWidget);
    await tester.tap(find.text('Xem'));
    await tester.pumpAndSettle();
    expect(fake.calls, ['dismiss']);
    expect(router.state.uri.path, '/feed');
  });

  testWidgets('rejected: can be closed', (tester) async {
    final (fake, _) = await _pump(
      tester,
      const UploadFlowState(stage: UploadStage.rejected),
    );
    expect(find.text('Ảnh không được chấp nhận'), findsOneWidget);
    await tester.tap(find.bySemanticsLabel('Đóng'));
    expect(fake.calls, ['dismiss']);
    expect(find.byType(Icon), findsNothing);
  });

  testWidgets('failed: shows the reason, retries and closes', (tester) async {
    const problem = ApiProblem(status: 413, code: 'PAYLOAD_TOO_LARGE');
    final (fake, _) = await _pump(
      tester,
      const UploadFlowState(stage: UploadStage.failed, error: problem),
    );
    expect(
      find.text(messageForApiCode(problem.code, status: problem.status)),
      findsOneWidget,
    );
    await tester.tap(find.text('Thử lại'));
    await tester.tap(find.bySemanticsLabel('Đóng'));
    expect(fake.calls, ['retry', 'dismiss']);
  });

  testWidgets('close is a 48pt target', (tester) async {
    await _pump(tester, const UploadFlowState(stage: UploadStage.rejected));
    final size = tester.getSize(find.bySemanticsLabel('Đóng'));
    expect(size.width, greaterThanOrEqualTo(48));
    expect(size.height, greaterThanOrEqualTo(48));
  });

  testWidgets('failed without an API problem asks to check the network', (
    tester,
  ) async {
    await _pump(
      tester,
      const UploadFlowState(stage: UploadStage.failed, error: 'boom'),
    );
    expect(find.text('Gửi lỗi, kiểm tra mạng'), findsOneWidget);
  });
}
