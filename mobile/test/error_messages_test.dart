import 'package:animal_locket/core/network/error_messages.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('maps stable RFC 7807 codes to Vietnamese messages', () {
    expect(messageForApiCode('INVALID_ALIAS'), contains('Biệt danh'));
    expect(messageForApiCode('SWIPE_LIMIT'), contains('hết lượt'));
    expect(messageForApiCode('GLYPH_NOT_ACTIVE'), contains('không còn'));
  });

  test('falls back by status and for unknown codes', () {
    expect(messageForApiCode(null, status: 429), contains('quá nhanh'));
    expect(messageForApiCode('UNKNOWN'), contains('Có lỗi'));
  });
}
