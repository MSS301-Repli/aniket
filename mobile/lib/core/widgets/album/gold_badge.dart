import 'dart:math' as math;

import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:flutter/material.dart';

const _gold = Color(0xFFF2C14E);
const _goldInk = Color(0xFF5C3F00);

/// Huy hiệu vàng tròn dán nghiêng: tia sét + [label] (Boost, Pro).
class GoldBadge extends StatelessWidget {
  const GoldBadge({super.key, required this.label, this.size = 56});
  final String label;
  final double size;

  @override
  Widget build(BuildContext context) => Transform.rotate(
    angle: 12 * math.pi / 180,
    child: Container(
      width: size,
      height: size,
      decoration: const BoxDecoration(
        color: _gold,
        shape: BoxShape.circle,
        boxShadow: [
          BoxShadow(
            color: Color(0x33000000),
            blurRadius: 6,
            offset: Offset(0, 2),
          ),
        ],
      ),
      padding: EdgeInsets.all(size / 7),
      // Co lại khi chữ to để vừa huy hiệu tròn.
      child: FittedBox(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const AlbumIcon(AlbumIcons.bolt, size: 16, color: _goldInk),
            Text(
              label,
              style: const TextStyle(
                color: _goldInk,
                fontSize: 11,
                fontWeight: FontWeight.w800,
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
