import 'package:animal_locket/core/assets/animal_asset_resolver.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const resolver = AnimalAssetResolver();

  test('resolves known animal codes to their emoji', () {
    expect(resolver.emojiFor('fox'), '🦊');
    expect(resolver.emojiFor('panda'), '🐼');
    expect(resolver.emojiFor('cat'), '🐱');
    expect(resolver.emojiFor('polar'), '🐻‍❄️');
  });

  test('accepts a complete avatar code and normalizes its animal code', () {
    expect(AnimalAssetResolver.emoji('1:FOX:0:none'), '🦊');
    expect(resolver.emojiFor('  dog  '), '🐶');
  });

  test('uses the paw fallback for null, blank, and unknown codes', () {
    expect(resolver.emojiFor(null), '🐾');
    expect(resolver.emojiFor('  '), '🐾');
    expect(resolver.emojiFor('unknown-animal'), '🐾');
  });
}
