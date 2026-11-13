import 'dart:async';

import 'package:animal_locket/core/config/app_config.dart';
import 'package:animal_locket/features/devices/data/devices_repository_impl.dart';
import 'package:animal_locket/features/devices/domain/devices_repository.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class PushService {
  PushService(this._messaging, this._devices);
  final FirebaseMessaging _messaging;
  final DevicesRepository _devices;
  String? currentToken;
  StreamSubscription<String>? _tokenRefresh;
  Future<void> initialize() async {
    await _messaging.requestPermission();
    _tokenRefresh = _messaging.onRegistered.listen(_register);
    await _messaging.register();
  }

  Future<void> _register(String token) async {
    currentToken = token;
    await _devices.register(
      token: token,
      platform: defaultTargetPlatform == TargetPlatform.iOS ? 'IOS' : 'ANDROID',
    );
  }

  Future<void> unregister() async {
    final token = currentToken;
    if (token != null) await _devices.unregister(token);
    currentToken = null;
    await _messaging.unregister();
  }

  void dispose() => _tokenRefresh?.cancel();
}

final pushServiceProvider = Provider<PushService?>((ref) {
  if (!ref.watch(appConfigProvider).enablePush) return null;
  final service = PushService(
    FirebaseMessaging.instance,
    ref.watch(devicesRepositoryProvider),
  );
  ref.onDispose(service.dispose);
  return service;
});

final pushBootstrapProvider = FutureProvider<void>((ref) async {
  if (!ref.watch(appConfigProvider).enablePush) return;
  await Firebase.initializeApp();
  await ref.watch(pushServiceProvider)?.initialize();
});
