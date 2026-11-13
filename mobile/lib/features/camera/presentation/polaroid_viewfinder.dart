import 'dart:math' as math;

import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/washi_photo_card.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

/// Khung ngắm là tấm polaroid đang hiện hình (spec §7.4, người dùng chọn kiểu A):
/// viền trắng, lề dưới dày ghi ngày viết tay, góc ảnh in ngày số cam kiểu máy
/// film, băng washi ở mép trên, nghiêng nhẹ.
class PolaroidViewfinder extends StatelessWidget {
  const PolaroidViewfinder({
    super.key,
    required this.child,
    required this.date,
  });

  final Widget child;
  final DateTime date;

  /// Màu cam phát sáng của số ngày trên máy ảnh film.
  static const _filmOrange = Color(0xFFFF8A2A);

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    return Transform.rotate(
      angle: -1 * math.pi / 180,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Positioned.fill(
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: const Color(0xFFFFFFFF),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x405A3C1E),
                    blurRadius: 20,
                    offset: Offset(0, 8),
                  ),
                ],
              ),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
                child: Column(
                  children: [
                    Expanded(
                      child: Stack(
                        fit: StackFit.expand,
                        children: [
                          ClipRect(child: child),
                          Positioned(
                            right: 10,
                            bottom: 8,
                            child: IgnorePointer(
                              child: ExcludeSemantics(
                                child: Text(
                                  DateFormat("''yy MM dd").format(date),
                                  style: const TextStyle(
                                    fontFamily: 'monospace',
                                    fontFamilyFallback: [
                                      'Courier New',
                                      'Courier',
                                    ],
                                    fontWeight: FontWeight.w700,
                                    fontSize: 15,
                                    letterSpacing: 1,
                                    color: _filmOrange,
                                    shadows: [
                                      Shadow(
                                        color: Color(0x99FF7A1A),
                                        blurRadius: 4,
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    SizedBox(
                      height: 46,
                      child: Center(
                        child: Text(
                          DateFormat('dd · MM · yyyy').format(date),
                          style: TextStyle(
                            fontFamily: AppFonts.hand,
                            fontSize: 20,
                            color: p.muted,
                          ),
                        ),
                      ),
                    ),
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
                angle: -3 * math.pi / 180,
                child: Opacity(
                  opacity: .85,
                  child: CustomPaint(
                    size: const Size(84, 24),
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
