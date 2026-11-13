import 'dart:async';

import 'package:animal_locket/features/discovery/data/discovery_repository_impl.dart';
import 'package:animal_locket/features/discovery/domain/discovery_repository.dart';
import 'package:animal_locket/features/profile/data/user_repository_impl.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';

class DiscoveryState {
  const DiscoveryState({
    required this.enabled,
    this.cards = const [],
    this.swipesLeftToday = 0,
    this.degraded = false,
    this.busy = false,
    this.lastResult,
    this.locationOff = false,
  });
  final bool enabled;
  final List<DiscoveryCard> cards;
  final int swipesLeftToday;
  final bool degraded;
  final bool busy;
  final String? lastResult;

  /// Chưa gửi được vị trí (chưa cấp quyền / tắt GPS): chỉ thấy người đang online.
  final bool locationOff;
  DiscoveryState copyWith({
    bool? enabled,
    List<DiscoveryCard>? cards,
    int? swipesLeftToday,
    bool? degraded,
    bool? busy,
    String? lastResult,
    bool? locationOff,
  }) => DiscoveryState(
    enabled: enabled ?? this.enabled,
    cards: cards ?? this.cards,
    swipesLeftToday: swipesLeftToday ?? this.swipesLeftToday,
    degraded: degraded ?? this.degraded,
    busy: busy ?? this.busy,
    lastResult: lastResult,
    locationOff: locationOff ?? this.locationOff,
  );
}

class DiscoveryController extends AsyncNotifier<DiscoveryState> {
  /// Server giữ vị trí 30 phút; gửi lại 5 phút một lần khi đang ở tab Khám phá.
  static const _locationEvery = Duration(minutes: 5);

  Timer? _heartbeatTimer;
  Timer? _locationTimer;
  bool _active = false;
  @override
  Future<DiscoveryState> build() async {
    ref.onDispose(() {
      _heartbeatTimer?.cancel();
      _locationTimer?.cancel();
    });
    final profile = await ref.read(userRepositoryProvider).me();
    if (!profile.discoverable) return const DiscoveryState(enabled: false);
    // Gửi vị trí trước khi lấy thẻ để người ở gần có mặt ngay trong bộ thẻ đầu.
    final located = await _sendLocation(prompt: true);
    return _loadDeck(enabled: true, locationOff: !located);
  }

  Future<DiscoveryState> _loadDeck({
    required bool enabled,
    bool locationOff = false,
  }) async {
    final deck = await ref.read(discoveryRepositoryProvider).cards();
    return DiscoveryState(
      enabled: enabled,
      cards: deck.cards,
      swipesLeftToday: deck.swipesLeftToday,
      degraded: deck.degraded,
      locationOff: locationOff,
    );
  }

  /// Gửi vị trí hiện tại nếu được phép. [prompt]: hỏi quyền khi chưa từng hỏi.
  /// Trả về false khi tắt GPS, chưa có quyền hoặc lấy vị trí lỗi.
  Future<bool> _sendLocation({bool prompt = false}) async {
    try {
      if (!await Geolocator.isLocationServiceEnabled()) return false;
      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied && prompt) {
        permission = await Geolocator.requestPermission();
      }
      if (permission != LocationPermission.always &&
          permission != LocationPermission.whileInUse) {
        return false;
      }
      final position = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.low,
          timeLimit: Duration(seconds: 10),
        ),
      );
      await ref
          .read(discoveryRepositoryProvider)
          .updateLocation(lat: position.latitude, lon: position.longitude);
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<void> refresh() async {
    final enabled = state.value?.enabled ?? true;
    final located = enabled && await _sendLocation();
    state = await AsyncValue.guard(
      () => _loadDeck(enabled: enabled, locationOff: enabled && !located),
    );
  }

  /// Nút "Bật vị trí": hỏi quyền lại, hoặc mở cài đặt nếu người dùng đã chặn hẳn.
  Future<void> requestLocation() async {
    if (!await Geolocator.isLocationServiceEnabled()) {
      await Geolocator.openLocationSettings();
      return;
    }
    if (await Geolocator.checkPermission() ==
        LocationPermission.deniedForever) {
      await Geolocator.openAppSettings();
      return;
    }
    final located = await _sendLocation(prompt: true);
    if (located) await refresh();
  }

  Future<void> enable() async {
    state = const AsyncLoading();
    try {
      await ref.read(userRepositoryProvider).update(discoverable: true);
      final located = await _sendLocation(prompt: true);
      state = AsyncData(await _loadDeck(enabled: true, locationOff: !located));
      setActive(_active);
    } catch (error, stackTrace) {
      state = AsyncError(error, stackTrace);
    }
  }

  Future<String?> swipe(bool right) async {
    final current = state.value;
    if (current == null || current.cards.isEmpty || current.busy) return null;
    final card = current.cards.first;
    state = AsyncData(current.copyWith(busy: true));
    try {
      final result = await ref
          .read(discoveryRepositoryProvider)
          .swipe(card.publicId, right: right);
      final remaining = current.cards.skip(1).toList();
      state = AsyncData(
        current.copyWith(
          cards: remaining,
          swipesLeftToday: result.swipesLeftToday,
          busy: false,
          lastResult: result.result,
        ),
      );
      if (remaining.length <= 3) unawaited(_prefetch());
      return result.result;
    } catch (error, stackTrace) {
      state = AsyncError(error, stackTrace);
      rethrow;
    }
  }

  Future<void> _prefetch() async {
    final current = state.value;
    if (current == null) return;
    final deck = await ref.read(discoveryRepositoryProvider).cards();
    final seen = current.cards.map((item) => item.publicId).toSet();
    state = AsyncData(
      current.copyWith(
        cards: [
          ...current.cards,
          ...deck.cards.where((item) => seen.add(item.publicId)),
        ],
        swipesLeftToday: deck.swipesLeftToday,
        degraded: current.degraded || deck.degraded,
      ),
    );
  }

  Future<void> boost() => ref.read(discoveryRepositoryProvider).boost();
  void setActive(bool active) {
    _active = active;
    _heartbeatTimer?.cancel();
    _locationTimer?.cancel();
    if (!active || state.value?.enabled != true) return;
    unawaited(ref.read(discoveryRepositoryProvider).heartbeat());
    _heartbeatTimer = Timer.periodic(
      const Duration(seconds: 30),
      (_) => unawaited(ref.read(discoveryRepositoryProvider).heartbeat()),
    );
    _locationTimer = Timer.periodic(
      _locationEvery,
      (_) => unawaited(_sendLocation()),
    );
  }
}

final discoveryControllerProvider =
    AsyncNotifierProvider<DiscoveryController, DiscoveryState>(
      DiscoveryController.new,
    );
