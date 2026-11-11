import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:animal_locket/core/auth/session_manager.dart';
import 'package:animal_locket/core/config/app_config.dart';
import 'package:animal_locket/core/config/backend_mode.dart';
import 'package:animal_locket/features/chat/data/chat_socket_adapter.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

enum ChatConnectionState { disconnected, connecting, connected }

class ChatRealtimeService {
  ChatRealtimeService(this._config, this._session);
  final AppConfig _config;
  final SessionManager _session;
  final _events = StreamController<Map<String, dynamic>>.broadcast();
  WebSocketChannel? _channel;
  StreamSubscription<Object?>? _subscription;
  Timer? _ping;
  Timer? _reconnect;
  bool _stopped = true;
  int _attempt = 0;
  ChatConnectionState state = ChatConnectionState.disconnected;
  Stream<Map<String, dynamic>> get events => _events.stream;

  Future<void> connect() async {
    _stopped = false;
    if (state != ChatConnectionState.disconnected) return;
    state = ChatConnectionState.connecting;
    final token = await _session.accessToken();
    if (token == null) {
      state = ChatConnectionState.disconnected;
      return;
    }
    try {
      final channel = connectChatSocket(Uri.parse(_config.webSocketUrl), token);
      _channel = channel;
      await channel.ready;
      state = ChatConnectionState.connected;
      _attempt = 0;
      _events.add(const {
        'type': 'connection',
        'data': {'state': 'connected'},
      });
      _subscription = channel.stream.listen(
        _onFrame,
        onError: (_) => _onClosed(),
        onDone: _onClosed,
      );
      _ping?.cancel();
      _ping = Timer.periodic(
        const Duration(seconds: 45),
        (_) => send({'type': 'ping'}),
      );
    } catch (_) {
      state = ChatConnectionState.disconnected;
      _scheduleReconnect();
    }
  }

  void _onFrame(Object? frame) {
    if (frame is! String) return;
    final decoded = jsonDecode(frame);
    if (decoded is Map<String, dynamic>) _events.add(decoded);
  }

  void send(Map<String, dynamic> frame) {
    if (state == ChatConnectionState.connected) {
      _channel?.sink.add(jsonEncode(frame));
    }
  }

  void _onClosed() {
    final wasConnected = state == ChatConnectionState.connected;
    state = ChatConnectionState.disconnected;
    _ping?.cancel();
    if (wasConnected && !_events.isClosed) {
      _events.add(const {
        'type': 'connection',
        'data': {'state': 'disconnected'},
      });
    }
    _scheduleReconnect();
  }

  /// App quay lại màn hình chính: bỏ thời gian chờ, nối lại ngay, và báo cho màn hình chat
  /// tải bù tin (tin đến lúc mất kết nối không được server đẩy lại).
  void reconnectNow() {
    if (_stopped) return;
    _reconnect?.cancel();
    _attempt = 0;
    if (state == ChatConnectionState.disconnected) {
      unawaited(connect());
    } else if (state == ChatConnectionState.connected) {
      // Kết nối có thể đã chết khi app ở nền; ping để phát hiện sớm.
      send({'type': 'ping'});
    }
    if (!_events.isClosed) _events.add(const {'type': 'resumed'});
  }

  void _scheduleReconnect() {
    if (_stopped || _reconnect?.isActive == true) return;
    final seconds = min(30, 1 << min(_attempt++, 5));
    final jitter = Random().nextInt(500);
    _reconnect = Timer(
      Duration(seconds: seconds, milliseconds: jitter),
      connect,
    );
  }

  Future<void> close() async {
    _stopped = true;
    _reconnect?.cancel();
    _ping?.cancel();
    await _subscription?.cancel();
    await _channel?.sink.close();
    state = ChatConnectionState.disconnected;
  }

  void dispose() {
    close();
    _events.close();
  }
}

final chatRealtimeProvider = Provider<ChatRealtimeService>((ref) {
  final service = ChatRealtimeService(
    ref.watch(appConfigProvider),
    ref.watch(sessionManagerProvider),
  );
  ref.onDispose(service.dispose);
  if (ref.watch(backendModeProvider) == BackendMode.live) {
    unawaited(service.connect());
    final lifecycle = AppLifecycleListener(onResume: service.reconnectNow);
    ref.onDispose(lifecycle.dispose);
  }
  return service;
});
