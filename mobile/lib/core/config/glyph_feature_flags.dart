import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Chat chỉ bằng ký hiệu nên gửi/tạo ký hiệu bật mặc định; tắt bằng --dart-define=...=false khi cần rollback.
class GlyphFeatureFlags {
  const GlyphFeatureFlags({
    required this.readEnabled,
    required this.personalLibraryEnabled,
    required this.publishEnabled,
    required this.sendEnabled,
  });

  factory GlyphFeatureFlags.fromEnvironment() => const GlyphFeatureFlags(
    readEnabled: bool.fromEnvironment('GLYPH_READ_ENABLED', defaultValue: true),
    personalLibraryEnabled: bool.fromEnvironment(
      'GLYPH_PERSONAL_LIBRARY_ENABLED',
      defaultValue: true,
    ),
    publishEnabled: bool.fromEnvironment(
      'GLYPH_PUBLISH_ENABLED',
      defaultValue: true,
    ),
    sendEnabled: bool.fromEnvironment(
      'GLYPH_SEND_ENABLED',
      defaultValue: true,
    ),
  );

  final bool readEnabled;
  final bool personalLibraryEnabled;
  final bool publishEnabled;
  final bool sendEnabled;
}

final glyphFeatureFlagsProvider = Provider<GlyphFeatureFlags>(
  (_) => GlyphFeatureFlags.fromEnvironment(),
);
