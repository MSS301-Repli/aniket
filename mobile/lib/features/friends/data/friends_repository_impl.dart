import 'package:animal_locket/core/config/backend_mode.dart';
import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/network/api_client.dart';
import 'package:animal_locket/features/friends/domain/friends_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class LiveFriendsRepository implements FriendsRepository {
  const LiveFriendsRepository(this._api);
  final ApiClient _api;
  Future<List<T>> _list<T>(
    String path,
    T Function(Map<String, dynamic>) decode,
  ) async => ((await _api.get<List<dynamic>>(path)).data ?? const [])
      .cast<Map<String, dynamic>>()
      .map(decode)
      .toList();
  @override
  Future<List<PublicProfile>> friends() =>
      _list('/friends', PublicProfile.fromJson);
  @override
  Future<List<FriendRequest>> incoming() =>
      _list('/friends/requests/incoming', FriendRequest.fromJson);
  @override
  Future<List<FriendRequest>> outgoing() =>
      _list('/friends/requests/outgoing', FriendRequest.fromJson);
  @override
  Future<String> request(String publicId) async =>
      ((await _api.post<Map<String, dynamic>>(
            '/friends/requests',
            data: {'toPublicId': publicId},
          )).data?['result']
          as String?) ??
      'REQUESTED';
  @override
  Future<void> accept(String requestId) async =>
      _api.post<void>('/friends/requests/$requestId/accept');
  @override
  Future<void> decline(String requestId) async =>
      _api.post<void>('/friends/requests/$requestId/decline');
  @override
  Future<void> cancel(String requestId) async =>
      _api.delete<void>('/friends/requests/$requestId');
  @override
  Future<void> remove(String publicId) async =>
      _api.delete<void>('/friends/$publicId');
  @override
  Future<Map<String, bool>> presence(Iterable<String> publicIds) async {
    final ids = publicIds.take(200).join(',');
    if (ids.isEmpty) return const {};
    final data = (await _api.get<Map<String, dynamic>>(
      '/discovery/presence',
      queryParameters: {'ids': ids},
    )).data;
    return ((data?['online'] as Map?)?.cast<String, dynamic>() ?? const {}).map(
      (key, value) => MapEntry(key, value == true),
    );
  }
}

class LiveSafetyRepository implements SafetyRepository {
  const LiveSafetyRepository(this._api);
  final ApiClient _api;
  @override
  Future<List<PublicProfile>> blocked() async =>
      ((await _api.get<List<dynamic>>('/blocks')).data ?? const [])
          .cast<Map<String, dynamic>>()
          .map(PublicProfile.fromJson)
          .toList();
  @override
  Future<void> block(String publicId) async =>
      _api.put<void>('/blocks/$publicId');
  @override
  Future<void> unblock(String publicId) async =>
      _api.delete<void>('/blocks/$publicId');
  @override
  Future<void> report({
    required ReportTarget targetType,
    required String targetPublicId,
    String? targetRef,
    required String reason,
    String? details,
  }) async => _api.post<void>(
    '/reports',
    data: {
      'targetType': targetType.name.toUpperCase(),
      'targetPublicId': targetPublicId,
      'targetRef': ?targetRef,
      'reason': reason,
      'details': ?details,
    },
  );
}

class DemoFriendsRepository implements FriendsRepository {
  final _friends = <PublicProfile>[
    const PublicProfile(
      publicId: 'demo-panda',
      alias: 'Mây Nhỏ',
      leadAnimal: 'panda',
    ),
    const PublicProfile(
      publicId: 'demo-tiger',
      alias: 'Hổ Mơ Màng',
      leadAnimal: 'tiger',
    ),
  ];
  final _incoming = <FriendRequest>[
    FriendRequest(
      id: 'request-1',
      user: const PublicProfile(
        publicId: 'demo-rabbit',
        alias: 'Thỏ Tháng Tư',
        leadAnimal: 'rabbit',
      ),
      createdAt: DateTime.now().toUtc(),
    ),
  ];
  final _outgoing = <FriendRequest>[];
  @override
  Future<List<PublicProfile>> friends() async => List.unmodifiable(_friends);
  @override
  Future<List<FriendRequest>> incoming() async => List.unmodifiable(_incoming);
  @override
  Future<List<FriendRequest>> outgoing() async => List.unmodifiable(_outgoing);
  @override
  Future<String> request(String publicId) async => 'REQUESTED';
  @override
  Future<void> accept(String requestId) async {
    final match = _incoming.where((item) => item.id == requestId);
    if (match.isNotEmpty) _friends.add(match.first.user);
    _incoming.removeWhere((item) => item.id == requestId);
  }

  @override
  Future<void> decline(String requestId) async =>
      _incoming.removeWhere((item) => item.id == requestId);
  @override
  Future<void> cancel(String requestId) async =>
      _outgoing.removeWhere((item) => item.id == requestId);
  @override
  Future<void> remove(String publicId) async =>
      _friends.removeWhere((item) => item.publicId == publicId);
  @override
  Future<Map<String, bool>> presence(Iterable<String> publicIds) async => {
    for (final id in publicIds) id: id.hashCode.isEven,
  };
}

class DemoSafetyRepository implements SafetyRepository {
  final List<PublicProfile> _blocked = [];
  @override
  Future<List<PublicProfile>> blocked() async => List.unmodifiable(_blocked);
  @override
  Future<void> block(String publicId) async =>
      _blocked.add(PublicProfile(publicId: publicId, alias: 'Đã chặn'));
  @override
  Future<void> unblock(String publicId) async =>
      _blocked.removeWhere((item) => item.publicId == publicId);
  @override
  Future<void> report({
    required ReportTarget targetType,
    required String targetPublicId,
    String? targetRef,
    required String reason,
    String? details,
  }) async {}
}

final liveFriendsRepositoryProvider = Provider<FriendsRepository>(
  (ref) => LiveFriendsRepository(ref.watch(apiClientProvider)),
);
final demoFriendsRepositoryProvider = Provider<FriendsRepository>(
  (ref) => DemoFriendsRepository(),
);
final friendsRepositoryProvider = Provider<FriendsRepository>(
  (ref) => ref.watch(backendModeProvider) == BackendMode.demo
      ? ref.watch(demoFriendsRepositoryProvider)
      : ref.watch(liveFriendsRepositoryProvider),
);
final liveSafetyRepositoryProvider = Provider<SafetyRepository>(
  (ref) => LiveSafetyRepository(ref.watch(apiClientProvider)),
);
final demoSafetyRepositoryProvider = Provider<SafetyRepository>(
  (ref) => DemoSafetyRepository(),
);
final safetyRepositoryProvider = Provider<SafetyRepository>(
  (ref) => ref.watch(backendModeProvider) == BackendMode.demo
      ? ref.watch(demoSafetyRepositoryProvider)
      : ref.watch(liveSafetyRepositoryProvider),
);
