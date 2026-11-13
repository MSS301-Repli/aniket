import 'package:animal_locket/core/cache/cache_database.dart';
import 'package:animal_locket/core/config/backend_mode.dart';
import 'package:animal_locket/core/config/glyph_feature_flags.dart';
import 'package:animal_locket/core/network/api_client.dart';
import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/features/chat/data/glyph_local_data_source.dart';
import 'package:animal_locket/features/chat/data/glyph_remote_data_source.dart';
import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:animal_locket/features/chat/domain/glyph_point_codec.dart';
import 'package:animal_locket/features/chat/domain/glyph_recognizer.dart';
import 'package:animal_locket/features/chat/domain/glyph_repository.dart';
import 'package:animal_locket/features/chat/domain/glyph_server_shape.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

class GlyphRepositoryImpl implements GlyphRepository {
  GlyphRepositoryImpl({
    required this.cache,
    required ApiClient api,
    required this.mode,
    required this.flags,
  }) : _remote = GlyphRemoteDataSource(api);

  final Future<CacheDatabase> cache;
  final GlyphRemoteDataSource _remote;
  final BackendMode mode;
  final GlyphFeatureFlags flags;
  final _uuid = const Uuid();
  final _recognizer = const GlyphRecognizer();
  final _codec = const GlyphPointCodec();

  Future<GlyphLocalDataSource> get _local async =>
      GlyphLocalDataSource(await cache);

  @override
  Future<void> ensureSystemGlyphs() async =>
      (await _local).ensureSystemGlyphs();

  @override
  Future<List<GlyphDefinition>> personalGlyphs(String accountPublicId) async {
    final local = await _local;
    await local.ensureSystemGlyphs();
    return local.personalGlyphs(accountPublicId);
  }

  Future<List<GlyphDefinition>> availableGlyphs(
    String accountPublicId,
    String conversationId,
  ) async {
    final local = await _local;
    await local.ensureSystemGlyphs();
    final system = await local.systemGlyphs();
    final personal = await local.personalGlyphs(accountPublicId);
    final conversation = await local.conversationGlyphs(
      accountPublicId,
      conversationId,
    );
    final localIds = {
      ...system.map((glyph) => glyph.id),
      ...personal.map((glyph) => glyph.id),
    };
    return [
      ...system,
      ...personal,
      ...conversation.where(
        (glyph) =>
            glyph.sourceLocalId == null ||
            !localIds.contains(glyph.sourceLocalId),
      ),
    ].where((glyph) => glyph.isAvailable).toList(growable: false);
  }

  /// Every glyph a message in this conversation may reference, for rendering
  /// bubbles. Unlike [availableGlyphs] (the picker), this keeps the
  /// conversation copy of a local glyph — sent messages carry that copy's id —
  /// and glyphs retired after the message was sent.
  Future<List<GlyphDefinition>> messageGlyphs(
    String accountPublicId,
    String conversationId,
  ) async {
    final local = await _local;
    await local.ensureSystemGlyphs();
    return [
      ...await local.systemGlyphs(),
      ...await local.personalGlyphs(accountPublicId),
      ...await local.conversationGlyphs(accountPublicId, conversationId),
    ].where((glyph) => glyph.points.isNotEmpty).toList(growable: false);
  }

  @override
  Future<List<GlyphDefinition>> conversationGlyphs(
    String accountPublicId,
    String conversationId,
  ) async => (await _local).conversationGlyphs(accountPublicId, conversationId);

  @override
  Future<GlyphDefinition> savePersonalGlyph({
    required String accountPublicId,
    required String meaning,
    required int colorArgb,
    required List<GlyphPoint> points,
    String? localId,
  }) async {
    final normalizedMeaning = meaning.trim();
    if (normalizedMeaning.isEmpty || normalizedMeaning.length > 64) {
      throw const FormatException('Ý nghĩa glyph phải có từ 1 đến 64 ký tự.');
    }
    final existing = await personalGlyphs(accountPublicId);
    if (localId == null && existing.length >= 200) {
      throw StateError('Đã đạt giới hạn 200 ký hiệu cá nhân.');
    }
    final normalized = _recognizer.normalize(points);
    if (normalized.length != GlyphPointCodec.pointCount) {
      throw const FormatException('Nét vẽ chưa đủ dữ liệu.');
    }
    final bytes = _codec.encode(normalized);
    final now = DateTime.now().toUtc();
    final glyph = GlyphDefinition(
      id: localId ?? 'local-${_uuid.v4()}',
      kind: GlyphKind.personal,
      accountPublicId: accountPublicId,
      meaning: normalizedMeaning,
      colorArgb: colorArgb,
      points: normalized,
      shapeHash: _codec.hash(bytes),
      createdAt: now,
      updatedAt: now,
    );
    await (await _local).savePersonal(glyph);
    return glyph;
  }

