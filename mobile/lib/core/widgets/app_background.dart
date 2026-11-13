import 'dart:math' as math;

import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:flutter/material.dart';

/// Nền giấy theo theme, có vài khối giấy gấp xoay chéo tràn mép (spec §3).
class AppBackground extends StatelessWidget {
  const AppBackground({super.key, required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) =>
      CustomPaint(painter: FoldedPaperPainter(context.album), child: child);
}

class FoldedPaperPainter extends CustomPainter {
  const FoldedPaperPainter(this.palette);
  final AlbumPalette palette;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(Offset.zero & size, Paint()..color = palette.background);
    // Kích thước gốc theo mockup trên màn 340 điểm, co giãn theo bề rộng.
    final s = size.width / 340;
    _square(
      canvas,
      palette.fold1,
      Offset(size.width - 20 * s, 25 * s),
      190 * s,
      45,
    );
    _square(
      canvas,
      palette.fold2,
      Offset(-6 * s, size.height * .55),
      120 * s,
      28,
    );
    _square(
      canvas,
      palette.fold3,
      Offset(size.width + 5 * s, size.height - 85 * s),
      90 * s,
      60,
    );
  }

  void _square(
    Canvas canvas,
    Color color,
    Offset center,
    double side,
    double degrees,
  ) {
    canvas
      ..save()
      ..translate(center.dx, center.dy)
      ..rotate(degrees * math.pi / 180)
      ..drawRect(
        Rect.fromCenter(center: Offset.zero, width: side, height: side),
        Paint()..color = color,
      )
      ..restore();
  }

  @override
  bool shouldRepaint(FoldedPaperPainter oldDelegate) =>
      oldDelegate.palette != palette;
}
