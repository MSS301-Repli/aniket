import 'dart:math' as math;

import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/washi_photo_card.dart';
import 'package:flutter/material.dart';

/// Ảnh polaroid: khung giấy, ảnh vuông, lề dưới dày có chú thích viết tay,
/// băng washi ở mép trên, hơi nghiêng (spec §7.3).
class Polaroid extends StatelessWidget {
  const Polaroid({
    super.key,
    required this.child,
    required this.caption,
    this.width = 186,
    this.tiltDegrees = -2.5,
  });

  final Widget child;
  final String caption;
  final double width;
  final double tiltDegrees;

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    final tape = width * .34;
    return Transform.rotate(
      angle: tiltDegrees * math.pi / 180,
      child: SizedBox(
        width: width,
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            DecoratedBox(
              decoration: BoxDecoration(
                color: p.paper,
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x2E5A3C1E),
                    blurRadius: 14,
                    offset: Offset(0, 6),
                  ),
                ],
              ),
              child: Padding(
                padding: EdgeInsets.fromLTRB(
                  width * .05,
                  width * .05,
                  width * .05,
                  6,
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    AspectRatio(aspectRatio: 1, child: ClipRect(child: child)),
                    const SizedBox(height: 4),
                    Text(
                      caption,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontFamily: AppFonts.hand,
                        fontSize: math.max(15, width * .1),
                        color: p.ink,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            Positioned(
              top: -10,
              left: (width - tape) / 2,
              child: Transform.rotate(
                angle: -4 * math.pi / 180,
                child: Opacity(
                  opacity: .85,
                  child: CustomPaint(
                    size: Size(tape, 20),
                    painter: WashiTapePainter(p.tape1, p.tape2),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
