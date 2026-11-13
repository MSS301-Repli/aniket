import 'package:animal_locket/features/chat/data/system_glyphs.dart';
import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:animal_locket/features/chat/domain/glyph_recognizer.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const recognizer = GlyphRecognizer();
  final dictionary = buildSystemGlyphs();

  test('normalizes every usable stroke to 64 points', () {
    final normalized = recognizer.normalize(const [
      GlyphPoint(10, 10),
      GlyphPoint(20, 30),
      GlyphPoint(40, 10),
    ]);
    expect(normalized, hasLength(64));
    expect(
      normalized.every((point) => point.x.isFinite && point.y.isFinite),
      isTrue,
    );
  });

  test('recognizes a system glyph in forward and reverse direction', () {
    final target = dictionary.firstWhere((glyph) => glyph.id == 'sys-ok');
    final forward = recognizer.recognize(target.points, dictionary);
    final reverse = recognizer.recognize(
      target.points.reversed.toList(),
      dictionary,
    );

    expect(forward.best?.id, target.id);
    expect(forward.score, greaterThan(.9));
    expect(reverse.best?.id, target.id);
    expect(reverse.score, greaterThan(.9));
  });

  test('marks nearly identical top candidates as ambiguous', () {
    final original = dictionary.first;
    final duplicate = original.copyWith(id: 'duplicate');
    final result = recognizer.recognize(original.points, [original, duplicate]);
    expect(result.ambiguous, isTrue);
    expect(result.matched, isFalse);
  });
}
