import 'dart:typed_data';

import 'package:animal_locket/features/chat/domain/glyph.dart';

class GlyphPointCodec {
  const GlyphPointCodec();

  static const pointCount = 64;
  static const coordinateScale = 1000.0;
  static const bytesPerGlyph = pointCount * 2 * 2;

  Uint8List encode(List<GlyphPoint> points) {
    if (points.length != pointCount) {
      throw const FormatException('Glyph phải có đúng 64 điểm.');
    }
    final data = ByteData(bytesPerGlyph);
    for (var i = 0; i < points.length; i++) {
      final x = _quantize(points[i].x);
      final y = _quantize(points[i].y);
      data.setInt16(i * 4, x, Endian.big);
      data.setInt16(i * 4 + 2, y, Endian.big);
    }
    return data.buffer.asUint8List();
  }

  List<GlyphPoint> decode(Uint8List bytes) {
    if (bytes.lengthInBytes != bytesPerGlyph) {
      throw const FormatException('Dữ liệu điểm glyph không hợp lệ.');
    }
    final data = ByteData.sublistView(bytes);
    return List<GlyphPoint>.generate(pointCount, (index) {
      final x = data.getInt16(index * 4, Endian.big) / coordinateScale;
      final y = data.getInt16(index * 4 + 2, Endian.big) / coordinateScale;
      return GlyphPoint(x, y);
    }, growable: false);
  }

  String hash(Uint8List bytes) {
    // FNV-1a 64-bit is deterministic and only used for local collision hints.
    // The backend contract recomputes its own canonical SHA-256 hash.
    var value = BigInt.parse('14695981039346656037');
    final prime = BigInt.parse('1099511628211');
    final mask = (BigInt.one << 64) - BigInt.one;
    for (final byte in bytes) {
      value ^= BigInt.from(byte);
      value = (value * prime) & mask;
    }
    return value.toRadixString(16).padLeft(16, '0');
  }

  int _quantize(double value) {
    if (!value.isFinite) {
      throw const FormatException('Tọa độ glyph không hữu hạn.');
    }
    final clamped = value.clamp(-1.0, 1.0);
    return (clamped * coordinateScale).round();
  }
}
