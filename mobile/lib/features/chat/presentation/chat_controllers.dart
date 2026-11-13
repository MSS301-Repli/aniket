import 'dart:async';

import 'package:animal_locket/core/auth/auth_controller.dart';
import 'package:animal_locket/core/config/backend_mode.dart';
import 'package:animal_locket/core/config/glyph_feature_flags.dart';
import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/features/chat/data/chat_realtime_service.dart';
import 'package:animal_locket/features/chat/data/chat_repository_impl.dart';
import 'package:animal_locket/features/chat/data/glyph_local_data_source.dart';
import 'package:animal_locket/features/chat/data/glyph_repository_impl.dart';
import 'package:animal_locket/features/chat/domain/chat_repository.dart';
import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:animal_locket/features/chat/presentation/glyph_controllers.dart';
import 'package:animal_locket/features/profile/data/user_repository_impl.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

final conversationsProvider = FutureProvider<List<Conversation>>(
  (ref) => ref.watch(chatRepositoryProvider).conversations(),
);
final chatProfileProvider = FutureProvider.family<PublicProfile, String>(
  (ref, publicId) => ref.watch(userRepositoryProvider).publicProfile(publicId),
);

class ConversationState {
  const ConversationState({
    required this.messages,
    this.closedReason,
    this.actionError,
  });
  final List<ChatMessage> messages;
  final String? closedReason;
  final Object? actionError;
  ConversationState copyWith({
    List<ChatMessage>? messages,
    String? closedReason,
    Object? actionError,
  }) => ConversationState(
    messages: messages ?? this.messages,
    closedReason: closedReason ?? this.closedReason,
    actionError: actionError,
  );
}

class _PendingMessage {
  _PendingMessage({
    required this.tempId,
    required this.clientId,
    required this.type,
    required this.retries,
    this.text,
    this.glyphIds = const [],
    this.glyphs = const [],
  });
  final String tempId;
  final String clientId;
  final ChatMessageType type;
  final String? text;
  final List<String> glyphIds;
  final List<GlyphDefinition> glyphs;
  final int retries;
}

class ConversationController extends AsyncNotifier<ConversationState> {
  ConversationController(this.conversationId);
  final String conversationId;
  final _uuid = const Uuid();
  final Map<String, _PendingMessage> _pending = {};
  final Map<String, Timer> _timers = {};
  StreamSubscription<Map<String, dynamic>>? _events;
  static const _pollEvery = Duration(seconds: 5);
  Timer? _poll;
  bool _catchingUp = false;

  @override
  Future<ConversationState> build() async {
    final realtime = ref.watch(chatRealtimeProvider);
    _events = realtime.events.listen(_onEvent);
    ref.onDispose(() {
      _stopPolling();
      _events?.cancel();
      for (final timer in _timers.values) {
        timer.cancel();
      }
    });
    final messages = await ref
        .read(chatRepositoryProvider)
        .messages(conversationId);
    final me =
        ref.read(authControllerProvider).profile?.publicId ?? 'demo-account';
    final local = await ref.read(glyphLocalDataSourceProvider.future);
    final queued = await local.pendingChat(me, conversationId);
    for (final item in queued) {
      final type = item.messageType == 'GLYPH'
          ? ChatMessageType.glyph
          : item.messageType == 'IMAGE'
          ? ChatMessageType.image
          : ChatMessageType.text;
      messages.add(
        ChatMessage(
          id: 'local-${item.clientId}',
          conversationId: conversationId,
          senderId: me,
          type: type,
          text: item.payload['text'] as String?,
          mediaId: item.payload['mediaId'] as String?,
          glyphIds: (item.payload['glyphIds'] as List? ?? const [])
              .map((value) => value.toString())
              .toList(growable: false),
          createdAt: item.createdAt,
          clientId: item.clientId,
          pending: true,
        ),
      );
    }
    unawaited(ref.read(chatRepositoryProvider).markRead(conversationId));
    realtime.send({'type': 'read', 'conversationId': conversationId});
    messages.sort((a, b) => a.createdAt.compareTo(b.createdAt));
    if (queued.isNotEmpty) {
      scheduleMicrotask(() => _resumeQueued(queued));
    }
    if (realtime.state != ChatConnectionState.connected &&
        ref.read(backendModeProvider) == BackendMode.live) {
      _startPolling();
    }
    return ConversationState(messages: messages);
  }

