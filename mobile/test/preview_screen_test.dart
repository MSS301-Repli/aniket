import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/washi_photo_card.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/features/camera/presentation/preview_screen.dart';
import 'package:animal_locket/features/camera/presentation/upload_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

class _FakeUpload extends UploadController {
  _FakeUpload(this.initial);
  final UploadFlowState initial;

  @override
  UploadFlowState build() => initial;
}

Future<GoRouter> _pump(
  WidgetTester tester, {
  UploadStage stage = UploadStage.idle,
  double width = 390,
  double height = 844,
  double textScale = 1,
}) async {
  tester.view.physicalSize = Size(width, height);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final router = GoRouter(
    routes: [
      GoRoute(path: '/', builder: (_, _) => const Text('camera page')),
      GoRoute(path: '/preview', builder: (_, _) => const PreviewScreen()),
    ],
  );
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        uploadControllerProvider.overrideWith(
          () => _FakeUpload(UploadFlowState(stage: stage)),
        ),
      ],
      child: MaterialApp.router(
        theme: AppTheme.forPalette(AlbumPalette.of(AlbumThemeId.shu)),
        routerConfig: router,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
      ),
    ),
  );
  router.push('/preview');
  await tester.pumpAndSettle();
  return router;
}

void main() {
  testWidgets('a taped print keeps 4:5 even without a photo', (tester) async {
    await _pump(tester);
    expect(find.text('Xem trước'), findsOneWidget);
    final frame = find.byWidgetPredicate(
      (w) => w is AspectRatio && w.aspectRatio == 0.8,
    );
    expect(frame, findsOneWidget);
    expect(
      find.descendant(of: frame, matching: find.byType(AlbumIcon)),
      findsOneWidget,
    );
    expect(
      find.byWidgetPredicate(
        (w) => w is CustomPaint && w.painter is WashiTapePainter,
      ),
      findsWidgets,
    );
    // Only the app bar's system back arrow is a Material icon.
    expect(
      find.byType(Icon).evaluate().length,
      find
          .descendant(of: find.byType(BackButton), matching: find.byType(Icon))
          .evaluate()
          .length,
    );
  });

  testWidgets('the note is handwritten on the print, up to 200 characters', (
    tester,
  ) async {
    await _pump(tester);
    final field = tester.widget<TextField>(find.byType(TextField));
    expect(field.maxLength, 200);
    expect(field.decoration?.labelText, 'Lời nhắn');
    expect(field.decoration?.hintText, 'Viết gì đó cho bạn bè…');
    expect(field.style?.fontFamily, AppFonts.hand);
  });

  testWidgets('the counter turns red at the 200-character limit', (
    tester,
  ) async {
    await _pump(tester);
    Color? counterColor(String text) =>
        tester.widget<Text>(find.text(text)).style?.color;
    await tester.enterText(find.byType(TextField), 'a' * 199);
    await tester.pump();
    final normal = counterColor('199/200');
    await tester.enterText(find.byType(TextField), 'a' * 200);
    await tester.pump();
    final full = counterColor('200/200');
    expect(full, isNot(normal));
    expect(full, AlbumPalette.of(AlbumThemeId.shu).danger);
  });

  testWidgets('enter closes the keyboard instead of adding a line', (
    tester,
  ) async {
    await _pump(tester);
    final field = tester.widget<TextField>(find.byType(TextField));
    expect(field.textInputAction, TextInputAction.done);
    expect(field.keyboardType, TextInputType.text);
  });

  testWidgets('send button and the retake label', (tester) async {
    final router = await _pump(tester);
    final send = tester.widget<PaperButton>(
      find.widgetWithText(PaperButton, 'Gửi cho bạn bè'),
    );
    expect(send.icon, AlbumIcons.send);
    expect(send.onPressed, isNotNull);
    // Chụp lại (có icon) bên trái, gửi bên phải trên cùng một hàng.
    final retake = find.widgetWithText(PaperButton, 'Chụp lại');
    expect(tester.widget<PaperButton>(retake).icon, AlbumIcons.camera);
    expect(
      tester.getCenter(retake).dy,
      closeTo(
        tester.getCenter(find.widgetWithText(PaperButton, 'Gửi cho bạn bè')).dy,
        1,
      ),
    );
    expect(
      tester.getCenter(retake).dx,
      lessThan(
        tester.getCenter(find.widgetWithText(PaperButton, 'Gửi cho bạn bè')).dx,
      ),
    );
    expect(
      find.text(
        'Ảnh được gửi cho toàn bộ bạn bè. Bản đầu chưa hỗ trợ chọn từng người nhận.',
      ),
      findsOneWidget,
    );
    await tester.tap(find.text('Chụp lại'));
    await tester.pumpAndSettle();
    expect(router.state.uri.path, '/');
  });

  testWidgets('while another photo is sending, send is off', (tester) async {
    await _pump(tester, stage: UploadStage.sending);
    final send = tester.widget<PaperButton>(
      find.widgetWithText(PaperButton, 'Đang gửi ảnh trước…'),
    );
    expect(send.onPressed, isNull);
  });

  testWidgets('fits a narrow phone at 1.5x text', (tester) async {
    await _pump(tester, width: 360, height: 640, textScale: 1.5);
    expect(tester.takeException(), isNull);
  });
}
