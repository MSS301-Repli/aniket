import 'package:animal_locket/core/network/api_client.dart';
import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:animal_locket/features/chat/domain/glyph_server_shape.dart';

class GlyphRemoteDataSource {
  const GlyphRemoteDataSource(this._api);

  final ApiClient _api;

  Future<GlyphSyncPage> sync(
    String conversationId, {
    required int afterRevision,
    int limit = 100,
  }) async {
    final data =
        (await _api.get<Map<String, dynamic>>(
          '/chat/conversations/$conversationId/glyphs',
          queryParameters: {
            'afterRevision': afterRevision,
            'limit': limit.clamp(1, 100),
          },
        )).data ??
        const <String, dynamic>{};
    final items = (data['items'] as List? ?? const [])
        .whereType<Map>()
        .map((item) => _fromJson(item.cast<String, dynamic>(), conversationId))
        .toList(growable: false);
    final revision = (data['revision'] as num?)?.toInt() ?? afterRevision;
    return GlyphSyncPage(
      revision: revision,
      items: items,
      hasMore: data['hasMore'] == true,
      nextAfterRevision:
          (data['nextAfterRevision'] as num?)?.toInt() ?? revision,
    );
  }

  Future<GlyphDefinition> publish({
    required String conversationId,
    required String clientMutationId,
    required GlyphDefinition glyph,
  }) async {
    final response = await _api.post<Map<String, dynamic>>(
      '/chat/conversations/$conversationId/glyphs',
      data: {
        'clientMutationId': clientMutationId,
        'meaning': glyph.meaning,
        'color': _colorHex(glyph.colorArgb),
        'points': glyphWirePoints(glyph.points),
        'pointCount': 64,
      },
    );
    return _fromJson(
      response.data!,
      conversationId,
    ).copyWith(sourceLocalId: glyph.id);
  }

  Future<void> archive(String conversationId, String glyphId) =>
      _api.delete<void>('/chat/conversations/$conversationId/glyphs/$glyphId');

  GlyphDefinition _fromJson(Map<String, dynamic> json, String conversationId) {
    final raw = (json['points'] as List? ?? const [])
        .whereType<num>()
        .map((value) => value.toDouble())
        .toList(growable: false);
    final points = <GlyphPoint>[];
    for (var i = 0; i + 1 < raw.length; i += 2) {
      points.add(GlyphPoint(raw[i] / 1000, raw[i + 1] / 1000));
    }
    final createdAt =
        DateTime.tryParse(json['createdAt']?.toString() ?? '')?.toUtc() ??
        DateTime.now().toUtc();
    return GlyphDefinition(
      id: json['id'].toString(),
      kind: GlyphKind.conversation,
      conversationId: conversationId,
      creatorPublicId: json['creatorPublicId']?.toString(),
      meaning: json['meaning']?.toString() ?? '',
      colorArgb: _parseColor(json['color']?.toString()),
      points: points,
      shapeHash: json['shapeHash']?.toString() ?? '',
      status: json['status'] == 'ARCHIVED'
          ? GlyphStatus.archived
          : GlyphStatus.active,
      serverRevision: (json['revision'] as num?)?.toInt() ?? 0,
      createdAt: createdAt,
      updatedAt:
          DateTime.tryParse(json['updatedAt']?.toString() ?? '')?.toUtc() ??
          createdAt,
    );
  }

  int _parseColor(String? value) {
    final text = (value ?? '').replaceFirst('#', '');
    final parsed = int.tryParse(text, radix: 16);
    if (parsed == null) return 0xFF818CF8;
    return text.length == 6 ? 0xFF000000 | parsed : parsed;
  }

  String _colorHex(int argb) =>
      '#${(argb & 0xFFFFFF).toRadixString(16).padLeft(6, '0').toUpperCase()}';
}
