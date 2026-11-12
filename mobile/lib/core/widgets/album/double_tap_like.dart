import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:flutter/material.dart';

/// Chạm hai lần vào ảnh để thả tim: một trái tim son nảy lên rồi mờ dần.
/// Chỉ thích, không bao giờ bỏ thích (để tránh bỏ thích nhầm); [onLike] chỉ
/// được gọi khi [liked] còn false. Bấm [enabled] = false để tắt (ảnh chưa đăng).
class DoubleTapLike extends StatefulWidget {
  const DoubleTapLike({
    super.key,
    required this.child,
    required this.liked,
    required this.onLike,
    this.enabled = true,
  });

  final Widget child;
  final bool liked;
  final VoidCallback onLike;
  final bool enabled;

  @override
  State<DoubleTapLike> createState() => _DoubleTapLikeState();
}

class _DoubleTapLikeState extends State<DoubleTapLike>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pop = AnimationController(vsync: this);

  @override
  void dispose() {
    _pop.dispose();
    super.dispose();
  }

  void _onDoubleTap() {
    if (!widget.liked) widget.onLike();
    _pop
      ..duration = MediaQuery.disableAnimationsOf(context)
          ? Duration.zero
          : const Duration(milliseconds: 750)
      ..forward(from: 0);
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.enabled) return widget.child;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onDoubleTap: _onDoubleTap,
      child: Stack(
        alignment: Alignment.center,
        children: [
          widget.child,
          IgnorePointer(
            child: ExcludeSemantics(
              child: AnimatedBuilder(
                animation: _pop,
                builder: (context, _) {
                  final t = _pop.value;
                  // Nảy lên nhanh (0–35%), giữ, rồi mờ dần (60–100%).
                  final scale =
                      Curves.easeOutBack.transform((t / .35).clamp(0, 1)) * 1.0;
                  final opacity = t == 0 || t == 1
                      ? 0.0
                      : (1 - ((t - .6) / .4).clamp(0, 1)).toDouble();
                  return Opacity(
                    opacity: opacity,
                    child: Transform.scale(
                      scale: scale,
                      child: AlbumIcon(
                        AlbumIcons.heart,
                        size: 88,
                        color: context.album.accent,
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
        ],
      ),
    );
  }
}
