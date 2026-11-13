import 'dart:math' as math;

import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/paper_tile_button.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:flutter/material.dart';

class GlyphDrawingPad extends StatefulWidget {
  const GlyphDrawingPad({
    super.key,
    required this.semanticsLabel,
    required this.hint,
    required this.clearLabel,
    required this.onStrokeComplete,
    this.color,
    this.initialPoints = const [],
    this.height = 170,
  });

  final String semanticsLabel;
  final String hint;
  final String clearLabel;
  final ValueChanged<List<GlyphPoint>> onStrokeComplete;

  /// Màu nét; mặc định `accent` của theme.
  final Color? color;
  final List<GlyphPoint> initialPoints;
  final double height;

  @override
  State<GlyphDrawingPad> createState() => _GlyphDrawingPadState();
}

class _GlyphDrawingPadState extends State<GlyphDrawingPad> {
  late List<GlyphPoint> _points = [...widget.initialPoints];

  void _start(DragStartDetails details) {
    setState(
      () => _points = [
        GlyphPoint(details.localPosition.dx, details.localPosition.dy),
      ],
    );
  }

  void _update(DragUpdateDetails details) {
    final point = GlyphPoint(
      details.localPosition.dx,
      details.localPosition.dy,
    );
    if (_points.isNotEmpty && _points.last.distanceTo(point) < 2) return;
    setState(() => _points = [..._points, point]);
  }

  void _end(DragEndDetails _) {
    if (_points.length >= 2) {
      widget.onStrokeComplete(List.unmodifiable(_points));
    }
  }

  void _clear() {
    setState(() => _points = []);
    widget.onStrokeComplete(const []);
  }

  @override
  Widget build(BuildContext context) => Semantics(
    label: widget.semanticsLabel,
    container: true,
    explicitChildNodes: true,
    child: SizedBox(
      height: widget.height,
      width: double.infinity,
      // Trang giấy chấm lưới, viền gạch đứt (spec §7.6).
      child: CustomPaint(
        painter: _DotGridPainter(
          paper: context.album.paper,
          dot: context.album.fold2,
          border: context.album.fold3,
        ),
        child: Stack(
          children: [
            Positioned.fill(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onPanStart: _start,
                onPanUpdate: _update,
                onPanEnd: _end,
                child: CustomPaint(
                  painter: GlyphStrokePainter(
                    _points,
                    widget.color ?? context.album.accent,
                  ),
                  child: _points.isEmpty
                      ? Center(
                          child: Text(
                            widget.hint,
                            style: TextStyle(
                              fontFamily: AppFonts.hand,
                              fontSize: 17,
                              color: context.album.muted,
                            ),
                          ),
                        )
                      : null,
                ),
              ),
            ),
            Positioned(
              right: 6,
              top: 6,
              child: PaperTileButton(
                icon: AlbumIcons.refresh,
                label: widget.clearLabel,
                size: 48,
                onPressed: _points.isEmpty ? null : _clear,
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _DotGridPainter extends CustomPainter {
  const _DotGridPainter({
    required this.paper,
    required this.dot,
    required this.border,
  });
  final Color paper;
  final Color dot;
  final Color border;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    canvas.drawRect(rect, Paint()..color = paper);
    final dots = Paint()..color = dot;
    for (double y = 12; y < size.height; y += 16) {
      for (double x = 12; x < size.width; x += 16) {
        canvas.drawCircle(Offset(x, y), 1.4, dots);
      }
    }
    // Viền gạch đứt quanh trang.
    final line = Paint()
      ..color = border
      ..strokeWidth = 1.4;
    void dash(Offset a, Offset b) {
      final length = (b - a).distance;
      final step = (b - a) / length;
      for (double d = 0; d < length; d += 10) {
        canvas.drawLine(a + step * d, a + step * math.min(d + 6, length), line);
      }
    }

    final r = rect.deflate(.7);
    dash(r.topLeft, r.topRight);
    dash(r.topRight, r.bottomRight);
    dash(r.bottomRight, r.bottomLeft);
    dash(r.bottomLeft, r.topLeft);
  }

  @override
  bool shouldRepaint(_DotGridPainter oldDelegate) =>
      oldDelegate.paper != paper ||
      oldDelegate.dot != dot ||
      oldDelegate.border != border;
}

class GlyphStrokePainter extends CustomPainter {
  const GlyphStrokePainter(this.points, this.color);

  final List<GlyphPoint> points;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    if (points.length < 2) return;
    final paint = Paint()
      ..color = color
      ..strokeWidth = 5
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..style = PaintingStyle.stroke;
    final path = Path()..moveTo(points.first.x, points.first.y);
    for (final point in points.skip(1)) {
      path.lineTo(point.x, point.y);
    }
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(GlyphStrokePainter oldDelegate) =>
      oldDelegate.points != points || oldDelegate.color != color;
}

class GlyphIcon extends StatelessWidget {
  const GlyphIcon({
    super.key,
    required this.glyph,
    this.size = 36,
    this.strokeWidth = 4,
  });

  final GlyphDefinition glyph;
  final double size;
  final double strokeWidth;

  @override
  Widget build(BuildContext context) => Semantics(
    label: glyph.meaning,
    image: true,
    child: SizedBox.square(
      dimension: size,
      child: CustomPaint(
        painter: _NormalizedGlyphPainter(
          glyph.points,
          Color(glyph.colorArgb),
          strokeWidth,
        ),
      ),
    ),
  );
}

class _NormalizedGlyphPainter extends CustomPainter {
  const _NormalizedGlyphPainter(this.points, this.color, this.strokeWidth);

  final List<GlyphPoint> points;
  final Color color;
  final double strokeWidth;

  @override
  void paint(Canvas canvas, Size size) {
    if (points.length < 2) return;
    final padding = size.shortestSide * .14;
    final usable = size.shortestSide - padding * 2;
    Offset map(GlyphPoint point) => Offset(
      padding + ((point.x + 1) / 2) * usable,
      padding + ((point.y + 1) / 2) * usable,
    );

    final first = map(points.first);
    final path = Path()..moveTo(first.dx, first.dy);
    for (final point in points.skip(1)) {
      final mapped = map(point);
      path.lineTo(mapped.dx, mapped.dy);
    }
    canvas.drawPath(
      path,
      Paint()
        ..color = color
        ..strokeWidth = strokeWidth
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round
        ..style = PaintingStyle.stroke,
    );
  }

  @override
  bool shouldRepaint(_NormalizedGlyphPainter oldDelegate) =>
      oldDelegate.points != points ||
      oldDelegate.color != color ||
      oldDelegate.strokeWidth != strokeWidth;
}
