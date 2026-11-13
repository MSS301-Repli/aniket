import 'dart:math' as math;

enum GlyphKind { system, personal, conversation }

enum GlyphStatus { active, archived }

class GlyphPoint {
  const GlyphPoint(this.x, this.y);

  final double x;
  final double y;

  double distanceTo(GlyphPoint other) {
    final dx = other.x - x;
    final dy = other.y - y;
    return math.sqrt(dx * dx + dy * dy);
  }
}

class GlyphDefinition {
  const GlyphDefinition({
    required this.id,
    required this.kind,
    required this.meaning,
    required this.colorArgb,
    required this.points,
    required this.shapeHash,
    required this.createdAt,
    required this.updatedAt,
    this.accountPublicId,
    this.conversationId,
    this.creatorPublicId,
    this.sourceLocalId,
    this.status = GlyphStatus.active,
    this.serverRevision,
    this.hiddenLocally = false,
  });

  final String id;
  final GlyphKind kind;
  final String? accountPublicId;
  final String? conversationId;
  final String? creatorPublicId;
  final String? sourceLocalId;
  final String meaning;
  final int colorArgb;
  final List<GlyphPoint> points;
  final String shapeHash;
  final GlyphStatus status;
  final int? serverRevision;
  final bool hiddenLocally;
  final DateTime createdAt;
  final DateTime updatedAt;

  bool get isAvailable => status == GlyphStatus.active && !hiddenLocally;

  GlyphDefinition copyWith({
    String? id,
    GlyphKind? kind,
    String? accountPublicId,
    String? conversationId,
    String? creatorPublicId,
    String? sourceLocalId,
    String? meaning,
    int? colorArgb,
    List<GlyphPoint>? points,
    String? shapeHash,
    GlyphStatus? status,
    int? serverRevision,
    bool? hiddenLocally,
    DateTime? updatedAt,
  }) => GlyphDefinition(
    id: id ?? this.id,
    kind: kind ?? this.kind,
    accountPublicId: accountPublicId ?? this.accountPublicId,
    conversationId: conversationId ?? this.conversationId,
    creatorPublicId: creatorPublicId ?? this.creatorPublicId,
    sourceLocalId: sourceLocalId ?? this.sourceLocalId,
    meaning: meaning ?? this.meaning,
    colorArgb: colorArgb ?? this.colorArgb,
    points: points ?? this.points,
    shapeHash: shapeHash ?? this.shapeHash,
    status: status ?? this.status,
    serverRevision: serverRevision ?? this.serverRevision,
    hiddenLocally: hiddenLocally ?? this.hiddenLocally,
    createdAt: createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
  );
}

class GlyphRecognition {
  const GlyphRecognition({
    required this.best,
    required this.score,
    required this.alternatives,
    required this.ambiguous,
  });

  final GlyphDefinition? best;
  final double score;
  final List<GlyphCandidate> alternatives;
  final bool ambiguous;

  bool get matched => best != null && score >= 0.75 && !ambiguous;
}

class GlyphCandidate {
  const GlyphCandidate(this.glyph, this.score);

  final GlyphDefinition glyph;
  final double score;
}

class GlyphSyncPage {
  const GlyphSyncPage({
    required this.revision,
    required this.items,
    required this.hasMore,
    required this.nextAfterRevision,
  });

  final int revision;
  final List<GlyphDefinition> items;
  final bool hasMore;
  final int nextAfterRevision;
}
