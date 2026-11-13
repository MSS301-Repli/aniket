import 'dart:typed_data';

import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:animal_locket/features/chat/domain/glyph_point_codec.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const codec = GlyphPointCodec();

  test('round trips exactly 64 quantized points', () {
    final points = List.generate(
      GlyphPointCodec.pointCount,
      (index) => GlyphPoint(-1 + index / 31.5, 1 - index / 31.5),
    );
    final bytes = codec.encode(points);
    final decoded = codec.decode(bytes);

    expect(bytes, hasLength(GlyphPointCodec.bytesPerGlyph));
    expect(decoded, hasLength(GlyphPointCodec.pointCount));
    expect(decoded.first.x, closeTo(-1, .001));
    expect(decoded.last.x, closeTo(1, .001));
    expect(codec.hash(bytes), hasLength(16));
    expect(codec.hash(bytes), codec.hash(Uint8List.fromList(bytes)));
  });

  test('rejects wrong point and blob lengths', () {
    expect(() => codec.encode(const [GlyphPoint(0, 0)]), throwsFormatException);
    expect(() => codec.decode(Uint8List(4)), throwsFormatException);
  });

  test('rejects non finite coordinates', () {
    final points = List<GlyphPoint>.filled(
      GlyphPointCodec.pointCount,
      const GlyphPoint(double.nan, 0),
    );
    expect(() => codec.encode(points), throwsFormatException);
  });
}
