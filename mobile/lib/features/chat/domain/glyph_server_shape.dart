import 'dart:convert';

import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:crypto/crypto.dart';

/// Mirror of the backend `GlyphShapes` (chat-service). The server rejects a
/// second ACTIVE glyph with the same canonical shape (`GLYPH_DUPLICATE`), so
/// the client must recognise a shape that is already published using exactly
/// the server's hash. Keep in sync with GlyphShapes.java.
const glyphWireRange = 1000;

/// Integer coordinates sent to `POST .../glyphs` (x0, y0, x1, y1, ...).
List<int> glyphWirePoints(List<GlyphPoint> points) => points
    .expand(
      (point) => [
        (point.x.clamp(-1.0, 1.0) * glyphWireRange).round(),
        (point.y.clamp(-1.0, 1.0) * glyphWireRange).round(),
      ],
    )
    .toList(growable: false);

/// SHA-256 hex of the canonical shape, identical to `GlyphShapes.hash(
/// GlyphShapes.canonicalize(points))`; null when the server would reject the
/// points anyway.
String? serverShapeHash(List<GlyphPoint> points) {
  final wire = glyphWirePoints(points);
  final pairs = wire.length ~/ 2;
  if (pairs == 0 || wire.length.isOdd) return null;
  var cx = 0.0, cy = 0.0;
  for (var i = 0; i < wire.length; i += 2) {
    cx += wire[i];
    cy += wire[i + 1];
  }
  cx /= pairs;
  cy /= pairs;
  var max = 0.0;
  for (var i = 0; i < wire.length; i += 2) {
    final dx = (wire[i] - cx).abs(), dy = (wire[i + 1] - cy).abs();
    if (dx > max) max = dx;
    if (dy > max) max = dy;
  }
  if (max < 1e-9) return null;
  final scale = glyphWireRange / max;
  final text = StringBuffer();
  for (var i = 0; i < wire.length; i += 2) {
    text
      ..write(_javaRoundClamp((wire[i] - cx) * scale))
      ..write(',')
      ..write(_javaRoundClamp((wire[i + 1] - cy) * scale))
      ..write(',');
  }
  return sha256.convert(ascii.encode(text.toString())).toString();
}

/// Java `Math.round` (half rounds toward +infinity, unlike Dart's `round`)
/// followed by the server clamp to [-RANGE, RANGE].
int _javaRoundClamp(double value) =>
    (value + 0.5).floor().clamp(-glyphWireRange, glyphWireRange);
