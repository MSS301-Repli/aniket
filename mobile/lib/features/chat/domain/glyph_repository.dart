import 'package:animal_locket/features/chat/domain/glyph.dart';

abstract interface class GlyphRepository {
  Future<void> ensureSystemGlyphs();

  Future<List<GlyphDefinition>> personalGlyphs(String accountPublicId);

  Future<List<GlyphDefinition>> conversationGlyphs(
    String accountPublicId,
    String conversationId,
  );

  Future<GlyphDefinition> savePersonalGlyph({
    required String accountPublicId,
    required String meaning,
    required int colorArgb,
    required List<GlyphPoint> points,
    String? localId,
  });

  Future<void> deactivatePersonalGlyph(String accountPublicId, String localId);

  Future<GlyphDefinition> publishGlyph({
    required String accountPublicId,
    required String conversationId,
    required GlyphDefinition personalGlyph,
  });

  Future<int> syncConversation(String accountPublicId, String conversationId);

  Future<void> retryPendingMutations(String accountPublicId);
}
