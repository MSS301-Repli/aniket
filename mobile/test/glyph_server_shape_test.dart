import 'dart:io';

import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:animal_locket/features/chat/domain/glyph_server_shape.dart';
import 'package:flutter_test/flutter_test.dart';

/// Reference hashes produced by chat-service `GlyphShapes.hash(canonicalize(..))`
/// (Java) for the same integer points. If the server algorithm changes,
/// regenerate test/fixtures/glyph_server_hash.txt.
void main() {
  final cases = File('test/fixtures/glyph_server_hash.txt')
      .readAsLinesSync()
      .where((line) => line.contains('|'))
      .map((line) {
        final [points, hash] = line.split('|');
        final ints = points.split(',').map(int.parse).toList();
        return (
          points: [
            for (var i = 0; i < ints.length; i += 2)
              GlyphPoint(ints[i] / 1000, ints[i + 1] / 1000),
          ],
          hash: hash,
        );
      })
      .toList();

  test('fixture is not empty', () => expect(cases, hasLength(31)));

  test('matches the server canonical hash for every reference shape', () {
    for (final c in cases) {
      expect(serverShapeHash(c.points), c.hash);
    }
  });

  test('degenerate shape is rejected like the server', () {
    expect(serverShapeHash(List.filled(64, const GlyphPoint(.3, .3))), isNull);
  });
}