  Future<void> _resumeQueued(List<ChatOutboxRecord> queued) async {
    for (final item in queued) {
      if (item.messageType == 'IMAGE') continue;
      final pending = _PendingMessage(
        tempId: 'local-${item.clientId}',
        clientId: item.clientId,
        type: item.messageType == 'GLYPH'
            ? ChatMessageType.glyph
            : ChatMessageType.text,
        text: item.payload['text'] as String?,
        glyphIds: (item.payload['glyphIds'] as List? ?? const [])
            .map((value) => value.toString())
            .toList(growable: false),
        retries: item.attemptCount,
      );
      if (ref.read(chatRealtimeProvider).state ==
          ChatConnectionState.connected) {
        _sendSocket(pending);
      } else {
        await _sendRest(pending);
      }
    }
  }

  Future<void> sendText(String text) async {
    final normalized = text.trim();
    final current = state.value;
    if (current == null ||
        normalized.isEmpty ||
        normalized.length > 2000 ||
        current.closedReason != null) {
      return;
    }
    final me = ref.read(authControllerProvider).profile?.publicId ?? '';
    final tempId = 'local-${_uuid.v4()}';
    final clientId = _uuid.v4();
    final optimistic = ChatMessage(
      id: tempId,
      conversationId: conversationId,
      senderId: me,
      type: ChatMessageType.text,
      text: normalized,
      createdAt: DateTime.now().toUtc(),
      clientId: clientId,
      pending: true,
    );
    state = AsyncData(
      current.copyWith(messages: [...current.messages, optimistic]),
    );
    await _enqueueOutbox(
      clientId: clientId,
      messageType: 'TEXT',
      payload: {'text': normalized},
    );
    final realtime = ref.read(chatRealtimeProvider);
    if (realtime.state != ChatConnectionState.connected) {
      await _sendRest(
        _PendingMessage(
          tempId: tempId,
          clientId: clientId,
          type: ChatMessageType.text,
          text: normalized,
          retries: 0,
        ),
      );
      return;
    }
    _sendSocket(
      _PendingMessage(
        tempId: tempId,
        clientId: clientId,
        type: ChatMessageType.text,
        text: normalized,
        retries: 0,
      ),
    );
  }

  Future<bool> sendGlyphs(List<GlyphDefinition> glyphs) async {
    final current = state.value;
    if (current == null ||
        glyphs.isEmpty ||
        glyphs.length > 12 ||
        current.closedReason != null) {
      return false;
    }
    final mode = ref.read(backendModeProvider);
    final flags = ref.read(glyphFeatureFlagsProvider);
    if (mode == BackendMode.live &&
        (!flags.publishEnabled || !flags.sendEnabled)) {
      state = AsyncData(
        current.copyWith(actionError: const GlyphFeatureUnavailable()),
      );
      return false;
    }
    final me =
        ref.read(authControllerProvider).profile?.publicId ?? 'demo-account';
    final repository = ref.read(glyphRepositoryProvider);
    final ids = <String>[];
    try {
      for (final glyph in glyphs) {
        if (glyph.kind == GlyphKind.conversation) {
          ids.add(glyph.id);
        } else {
          final published = await repository.publishGlyph(
            accountPublicId: me,
            conversationId: conversationId,
            personalGlyph: glyph,
          );
          ids.add(published.id);
        }
      }
      ref.invalidate(conversationGlyphsProvider(conversationId));
    } catch (error) {
      state = AsyncData(current.copyWith(actionError: error));
      return false;
    }
    final tempId = 'local-${_uuid.v4()}';
    final clientId = _uuid.v4();
    final optimistic = ChatMessage(
      id: tempId,
      conversationId: conversationId,
      senderId: me,
      type: ChatMessageType.glyph,
      text: glyphs.map((item) => item.meaning).join(' · '),
      glyphIds: List.unmodifiable(ids),
      createdAt: DateTime.now().toUtc(),
      clientId: clientId,
      pending: true,
    );
    state = AsyncData(
      current.copyWith(messages: [...current.messages, optimistic]),
    );
    await _enqueueOutbox(
      clientId: clientId,
      messageType: 'GLYPH',
      payload: {'glyphIds': ids},
    );
    final pending = _PendingMessage(
      tempId: tempId,
      clientId: clientId,
      type: ChatMessageType.glyph,
      glyphIds: ids,
      glyphs: List.unmodifiable(glyphs),
      retries: 0,
    );
    if (ref.read(chatRealtimeProvider).state == ChatConnectionState.connected) {
      _sendSocket(pending);
      return true;
    } else {
      return _sendRest(pending);
    }
  }

