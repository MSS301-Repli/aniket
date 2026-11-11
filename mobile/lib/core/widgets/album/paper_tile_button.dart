import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:flutter/material.dart';

/// Ô giấy vuông gấp góc chỉ có icon (hoặc [child], ví dụ một glyph);
/// [label] dành cho trình đọc màn hình (spec §7.4).
class PaperTileButton extends StatelessWidget {
  const PaperTileButton({
    super.key,
    this.icon,
    this.child,
    required this.label,
    required this.onPressed,
    this.size = 52,
  }) : assert(icon != null || child != null);

  final AlbumIcons? icon;
  final Widget? child;
  final String label;
  final VoidCallback? onPressed;
  final double size;

  static const _corner = 12.0;

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    final enabled = onPressed != null;
    return Semantics(
      button: true,
      enabled: enabled,
      label: label,
      excludeSemantics: true,
      onTap: onPressed,
      child: Tooltip(
        message: label,
        excludeFromSemantics: true,
        child: SizedBox.square(
          dimension: size,
          child: DecoratedBox(
            decoration: const BoxDecoration(
              boxShadow: [
                BoxShadow(
                  color: Color(0x265A3C1E),
                  blurRadius: 8,
                  offset: Offset(0, 3),
                ),
              ],
            ),
            child: Stack(
              children: [
                Positioned.fill(
                  child: ClipPath(
                    clipper: const _CutCornerClipper(_corner),
                    child: Material(
                      color: p.paper,
                      child: InkWell(
                        onTap: onPressed,
                        child: Center(
                          child:
                              child ??
                              AlbumIcon(
                                icon!,
                                color: enabled ? p.ink : p.muted,
                              ),
                        ),
                      ),
                    ),
                  ),
                ),
                Positioned(
                  right: 0,
                  top: 0,
                  child: CustomPaint(
                    size: const Size.square(_corner),
                    painter: FoldedCornerPainter(p.fold2),
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

class _CutCornerClipper extends CustomClipper<Path> {
  const _CutCornerClipper(this.corner);
  final double corner;

  @override
  Path getClip(Size size) => Path()
    ..moveTo(0, 0)
    ..lineTo(size.width - corner, 0)
    ..lineTo(size.width, corner)
    ..lineTo(size.width, size.height)
    ..lineTo(0, size.height)
    ..close();

  @override
  bool shouldReclip(_CutCornerClipper oldClipper) =>
      oldClipper.corner != corner;
}
