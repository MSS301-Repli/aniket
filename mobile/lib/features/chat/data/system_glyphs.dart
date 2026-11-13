import 'dart:math' as math;

import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:animal_locket/features/chat/domain/glyph_point_codec.dart';
import 'package:animal_locket/features/chat/domain/glyph_recognizer.dart';
import 'package:animal_locket/core/l10n/tr.dart';

List<GlyphDefinition> buildSystemGlyphs({DateTime? now}) {
  final recognizer = const GlyphRecognizer();
  final codec = const GlyphPointCodec();
  final timestamp = now ?? DateTime.fromMillisecondsSinceEpoch(0, isUtc: true);
  final raw = <(String, String, int, List<GlyphPoint>)>[
    (
      'sys-ok',
      tr.glyphSysAgree,
      0xFF34D399,
      const [
        GlyphPoint(20, 25),
        GlyphPoint(35, 55),
        GlyphPoint(50, 85),
        GlyphPoint(65, 55),
        GlyphPoint(80, 20),
      ],
    ),
    (
      'sys-urgent',
      tr.glyphSysHurry,
      0xFFFBBF24,
      const [
        GlyphPoint(65, 15),
        GlyphPoint(30, 50),
        GlyphPoint(70, 50),
        GlyphPoint(35, 88),
      ],
    ),
    (
      'sys-busy',
      tr.glyphSysBusy,
      0xFF818CF8,
      const [
        GlyphPoint(50, 15),
        GlyphPoint(20, 80),
        GlyphPoint(80, 80),
        GlyphPoint(50, 15),
      ],
    ),
    ('sys-coffee', tr.glyphSysCoffee, 0xFFF472B6, _spiral()),
    ('sys-hungry', tr.glyphSysHungry, 0xFFFB923C, _circle()),
    ('sys-coming', tr.glyphSysComing, 0xFF38BDF8, _wave()),
  ];
  return raw
      .map((item) {
        final points = recognizer.normalize(item.$4);
        final bytes = codec.encode(points);
        return GlyphDefinition(
          id: item.$1,
          kind: GlyphKind.system,
          meaning: item.$2,
          colorArgb: item.$3,
          points: points,
          shapeHash: codec.hash(bytes),
          createdAt: timestamp,
          updatedAt: timestamp,
        );
      })
      .toList(growable: false);
}

List<GlyphPoint> _spiral() {
  final points = <GlyphPoint>[];
  for (var t = 0.0; t <= math.pi * 3.5; t += 0.25) {
    final radius = 6 + t * 3.6;
    points.add(
      GlyphPoint(50 + radius * math.cos(t), 50 + radius * math.sin(t)),
    );
  }
  return points;
}

List<GlyphPoint> _circle() {
  final points = <GlyphPoint>[];
  for (var t = 0.0; t <= math.pi * 2.1; t += 0.2) {
    points.add(GlyphPoint(50 + 34 * math.cos(t), 50 + 34 * math.sin(t)));
  }
  return points;
}

List<GlyphPoint> _wave() {
  final points = <GlyphPoint>[];
  for (var x = 15.0; x <= 85; x += 4) {
    points.add(
      GlyphPoint(x, 50 + math.sin(((x - 15) / 70) * math.pi * 2) * 24),
    );
  }
  return points;
}

/// Ý nghĩa ký hiệu hệ thống theo ngôn ngữ đang chọn. Bảng lưu trên máy chỉ giữ
/// hình nét; nghĩa luôn được dịch lại khi đọc.
GlyphDefinition localizeSystemGlyph(GlyphDefinition glyph) {
  final meaning = switch (glyph.id) {
    'sys-ok' => tr.glyphSysAgree,
    'sys-urgent' => tr.glyphSysHurry,
    'sys-busy' => tr.glyphSysBusy,
    'sys-coffee' => tr.glyphSysCoffee,
    'sys-hungry' => tr.glyphSysHungry,
    'sys-coming' => tr.glyphSysComing,
    _ => null,
  };
  return meaning == null ? glyph : glyph.copyWith(meaning: meaning);
}
