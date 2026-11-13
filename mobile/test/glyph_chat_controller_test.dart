import 'dart:async';
import 'dart:io';

import 'package:animal_locket/core/auth/auth_controller.dart';
import 'package:animal_locket/core/auth/session_manager.dart';
import 'package:animal_locket/core/cache/cache_database.dart';
import 'package:animal_locket/core/config/app_config.dart';
import 'package:animal_locket/core/config/backend_mode.dart';
import 'package:animal_locket/core/config/glyph_feature_flags.dart';
import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/network/api_client.dart';
import 'package:animal_locket/core/network/token_store.dart';
import 'package:animal_locket/features/chat/data/chat_realtime_service.dart';
import 'package:animal_locket/features/chat/data/chat_repository_impl.dart';
import 'package:animal_locket/features/chat/data/glyph_local_data_source.dart';
import 'package:animal_locket/features/chat/data/glyph_repository_impl.dart';
import 'package:animal_locket/features/chat/data/system_glyphs.dart';
import 'package:animal_locket/features/chat/domain/chat_repository.dart';
import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:animal_locket/features/chat/domain/glyph_server_shape.dart';
import 'package:animal_locket/features/chat/presentation/chat_controllers.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

const _config = AppConfig(
  apiBaseUrl: 'http://localhost',
  webSocketUrl: 'ws://localhost',
  keycloakIssuer: 'http://localhost/realms/test',
  keycloakClientId: 'test',
  oauthRedirectUrl: 'test:/oauth',
  enablePush: false,
  enableBilling: false,
  backendTests: false,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  sqfliteFfiInit();

  late Directory directory;
  late CacheDatabase cache;
  late GlyphLocalDataSource local;
  late _FakeRealtime realtime;
  late _FakeChatRepository chat;
  late ProviderContainer container;

  setUp(() async {
    directory = await Directory.systemTemp.createTemp(
      'animal-locket-chat-controller',
    );
    cache = await CacheDatabase.open(
      factory: databaseFactoryFfi,
      path: '${directory.path}${Platform.pathSeparator}cache.db',
    );
    local = GlyphLocalDataSource(cache);
    realtime = _FakeRealtime();
    chat = _FakeChatRepository();
    container = ProviderContainer(
      overrides: [
        authControllerProvider.overrideWith(_ReadyAuthController.new),
        chatRepositoryProvider.overrideWithValue(chat),
        chatRealtimeProvider.overrideWithValue(realtime),
        glyphLocalDataSourceProvider.overrideWith((_) async => local),
        cacheDatabaseProvider.overrideWith((_) async => cache),
        glyphFeatureFlagsProvider.overrideWithValue(
          const GlyphFeatureFlags(
            readEnabled: true,
            personalLibraryEnabled: true,
            publishEnabled: true,
            sendEnabled: true,
          ),
        ),
      ],
    );
  });

  tearDown(() async {
    container.dispose();
    realtime.dispose();
    await cache.close();
    if (await directory.exists()) await directory.delete(recursive: true);
  });

  GlyphRepositoryImpl repo() => GlyphRepositoryImpl(
    cache: Future.value(cache),
    api: ApiClient(
      baseUrl: 'http://localhost',
      sessionManager: SessionManager(_config, _MemoryTokenStore()),
    ),
    mode: BackendMode.live,
    flags: const GlyphFeatureFlags(
      readEnabled: true,
      personalLibraryEnabled: true,
      publishEnabled: true,
      sendEnabled: true,
    ),
  );

  test('resending a published shape reuses the server copy (no 409)', () async {
    final repository = repo();
    await repository.ensureSystemGlyphs();
    final system = buildSystemGlyphs().first;
    // Copy as synced from the server: server hash, no local link (e.g. the
    // other participant published this shape first).
    await local.upsertConversationGlyphs(
      accountPublicId: 'account-a',
      conversationId: 'conversation-1',
      glyphs: [
        _conversationGlyph('server-copy').copyWith(
          shapeHash: serverShapeHash(system.points),
          creatorPublicId: 'account-b',
        ),
      ],
      checkpointRevision: 5,
    );

    // No network in this test: a publish request would throw.
    final first = await repository.publishGlyph(
      accountPublicId: 'account-a',
      conversationId: 'conversation-1',
      personalGlyph: system,
    );
    final second = await repository.publishGlyph(
      accountPublicId: 'account-a',
      conversationId: 'conversation-1',
      personalGlyph: system,
    );
    expect(first.id, 'server-copy');
    expect(second.id, 'server-copy');
    expect(await local.lastRevision('account-a', 'conversation-1'), 5);
    final picker = await repository.availableGlyphs(
      'account-a',
      'conversation-1',
    );
    expect(picker.map((g) => g.id), isNot(contains('server-copy')));
  });

  test('bubbles resolve the conversation copy the picker hides', () async {
    final repository = repo();
    await repository.ensureSystemGlyphs();
    final system = buildSystemGlyphs().first;
    // What publishGlyph stores on the sender's device: a server copy linked to
    // the local (here: system) glyph. Messages carry this copy's id.
    await local.upsertConversationGlyphs(
      accountPublicId: 'account-a',
      conversationId: 'conversation-1',
      glyphs: [
        _conversationGlyph('server-copy').copyWith(sourceLocalId: system.id),
      ],
      checkpointRevision: 1,
    );

    final picker = await repository.availableGlyphs(
      'account-a',
      'conversation-1',
    );
    final bubbles = await repository.messageGlyphs(
      'account-a',
      'conversation-1',
    );
    expect(picker.map((g) => g.id), isNot(contains('server-copy')));
    expect(bubbles.map((g) => g.id), contains('server-copy'));
  });

  test('adds optimistic glyph then resolves it from websocket ack', () async {
    realtime.state = ChatConnectionState.connected;
    final provider = conversationControllerProvider('conversation-1');
    await container.read(provider.future);
    final controller = container.read(provider.notifier);
    final glyph = _conversationGlyph('glyph-1');

    expect(await controller.sendGlyphs([glyph]), isTrue);
    final optimistic = container.read(provider).requireValue.messages.single;
    expect(optimistic.pending, isTrue);
    expect(optimistic.glyphIds, ['glyph-1']);
    expect(realtime.sent.last['type'], 'send');
    expect(
      await local.pendingChat('account-a', 'conversation-1'),
      hasLength(1),
    );

    realtime.emit({
      'type': 'ack',
      'data': {'clientId': optimistic.clientId, 'messageId': 'message-1'},
    });
    await Future<void>.delayed(const Duration(milliseconds: 20));

    final acknowledged = container.read(provider).requireValue.messages.single;
    expect(acknowledged.id, 'message-1');
    expect(acknowledged.pending, isFalse);
    expect(await local.pendingChat('account-a', 'conversation-1'), isEmpty);
  });

  test(
    'server echo arriving before ack does not duplicate the bubble',
    () async {
      // chat-service phát 'message' cho chính người gửi TRƯỚC khi trả 'ack'.
      realtime.state = ChatConnectionState.connected;
      final provider = conversationControllerProvider('conversation-1');
      await container.read(provider.future);
      final controller = container.read(provider.notifier);

      expect(
        await controller.sendGlyphs([_conversationGlyph('glyph-1')]),
        isTrue,
      );
      final optimistic = container.read(provider).requireValue.messages.single;

      realtime.emit({
        'type': 'message',
        'data': {
          'id': 'message-1',
          'conversationId': 'conversation-1',
          'senderId': 'account-a',
          'type': 'GLYPH',
          'text': 'xin chào',
          'glyphIds': ['glyph-1'],
          'createdAt': DateTime.now().toUtc().toIso8601String(),
          'clientId': optimistic.clientId,
        },
      });
      await Future<void>.delayed(const Duration(milliseconds: 20));
      var messages = container.read(provider).requireValue.messages;
      expect(messages, hasLength(1));
      expect(messages.single.id, 'message-1');
      expect(messages.single.pending, isFalse);

      realtime.emit({
        'type': 'ack',
        'data': {'clientId': optimistic.clientId, 'messageId': 'message-1'},
      });
      await Future<void>.delayed(const Duration(milliseconds: 20));
      messages = container.read(provider).requireValue.messages;
      expect(messages, hasLength(1), reason: 'ack không được tạo bản thứ hai');
      expect(await local.pendingChat('account-a', 'conversation-1'), isEmpty);
      expect(
        realtime.sent.where((f) => f['type'] == 'send'),
        hasLength(1),
        reason: 'chỉ gửi một lần',
      );
    },
  );

  test('reconnect and resume fetch messages missed while offline', () async {
    realtime.state = ChatConnectionState.connected;
    final provider = conversationControllerProvider('conversation-1');
    await container.read(provider.future);
    expect(container.read(provider).requireValue.messages, isEmpty);

    // Tin đến lúc WebSocket đứt: server không đẩy lại, chỉ có qua REST.
    ChatMessage missed(String id, int minute) => ChatMessage(
      id: id,
      conversationId: 'conversation-1',
      senderId: 'account-b',
      type: ChatMessageType.text,
      text: 'xin chào $id',
      createdAt: DateTime.utc(2026, 1, 1, 0, minute),
    );
    chat.serverMessages = [missed('m1', 1)];
    realtime.emit({
      'type': 'connection',
      'data': {'state': 'connected'},
    });
    await pumpEventQueue();
    expect(container.read(provider).requireValue.messages.map((m) => m.id), [
      'm1',
    ]);

    chat.serverMessages = [missed('m1', 1), missed('m2', 2)];
    realtime.emit({'type': 'resumed'});
    await pumpEventQueue();
    expect(container.read(provider).requireValue.messages.map((m) => m.id), [
      'm1',
      'm2',
    ], reason: 'không nhân đôi tin đã có');
  });

  test('uses REST while disconnected and locks composer when closed', () async {
    realtime.state = ChatConnectionState.disconnected;
    final provider = conversationControllerProvider('conversation-1');
    await container.read(provider.future);
    final controller = container.read(provider.notifier);

    expect(
      await controller.sendGlyphs([_conversationGlyph('glyph-rest')]),
      isTrue,
    );
    expect(chat.glyphSendCount, 1);
    expect(
      container.read(provider).requireValue.messages.single.pending,
      isFalse,
    );
    expect(await local.pendingChat('account-a', 'conversation-1'), isEmpty);

    realtime.emit({
      'type': 'closed',
      'data': {'conversationId': 'conversation-1', 'reason': 'CLOSED_BY_USER'},
    });
    expect(
      container.read(provider).requireValue.closedReason,
      'CLOSED_BY_USER',
    );
    expect(
      await controller.sendGlyphs([_conversationGlyph('glyph-blocked')]),
      isFalse,
    );
    expect(chat.glyphSendCount, 1);
  });
}