  void _sendSocket(_PendingMessage pending) {
    _pending[pending.clientId] = pending;
    final frame = <String, dynamic>{
      'type': 'send',
      'conversationId': conversationId,
      'clientId': pending.clientId,
    };
    if (pending.type == ChatMessageType.glyph) {
      frame['messageType'] = 'GLYPH';
      frame['glyphIds'] = pending.glyphIds;
    } else {
      frame['text'] = pending.text;
    }
    ref.read(chatRealtimeProvider).send(frame);
    _timers[pending.clientId] = Timer(const Duration(seconds: 10), () async {
      final expired = _pending.remove(pending.clientId);
      _timers.remove(pending.clientId);
      if (expired == null) return;
      if (expired.retries == 0 &&
          ref.read(chatRealtimeProvider).state ==
              ChatConnectionState.connected) {
        _sendSocket(
          _PendingMessage(
            tempId: expired.tempId,
            clientId: expired.clientId,
            type: expired.type,
            text: expired.text,
            glyphIds: expired.glyphIds,
            glyphs: expired.glyphs,
            retries: 1,
          ),
        );
      } else {
        await _sendRest(expired);
      }
    });
  }

  Future<bool> _sendRest(_PendingMessage pending) async {
    try {
      final repository = ref.read(chatRepositoryProvider);
      final saved = pending.type == ChatMessageType.glyph
          ? await repository.sendGlyphs(
              conversationId,
              pending.glyphIds,
              clientId: pending.clientId,
            )
          : await repository.sendText(conversationId, pending.text!);
      _replace(pending.tempId, saved);
      await _completeOutbox(pending.clientId);
      return true;
    } catch (error) {
      final local = await ref.read(glyphLocalDataSourceProvider.future);
      final retryable =
          error is! ApiProblem ||
          error.status == null ||
          error.status! >= 500 ||
          error.status == 429;
      await local.failChat(
        pending.clientId,
        attemptCount: pending.retries,
        retryable: retryable,
      );
      _replace(
        pending.tempId,
        _find(pending.tempId)?.copyWith(pending: false, failed: true),
      );
      final current = state.value;
      if (current != null) {
        final code = error is ApiProblem ? error.code : null;
        state = AsyncData(
          current.copyWith(
            actionError: error,
            closedReason: code == 'FORBIDDEN' || code == 'CONVERSATION_CLOSED'
                ? code
                : current.closedReason,
          ),
        );
        if (code == 'GLYPH_NOT_ACTIVE') {
          await ref
              .read(conversationGlyphsProvider(conversationId).notifier)
              .refresh();
        }
      }
      _restoreGlyphComposer(pending);
      return false;
    }
  }

  Future<void> _enqueueOutbox({
    required String clientId,
    required String messageType,
    required Map<String, dynamic> payload,
  }) async {
    final me =
        ref.read(authControllerProvider).profile?.publicId ?? 'demo-account';
    final local = await ref.read(glyphLocalDataSourceProvider.future);
    await local.enqueueChat(
      ChatOutboxRecord(
        clientId: clientId,
        accountPublicId: me,
        conversationId: conversationId,
        messageType: messageType,
        payload: payload,
        attemptCount: 0,
        createdAt: DateTime.now().toUtc(),
      ),
    );
  }

  Future<void> _completeOutbox(String clientId) async {
    final local = await ref.read(glyphLocalDataSourceProvider.future);
    await local.completeChat(clientId);
  }

  ChatMessage? _find(String id) =>
      state.value?.messages.where((item) => item.id == id).firstOrNull;
  void _replace(String id, ChatMessage? replacement) {
    if (replacement == null) return;
    final current = state.value;
    if (current == null) return;
    state = AsyncData(
      current.copyWith(
        messages: current.messages
            .map((item) => item.id == id ? replacement : item)
            .toList(),
      ),
    );
  }

