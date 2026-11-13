import 'dart:math' as math;

import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:flutter/material.dart';

/// Vệt cọ khô: một nhát sơn quét chéo, thân đặc màu, hai đầu và mép tưa
/// thành sợi cọ, vài hạt sơn văng. Màu lấy từ palette đang chọn nên đổi theme
/// là đổi màu theo. [seed] cố định hình dạng (không nhấp nháy khi vẽ lại).
class WatercolorWash extends StatelessWidget {
  const WatercolorWash({super.key, required this.size, this.seed = 0});
  final Size size;
  final int seed;

  @override
  Widget build(BuildContext context) => RepaintBoundary(
    child: CustomPaint(
      size: size,
      painter: _BrushPainter(context.album.tape1, seed),
    ),
  );
}

class _BrushPainter extends CustomPainter {
  const _BrushPainter(this.color, this.seed);
  final Color color;
  final int seed;

  @override
  void paint(Canvas canvas, Size size) {
    final rnd = math.Random(seed * 131 + 7);
    double r(double a, double b) => a + rnd.nextDouble() * (b - a);
    final w = size.width, h = size.height;
    final paint = Paint()..color = color.withValues(alpha: .78);

    // Nhát cọ hơi dốc lên về bên phải.
    canvas.save();
    canvas.translate(w / 2, h / 2);
    canvas.rotate(-10 * math.pi / 180);
    canvas.translate(-w / 2, -h / 2);

    // Thân: mép trên/dưới gợn nhẹ, hai đầu vát lệch nhau như nét quét.
    final top = h * .24, bottom = h * .78;
    final body = Path()..moveTo(w * .16, top + r(0, 2));
    for (var x = w * .2; x <= w * .84; x += w * .06) {
      body.lineTo(x, top + r(-1.2, 1.6));
    }
    body
      ..lineTo(w * .9, top + h * .1)
      ..lineTo(w * .86, bottom - h * .08);
    for (var x = w * .8; x >= w * .12; x -= w * .06) {
      body.lineTo(x, bottom + r(-1.6, 1.2));
    }
    body
      ..lineTo(w * .08, bottom - h * .12)
      ..close();
    canvas.drawPath(body, paint);

    // Sợi cọ tưa ra ở hai đầu: vạch mảnh dài ngắn khác nhau theo hướng quét.
    final streak = Paint()
      ..color = paint.color
      ..strokeCap = StrokeCap.round;
    for (var i = 0; i < 16; i++) {
      final y = r(top + 1, bottom - 1);
      final right = i.isEven;
      final start = right ? r(w * .78, w * .86) : r(w * .1, w * .2);
      final len = r(3, w * .16);
      canvas.drawLine(
        Offset(start, y),
        Offset(right ? start + len : start - len, y + r(-1, 1)),
        streak..strokeWidth = r(.7, 2.2),
      );
    }
    // Vài sợi bám dọc mép trên/dưới.
    for (var i = 0; i < 8; i++) {
      final onTop = i.isEven;
      final y = onTop ? top - r(.5, 2.5) : bottom + r(.5, 2.5);
      final x = r(w * .25, w * .75);
      canvas.drawLine(
        Offset(x, y),
        Offset(x + r(4, 12), y),
        streak..strokeWidth = r(.6, 1.4),
      );
    }
    // Hạt sơn văng nhỏ gần hai đầu.
    for (var i = 0; i < 5; i++) {
      final right = i.isOdd;
      canvas.drawCircle(
        Offset(right ? r(w * .9, w) : r(0, w * .1), r(top, bottom)),
        r(.6, 1.3),
        paint,
      );
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(_BrushPainter o) => o.color != color || o.seed != seed;
}
