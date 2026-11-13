import 'package:web_socket_channel/web_socket_channel.dart';

WebSocketChannel connectChatSocket(Uri uri, String token) =>
    throw UnsupportedError(
      'Live chat WebSocket is supported on Android and iOS only.',
    );
