import 'package:animal_locket/core/config/backend_mode.dart';
import 'package:animal_locket/core/network/api_client.dart';
import 'package:animal_locket/features/discovery/domain/discovery_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class LiveDiscoveryRepository implements DiscoveryRepository {
  const LiveDiscoveryRepository(this._api);
  final ApiClient _api;
  @override
  Future<void> updateLocation({
    required double lat,
    required double lon,
  }) async =>
      _api.put<void>('/discovery/location', data: {'lat': lat, 'lon': lon});
  @override
  Future<void> deleteLocation() async =>
      _api.delete<void>('/discovery/location');
  @override
  Future<void> heartbeat() async => _api.post<void>('/discovery/heartbeat');
  @override
  Future<DiscoveryDeck> cards({int limit = 20}) async {
    final data = (await _api.get<Map<String, dynamic>>(
      '/discovery/cards',
      queryParameters: {'limit': limit},
    )).data!;
    return DiscoveryDeck(
      cards: ((data['cards'] as List?) ?? const [])
          .cast<Map<String, dynamic>>()
          .map(DiscoveryCard.fromJson)
          .toList(),
      degraded: data['degraded'] as bool? ?? false,
      swipesLeftToday: (data['swipesLeftToday'] as num?)?.toInt() ?? 0,
    );
  }

  @override
  Future<SwipeResult> swipe(String publicId, {required bool right}) async {
    final data = (await _api.post<Map<String, dynamic>>(
      '/discovery/swipes',
      data: {'targetPublicId': publicId, 'direction': right ? 'RIGHT' : 'LEFT'},
    )).data!;
    return SwipeResult(
      result: data['result'] as String? ?? 'SKIPPED',
      swipesLeftToday: (data['swipesLeftToday'] as num?)?.toInt() ?? 0,
    );
  }

  @override
  Future<void> boost() async => _api.post<void>('/discovery/boost');
}

class DemoDiscoveryRepository implements DiscoveryRepository {
  int left = 8;
  @override
  Future<void> updateLocation({
    required double lat,
    required double lon,
  }) async {}
  @override
  Future<void> deleteLocation() async {}
  @override
  Future<void> heartbeat() async {}
  @override
  Future<DiscoveryDeck> cards({int limit = 20}) async => DiscoveryDeck(
    cards: const [
      DiscoveryCard(
        publicId: 'demo-panda',
        alias: 'Mây Bồng Bềnh',
        leadAnimal: 'panda',
        distanceBand: 'UNDER_1KM',
        online: true,
        boosted: false,
      ),
      DiscoveryCard(
        publicId: 'demo-fox',
        alias: 'Nắng Nhẹ',
        leadAnimal: 'fox',
        distanceBand: '1_TO_3KM',
        online: false,
        boosted: true,
      ),
      DiscoveryCard(
        publicId: 'demo-tiger',
        alias: 'Hổ Vẽ Mây',
        leadAnimal: 'tiger',
        online: true,
        boosted: false,
      ),
    ],
    degraded: false,
    swipesLeftToday: left,
  );
  @override
  Future<SwipeResult> swipe(String publicId, {required bool right}) async {
    if (right) left--;
    return SwipeResult(
      result: right && publicId == 'demo-tiger'
          ? 'MATCHED'
          : right
          ? 'REQUESTED'
          : 'SKIPPED',
      swipesLeftToday: left,
    );
  }

  @override
  Future<void> boost() async {}
}

final liveDiscoveryRepositoryProvider = Provider<DiscoveryRepository>(
  (ref) => LiveDiscoveryRepository(ref.watch(apiClientProvider)),
);
final demoDiscoveryRepositoryProvider = Provider<DiscoveryRepository>(
  (ref) => DemoDiscoveryRepository(),
);
final discoveryRepositoryProvider = Provider<DiscoveryRepository>(
  (ref) => ref.watch(backendModeProvider) == BackendMode.demo
      ? ref.watch(demoDiscoveryRepositoryProvider)
      : ref.watch(liveDiscoveryRepositoryProvider),
);
