import 'dart:math' as math;

import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:flutter/material.dart';

/// Nút bật/tắt dạng con dấu hanko khắc hình [mark] (mặc định trái tim cho nút thích,
/// dấu tích cho ô đồng ý). Không dùng chữ (spec §2). "Đóng dấu" khi bật.
class HankoStamp extends StatefulWidget {
  const HankoStamp({
    super.key,
    required this.stamped,
    required this.onPressed,
    required this.tooltip,
    this.size = 34,
    this.mark = AlbumIcons.heart,
    this.toggle = true,
  });

  final bool stamped;
  final VoidCallback? onPressed;
  final String tooltip;
  final double size;
  final AlbumIcons mark;

  /// false: con dấu là nút hành động (Chấp nhận, Kết bạn), không mang trạng thái bật/tắt.
  final bool toggle;

  @override
  State<HankoStamp> createState() => _HankoStampState();
}

class _HankoStampState extends State<HankoStamp>
    with SingleTickerProviderStateMixin {
  static const _restAngle = -12 * math.pi / 180;
  static const _pressAngle = -18 * math.pi / 180;

  late final AnimationController _press = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 180),
    value: 1,
  );

  @override
  void didUpdateWidget(HankoStamp old) {
    super.didUpdateWidget(old);
    if (widget.stamped && !old.stamped) {
      if (MediaQuery.disableAnimationsOf(context)) {
        _press.value = 1;
      } else {
        _press.forward(from: 0);
      }
    }
  }

  @override
  void dispose() {
    _press.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    final on = widget.stamped;
    final enabled = widget.onPressed != null;
    // Tắt thì luôn trông "chưa đóng dấu", kể cả con dấu hành động (stamped: true).
    final ink = on && enabled ? p.accent : p.muted;
    return Semantics(
      button: true,
      toggled: widget.toggle ? on : null,
      enabled: widget.onPressed != null,
      label: widget.tooltip,
      child: Tooltip(
        message: widget.tooltip,
        excludeFromSemantics: true,
        child: InkResponse(
          onTap: widget.onPressed,
          radius: 26,
          child: SizedBox(
            width: math.max(48, widget.size),
            height: math.max(48, widget.size),
            child: Center(
              child: AnimatedBuilder(
                animation: _press,
                builder: (context, child) {
                  final t = Curves.easeOutBack.transform(_press.value);
                  return Transform.rotate(
                    angle: _pressAngle + (_restAngle - _pressAngle) * t,
                    child: Transform.scale(scale: 1.15 - .15 * t, child: child),
                  );
                },
                child: Container(
                  width: widget.size,
                  height: widget.size,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: on && enabled ? p.accentSoft : Colors.transparent,
                    border: Border.all(color: ink, width: on ? 2.5 : 2),
                  ),
                  padding: EdgeInsets.all(widget.size * .2),
                  child: FittedBox(child: AlbumIcon(widget.mark, color: ink)),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