  @override
  Future<void> deactivatePersonalGlyph(
    String accountPublicId,
    String localId,
  ) => _local.then(
    (local) => local.deactivatePersonal(accountPublicId, localId),
  );

  @override
  Future<GlyphDefinition> publishGlyph({
    required String accountPublicId,
    required String conversationId,
    required GlyphDefinition personalGlyph,
  }) async {
    final local = await _local;
    final existing = await _activeCopy(
      local,
      accountPublicId,
      conversationId,
      personalGlyph,
    );
    if (existing != null) return existing;
    if (mode == BackendMode.live && !flags.publishEnabled) {
      throw const GlyphFeatureUnavailable();
    }
    final operationId = _uuid.v4();
    final payload = <String, dynamic>{
      'meaning': personalGlyph.meaning,
      'colorArgb': personalGlyph.colorArgb,
      'points': personalGlyph.points
          .expand((point) => [point.x, point.y])
          .toList(growable: false),
    };
    await local.enqueueMutation(
      GlyphMutationRecord(
        operationId: operationId,
        accountPublicId: accountPublicId,
        conversationId: conversationId,
        action: 'PUBLISH',
        localGlyphId: personalGlyph.id,
        payload: payload,
        attemptCount: 0,
      ),
    );
    try {
      final GlyphDefinition published;
      if (mode == BackendMode.demo) {
        final now = DateTime.now().toUtc();
        published = personalGlyph.copyWith(
          id: 'demo-$conversationId-${personalGlyph.id}',
          kind: GlyphKind.conversation,
          accountPublicId: accountPublicId,
          conversationId: conversationId,
          creatorPublicId: accountPublicId,
          sourceLocalId: personalGlyph.id,
          serverRevision: now.microsecondsSinceEpoch,
          updatedAt: now,
        );
      } else {
        published = await _remote.publish(
          conversationId: conversationId,
          clientMutationId: operationId,
          glyph: personalGlyph,
        );
      }
      await local.upsertConversationGlyphs(
        accountPublicId: accountPublicId,
        conversationId: conversationId,
        glyphs: [published],
        // Keep the checkpoint: revisions between it and ours (other people's
        // glyphs) have not been synced yet and must not be skipped.
        checkpointRevision: await local.lastRevision(
          accountPublicId,
          conversationId,
        ),
      );
      await local.completeMutation(operationId);
      return published;
    } catch (error) {
      final code = error is ApiProblem ? error.code : null;
      if (code == 'GLYPH_DUPLICATE') {
        // Same shape already published (by the other person, another device,
        // or a cache without the local link): use that copy instead.
        await syncConversation(accountPublicId, conversationId);
        final copy = await _activeCopy(
          local,
          accountPublicId,
          conversationId,
          personalGlyph,
        );
        if (copy != null) {
          await local.completeMutation(operationId);
          return copy;
        }
      }
      await local.failMutation(
        operationId,
        attemptCount: 0,
        errorCode: code,
        retryable: error is! GlyphFeatureUnavailable,
      );
      rethrow;
    }
  }

  /// The ACTIVE conversation glyph with [source]'s shape, if any. Matches on
  /// the server's canonical hash, so it also finds copies published by the
  /// other participant; links an unlinked copy to [source] so the picker does
  /// not list the shape twice.
  Future<GlyphDefinition?> _activeCopy(
    GlyphLocalDataSource local,
    String accountPublicId,
    String conversationId,
    GlyphDefinition source,
  ) async {
    final serverHash = serverShapeHash(source.points);
    final copies =
        (await local.conversationGlyphs(accountPublicId, conversationId)).where(
          (item) =>
              item.status == GlyphStatus.active &&
              (item.shapeHash == serverHash ||
                  // Demo copies carry the local hash.
                  (item.sourceLocalId == source.id &&
                      item.shapeHash == source.shapeHash)),
        );
    final copy =
        copies.where((item) => item.sourceLocalId == source.id).firstOrNull ??
        copies.firstOrNull;
    if (copy == null || copy.sourceLocalId != null) return copy;
    final linked = copy.copyWith(sourceLocalId: source.id);
    await local.upsertConversationGlyphs(
      accountPublicId: accountPublicId,
      conversationId: conversationId,
      glyphs: [linked],
      checkpointRevision: await local.lastRevision(
        accountPublicId,
        conversationId,
      ),
    );
    return linked;
  }

