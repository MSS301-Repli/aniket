import 'dart:math' as math;

import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:flutter/material.dart';

/// Ảnh in dán vào sổ: viền giấy, bóng mềm, hơi nghiêng, băng washi ở mép trên
/// (spec §3, mockup combined-v1).
class WashiPhotoCard extends StatelessWidget {
  const WashiPhotoCard({
    super.key,
    required this.photo,
    required this.header,
    this.caption,
    this.footer,
    this.tiltDegrees = -1.4,
  });

  final Widget photo;
  final Widget header;
  final Widget? caption;
  final Widget? footer;
  final double tiltDegrees;

  /// Nghiêng xen kẽ để cả trang trông như ảnh dán tay.
  static double tiltFor(int index) => index.isEven ? -1.4 : 1.2;

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    return Transform.rotate(
      angle: tiltDegrees * math.pi / 180,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          DecoratedBox(
            decoration: BoxDecoration(
              color: p.paper,
              boxShadow: const [
                BoxShadow(
                  color: Color(0x245A3C1E),
                  blurRadius: 16,
                  offset: Offset(0, 6),
                ),
              ],
            ),
            // Material trong suốt nằm trên lớp giấy để InkWell/InkResponse
            // bên trong (hàng tác giả, con dấu) hiện hiệu ứng gợn khi chạm.
            child: Material(
              type: MaterialType.transparency,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(8, 8, 8, 6),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    // Khung ảnh luôn 4:5 nhưng co lại khi chiều cao không đủ,
                    // để cả thẻ vừa một trang. Thẻ cần chiều cao có giới hạn.
                    Flexible(
                      child: Align(
                        heightFactor: 1,
                        child: AspectRatio(aspectRatio: 4 / 5, child: photo),
                      ),
                    ),
                    const SizedBox(height: 4),
                    header,
                    if (caption != null) ...[
                      const SizedBox(height: 2),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: caption!,
                      ),
                    ],
                    if (footer != null) ...[const SizedBox(height: 6), footer!],
                  ],
                ),
              ),
            ),
          ),
          Positioned(
            top: -12,
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

/// Băng giấy washi sọc chéo 45°, mỗi sọc 6px.
class WashiTapePainter extends CustomPainter {
  const WashiTapePainter(this.color1, this.color2);
  final Color color1;
  final Color color2;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    canvas
      ..save()
      ..clipRect(rect)
      ..drawRect(rect, Paint()..color = color2);
    final stripe = Paint()
      ..color = color1
      ..strokeWidth = 6 / math.sqrt2 * 2;
    for (double x = -size.height; x < size.width + size.height; x += 12) {
      canvas.drawLine(
        Offset(x, size.height),
        Offset(x + size.height, 0),
        stripe,
      );
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(WashiTapePainter oldDelegate) =>
      oldDelegate.color1 != color1 || oldDelegate.color2 != color2;
}
