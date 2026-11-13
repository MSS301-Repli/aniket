import 'dart:math' as math;

import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:flutter/material.dart';

/// Nút giấy (spec §7.3). Mặc định: dải giấy màu son, mép trên/dưới răng cưa như
/// giấy xé, góc phải trên gấp mép kiểu origami. `.tape`: nhãn giấy có mẩu washi.
class PaperButton extends StatelessWidget {
  const PaperButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.trailingIcon,
  }) : _tape = false;

  const PaperButton.tape({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.trailingIcon,
  }) : _tape = true;

  final String label;
  final VoidCallback? onPressed;
  final AlbumIcons? icon;
  final AlbumIcons? trailingIcon;
  final bool _tape;

  static const _corner = 14.0;

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    final enabled = onPressed != null;
    final Color fill;
    final Color fore;
    if (_tape) {
      fill = p.paper;
      fore = enabled ? p.accent : p.muted;
    } else {
      fill = enabled ? p.accent : p.fold2;
      fore = enabled ? const Color(0xFFFFFFFF) : p.muted;
    }
    final content = Row(
      mainAxisAlignment: MainAxisAlignment.center,
      // Nhãn giấy co theo chữ; nút chính trải hết chiều ngang.
      mainAxisSize: _tape ? MainAxisSize.min : MainAxisSize.max,
      children: [
        if (icon != null) ...[
          AlbumIcon(icon!, size: 20, color: fore),
          const SizedBox(width: 8),
        ],
        Flexible(
          child: Text(
            label,
            textAlign: TextAlign.center,
            style: TextStyle(
              color: fore,
              fontWeight: FontWeight.w600,
              fontSize: 15,
            ),
          ),
        ),
        if (trailingIcon != null) ...[
          const SizedBox(width: 8),
          AlbumIcon(trailingIcon!, size: 20, color: fore),
        ],
      ],
    );
    final paper = ClipPath(
      clipper: TornPaperClipper(corner: _tape ? 0 : _corner),
      child: Material(
        color: fill,
        child: InkWell(
          onTap: onPressed,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 52),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(22, 14, 22, 14),
              // Nhãn giấy co theo chữ kể cả khi còn chỗ; nút chính trải ngang.
              child: Center(widthFactor: _tape ? 1 : null, child: content),
            ),
          ),
        ),
      ),
    );
    return Semantics(
      button: true,
      enabled: enabled,
      label: label,
      excludeSemantics: true,
      onTap: onPressed,
      child: DecoratedBox(
        // Bóng mềm như mảnh giấy nằm trên trang.
        decoration: BoxDecoration(
          boxShadow: [
            if (enabled)
              const BoxShadow(
                color: Color(0x1F5A3C1E),
                blurRadius: 8,
                offset: Offset(0, 3),
              ),
          ],
        ),
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            paper,
            if (!_tape)
              Positioned(
                right: 0,
                top: 0,
                child: CustomPaint(
                  size: const Size(_corner, _corner),
                  painter: FoldedCornerPainter(
                    enabled ? p.accentDeep : Color.lerp(p.fold2, p.ink, .12)!,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// Mép trên và dưới xé tay: ít răng, bước và độ sâu không đều như giấy bị xé;
/// [corner] > 0 thì cắt chéo góc phải trên (chỗ để mảnh gấp mép).
/// Hình dạng cố định theo kích thước nên không nhấp nháy khi vẽ lại.
class TornPaperClipper extends CustomClipper<Path> {
  const TornPaperClipper({this.corner = 0});
  final double corner;

  static const _minStep = 11.0;
  static const _maxStep = 26.0;
  static const _depth = 3.6;

  @override
  Path getClip(Size size) {
    final w = size.width, h = size.height;
    final rnd = math.Random(w.round() * 31 + h.round());
    // Một mép: các điểm gãy cách nhau không đều, độ lệch ngẫu nhiên 0.._depth.
    List<Offset> edge(double from, double to, double y, double dir) {
      final pts = <Offset>[];
      var x = from;
      final sign = to > from ? 1 : -1;
      while (true) {
        x += sign * (_minStep + rnd.nextDouble() * (_maxStep - _minStep));
        if ((x - to) * sign >= -_minStep * .6) break;
        pts.add(Offset(x, y + dir * rnd.nextDouble() * _depth));
      }
      return pts;
    }

    final path = Path()..moveTo(0, _depth * .5);
    for (final o in edge(0, w - corner, 0, 1)) {
      path.lineTo(o.dx, o.dy);
    }
    path
      ..lineTo(w - corner, 0)
      ..lineTo(w, corner)
      ..lineTo(w, h - _depth * .5);
    for (final o in edge(w, 0, h, -1)) {
      path.lineTo(o.dx, o.dy);
    }
    return path
      ..lineTo(0, h - _depth * .5)
      ..close();
  }

  @override
  bool shouldReclip(TornPaperClipper oldClipper) => oldClipper.corner != corner;
}

/// Mảnh giấy gấp ở góc phải trên: tam giác màu sẫm, cạnh huyền nhìn vào trong.
class FoldedCornerPainter extends CustomPainter {
  const FoldedCornerPainter(this.color);
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawPath(
      Path()
        ..moveTo(0, 0)
        ..lineTo(0, size.height)
        ..lineTo(size.width, size.height)
        ..close(),
      Paint()..color = color,
    );
  }

  @override
  bool shouldRepaint(FoldedCornerPainter oldDelegate) =>
      oldDelegate.color != color;
}
