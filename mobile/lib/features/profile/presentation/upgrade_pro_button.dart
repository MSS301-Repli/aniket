import 'dart:math' as math;

import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:flutter/material.dart';

/// Nút chữ nhật màu accent mời nâng cấp Pro. Khi hiện ra, một vệt sáng lướt
/// qua và tia sét lắc nhẹ, lặp vài lần rồi dừng để không gây rối mắt.
class UpgradeProButton extends StatefulWidget {
  const UpgradeProButton({
    super.key,
    required this.label,
    required this.onPressed,
  });

  final String label;
  final VoidCallback onPressed;

  @override
  State<UpgradeProButton> createState() => _UpgradeProButtonState();
}

class _UpgradeProButtonState extends State<UpgradeProButton>
    with SingleTickerProviderStateMixin {
  static const _cycles = 3;
  late final AnimationController _shine = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 2600),
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (MediaQuery.disableAnimationsOf(context)) {
      _shine.value = 0;
    } else if (!_shine.isAnimating) {
      _shine.repeat(count: _cycles);
    }
  }

  @override
  void dispose() {
    _shine.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    const fore = Color(0xFFFFFFFF);
    return Semantics(
      button: true,
      label: widget.label,
      excludeSemantics: true,
      onTap: widget.onPressed,
      child: Material(
        color: p.accent,
        borderRadius: BorderRadius.circular(12),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: widget.onPressed,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 52),
            child: AnimatedBuilder(
              animation: _shine,
              builder: (context, _) {
                final t = _shine.value;
                // Tia sét lắc ở 50–65% chu kỳ; vệt sáng lướt ở 0–45%.
                final shake = t > .5 && t < .65
                    ? math.sin((t - .5) / .15 * math.pi * 3) * .22
                    : 0.0;
                return Stack(
                  children: [
                    Positioned.fill(
                      child: IgnorePointer(
                        child: LayoutBuilder(
                          builder: (context, box) {
                            if (t >= .45 || t == 0) {
                              return const SizedBox.shrink();
                            }
                            const band = 56.0;
                            final left =
                                (t / .45) * (box.maxWidth + band * 2) - band;
                            return Stack(
                              children: [
                                Positioned(
                                  left: left,
                                  top: -10,
                                  bottom: -10,
                                  width: band,
                                  child: Transform(
                                    transform: Matrix4.skewX(-.35),
                                    child: DecoratedBox(
                                      decoration: BoxDecoration(
                                        gradient: LinearGradient(
                                          colors: [
                                            fore.withValues(alpha: 0),
                                            fore.withValues(alpha: .32),
                                            fore.withValues(alpha: 0),
                                          ],
                                        ),
                                      ),
                                    ),
                                  ),
                                ),
                              ],
                            );
                          },
                        ),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 20,
                        vertical: 14,
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Transform.rotate(
                            angle: shake,
                            child: const AlbumIcon(
                              AlbumIcons.bolt,
                              size: 20,
                              color: fore,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Flexible(
                            child: Text(
                              widget.label,
                              textAlign: TextAlign.center,
                              style: const TextStyle(
                                color: fore,
                                fontWeight: FontWeight.w700,
                                fontSize: 15,
                              ),
                            ),
                          ),
                        ],
                      ),
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