  void _onEvent(Map<String, dynamic> frame) {
    final type = frame['type'];
    final data = (frame['data'] as Map?)?.cast<String, dynamic>() ?? const {};
    if (type == 'connection') {
      if (data['state'] == 'connected') {
        _stopPolling();
        unawaited(_catchUp());
      } else {
        _startPolling();
      }
      return;
    }
    if (type == 'resumed') {
      unawaited(_catchUp());
      if (ref.read(chatRealtimeProvider).state !=
          ChatConnectionState.connected) {
        _startPolling();
      }
      return;
    }
    if (type == 'message' &&
        data['conversationId'].toString() == conversationId) {
      final message = ChatMessage.fromJson(data);
      final current = state.value;
      if (current != null) {
        // Server phát tin cho cả chính người gửi TRƯỚC khi trả 'ack': khớp cả theo clientId
        // để thay bong bóng tạm (id 'local-...') thay vì thêm bong bóng thứ hai.
        final index = current.messages.indexWhere(
          (item) =>
              item.id == message.id ||
              (message.clientId != null && item.clientId == message.clientId),
        );
        if (index < 0) {
          state = AsyncData(
            current.copyWith(
              messages: [...current.messages, message]
                ..sort((a, b) => a.createdAt.compareTo(b.createdAt)),
            ),
          );
        } else if (current.messages[index].pending ||
            current.messages[index].id != message.id) {
          final messages = [...current.messages];
          messages[index] = message;
          state = AsyncData(current.copyWith(messages: messages));
        }
      }
      // Tin của mình đã về: coi như ack (không gửi lại khi hết 10 giây).
      final echoed = message.clientId;
      if (echoed != null && _pending.remove(echoed) != null) {
        _timers.remove(echoed)?.cancel();
        unawaited(_completeOutbox(echoed));
      }
      if (message.type == ChatMessageType.glyph) {
        // Làm mới từ điển chạy nền; lỗi mạng thì giữ bản cache, không ném lỗi ra ngoài.
        unawaited(
          ref
              .read(conversationGlyphsProvider(conversationId).notifier)
              .refresh()
              .catchError((Object _) {}),
        );
      }
    } else if (type == 'ack') {
      final clientId = data['clientId'] as String?;
      if (clientId == null) return;
      final pending = _pending.remove(clientId);
      _timers.remove(clientId)?.cancel();
      if (pending != null) {
        unawaited(_completeOutbox(clientId));
        final optimistic = _find(pending.tempId);
        final messageId = data['messageId'].toString();
        final current = state.value;
        if (optimistic != null &&
            current != null &&
            current.messages.any((item) => item.id == messageId)) {
          // Bản thật đã có (về qua khung 'message'): bỏ bản tạm.
          state = AsyncData(
            current.copyWith(
              messages: current.messages
                  .where((item) => item.id != pending.tempId)
                  .toList(),
            ),
          );
        } else if (optimistic != null) {
          _replace(
            pending.tempId,
            optimistic.copyWith(
              id: data['messageId'].toString(),
              pending: false,
            ),
          );
        }
      }
    } else if (type == 'closed' &&
        data['conversationId'].toString() == conversationId) {
      final current = state.value;
      if (current != null) {
        state = AsyncData(
          current.copyWith(closedReason: data['reason'] as String? ?? 'CLOSED'),
        );
      }
    } else if (type == 'error') {
      final clientId = data['clientId'] as String?;
      final pending = clientId == null ? null : _pending.remove(clientId);
      if (clientId != null) _timers.remove(clientId)?.cancel();
      if (pending != null) {
        final item = _find(pending.tempId);
        if (item != null) {
          _replace(item.id, item.copyWith(pending: false, failed: true));
        }
        unawaited(_handleSocketError(pending, data));
      }
    } else if (type == 'dictionary_changed' &&
        data['conversationId'].toString() == conversationId) {
      ref.invalidate(conversationGlyphsProvider(conversationId));
    }
  }

