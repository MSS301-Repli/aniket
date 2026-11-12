import 'dart:math' as math;

import 'package:flutter/material.dart';

/// Bốn theme màu của app (spec §4). Theme chỉ đổi bảng màu, không đổi bố cục.
enum AlbumThemeId { shu, yuzu, sakura, sora }

/// Bảng màu "album giấy gấp", gắn vào [ThemeData.extensions].
/// Màn đã làm lại chỉ lấy màu từ đây (qua `context.album`).
@immutable
class AlbumPalette extends ThemeExtension<AlbumPalette> {
  const AlbumPalette({
    required this.background,
    required this.paper,
    required this.fold1,
    required this.fold2,
    required this.fold3,
    required this.tape1,
    required this.tape2,
    required this.accent,
    required this.accentDeep,
    required this.accentSoft,
    required this.muted,
    required this.ink,
    required this.danger,
  });

  final Color background;
  final Color paper;
  final Color fold1;
  final Color fold2;
  final Color fold3;
  final Color tape1;
  final Color tape2;
  final Color accent;
  final Color accentDeep;
  final Color accentSoft;
  final Color muted;
  final Color ink;
  final Color danger;

  static const _ink = Color(0xFF2B2420);
  static const _danger = Color(0xFFA8261E);

  static const _palettes = <AlbumThemeId, AlbumPalette>{
    AlbumThemeId.shu: AlbumPalette(
      background: Color(0xFFFFF8EF),
      paper: Color(0xFFFFFDF8),
      fold1: Color(0xFFFFE3C2),
      fold2: Color(0xFFFFE9DC),
      fold3: Color(0xFFFDE4C8),
      tape1: Color(0xFFF4B9A3),
      tape2: Color(0xFFF9D3C3),
      accent: Color(0xFFB8402A),
      accentDeep: Color(0xFF8A2C1B),
      accentSoft: Color(0xFFFDEAE2),
      muted: Color(0xFF6A5B4E),
      ink: _ink,
      danger: _danger,
    ),
    AlbumThemeId.yuzu: AlbumPalette(
      background: Color(0xFFFFFAEA),
      paper: Color(0xFFFFFEF8),
      fold1: Color(0xFFFFEEB8),
      fold2: Color(0xFFFFF3D1),
      fold3: Color(0xFFFFE6A6),
      tape1: Color(0xFFF7CF6B),
      tape2: Color(0xFFFBE3A3),
      accent: Color(0xFFA35C00),
      accentDeep: Color(0xFF7A4400),
      accentSoft: Color(0xFFFFF4D6),
      muted: Color(0xFF6B5D45),
      ink: _ink,
      danger: _danger,
    ),
    AlbumThemeId.sakura: AlbumPalette(
      background: Color(0xFFFFF6F1),
      paper: Color(0xFFFFFDFB),
      fold1: Color(0xFFFFE0D2),
      fold2: Color(0xFFFFDBE5),
      fold3: Color(0xFFFFE9C9),
      tape1: Color(0xFFF6B8C8),
      tape2: Color(0xFFFBD7E1),
      accent: Color(0xFFB23A5B),
      accentDeep: Color(0xFF842640),
      accentSoft: Color(0xFFFBDDE6),
      muted: Color(0xFF6C5A58),
      ink: _ink,
      danger: _danger,
    ),
    AlbumThemeId.sora: AlbumPalette(
      background: Color(0xFFF6F9FB),
      paper: Color(0xFFFFFFFF),
      fold1: Color(0xFFD9EBF6),
      fold2: Color(0xFFE8F2F9),
      fold3: Color(0xFFFFE9D6),
      tape1: Color(0xFF9CC7E3),
      tape2: Color(0xFFC8E1F1),
      accent: Color(0xFF2F5F8A),
      accentDeep: Color(0xFF204465),
      accentSoft: Color(0xFFDCEAF5),
      muted: Color(0xFF56616B),
      ink: _ink,
      danger: _danger,
    ),
  };

  static AlbumPalette of(AlbumThemeId id) => _palettes[id]!;

  @override
  AlbumPalette copyWith({
    Color? background,
    Color? paper,
    Color? fold1,
    Color? fold2,
    Color? fold3,
    Color? tape1,
    Color? tape2,
    Color? accent,
    Color? accentDeep,
    Color? accentSoft,
    Color? muted,
    Color? ink,
    Color? danger,
  }) => AlbumPalette(
    background: background ?? this.background,
    paper: paper ?? this.paper,
    fold1: fold1 ?? this.fold1,
    fold2: fold2 ?? this.fold2,
    fold3: fold3 ?? this.fold3,
    tape1: tape1 ?? this.tape1,
    tape2: tape2 ?? this.tape2,
    accent: accent ?? this.accent,
    accentDeep: accentDeep ?? this.accentDeep,
    accentSoft: accentSoft ?? this.accentSoft,
    muted: muted ?? this.muted,
    ink: ink ?? this.ink,
    danger: danger ?? this.danger,
  );

  @override
  AlbumPalette lerp(AlbumPalette? other, double t) {
    if (other == null) return this;
    Color c(Color a, Color b) => Color.lerp(a, b, t)!;
    return AlbumPalette(
      background: c(background, other.background),
      paper: c(paper, other.paper),
      fold1: c(fold1, other.fold1),
      fold2: c(fold2, other.fold2),
      fold3: c(fold3, other.fold3),
      tape1: c(tape1, other.tape1),
      tape2: c(tape2, other.tape2),
      accent: c(accent, other.accent),
      accentDeep: c(accentDeep, other.accentDeep),
      accentSoft: c(accentSoft, other.accentSoft),
      muted: c(muted, other.muted),
      ink: c(ink, other.ink),
      danger: c(danger, other.danger),
    );
  }
}

extension AlbumContext on BuildContext {
  /// Bảng màu đang dùng; ngoài cây theme của app (ví dụ test dựng MaterialApp
  /// trần) thì rơi về Shu thay vì crash.
  AlbumPalette get album =>
      Theme.of(this).extension<AlbumPalette>() ??
      AlbumPalette.of(AlbumThemeId.shu);
}

/// Tỉ lệ tương phản WCAG 2.x giữa hai màu (1.0 … 21.0).
double contrastRatio(Color a, Color b) {
  final la = _luminance(a), lb = _luminance(b);
  return (math.max(la, lb) + .05) / (math.min(la, lb) + .05);
}

double _luminance(Color c) {
  double ch(double v) =>
      v <= .03928 ? v / 12.92 : math.pow((v + .055) / 1.055, 2.4).toDouble();
  return .2126 * ch(c.r) + .7152 * ch(c.g) + .0722 * ch(c.b);
}
