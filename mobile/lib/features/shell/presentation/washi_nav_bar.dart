import 'dart:math' as math;

import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/album/watercolor_wash.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:flutter/material.dart';

class WashiNavItem {
  const WashiNavItem({required this.icon, required this.label});
  final AlbumIcons icon;
  final String label;
}

/// Thanh tab "băng washi & polaroid" (spec §3, mockup nav-options-v1 phương án 3).
/// Mục Ảnh (icon photos) là một tấm polaroid nhỏ nghiêng; tab đang chọn có
/// mẩu washi dán chéo phía sau.
class WashiNavBar extends StatelessWidget {
  const WashiNavBar({
    super.key,
    required this.items,
    required this.currentIndex,
    required this.onSelected,
  });

  final List<WashiNavItem> items;
  final int currentIndex;
  final ValueChanged<int> onSelected;

  static const _height = 68.0;
  static const _washWidth = 72.0;
  static const _radius = BorderRadius.only(
    topLeft: Radius.circular(4),
    topRight: Radius.circular(4),
  );

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    final labelSize = MediaQuery.textScalerOf(context).scale(11);
    final barHeight = labelSize <= 12
        ? _height
        : math.max(_height, 42 + labelSize * 3.2);
    final slide = MediaQuery.disableAnimationsOf(context)
        ? Duration.zero
        : const Duration(milliseconds: 200);
    return SafeArea(
      // Trải hết chiều ngang màn hình, chỉ chừa vùng an toàn phía dưới.
      minimum: EdgeInsets.zero,
      child: DecoratedBox(
        decoration: const BoxDecoration(
          borderRadius: _radius,
          boxShadow: [
            BoxShadow(
              color: Color(0x1A3C2814),
              blurRadius: 22,
              offset: Offset(0, 10),
            ),
          ],
        ),
        // Material (không phải Container) để hiệu ứng gợn khi chạm hiện lên.
        child: Material(
          color: p.paper,
          borderRadius: _radius,
          child: SizedBox(
            height: barHeight,
            child: LayoutBuilder(
              builder: (context, box) {
                final slot = box.maxWidth / items.length;
                return Stack(
                  clipBehavior: Clip.none,
                  children: [
                    Positioned(
                      top: 0,
                      left: 0,
                      right: 0,
                      child: CustomPaint(
                        size: const Size.fromHeight(3),
                        painter: _TornEdgePainter(p.fold1),
                      ),
                    ),
                    // Vệt cọ trượt tới tab đang chọn, loe ra khi tới nơi.
                    AnimatedPositioned(
                      duration: slide,
                      curve: Curves.easeOutCubic,
                      left: slot * currentIndex + (slot - _washWidth) / 2,
                      top: 8,
                      child: TweenAnimationBuilder<double>(
                        key: ValueKey(currentIndex),
                        tween: Tween(begin: 0, end: 1),
                        duration: slide * 1.5,
                        curve: Curves.easeOutCubic,
                        builder: (_, t, child) => Opacity(
                          opacity: t,
                          child: Transform.scale(
                            scale: .78 + .22 * t,
                            child: child,
                          ),
                        ),
                        child: WatercolorWash(
                          size: const Size(_washWidth, 38),
                          seed: currentIndex,
                        ),
                      ),
                    ),
                    Row(
                      children: [
                        for (var i = 0; i < items.length; i++)
                          Expanded(
                            child: _NavButton(
                              item: items[i],
                              selected: i == currentIndex,
                              polaroid: items[i].icon == AlbumIcons.photos,
                              pop: slide,
                              onTap: () => onSelected(i),
                            ),
                          ),
                      ],
                    ),
                  ],
                );
              },
            ),
          ),
        ),
      ),
    );
  }
}

class _NavButton extends StatelessWidget {
  const _NavButton({
    required this.item,
    required this.selected,
    required this.polaroid,
    required this.pop,
    required this.onTap,
  });

  final WashiNavItem item;
  final bool selected;
  final bool polaroid;

  /// Thời lượng nảy nhẹ của icon khi tab được chọn (0 khi giảm chuyển động).
  final Duration pop;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    final color = selected ? p.accent : p.muted;
    return MergeSemantics(
      child: Semantics(
        button: true,
        selected: selected,
        label: item.label,
        child: InkResponse(
          onTap: onTap,
          radius: 30,
          child: Padding(
            padding: const EdgeInsets.only(bottom: 9, left: 2, right: 2),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                AnimatedScale(
                  scale: selected ? 1.1 : 1,
                  duration: pop,
                  curve: Curves.easeOutBack,
                  child: SizedBox(
                    height: 30,
                    width: 48,
                    child: polaroid
                        ? const _Polaroid()
                        : Center(child: AlbumIcon(item.icon, color: color)),
                  ),
                ),
                const SizedBox(height: 3),
                ExcludeSemantics(
                  child: Text(
                    item.label,
                    maxLines: 2,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: color,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Tấm polaroid nhỏ nghiêng, nhô lên khỏi mép thanh tab.
class _Polaroid extends StatelessWidget {
  const _Polaroid();

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    return OverflowBox(
      maxHeight: 48,
      alignment: Alignment.bottomCenter,
      child: Transform.rotate(
        angle: 4 * math.pi / 180,
        child: Container(
          width: 42,
          height: 48,
          padding: const EdgeInsets.fromLTRB(4, 4, 4, 11),
          decoration: const BoxDecoration(
            color: Color(0xFFFFFFFF),
            boxShadow: [
              BoxShadow(
                color: Color(0x385A3C1E),
                blurRadius: 10,
                offset: Offset(0, 5),
              ),
            ],
          ),
          child: ColoredBox(
            color: p.accent,
            child: const Center(
              child: AlbumIcon(
                AlbumIcons.photos,
                size: 18,
                color: Color(0xFFFFFFFF),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Mép trên như giấy xé: gạch ngắn 8px, khe 6px.
class _TornEdgePainter extends CustomPainter {
  const _TornEdgePainter(this.color);
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = color;
    for (double x = 0; x < size.width; x += 14) {
      canvas.drawRect(Rect.fromLTWH(x, 0, 8, size.height), paint);
    }
  }

  @override
  bool shouldRepaint(_TornEdgePainter oldDelegate) =>
      oldDelegate.color != color;
}
