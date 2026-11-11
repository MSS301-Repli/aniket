import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/paper_tile_button.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/features/camera/presentation/camera_screen.dart';
import 'package:animal_locket/features/camera/presentation/polaroid_viewfinder.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

const _permissions = MethodChannel('flutter.baseflow.com/permissions/methods');

Future<List<String>> _pump(
  WidgetTester tester, {
  double width = 390,
  double height = 844,
  double textScale = 1,
}) async {
  final calls = <String>[];
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(_permissions, (
    call,
  ) async {
    calls.add(call.method);
    // 0 = denied: the screen shows the permission card, no camera plugin needed.
    return switch (call.method) {
      'checkPermissionStatus' => 0,
      'requestPermissions' => {1: 0},
      _ => null,
    };
  });
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      _permissions,
      null,
    ),
  );
  tester.view.physicalSize = Size(width, height);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      child: MaterialApp(
        theme: AppTheme.forPalette(AlbumPalette.of(AlbumThemeId.shu)),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: const CameraScreen(),
      ),
    ),
  );
  await tester.pump();
  await tester.pump();
  return calls;
}

void main() {
  testWidgets('permission card inside the photo, with the corrected copy', (
    tester,
  ) async {
    await _pump(tester);
    expect(find.text('Khoảnh khắc mới'), findsOneWidget);
    expect(find.text('Cho phép camera để chụp khoảnh khắc.'), findsOneWidget);
    expect(find.textContaining('khuôn mặt'), findsNothing);
    expect(
      find.descendant(
        of: find.byType(PolaroidViewfinder),
        matching: find.widgetWithText(PaperButton, 'Cho phép camera'),
      ),
      findsOneWidget,
    );
    expect(find.byType(Icon), findsNothing);
  });

  testWidgets('"Cho phép camera" asks for the permission', (tester) async {
    final calls = await _pump(tester);
    await tester.tap(find.text('Cho phép camera'));
    await tester.pump();
    expect(calls, contains('requestPermissions'));
  });

  testWidgets('gallery and flip are named paper tiles', (tester) async {
    await _pump(tester);
    final tiles = tester
        .widgetList<PaperTileButton>(find.byType(PaperTileButton))
        .map((t) => (t.label, t.icon))
        .toList();
    expect(tiles, [
      ('Chọn từ thư viện', AlbumIcons.gallery),
      ('Lật camera', AlbumIcons.flipCamera),
    ]);
  });

  testWidgets('shutter and flash are off until the camera is ready', (
    tester,
  ) async {
    await _pump(tester);
    expect(
      tester.getSemantics(find.bySemanticsLabel('Chụp ảnh')),
      isSemantics(isButton: true, hasEnabledState: true, isEnabled: false),
    );
    expect(
      tester.getSemantics(find.bySemanticsLabel('Đổi chế độ đèn flash')),
      isSemantics(isButton: true, hasEnabledState: true, isEnabled: false),
    );
    expect(
      tester
          .widgetList<AlbumIcon>(find.byType(AlbumIcon))
          .any((i) => i.icon == AlbumIcons.flashAuto),
      isTrue,
    );
  });

  testWidgets('permission card sits in the middle of the photo', (
    tester,
  ) async {
    await _pump(tester);
    final frame = tester.getRect(find.byType(PolaroidViewfinder));
    final text = tester.getCenter(
      find.text('Cho phép camera để chụp khoảnh khắc.'),
    );
    expect(text.dy, closeTo(frame.center.dy, frame.height * .12));
  });

  testWidgets('fits a short narrow phone at 1.5x text', (tester) async {
    await _pump(tester, width: 360, height: 640, textScale: 1.5);
    expect(tester.takeException(), isNull);
  });
}