  @override
  Future<int> syncConversation(
    String accountPublicId,
    String conversationId,
  ) async {
    final local = await _local;
    var revision = await local.lastRevision(accountPublicId, conversationId);
    if (mode == BackendMode.demo || !flags.readEnabled) return revision;
    try {
      var hasMore = true;
      while (hasMore) {
        final page = await _remote.sync(
          conversationId,
          afterRevision: revision,
        );
        revision = page.nextAfterRevision;
        await local.upsertConversationGlyphs(
          accountPublicId: accountPublicId,
          conversationId: conversationId,
          glyphs: page.items,
          checkpointRevision: revision,
        );
        hasMore = page.hasMore;
      }
      return revision;
    } on ApiProblem catch (problem) {
      // The mobile contract can be shipped before the backend endpoint. A 404
      // keeps cached/system glyphs usable until GLYPH_READ_ENABLED is enabled
      // against a compatible backend.
      if (problem.status == 404) return revision;
      rethrow;
    }
  }

  @override
  Future<void> retryPendingMutations(String accountPublicId) async {
    final local = await _local;
    for (final record in await local.pendingMutations(accountPublicId)) {
      if (record.action != 'PUBLISH' || record.localGlyphId == null) continue;
      final personal = (await local.personalGlyphs(accountPublicId))
          .where((item) => item.id == record.localGlyphId)
          .firstOrNull;
      if (personal == null) {
        await local.completeMutation(record.operationId);
        continue;
      }
      if (mode != BackendMode.demo && !flags.publishEnabled) continue;
      final rawPoints = (record.payload['points'] as List? ?? const [])
          .whereType<num>()
          .map((value) => value.toDouble())
          .toList(growable: false);
      final retryPoints = <GlyphPoint>[];
      for (var index = 0; index + 1 < rawPoints.length; index += 2) {
        retryPoints.add(GlyphPoint(rawPoints[index], rawPoints[index + 1]));
      }
      final retryGlyph = personal.copyWith(
        meaning: record.payload['meaning']?.toString() ?? personal.meaning,
        colorArgb:
            (record.payload['colorArgb'] as num?)?.toInt() ??
            personal.colorArgb,
        points: retryPoints.length == 64 ? retryPoints : personal.points,
      );
      try {
        final published = mode == BackendMode.demo
            ? retryGlyph.copyWith(
                id: 'demo-${record.conversationId}-${retryGlyph.id}',
                kind: GlyphKind.conversation,
                accountPublicId: accountPublicId,
                conversationId: record.conversationId,
                creatorPublicId: accountPublicId,
                sourceLocalId: retryGlyph.id,
                serverRevision: DateTime.now().microsecondsSinceEpoch,
              )
            : await _remote.publish(
                conversationId: record.conversationId,
                clientMutationId: record.operationId,
                glyph: retryGlyph,
              );
        await local.upsertConversationGlyphs(
          accountPublicId: accountPublicId,
          conversationId: record.conversationId,
          glyphs: [published],
          checkpointRevision: await local.lastRevision(
            accountPublicId,
            record.conversationId,
          ),
        );
        await local.completeMutation(record.operationId);
      } catch (error) {
        final retryable =
            error is! ApiProblem ||
            error.status == null ||
            error.status! >= 500 ||
            error.status == 429;
        await local.failMutation(
          record.operationId,
          attemptCount: record.attemptCount,
          errorCode: error is ApiProblem ? error.code : null,
          retryable: retryable,
        );
      }
    }
  }
}

class GlyphFeatureUnavailable implements Exception {
  const GlyphFeatureUnavailable();
}

final glyphLocalDataSourceProvider = FutureProvider<GlyphLocalDataSource>(
  (ref) async =>
      GlyphLocalDataSource(await ref.watch(cacheDatabaseProvider.future)),
);

final glyphRepositoryProvider = Provider<GlyphRepositoryImpl>(
  (ref) => GlyphRepositoryImpl(
    cache: ref.watch(cacheDatabaseProvider.future),
    api: ref.watch(apiClientProvider),
    mode: ref.watch(backendModeProvider),
    flags: ref.watch(glyphFeatureFlagsProvider),
  ),
);

extension<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
