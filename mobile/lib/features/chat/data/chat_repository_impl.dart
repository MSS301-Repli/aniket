import 'package:animal_locket/core/cache/cache_database.dart';
import 'package:animal_locket/core/config/backend_mode.dart';
import 'package:animal_locket/core/network/api_client.dart';
import 'package:animal_locket/features/chat/domain/chat_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class LiveChatRepository implements ChatRepository {
  const LiveChatRepository(this._api, this._cache);
  final ApiClient _api;
  final Future<CacheDatabase> _cache;
  @override
  Future<Conversation> createConversation(String withPublicId) async =>
      Conversation.fromJson(
        (await _api.post<Map<String, dynamic>>(
          '/chat/conversations',
          data: {'withPublicId': withPublicId},
        )).data!,
      );
  @override
  Future<List<Conversation>> conversations() async {
    try {
      final items =
          ((await _api.get<List<dynamic>>('/chat/conversations')).data ??
                  const [])
              .cast<Map<String, dynamic>>()
              .map(Conversation.fromJson)
              .toList();
      await (await _cache).write(
        'chat:conversations',
        items.map((item) => item.toJson()).toList(),
      );
      return items;
    } catch (_) {
      final cached = await (await _cache).read('chat:conversations');
      if (cached is! List) rethrow;
      return cached
          .cast<Map<String, dynamic>>()
          .map(Conversation.fromJson)
          .toList();
    }
  }

  @override
  Future<List<ChatMessage>> messages(
    String conversationId, {
    String? before,
    int limit = 30,
  }) async {
    try {
      final items =
          ((await _api.get<List<dynamic>>(
                    '/chat/conversations/$conversationId/messages',
                    queryParameters: {'before': ?before, 'limit': limit},
                  )).data ??
                  const [])
              .cast<Map<String, dynamic>>()
              .map(ChatMessage.fromJson)
              .toList();
      if (before == null) {
        await (await _cache).write(
          'chat:messages:$conversationId',
          items.map((item) => item.toJson()).toList(),
        );
      }
      return items;
    } catch (_) {
      if (before != null) rethrow;
      final cached = await (await _cache).read('chat:messages:$conversationId');
      if (cached is! List) rethrow;
      return cached
          .cast<Map<String, dynamic>>()
          .map(ChatMessage.fromJson)
          .toList();
    }
  }

  @override
  Future<ChatMessage> sendText(String conversationId, String text) =>
      _send(conversationId, {'type': 'TEXT', 'text': text});
  @override
  Future<ChatMessage> sendImage(String conversationId, String mediaId) =>
      _send(conversationId, {'type': 'IMAGE', 'mediaId': mediaId});
  @override
  Future<ChatMessage> sendGlyphs(
    String conversationId,
    List<String> glyphIds, {
    required String clientId,
  }) => _send(conversationId, {
    'type': 'GLYPH',
    'glyphIds': glyphIds,
    'clientId': clientId,
  });
  Future<ChatMessage> _send(String id, Map<String, dynamic> data) async =>
      ChatMessage.fromJson(
        (await _api.post<Map<String, dynamic>>(
          '/chat/conversations/$id/messages',
          data: data,
        )).data!,
      );
  @override
  Future<void> markRead(String conversationId) async =>
      _api.post<void>('/chat/conversations/$conversationId/read');
}

class DemoChatRepository implements ChatRepository {
  final conversationsData = <Conversation>[
    Conversation(
      id: 'conversation-0',
      withPublicId: 'demo-panda',
      status: 'ACTIVE',
      unread: 2,
      lastMessageAt: DateTime.now().toUtc(),
    ),
    Conversation(
      id: 'conversation-1',
      withPublicId: 'demo-fox',
      status: 'ACTIVE',
      unread: 0,
      lastMessageAt: DateTime.now().toUtc().subtract(const Duration(hours: 2)),
    ),
  ];
  final messagesData = <String, List<ChatMessage>>{};
  @override
  Future<Conversation> createConversation(String withPublicId) async =>
      Conversation(
        id: 'demo-$withPublicId',
        withPublicId: withPublicId,
        status: 'ACTIVE',
        unread: 0,
      );
  @override
  Future<List<Conversation>> conversations() async => conversationsData;
  @override
  Future<List<ChatMessage>> messages(
    String conversationId, {
    String? before,
    int limit = 30,
  }) async => messagesData.putIfAbsent(
    conversationId,
    () => [
      ChatMessage(
        id: '1',
        conversationId: conversationId,
        senderId: 'demo-panda',
        type: ChatMessageType.text,
        text: 'Hôm nay đi dạo không?',
        createdAt: DateTime.now().toUtc().subtract(const Duration(minutes: 5)),
      ),
      ChatMessage(
        id: '2',
        conversationId: conversationId,
        senderId: 'demodemodemodemodemo',
        type: ChatMessageType.text,
        text: 'Có chứ! 4 giờ nhé 🌿',
        createdAt: DateTime.now().toUtc(),
      ),
    ],
  );
  @override
  Future<ChatMessage> sendText(String conversationId, String text) async {
    final message = ChatMessage(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      conversationId: conversationId,
      senderId: 'demodemodemodemodemo',
      type: ChatMessageType.text,
      text: text,
      createdAt: DateTime.now().toUtc(),
    );
    messagesData.putIfAbsent(conversationId, () => []).add(message);
    return message;
  }

  @override
  Future<ChatMessage> sendImage(String conversationId, String mediaId) async =>
      ChatMessage(
        id: 'image-demo',
        conversationId: conversationId,
        senderId: 'demodemodemodemodemo',
        type: ChatMessageType.image,
        mediaId: mediaId,
        createdAt: DateTime.now().toUtc(),
      );
  @override
  Future<ChatMessage> sendGlyphs(
    String conversationId,
    List<String> glyphIds, {
    required String clientId,
  }) async {
    final message = ChatMessage(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      conversationId: conversationId,
      senderId: 'demodemodemodemodemo',
      type: ChatMessageType.glyph,
      text: 'Tin nhắn ký hiệu',
      glyphIds: List.unmodifiable(glyphIds),
      createdAt: DateTime.now().toUtc(),
    );
    messagesData.putIfAbsent(conversationId, () => []).add(message);
    return message;
  }

  @override
  Future<void> markRead(String conversationId) async {}
}

final liveChatRepositoryProvider = Provider<ChatRepository>(
  (ref) => LiveChatRepository(
    ref.watch(apiClientProvider),
    ref.watch(cacheDatabaseProvider.future),
  ),
);
final demoChatRepositoryProvider = Provider<ChatRepository>(
  (ref) => DemoChatRepository(),
);
final chatRepositoryProvider = Provider<ChatRepository>(
  (ref) => ref.watch(backendModeProvider) == BackendMode.demo
      ? ref.watch(demoChatRepositoryProvider)
      : ref.watch(liveChatRepositoryProvider),
);
