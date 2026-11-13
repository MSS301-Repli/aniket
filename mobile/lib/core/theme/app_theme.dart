import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:flutter/material.dart';

abstract final class AppFonts {
  static const title = 'Lora';
  static const body = 'Be Vietnam Pro';

  /// Chỉ cho chữ "viết tay" (caption ảnh, ghi chú); không dùng cho chữ chức năng.
  static const hand = 'Patrick Hand';

  /// Chữ Nhật trang trí. Font là bản subset chỉ có "朱柚桜空瞬思い出をそっと";
  /// thêm chữ thì subset lại `ShipporiMincho-Medium.ttf` bằng `pyftsubset` với
  /// `--text` là toàn bộ chữ và `--layout-features='*'`, rồi sửa
  /// `test/no_japanese_text_test.dart`.
  static const jp = 'Shippori Mincho';
}

abstract final class AppTheme {
  static ThemeData get light => forPalette(AlbumPalette.of(AlbumThemeId.shu));

  /// ThemeData "album giấy gấp" dựng từ một bảng màu (spec §3–4).
  static ThemeData forPalette(AlbumPalette p) {
    final scheme = ColorScheme(
      brightness: Brightness.light,
      primary: p.accent,
      onPrimary: Colors.white,
      primaryContainer: p.accentSoft,
      onPrimaryContainer: p.accentDeep,
      secondary: p.accentDeep,
      onSecondary: Colors.white,
      surface: p.paper,
      onSurface: p.ink,
      onSurfaceVariant: p.muted,
      surfaceContainerHighest: p.fold2,
      outline: p.fold1,
      outlineVariant: p.fold2,
      error: p.danger,
      onError: Colors.white,
    );
    final base = ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      fontFamily: AppFonts.body,
    );
    TextStyle title(TextStyle? s, double size) =>
        (s ?? const TextStyle()).copyWith(
          fontFamily: AppFonts.title,
          fontSize: size,
          fontWeight: FontWeight.w700,
          color: p.ink,
        );
    final text = base.textTheme.apply(
      fontFamily: AppFonts.body,
      bodyColor: p.ink,
      displayColor: p.ink,
    );
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(14),
    );
    const buttonPadding = EdgeInsets.symmetric(horizontal: 22, vertical: 14);
    final buttonText = const TextStyle(
      fontFamily: AppFonts.body,
      fontWeight: FontWeight.w600,
      fontSize: 15,
    );
    OutlineInputBorder border(Color c, [double w = 1]) => OutlineInputBorder(
      borderRadius: BorderRadius.circular(14),
      borderSide: BorderSide(color: c, width: w),
    );

    return base.copyWith(
      scaffoldBackgroundColor: Colors.transparent,
      extensions: [p],
      textTheme: text.copyWith(
        displayLarge: title(text.displayLarge, 38),
        headlineLarge: title(text.headlineLarge, 30),
        headlineMedium: title(text.headlineMedium, 26),
        headlineSmall: title(text.headlineSmall, 22),
        titleLarge: title(text.titleLarge, 20),
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        foregroundColor: p.ink,
        titleTextStyle: TextStyle(
          fontFamily: AppFonts.title,
          fontSize: 22,
          fontWeight: FontWeight.w700,
          color: p.ink,
        ),
      ),
      // Nút chính như tấm giấy dày: màu nhấn, viền dưới đậm hơn.
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: p.accent,
          foregroundColor: Colors.white,
          disabledBackgroundColor: p.fold2,
          disabledForegroundColor: p.muted,
          padding: buttonPadding,
          textStyle: buttonText,
          shape: shape.copyWith(
            side: BorderSide(color: p.accentDeep, width: 1.5),
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: p.accent,
          backgroundColor: p.paper,
          side: BorderSide(color: p.accent, width: 1.5),
          padding: buttonPadding,
          textStyle: buttonText,
          shape: shape,
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: p.accent,
          textStyle: buttonText,
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: p.paper,
        border: border(p.fold1),
        enabledBorder: border(p.fold1),
        focusedBorder: border(p.accent, 1.5),
        errorBorder: border(p.danger),
        focusedErrorBorder: border(p.danger, 1.5),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: p.paper,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
        titleTextStyle: TextStyle(
          fontFamily: AppFonts.title,
          fontSize: 20,
          fontWeight: FontWeight.w700,
          color: p.ink,
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: p.ink,
        contentTextStyle: TextStyle(fontFamily: AppFonts.body, color: p.paper),
      ),
      progressIndicatorTheme: ProgressIndicatorThemeData(color: p.accent),
      iconTheme: IconThemeData(color: p.ink),
      dividerTheme: DividerThemeData(color: p.fold1),
    );
  }
}
