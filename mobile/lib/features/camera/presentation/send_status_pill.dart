import 'dart:math' as math;

import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/core/network/error_messages.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/album/washi_photo_card.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/features/camera/presentation/upload_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:animal_locket/core/l10n/tr.dart';

/// Xanh lá sẫm cho "Đã gửi" (đạt AA trên nền giấy).
const _sentGreen = Color(0xFF2F6B3A);

/// Nhãn giấy dán washi nổi trên camera: "Đang gửi…" → "Đã gửi" (tự ẩn) hoặc lỗi
/// + "Thử lại" (spec §7.4). Nền giấy đục nên đọc rõ trên mọi ảnh.
class SendStatusPill extends ConsumerWidget {
  const SendStatusPill({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = context.album;
    final upload = ref.watch(uploadControllerProvider);
    final notifier = ref.read(uploadControllerProvider.notifier);
    final Widget content = switch (upload.stage) {
      UploadStage.idle => const SizedBox.shrink(key: ValueKey('idle')),
      UploadStage.sending => _Pill(
        key: const ValueKey('sending'),
        leading: SizedBox(
          width: 16,
          height: 16,
          child: CircularProgressIndicator(strokeWidth: 2, color: p.accent),
        ),
        text: context.l10n.sendSending,
        color: p.accent,
      ),
      UploadStage.sent => _Pill(
        key: const ValueKey('sent'),
        leading: const AlbumIcon(AlbumIcons.check, size: 18, color: _sentGreen),
        text: context.l10n.sendSent,
        color: _sentGreen,
        actionLabel: context.l10n.view,
        onAction: () {
          notifier.dismiss();
          context.go('/feed');
        },
      ),
      UploadStage.rejected => _Pill(
        key: const ValueKey('rejected'),
        leading: AlbumIcon(AlbumIcons.alert, size: 18, color: p.danger),
        text: context.l10n.sendRejected,
        color: p.danger,
        onClose: notifier.dismiss,
      ),
      UploadStage.failed => _Pill(
        key: const ValueKey('failed'),
        leading: AlbumIcon(AlbumIcons.cloudOff, size: 18, color: p.danger),
        text: _failureText(upload.error),
        color: p.danger,
        actionLabel: context.l10n.retry,
        onAction: notifier.retry,
        onClose: notifier.dismiss,
      ),
    };
    return AnimatedSwitcher(
      duration: MediaQuery.disableAnimationsOf(context)
          ? Duration.zero
          : const Duration(milliseconds: 220),
      child: content,
    );
  }

  static String _failureText(Object? error) => error is ApiProblem
      ? messageForApiCode(error.code, status: error.status)
      : tr.sendFailed;
}

class _Pill extends StatelessWidget {
  const _Pill({
    super.key,
    required this.leading,
    required this.text,
    required this.color,
    this.actionLabel,
    this.onAction,
    this.onClose,
  });

  final Widget leading;
  final String text;
  final Color color;
  final String? actionLabel;
  final VoidCallback? onAction;
  final VoidCallback? onClose;

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    return Semantics(
      liveRegion: true,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          DecoratedBox(
            decoration: BoxDecoration(
              color: p.paper,
              boxShadow: const [
                BoxShadow(
                  color: Color(0x33000000),
                  blurRadius: 8,
                  offset: Offset(0, 3),
                ),
              ],
            ),
            child: Material(
              type: MaterialType.transparency,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(14, 4, 4, 4),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: 40),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      leading,
                      const SizedBox(width: 8),
                      Flexible(
                        child: Text(
                          text,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: color,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                      if (actionLabel != null)
                        TextButton(
                          onPressed: onAction,
                          style: TextButton.styleFrom(foregroundColor: color),
                          child: Text(
                            actionLabel!,
                            style: TextStyle(
                              fontWeight: FontWeight.w800,
                              decoration: TextDecoration.underline,
                              decorationColor: color,
                            ),
                          ),
                        )
                      else
                        const SizedBox(width: 10),
                      if (onClose != null)
                        Semantics(
                          container: true,
                          label: context.l10n.close,
                          button: true,
                          excludeSemantics: true,
                          onTap: onClose,
                          child: InkResponse(
                            onTap: onClose,
                            radius: 24,
                            child: SizedBox.square(
                              dimension: 48,
                              child: Center(
                                child: AlbumIcon(
                                  AlbumIcons.close,
                                  size: 18,
                                  color: p.muted,
                                ),
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          Positioned(
            left: -8,
            top: -9,
            child: Transform.rotate(
              angle: -12 * math.pi / 180,
              child: Opacity(
                opacity: .85,
                child: CustomPaint(
                  size: const Size(34, 14),
                  painter: WashiTapePainter(p.tape1, p.tape2),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
