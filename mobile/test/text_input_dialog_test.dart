import 'package:animal_locket/core/widgets/text_input_dialog.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  /// Mở hộp thoại; kết quả ghi vào [result] khi hộp thoại đóng.
  Future<void> open(WidgetTester tester, List<String?> result) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => TextButton(
            onPressed: () async {
              result.add(
                await showDialog<String>(
                  context: context,
                  builder: (_) => const TextInputDialog(
                    title: 'Đổi biệt danh',
                    label: 'Biệt danh',
                    confirmLabel: 'Lưu',
                    initialText: 'Cáo Vui',
                    maxLength: 20,
                  ),
                ),
              );
            },
            child: const Text('mở'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('mở'));
    await tester.pumpAndSettle();
  }

  testWidgets('trả về chữ đã nhập và đóng êm (không lỗi _dependents)', (
    tester,
  ) async {
    final result = <String?>[];
    await open(tester, result);
    expect(find.text('Cáo Vui'), findsOneWidget);

    await tester.enterText(find.byType(TextField), 'Mèo Mơ Màng');
    await tester.tap(find.text('Lưu'));
    // Chạy hết hiệu ứng đóng: trước đây controller bị huỷ giữa chừng ở đây.
    await tester.pumpAndSettle();

    expect(result, ['Mèo Mơ Màng']);
    expect(find.byType(TextInputDialog), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('bấm Huỷ trả về null', (tester) async {
    final result = <String?>[];
    await open(tester, result);
    await tester.tap(find.text('Huỷ'));
    await tester.pumpAndSettle();
    expect(result, [null]);
    expect(tester.takeException(), isNull);
  });
}
