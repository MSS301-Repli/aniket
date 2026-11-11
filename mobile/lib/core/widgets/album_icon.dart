import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

/// Bộ icon vẽ riêng của app (assets/icons), nét mảnh chủ đề sổ ảnh.
enum AlbumIcons {
  photos,
  feed,
  discover,
  chat,
  profile,
  comment,
  friends,
  emptyAlbum,
  cloudOff,
  imageBroken,
  image,
  heart,
  mail,
  arrowRight,
  dice,
  flashAuto,
  flashOn,
  flashOff,
  gallery,
  flipCamera,
  camera,
  send,
  close,
  alert,
  trash,
  hourglass,
  more,
  bolt,
  locationOff,
  flag,
  block,
  check,
  eye,
  eyeOff,
  book,
  plus,
  refresh,
  settings,
  edit,
  logout,
  userMinus;

  /// Tên file trong assets/icons (snake_case), ví dụ emptyAlbum → empty_album.
  String get asset =>
      name.replaceAllMapped(RegExp('[A-Z]'), (m) => '_${m[0]!.toLowerCase()}');
}

class AlbumIcon extends StatelessWidget {
  const AlbumIcon(
    this.icon, {
    super.key,
    this.size = 24,
    this.color,
    this.semanticLabel,
  });

  final AlbumIcons icon;
  final double size;
  final Color? color;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final tint = color ?? IconTheme.of(context).color ?? context.album.ink;
    return SvgPicture.asset(
      'assets/icons/${icon.asset}.svg',
      width: size,
      height: size,
      colorFilter: ColorFilter.mode(tint, BlendMode.srcIn),
      semanticsLabel: semanticLabel,
      excludeFromSemantics: semanticLabel == null,
    );
  }
}

/// Một SVG trang trí tô một màu; bị bỏ qua khi đọc màn hình.
class DecoSvg extends StatelessWidget {
  const DecoSvg(this.asset, {super.key, this.height, this.width, this.color});
  final String asset;
  final double? height;
  final double? width;
  final Color? color;

  @override
  Widget build(BuildContext context) => ExcludeSemantics(
    child: SvgPicture.asset(
      asset,
      height: height,
      width: width,
      colorFilter: ColorFilter.mode(
        color ?? context.album.accent,
        BlendMode.srcIn,
      ),
    ),
  );
}
