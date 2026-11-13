import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';

class CacheDatabase {
  CacheDatabase._(this._database);
  final Database _database;
  static const schemaVersion = 2;

  Database get rawDatabase => _database;

  static Future<CacheDatabase> open({
    DatabaseFactory? factory,
    String? path,
  }) async {
    final resolvedPath =
        path ??
        '${(await getApplicationSupportDirectory()).path}/animal_locket_cache.db';
    final database = await (factory ?? databaseFactory).openDatabase(
      resolvedPath,
      options: OpenDatabaseOptions(
        version: schemaVersion,
        onConfigure: (db) async {
          await db.execute('PRAGMA foreign_keys = ON');
        },
        onCreate: (db, _) async {
          await _createCacheEntries(db);
          await _createGlyphTables(db);
        },
        onUpgrade: (db, oldVersion, newVersion) async {
          if (oldVersion < 2) await _createGlyphTables(db);
        },
      ),
    );
    return CacheDatabase._(database);
  }

  static Future<void> _createCacheEntries(DatabaseExecutor db) async {
    await db.execute(
      'CREATE TABLE cache_entries (cache_key TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at INTEGER NOT NULL)',
    );
  }

  static Future<void> _createGlyphTables(DatabaseExecutor db) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS glyph_library (
        local_id TEXT PRIMARY KEY,
        account_public_id TEXT,
        kind TEXT NOT NULL CHECK (kind IN ('SYSTEM', 'PERSONAL')),
        meaning TEXT NOT NULL,
        color_argb INTEGER NOT NULL,
        points_blob BLOB NOT NULL,
        shape_hash TEXT NOT NULL,
        is_active INTEGER NOT NULL DEFAULT 1,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL,
        last_used_at INTEGER
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS conversation_glyphs (
        server_id TEXT PRIMARY KEY,
        account_public_id TEXT NOT NULL,
        conversation_id TEXT NOT NULL,
        creator_public_id TEXT NOT NULL,
        source_local_id TEXT,
        meaning TEXT NOT NULL,
        color_argb INTEGER NOT NULL,
        points_blob BLOB NOT NULL,
        shape_hash TEXT NOT NULL,
        status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
        server_revision INTEGER NOT NULL,
        hidden_locally INTEGER NOT NULL DEFAULT 0,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      )
    ''');
    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_conv_glyph_active
      ON conversation_glyphs(account_public_id, conversation_id, status)
    ''');
    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_conv_glyph_revision
      ON conversation_glyphs(account_public_id, conversation_id, server_revision)
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS glyph_sync_state (
        account_public_id TEXT NOT NULL,
        conversation_id TEXT NOT NULL,
        last_revision INTEGER NOT NULL DEFAULT 0,
        last_synced_at INTEGER,
        PRIMARY KEY(account_public_id, conversation_id)
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS glyph_mutation_outbox (
        operation_id TEXT PRIMARY KEY,
        account_public_id TEXT NOT NULL,
        conversation_id TEXT NOT NULL,
        action TEXT NOT NULL CHECK (action IN ('PUBLISH', 'ARCHIVE')),
        local_glyph_id TEXT,
        server_glyph_id TEXT,
        payload_json TEXT,
        state TEXT NOT NULL CHECK (state IN ('PENDING', 'SENDING', 'FAILED')),
        attempt_count INTEGER NOT NULL DEFAULT 0,
        next_attempt_at INTEGER,
        last_error_code TEXT,
        created_at INTEGER NOT NULL
      )
    ''');
    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_glyph_outbox_due
      ON glyph_mutation_outbox(account_public_id, state, next_attempt_at)
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS chat_outbox (
        client_id TEXT PRIMARY KEY,
        account_public_id TEXT NOT NULL,
        conversation_id TEXT NOT NULL,
        message_type TEXT NOT NULL CHECK (message_type IN ('TEXT', 'IMAGE', 'GLYPH')),
        payload_json TEXT NOT NULL,
        state TEXT NOT NULL CHECK (state IN ('PENDING', 'SENDING', 'FAILED')),
        attempt_count INTEGER NOT NULL DEFAULT 0,
        next_attempt_at INTEGER,
        created_at INTEGER NOT NULL
      )
    ''');
    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_chat_outbox_due
      ON chat_outbox(account_public_id, state, next_attempt_at)
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS dictionary_meta (
        meta_key TEXT PRIMARY KEY,
        meta_value TEXT NOT NULL
      )
    ''');
  }

  Future<void> write(String key, Object payload) async =>
      _database.insert('cache_entries', {
        'cache_key': key,
        'payload': jsonEncode(payload),
        'updated_at': DateTime.now().toUtc().millisecondsSinceEpoch,
      }, conflictAlgorithm: ConflictAlgorithm.replace);
  Future<Object?> read(String key) async {
    final rows = await _database.query(
      'cache_entries',
      columns: ['payload'],
      where: 'cache_key = ?',
      whereArgs: [key],
      limit: 1,
    );
    if (rows.isEmpty) return null;
    return jsonDecode(rows.first['payload']! as String);
  }

  Future<void> clear() async {
    await _database.transaction((txn) async {
      for (final table in const [
        'cache_entries',
        'glyph_library',
        'conversation_glyphs',
        'glyph_sync_state',
        'glyph_mutation_outbox',
        'chat_outbox',
        'dictionary_meta',
      ]) {
        await txn.delete(table);
      }
    });
  }

  Future<void> clearAccount(String accountPublicId) async {
    await _database.transaction((txn) async {
      for (final table in const [
        'glyph_library',
        'conversation_glyphs',
        'glyph_sync_state',
        'glyph_mutation_outbox',
        'chat_outbox',
      ]) {
        await txn.delete(
          table,
          where: 'account_public_id = ?',
          whereArgs: [accountPublicId],
        );
      }
    });
  }

  Future<void> close() => _database.close();
}

final cacheDatabaseProvider = FutureProvider<CacheDatabase>((ref) async {
  final database = await CacheDatabase.open();
  ref.onDispose(database.close);
  return database;
});
