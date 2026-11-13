import 'dart:convert';
import 'dart:typed_data';

import 'package:animal_locket/core/cache/cache_database.dart';
import 'package:animal_locket/features/chat/data/system_glyphs.dart';
import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:animal_locket/features/chat/domain/glyph_point_codec.dart';
import 'package:sqflite/sqflite.dart';

class GlyphMutationRecord {
  const GlyphMutationRecord({
    required this.operationId,
    required this.accountPublicId,
    required this.conversationId,
    required this.action,
    required this.payload,
    required this.attemptCount,
    this.localGlyphId,
    this.serverGlyphId,
  });

  final String operationId;
  final String accountPublicId;
  final String conversationId;
  final String action;
  final String? localGlyphId;
  final String? serverGlyphId;
  final Map<String, dynamic> payload;
  final int attemptCount;
}

class ChatOutboxRecord {
  const ChatOutboxRecord({
    required this.clientId,
    required this.accountPublicId,
    required this.conversationId,
    required this.messageType,
    required this.payload,
    required this.attemptCount,
    required this.createdAt,
  });

  final String clientId;
  final String accountPublicId;
  final String conversationId;
  final String messageType;
  final Map<String, dynamic> payload;
  final int attemptCount;
  final DateTime createdAt;
}

class GlyphLocalDataSource {
  GlyphLocalDataSource(this._cache, {this._codec = const GlyphPointCodec()});

  final CacheDatabase _cache;
  final GlyphPointCodec _codec;
  Database get _db => _cache.rawDatabase;

