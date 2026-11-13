import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/paper_list.dart';
import 'package:animal_locket/features/chat/domain/chat_repository.dart';
import 'package:animal_locket/features/chat/presentation/chat_controllers.dart';
import 'package:animal_locket/features/chat/presentation/chat_list_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'onboarding_test_harness.dart' show loadAvatarLibrary;

final _items = [
  Conversation(
    id: 'c1',
    withPublicId: 'foxfoxfoxfoxfoxfoxfo',
    status: 'ACTIVE',
    unread: 3,
    lastMessageAt: DateTime.utc(2026, 11, 10),
  ),
  const Conversation(
    id: 'c2',
    withPublicId: 'pandapandapandapanda',
    status: 'ACTIVE',
    unread: 0,
  ),
];

Future<void> _pump(
  WidgetTester tester,
  Future<List<Conversation>> Function() load, {
  double width = 390,
  double textScale = 1,
}) async {
  tester.view.physicalSize = Size(width, 844);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        conversationsProvider.overrideWith((_) => load()),
        chatProfileProvider.overrideWith(
          (_, id) async => PublicProfile(
            publicId: id,
            alias: id.startsWith('fox')
                ? 'Cáo Lém Lỉnh Có Cái Tên Rất Dài Để Thử Tràn Chữ'
                : 'Gấu Trúc',
            avatar: id.startsWith('fox') ? '1:fox:0:none' : '1:panda:3:none',
          ),
        ),
      ],
      child: MaterialApp(
        theme: AppTheme.forPalette(AlbumPalette.of(AlbumThemeId.shu)),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: const ChatListScreen(),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  setUp(loadAvatarLibrary);

  testWidgets('conversations on a ruled paper page with unread stamp', (
    tester,
  ) async {
    await _pump(tester, () async => _items);
    expect(find.text('Trò chuyện'), findsOneWidget);
    expect(find.text('Tin nhắn chỉ mở giữa bạn bè.'), findsOneWidget);
    expect(find.text('Gấu Trúc'), findsOneWidget);
    expect(find.text('Tin nhắn gần đây'), findsOneWidget);
    expect(find.text('Bắt đầu trò chuyện'), findsOneWidget);
    expect(find.byType(MiniPolaroidAvatar), findsNWidgets(2));
    expect(find.byType(DashedDivider), findsOneWidget);
    expect(find.bySemanticsLabel('3 tin chưa đọc'), findsOneWidget);
    expect(find.byType(Icon), findsNothing);
  });

  testWidgets('empty state keeps its text', (tester) async {
    await _pump(tester, () async => const []);
    expect(find.text('Chưa có cuộc trò chuyện nào.'), findsOneWidget);
  });

  testWidgets('error state offers a paper retry', (tester) async {
    await _pump(tester, () async => throw Exception('down'));
    expect(
      find.widgetWithText(PaperButton, 'Không tải được · Thử lại'),
      findsOneWidget,
    );
  });

  testWidgets('fits a narrow phone at 1.5x text', (tester) async {
    await _pump(tester, () async => _items, width: 360, textScale: 1.5);
    expect(tester.takeException(), isNull);
  });
}