  /// Mất WebSocket: hỏi server mỗi 5 giây để vẫn thấy tin mới cho tới khi nối lại được.
  void _startPolling() {
    if (_poll?.isActive == true) return;
    _poll = Timer.periodic(_pollEvery, (_) => unawaited(_catchUp()));
  }

  void _stopPolling() {
    _poll?.cancel();
    _poll = null;
  }

  /// Tải trang tin mới nhất và trộn vào danh sách đang có: thêm tin chưa có, thay bong bóng tạm
  /// bằng bản thật (khớp clientId). Không xoá gì, nên tin đang chờ gửi vẫn giữ nguyên.
  Future<void> _catchUp() async {
    if (_catchingUp) return;
    _catchingUp = true;
    try {
      final fresh = await ref
          .read(chatRepositoryProvider)
          .messages(conversationId);
      final current = state.value;
      if (current == null || fresh.isEmpty) return;
      final messages = [...current.messages];
      var changed = false;
      for (final message in fresh) {
        final index = messages.indexWhere(
          (item) =>
              item.id == message.id ||
              (message.clientId != null && item.clientId == message.clientId),
        );
        if (index < 0) {
          messages.add(message);
          changed = true;
        } else if (messages[index].pending ||
            messages[index].id != message.id) {
          messages[index] = message;
          changed = true;
        }
        final echoed = message.clientId;
        if (echoed != null && _pending.remove(echoed) != null) {
          _timers.remove(echoed)?.cancel();
          unawaited(_completeOutbox(echoed));
        }
      }
      if (!changed) return;
      messages.sort((a, b) => a.createdAt.compareTo(b.createdAt));
      state = AsyncData(current.copyWith(messages: messages));
      unawaited(ref.read(chatRepositoryProvider).markRead(conversationId));
    } catch (_) {
      // Mạng lỗi: lần hỏi sau thử lại.
    } finally {
      _catchingUp = false;
    }
  }

  Future<void> _handleSocketError(
    _PendingMessage pending,
    Map<String, dynamic> data,
  ) async {
    final code = data['code']?.toString();
    final retryable =
        code == 'CHAT_RATE_LIMIT' ||
        code == 'TEMPORARY_UNAVAILABLE' ||
        code == null;
    final local = await ref.read(glyphLocalDataSourceProvider.future);
    await local.failChat(
      pending.clientId,
      attemptCount: pending.retries,
      retryable: retryable,
    );
    if (code == 'GLYPH_NOT_ACTIVE') {
      await ref
          .read(conversationGlyphsProvider(conversationId).notifier)
          .refresh();
    }
    _restoreGlyphComposer(pending);
    final current = state.value;
    if (current != null &&
        (code == 'FORBIDDEN' || code == 'CONVERSATION_CLOSED')) {
      state = AsyncData(current.copyWith(closedReason: code));
    }
  }

  void _restoreGlyphComposer(_PendingMessage pending) {
    if (pending.type != ChatMessageType.glyph || pending.glyphs.isEmpty) return;
    final composer = ref.read(glyphComposerProvider(conversationId));
    if (composer.selected.isNotEmpty) return;
    final notifier = ref.read(glyphComposerProvider(conversationId).notifier);
    for (final glyph in pending.glyphs) {
      notifier.add(glyph);
    }
  }

  Future<void> retryMessage(ChatMessage message) async {
    final current = state.value;
    if (current == null || !message.failed) return;
    if (message.clientId != null) {
      await _completeOutbox(message.clientId!);
    }
    state = AsyncData(
      current.copyWith(
        messages: current.messages
            .where((item) => item.id != message.id)
            .toList(),
      ),
    );
    if (message.type == ChatMessageType.glyph) {
      final dictionary = await ref.read(
        conversationMessageGlyphsProvider(conversationId).future,
      );
      final byId = {for (final glyph in dictionary) glyph.id: glyph};
      await sendGlyphs(
        message.glyphIds
            .map((id) => byId[id])
            .whereType<GlyphDefinition>()
            .toList(),
      );
    } else if (message.type == ChatMessageType.text && message.text != null) {
      await sendText(message.text!);
    }
  }
}

final conversationControllerProvider =
    AsyncNotifierProvider.family<
      ConversationController,
      ConversationState,
      String
    >(ConversationController.new);
