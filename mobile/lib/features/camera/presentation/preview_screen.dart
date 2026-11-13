import 'dart:math' as math;
import 'dart:typed_data';

import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/washi_photo_card.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/app_page.dart';
import 'package:animal_locket/features/camera/presentation/upload_controller.dart';
import 'package:cross_file/cross_file.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:animal_locket/core/l10n/tr.dart';

/// Xem trước: ảnh in dán băng, lời nhắn viết tay ở lề dưới (spec §7.4).
class PreviewScreen extends ConsumerStatefulWidget {
  const PreviewScreen({super.key, this.imagePath});
  final String? imagePath;

  @override
  ConsumerState<PreviewScreen> createState() => _PreviewScreenState();
}

const _maxCaption = 200;

class _PreviewScreenState extends ConsumerState<PreviewScreen> {
  final _caption = TextEditingController();

  @override
  void dispose() {
    _caption.dispose();
    super.dispose();
  }

  /// Gửi chạy nền rồi quay về camera ngay; trạng thái "Đang gửi / Đã gửi" hiện ở màn hình camera.
  void _publish() {
    final path = widget.imagePath;
    if (path == null) return;
    final started = ref
        .read(uploadControllerProvider.notifier)
        .publish(input: XFile(path), caption: _caption.text);
    if (!started) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(context.l10n.previewSendingFirst)));
      return;
    }
    context.pop();
  }

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    final sending = ref.watch(uploadControllerProvider).sending;
    final photo = widget.imagePath == null
        ? ColoredBox(
            color: p.fold2,
            child: Center(child: AlbumIcon(AlbumIcons.image, color: p.muted)),
          )
        : FutureBuilder<List<int>>(
            future: XFile(widget.imagePath!).readAsBytes(),
            builder: (context, snapshot) {
              if (!snapshot.hasData) {
                return ColoredBox(
                  color: p.fold2,
                  child: const Center(child: CircularProgressIndicator()),
                );
              }
              return Image.memory(
                Uint8List.fromList(snapshot.data!),
                fit: BoxFit.cover,
              );
            },
          );
    return AppPage(
      title: context.l10n.previewTitle,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 12),
          _TapedPrint(
            photo: photo,
            note: TextField(
              controller: _caption,
              maxLength: _maxCaption,
              // Đạt giới hạn thì bộ đếm chuyển màu đỏ.
              buildCounter:
                  (
                    context, {
                    required currentLength,
                    required isFocused,
                    maxLength,
                  }) => Text(
                    '$currentLength/$maxLength',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: currentLength >= _maxCaption
                          ? FontWeight.w700
                          : FontWeight.w400,
                      color: currentLength >= _maxCaption ? p.danger : p.muted,
                    ),
                  ),
              minLines: 1,
              maxLines: 3,
              // Xuống dòng để hiện hết chữ, nhưng Enter vẫn là "xong" như trước.
              keyboardType: TextInputType.text,
              textInputAction: TextInputAction.done,
              style: TextStyle(
                fontFamily: AppFonts.hand,
                fontSize: 21,
                height: 1.2,
                color: p.ink,
              ),
              decoration: InputDecoration(
                labelText: context.l10n.messageLabel,
                hintText: context.l10n.messageHintFriends,
                hintStyle: TextStyle(
                  fontFamily: AppFonts.hand,
                  fontSize: 21,
                  color: p.muted,
                ),
                filled: false,
                isDense: true,
                border: UnderlineInputBorder(
                  borderSide: BorderSide(color: p.fold2, width: 1.5),
                ),
                enabledBorder: UnderlineInputBorder(
                  borderSide: BorderSide(color: p.fold2, width: 1.5),
                ),
                focusedBorder: UnderlineInputBorder(
                  borderSide: BorderSide(color: p.accent, width: 1.5),
                ),
              ),
            ),
          ),
          const SizedBox(height: 24),
          // Chụp lại bên trái, gửi (nút chính) bên phải.
          Row(
            children: [
              // Co lại (chữ xuống dòng) khi chữ to trên máy hẹp.
              Flexible(
                child: PaperButton.tape(
                  label: context.l10n.retake,
                  icon: AlbumIcons.camera,
                  onPressed: () => context.pop(),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: PaperButton(
                  label: sending
                      ? context.l10n.sendingPhoto
                      : context.l10n.sendToFriends,
                  icon: AlbumIcons.send,
                  onPressed: sending ? null : _publish,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Text(
            context.l10n.previewNote,
            textAlign: TextAlign.center,
            style: TextStyle(color: p.muted, fontSize: 11),
          ),
        ],
      ),
    );
  }
}

/// Ảnh in 4:5 trên khung giấy, nghiêng nhẹ, băng washi ở mép trên; lời nhắn ở lề dưới.
class _TapedPrint extends StatelessWidget {
  const _TapedPrint({required this.photo, required this.note});
  final Widget photo;
  final Widget note;

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    return Transform.rotate(
      angle: -1.2 * math.pi / 180,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          DecoratedBox(
            decoration: BoxDecoration(
              color: p.paper,
              boxShadow: const [
                BoxShadow(
                  color: Color(0x2A5A3C1E),
                  blurRadius: 16,
                  offset: Offset(0, 6),
                ),
              ],
            ),
            child: Material(
              type: MaterialType.transparency,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    AspectRatio(
                      aspectRatio: 4 / 5,
                      child: ClipRect(child: photo),
                    ),
                    const SizedBox(height: 6),
                    note,
                  ],
                ),
              ),
            ),
          ),
          Positioned(
            top: -11,
            left: 0,
            right: 0,
            child: Center(
              child: Transform.rotate(
                angle: 3 * math.pi / 180,
                child: Opacity(
                  opacity: .85,
                  child: CustomPaint(
                    size: const Size(86, 24),
                    painter: WashiTapePainter(p.tape1, p.tape2),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
