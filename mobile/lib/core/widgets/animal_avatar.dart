import 'package:animal_locket/core/assets/animal_asset_resolver.dart';
import 'package:animal_locket/core/avatar/avatar_library.dart';
import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:animal_locket/core/l10n/tr.dart';

/// Avatar con vật. Có [avatar] ("v:animal:palette:accessory") thì vẽ SVG từ thư viện;
/// không có hoặc chưa tải được thư viện thì dùng emoji của [leadAnimal] / [emoji].
class AnimalAvatar extends StatelessWidget {
  const AnimalAvatar({
    super.key,
    this.emoji,
    this.avatar,
    this.leadAnimal,
    this.size = 64,
    this.colorIndex = 0,
    this.locked = false,
    this.square = false,
  });

  final String? emoji;
  final String? avatar;
  final String? leadAnimal;
  final double size;
  final int colorIndex;
  final bool locked;

  /// Vẽ vuông (trong khung polaroid) thay vì cắt tròn.
  final bool square;

  @override
  Widget build(BuildContext context) {
    if (locked || avatar == null) return _fallback();
    final libs = AvatarLibraries.instance;
    return ValueListenableBuilder<int>(
      valueListenable: libs.revision,
      builder: (context, _, _) {
        final svg = libs.svgFor(avatar);
        if (svg == null) return _fallback();
        final name = libs.animalName(avatar);
        return Semantics(
          image: true,
          label: name == null
              ? tr.avatarSemantic
              : tr.avatarSemanticNamed(name),
          child: square
              ? SvgPicture.string(svg, width: size, height: size)
              : ClipOval(
                  child: SvgPicture.string(
                    svg,
                    width: size,
                    height: size,
                    fit: BoxFit.cover,
                  ),
                ),
        );
      },
    );
  }

  Widget _fallback() {
    final glyph =
        emoji ??
        AnimalAssetResolver.emoji(
          leadAnimal ?? AvatarCode.tryParse(avatar)?.animal,
        );
    return Semantics(
      image: !locked,
      label: locked ? tr.animalLocked : tr.avatarSemantic,
      child: Container(
        width: size,
        height: size,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: locked
              ? Colors.transparent
              : _pastels[colorIndex % _pastels.length],
          border: locked
              ? Border.all(color: const Color(0xFFE8E5EE), width: 2)
              : null,
        ),
        child: Text(
          locked ? '?' : glyph,
          style: TextStyle(fontSize: size * .52, fontWeight: FontWeight.w700),
        ),
      ),
    );
  }
}

/// Nền dự phòng khi chưa có SVG avatar.
const _pastels = <Color>[
  Color(0xFFFFF1C2),
  Color(0xFFB1EEF3),
  Color(0xFFD0F7A3),
  Color(0xFFF5D3B3),
  Color(0xFFE6DDF5),
  Color(0xFFFCE3CB),
];