  Future<void> ensureSystemGlyphs() async {
    final current = await _db.query(
      'dictionary_meta',
      where: 'meta_key = ?',
      whereArgs: ['system_dictionary_version'],
      limit: 1,
    );
    if (current.firstOrNull?['meta_value'] == '1') return;
    await _db.transaction((txn) async {
      for (final glyph in buildSystemGlyphs()) {
        await txn.insert(
          'glyph_library',
          _libraryRow(glyph),
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
      }
      await txn.insert('dictionary_meta', {
        'meta_key': 'system_dictionary_version',
        'meta_value': '1',
      }, conflictAlgorithm: ConflictAlgorithm.replace);
    });
  }

  Future<List<GlyphDefinition>> systemGlyphs() async {
    final rows = await _db.query(
      'glyph_library',
      where: "kind = 'SYSTEM' AND is_active = 1",
      orderBy: 'local_id ASC',
    );
    return rows
        .map(_libraryFromRow)
        .map(localizeSystemGlyph)
        .toList(growable: false);
  }

  Future<List<GlyphDefinition>> personalGlyphs(String accountPublicId) async {
    final rows = await _db.query(
      'glyph_library',
      where: "account_public_id = ? AND kind = 'PERSONAL' AND is_active = 1",
      whereArgs: [accountPublicId],
      orderBy: 'updated_at DESC',
    );
    return rows.map(_libraryFromRow).toList(growable: false);
  }

  Future<List<GlyphDefinition>> conversationGlyphs(
    String accountPublicId,
    String conversationId,
  ) async {
    final rows = await _db.query(
      'conversation_glyphs',
      where: 'account_public_id = ? AND conversation_id = ?',
      whereArgs: [accountPublicId, conversationId],
      orderBy: 'server_revision ASC',
    );
    return rows.map(_conversationFromRow).toList(growable: false);
  }

  Future<void> savePersonal(GlyphDefinition glyph) => _db.insert(
    'glyph_library',
    _libraryRow(glyph),
    conflictAlgorithm: ConflictAlgorithm.replace,
  );

  Future<void> deactivatePersonal(String accountPublicId, String localId) =>
      _db.update(
        'glyph_library',
        {
          'is_active': 0,
          'updated_at': DateTime.now().toUtc().millisecondsSinceEpoch,
        },
        where: "local_id = ? AND account_public_id = ? AND kind = 'PERSONAL'",
        whereArgs: [localId, accountPublicId],
      );

  Future<void> upsertConversationGlyphs({
    required String accountPublicId,
    required String conversationId,
    required Iterable<GlyphDefinition> glyphs,
    required int checkpointRevision,
  }) async {
    await _db.transaction((txn) async {
      for (final glyph in glyphs) {
        final row = _conversationRow(accountPublicId, conversationId, glyph);
        if (glyph.sourceLocalId == null) {
          final existing = await txn.query(
            'conversation_glyphs',
            columns: ['source_local_id'],
            where: 'server_id = ? AND account_public_id = ?',
            whereArgs: [glyph.id, accountPublicId],
            limit: 1,
          );
          final sourceLocalId = existing.firstOrNull?['source_local_id'];
          if (sourceLocalId != null) row['source_local_id'] = sourceLocalId;
        }
        await txn.insert(
          'conversation_glyphs',
          row,
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
      }
      await txn.insert('glyph_sync_state', {
        'account_public_id': accountPublicId,
        'conversation_id': conversationId,
        'last_revision': checkpointRevision,
        'last_synced_at': DateTime.now().toUtc().millisecondsSinceEpoch,
      }, conflictAlgorithm: ConflictAlgorithm.replace);
    });
  }

  Future<int> lastRevision(
    String accountPublicId,
    String conversationId,
  ) async {
    final rows = await _db.query(
      'glyph_sync_state',
      columns: ['last_revision'],
      where: 'account_public_id = ? AND conversation_id = ?',
      whereArgs: [accountPublicId, conversationId],
      limit: 1,
    );
    return (rows.firstOrNull?['last_revision'] as num?)?.toInt() ?? 0;
  }

  Future<void> enqueueMutation(GlyphMutationRecord record) =>
      _db.insert('glyph_mutation_outbox', {
        'operation_id': record.operationId,
        'account_public_id': record.accountPublicId,
        'conversation_id': record.conversationId,
        'action': record.action,
        'local_glyph_id': record.localGlyphId,
        'server_glyph_id': record.serverGlyphId,
        'payload_json': jsonEncode(record.payload),
        'state': 'PENDING',
        'attempt_count': record.attemptCount,
        'created_at': DateTime.now().toUtc().millisecondsSinceEpoch,
      }, conflictAlgorithm: ConflictAlgorithm.ignore);

  Future<List<GlyphMutationRecord>> pendingMutations(
    String accountPublicId,
  ) async {
    final now = DateTime.now().toUtc().millisecondsSinceEpoch;
    final rows = await _db.query(
      'glyph_mutation_outbox',
      where: "account_public_id = ? AND state IN ('PENDING', 'FAILED') AND (next_attempt_at IS NULL OR next_attempt_at <= ?)",
      whereArgs: [accountPublicId, now],
      orderBy: 'created_at ASC',
    );
    return rows
        .map((row) {
          final rawPayload = row['payload_json'] as String? ?? '{}';
          return GlyphMutationRecord(
            operationId: row['operation_id']! as String,
            accountPublicId: row['account_public_id']! as String,
            conversationId: row['conversation_id']! as String,
            action: row['action']! as String,
            localGlyphId: row['local_glyph_id'] as String?,
            serverGlyphId: row['server_glyph_id'] as String?,
            payload: (jsonDecode(rawPayload) as Map).cast<String, dynamic>(),
            attemptCount: (row['attempt_count'] as num?)?.toInt() ?? 0,
          );
        })
        .toList(growable: false);
  }

  Future<void> completeMutation(String operationId) => _db.delete(
    'glyph_mutation_outbox',
    where: 'operation_id = ?',
    whereArgs: [operationId],
  );

  Future<void> failMutation(
    String operationId, {
    required int attemptCount,
    String? errorCode,
    bool retryable = true,
  }) {
    final delaySeconds = [1, 2, 4, 8, 30, 60][attemptCount.clamp(0, 5)];
    return _db.update(
      'glyph_mutation_outbox',
      {
        'state': 'FAILED',
        'attempt_count': attemptCount + 1,
        'next_attempt_at': retryable
            ? DateTime.now()
                  .toUtc()
                  .add(Duration(seconds: delaySeconds))
                  .millisecondsSinceEpoch
            : DateTime.utc(9999, 12, 31).millisecondsSinceEpoch,
        'last_error_code': errorCode,
      },
      where: 'operation_id = ?',
      whereArgs: [operationId],
    );
  }

  Future<void> enqueueChat(ChatOutboxRecord record) =>
      _db.insert('chat_outbox', {
        'client_id': record.clientId,
        'account_public_id': record.accountPublicId,
        'conversation_id': record.conversationId,
        'message_type': record.messageType,
        'payload_json': jsonEncode(record.payload),
        'state': 'PENDING',
        'attempt_count': record.attemptCount,
        'created_at': record.createdAt.toUtc().millisecondsSinceEpoch,
      }, conflictAlgorithm: ConflictAlgorithm.replace);

  Future<void> completeChat(String clientId) =>
      _db.delete('chat_outbox', where: 'client_id = ?', whereArgs: [clientId]);

  Future<void> failChat(
    String clientId, {
    required int attemptCount,
    required bool retryable,
  }) {
    final delaySeconds = [1, 2, 4, 8, 30, 60][attemptCount.clamp(0, 5)];
    return _db.update(
      'chat_outbox',
      {
        'state': 'FAILED',
        'attempt_count': attemptCount + 1,
        'next_attempt_at': retryable
            ? DateTime.now()
                  .toUtc()
                  .add(Duration(seconds: delaySeconds))
                  .millisecondsSinceEpoch
            : DateTime.utc(9999, 12, 31).millisecondsSinceEpoch,
      },
      where: 'client_id = ?',
      whereArgs: [clientId],
    );
  }

  Future<List<ChatOutboxRecord>> pendingChat(
    String accountPublicId,
    String conversationId,
  ) async {
    final rows = await _db.query(
      'chat_outbox',
      where: "account_public_id = ? AND conversation_id = ? AND state IN ('PENDING', 'FAILED') AND (next_attempt_at IS NULL OR next_attempt_at <= ?)",
      whereArgs: [
        accountPublicId,
        conversationId,
        DateTime.now().toUtc().millisecondsSinceEpoch,
      ],
      orderBy: 'created_at ASC',
    );
    return rows
        .map((row) {
          final payload = (jsonDecode(row['payload_json']! as String) as Map)
              .cast<String, dynamic>();
          return ChatOutboxRecord(
            clientId: row['client_id']! as String,
            accountPublicId: row['account_public_id']! as String,
            conversationId: row['conversation_id']! as String,
            messageType: row['message_type']! as String,
            payload: payload,
            attemptCount: (row['attempt_count'] as num?)?.toInt() ?? 0,
            createdAt: _time(row['created_at']),
          );
        })
        .toList(growable: false);
  }

  Map<String, Object?> _libraryRow(GlyphDefinition glyph) => {
    'local_id': glyph.id,
    'account_public_id': glyph.accountPublicId,
    'kind': glyph.kind == GlyphKind.system ? 'SYSTEM' : 'PERSONAL',
    'meaning': glyph.meaning,
    'color_argb': glyph.colorArgb,
    'points_blob': _codec.encode(glyph.points),
    'shape_hash': glyph.shapeHash,
    'is_active': glyph.status == GlyphStatus.active ? 1 : 0,
    'created_at': glyph.createdAt.toUtc().millisecondsSinceEpoch,
    'updated_at': glyph.updatedAt.toUtc().millisecondsSinceEpoch,
  };

  Map<String, Object?> _conversationRow(
    String accountPublicId,
    String conversationId,
    GlyphDefinition glyph,
  ) => {
    'server_id': glyph.id,
    'account_public_id': accountPublicId,
    'conversation_id': conversationId,
    'creator_public_id': glyph.creatorPublicId ?? accountPublicId,
    'source_local_id': glyph.sourceLocalId,
    'meaning': glyph.meaning,
    'color_argb': glyph.colorArgb,
    'points_blob': _codec.encode(glyph.points),
    'shape_hash': glyph.shapeHash,
    'status': glyph.status == GlyphStatus.active ? 'ACTIVE' : 'ARCHIVED',
    'server_revision': glyph.serverRevision ?? 0,
    'hidden_locally': glyph.hiddenLocally ? 1 : 0,
    'created_at': glyph.createdAt.toUtc().millisecondsSinceEpoch,
    'updated_at': glyph.updatedAt.toUtc().millisecondsSinceEpoch,
  };

  GlyphDefinition _libraryFromRow(Map<String, Object?> row) => GlyphDefinition(
    id: row['local_id']! as String,
    accountPublicId: row['account_public_id'] as String?,
    kind: row['kind'] == 'SYSTEM' ? GlyphKind.system : GlyphKind.personal,
    meaning: row['meaning']! as String,
    colorArgb: (row['color_argb']! as num).toInt(),
    points: _codec.decode(row['points_blob']! as Uint8List),
    shapeHash: row['shape_hash']! as String,
    status: (row['is_active']! as num).toInt() == 1
        ? GlyphStatus.active
        : GlyphStatus.archived,
    createdAt: _time(row['created_at']),
    updatedAt: _time(row['updated_at']),
  );

  GlyphDefinition _conversationFromRow(Map<String, Object?> row) =>
      GlyphDefinition(
        id: row['server_id']! as String,
        accountPublicId: row['account_public_id']! as String,
        conversationId: row['conversation_id']! as String,
        creatorPublicId: row['creator_public_id']! as String,
        sourceLocalId: row['source_local_id'] as String?,
        kind: GlyphKind.conversation,
        meaning: row['meaning']! as String,
        colorArgb: (row['color_argb']! as num).toInt(),
        points: _codec.decode(row['points_blob']! as Uint8List),
        shapeHash: row['shape_hash']! as String,
        status: row['status'] == 'ARCHIVED'
            ? GlyphStatus.archived
            : GlyphStatus.active,
        serverRevision: (row['server_revision']! as num).toInt(),
        hiddenLocally: (row['hidden_locally']! as num).toInt() == 1,
        createdAt: _time(row['created_at']),
        updatedAt: _time(row['updated_at']),
      );

  DateTime _time(Object? value) =>
      DateTime.fromMillisecondsSinceEpoch((value! as num).toInt(), isUtc: true);
}

extension<T> on List<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
