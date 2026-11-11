import 'dart:io';

import 'package:animal_locket/core/cache/cache_database.dart';
import 'package:animal_locket/features/chat/data/glyph_local_data_source.dart';
import 'package:animal_locket/features/chat/data/system_glyphs.dart';
import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  sqfliteFfiInit();

  late Directory directory;
  late String path;

  setUp(() async {
    directory = await Directory.systemTemp.createTemp(
      'animal-locket-glyph-test',
    );
    path = '${directory.path}${Platform.pathSeparator}cache.db';
  });

  tearDown(() async {
    if (await directory.exists()) await directory.delete(recursive: true);
  });

  test('migrates v1 cache without losing cache entries', () async {
    final legacy = await databaseFactoryFfi.openDatabase(
      path,
      options: OpenDatabaseOptions(
        version: 1,
        onCreate: (db, _) async {
          await db.execute(
            'CREATE TABLE cache_entries (cache_key TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at INTEGER NOT NULL)',
          );
          await db.insert('cache_entries', {
            'cache_key': 'keep-me',
            'payload': '{"ok":true}',
            'updated_at': 1,
          });
        },
      ),
    );
    await legacy.close();

    final cache = await CacheDatabase.open(
      factory: databaseFactoryFfi,
      path: path,
    );
    expect(await cache.read('keep-me'), {'ok': true});
    final tables = await cache.rawDatabase.rawQuery(
      "SELECT name FROM sqlite_master WHERE type='table'",
    );
    final names = tables.map((row) => row['name']).toSet();
    expect(
      names,
      containsAll(['glyph_library', 'conversation_glyphs', 'chat_outbox']),
    );
    await cache.close();
  });

  test('seeds once and isolates personal glyphs by account', () async {
    final cache = await CacheDatabase.open(
      factory: databaseFactoryFfi,
      path: path,
    );
    final local = GlyphLocalDataSource(cache);
    await local.ensureSystemGlyphs();
    await local.ensureSystemGlyphs();
    expect(await local.systemGlyphs(), hasLength(buildSystemGlyphs().length));

    final personal = buildSystemGlyphs().first.copyWith(
      id: 'personal-a',
      kind: GlyphKind.personal,
      accountPublicId: 'account-a',
      meaning: 'Riêng A',
      updatedAt: DateTime.now().toUtc(),
    );
    await local.savePersonal(personal);
    expect(await local.personalGlyphs('account-a'), hasLength(1));
    expect(await local.personalGlyphs('account-b'), isEmpty);
    await cache.close();
  });

  test('persists chat outbox across database reopen', () async {
    var cache = await CacheDatabase.open(
      factory: databaseFactoryFfi,
      path: path,
    );
    var local = GlyphLocalDataSource(cache);
    await local.enqueueChat(
      ChatOutboxRecord(
        clientId: 'client-1',
        accountPublicId: 'account-a',
        conversationId: 'conversation-1',
        messageType: 'GLYPH',
        payload: const {
          'glyphIds': ['g-1'],
        },
        attemptCount: 0,
        createdAt: DateTime.utc(2026, 1, 1),
      ),
    );
    await cache.close();

    cache = await CacheDatabase.open(factory: databaseFactoryFfi, path: path);
    local = GlyphLocalDataSource(cache);
    final queued = await local.pendingChat('account-a', 'conversation-1');
    expect(queued, hasLength(1));
    expect(queued.single.payload['glyphIds'], ['g-1']);
    await cache.close();
  });

  test('persists glyph mutation outbox across database reopen', () async {
    var cache = await CacheDatabase.open(
      factory: databaseFactoryFfi,
      path: path,
    );
    var local = GlyphLocalDataSource(cache);
    await local.enqueueMutation(
      const GlyphMutationRecord(
        operationId: 'operation-1',
        accountPublicId: 'account-a',
        conversationId: 'conversation-1',
        action: 'PUBLISH',
        localGlyphId: 'local-1',
        payload: {'meaning': 'Chào'},
        attemptCount: 0,
      ),
    );
    await cache.close();

    cache = await CacheDatabase.open(factory: databaseFactoryFfi, path: path);
    local = GlyphLocalDataSource(cache);
    final queued = await local.pendingMutations('account-a');
    expect(queued, hasLength(1));
    expect(queued.single.operationId, 'operation-1');
    expect(queued.single.payload['meaning'], 'Chào');
    await cache.close();
  });

  test('stores tombstones and advances sync checkpoint atomically', () async {
    final cache = await CacheDatabase.open(
      factory: databaseFactoryFfi,
      path: path,
    );
    final local = GlyphLocalDataSource(cache);
    final source = buildSystemGlyphs().first;
    final tombstone = source.copyWith(
      id: 'server-glyph',
      kind: GlyphKind.conversation,
      accountPublicId: 'account-a',
      conversationId: 'conversation-1',
      creatorPublicId: 'account-b',
      status: GlyphStatus.archived,
      serverRevision: 4,
    );
    await local.upsertConversationGlyphs(
      accountPublicId: 'account-a',
      conversationId: 'conversation-1',
      glyphs: [tombstone],
      checkpointRevision: 4,
    );

    final stored = await local.conversationGlyphs(
      'account-a',
      'conversation-1',
    );
    expect(stored.single.status, GlyphStatus.archived);
    expect(await local.lastRevision('account-a', 'conversation-1'), 4);
    await cache.close();
  });

  test('keeps local source link when a later delta omits it', () async {
    final cache = await CacheDatabase.open(
      factory: databaseFactoryFfi,
      path: path,
    );
    final local = GlyphLocalDataSource(cache);
    final source = buildSystemGlyphs().first.copyWith(
      id: 'server-glyph',
      kind: GlyphKind.conversation,
      accountPublicId: 'account-a',
      conversationId: 'conversation-1',
      creatorPublicId: 'account-a',
      sourceLocalId: 'local-glyph',
      serverRevision: 1,
    );
    await local.upsertConversationGlyphs(
      accountPublicId: 'account-a',
      conversationId: 'conversation-1',
      glyphs: [source],
      checkpointRevision: 1,
    );
    await local.upsertConversationGlyphs(
      accountPublicId: 'account-a',
      conversationId: 'conversation-1',
      glyphs: [
        GlyphDefinition(
          id: source.id,
          kind: source.kind,
          accountPublicId: source.accountPublicId,
          conversationId: source.conversationId,
          creatorPublicId: source.creatorPublicId,
          meaning: 'Đã cập nhật',
          colorArgb: source.colorArgb,
          points: source.points,
          shapeHash: source.shapeHash,
          status: source.status,
          serverRevision: 2,
          createdAt: source.createdAt,
          updatedAt: source.updatedAt,
        ),
      ],
      checkpointRevision: 2,
    );

    final stored = await local.conversationGlyphs(
      'account-a',
      'conversation-1',
    );
    expect(stored.single.sourceLocalId, 'local-glyph');
    expect(stored.single.meaning, 'Đã cập nhật');
    await cache.close();
  });

  test('backoff hides failed chat item until its next attempt', () async {
    final cache = await CacheDatabase.open(
      factory: databaseFactoryFfi,
      path: path,
    );
    final local = GlyphLocalDataSource(cache);
    await local.enqueueChat(
      ChatOutboxRecord(
        clientId: 'client-backoff',
        accountPublicId: 'account-a',
        conversationId: 'conversation-1',
        messageType: 'GLYPH',
        payload: const {
          'glyphIds': ['glyph-1'],
        },
        attemptCount: 0,
        createdAt: DateTime.utc(2026, 1, 1),
      ),
    );
    await local.failChat('client-backoff', attemptCount: 0, retryable: true);

    expect(await local.pendingChat('account-a', 'conversation-1'), isEmpty);
    final row = (await cache.rawDatabase.query(
      'chat_outbox',
      where: 'client_id = ?',
      whereArgs: ['client-backoff'],
    )).single;
    expect(row['state'], 'FAILED');
    expect(row['attempt_count'], 1);
    expect(row['next_attempt_at'], isNotNull);
    await cache.close();
  });

  test('does not retry non retryable outbox failures', () async {
    final cache = await CacheDatabase.open(
      factory: databaseFactoryFfi,
      path: path,
    );
    final local = GlyphLocalDataSource(cache);
    await local.enqueueMutation(
      const GlyphMutationRecord(
        operationId: 'op-1',
        accountPublicId: 'account-a',
        conversationId: 'conversation-1',
        action: 'PUBLISH',
        localGlyphId: 'local-1',
        payload: {},
        attemptCount: 0,
      ),
    );
    await local.failMutation(
      'op-1',
      attemptCount: 0,
      errorCode: 'GLYPH_INVALID_POINTS',
      retryable: false,
    );
    expect(await local.pendingMutations('account-a'), isEmpty);
    await cache.close();
  });
}