GlyphDefinition _conversationGlyph(String id) {
  final source = buildSystemGlyphs().first;
  return source.copyWith(
    id: id,
    kind: GlyphKind.conversation,
    accountPublicId: 'account-a',
    conversationId: 'conversation-1',
    creatorPublicId: 'account-a',
    serverRevision: 1,
  );
}

class _ReadyAuthController extends AuthController {
  @override
  AuthState build() => AuthState.ready(
    MeProfile(
      publicId: 'account-a',
      ageGroup: AgeGroup.adult,
      discoverable: true,
      plan: AccountPlan.free,
      createdAt: DateTime.utc(2026),
    ),
  );
}

class _FakeChatRepository implements ChatRepository {
  var glyphSendCount = 0;

  /// Tin trên server; mặc định rỗng.
  var serverMessages = <ChatMessage>[];

  @override
  Future<List<ChatMessage>> messages(
    String conversationId, {
    String? before,
    int limit = 30,
  }) async => [...serverMessages];

  @override
  Future<ChatMessage> sendGlyphs(
    String conversationId,
    List<String> glyphIds, {
    required String clientId,
  }) async {
    glyphSendCount++;
    return ChatMessage(
      id: 'rest-$glyphSendCount',
      conversationId: conversationId,
      senderId: 'account-a',
      type: ChatMessageType.glyph,
      glyphIds: glyphIds,
      clientId: clientId,
      createdAt: DateTime.utc(2026),
    );
  }

