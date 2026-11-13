import 'package:animal_locket/core/network/api_client.dart';
import 'package:animal_locket/features/devices/domain/devices_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class LiveDevicesRepository implements DevicesRepository {
  const LiveDevicesRepository(this._api);
  final ApiClient _api;
  @override
  Future<void> register({
    required String token,
    required String platform,
  }) async =>
      _api.put<void>('/devices', data: {'token': token, 'platform': platform});
  @override
  Future<void> unregister(String token) async =>
      _api.delete<void>('/devices/$token');
}

final devicesRepositoryProvider = Provider<DevicesRepository>(
  (ref) => LiveDevicesRepository(ref.watch(apiClientProvider)),
);
