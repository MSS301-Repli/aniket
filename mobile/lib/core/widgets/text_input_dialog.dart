import 'package:flutter/material.dart';
import 'package:animal_locket/core/l10n/tr.dart';

/// Hộp thoại nhập một dòng chữ, trả về nội dung khi bấm xác nhận (null khi huỷ).
///
/// Tự giữ [TextEditingController] và chỉ huỷ trong [State.dispose]. Đừng tạo controller ở
/// nơi gọi rồi `dispose()` ngay sau `await showDialog`: hộp thoại còn đang chạy hiệu ứng đóng
/// nên ô nhập vẫn dùng controller → lỗi `'_dependents.isEmpty': is not true`.
class TextInputDialog extends StatefulWidget {
  const TextInputDialog({
    super.key,
    required this.title,
    required this.label,
    required this.confirmLabel,
    this.initialText = '',
    this.maxLength,
    this.cancelLabel,
  });

  final String title;
  final String label;
  final String confirmLabel;
  final String? cancelLabel;
  final String initialText;
  final int? maxLength;

  @override
  State<TextInputDialog> createState() => _TextInputDialogState();
}

class _TextInputDialogState extends State<TextInputDialog> {
  late final TextEditingController _controller = TextEditingController(
    text: widget.initialText,
  );

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _confirm() => Navigator.pop(context, _controller.text);

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(widget.title),
    content: TextField(
      controller: _controller,
      autofocus: true,
      maxLength: widget.maxLength,
      decoration: InputDecoration(labelText: widget.label),
      onSubmitted: (_) => _confirm(),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: Text(widget.cancelLabel ?? context.l10n.cancel),
      ),
      FilledButton(onPressed: _confirm, child: Text(widget.confirmLabel)),
    ],
  );
}