  @override
  Future<void> markRead(String conversationId) async {}

  @override
  Future<Conversation> createConversation(String withPublicId) =>
      throw UnimplementedError();

  @override
  Future<List<Conversation>> conversations() => throw UnimplementedError();

  @override
  Future<ChatMessage> sendImage(String conversationId, String mediaId) =>
      throw UnimplementedError();

  @override
  Future<ChatMessage> sendText(String conversationId, String text) =>
      throw UnimplementedError();
}

class _FakeRealtime extends ChatRealtimeService {
  _FakeRealtime()
    : super(_config, SessionManager(_config, _MemoryTokenStore()));

  final _controller = StreamController<Map<String, dynamic>>.broadcast(
    sync: true,
  );
  final sent = <Map<String, dynamic>>[];

  @override
  Stream<Map<String, dynamic>> get events => _controller.stream;

  void emit(Map<String, dynamic> frame) => _controller.add(frame);

  @override
  void send(Map<String, dynamic> frame) => sent.add(frame);

  @override
  void dispose() {
    _controller.close();
  }
}

class _MemoryTokenStore implements TokenStore {
  @override
  Future<void> clear() async {}

  @override
  Future<TokenBundle?> read() async => null;

  @override
  Future<void> save(TokenBundle bundle) async {}
}
