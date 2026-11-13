import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/features/friends/data/friends_repository_impl.dart';
import 'package:animal_locket/features/friends/domain/friends_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class FriendsState {
  const FriendsState({
    required this.friends,
    required this.incoming,
    required this.outgoing,
    required this.online,
    this.busyId,
  });
  final List<PublicProfile> friends;
  final List<FriendRequest> incoming;
  final List<FriendRequest> outgoing;
  final Map<String, bool> online;
  final String? busyId;
  FriendsState copyWith({String? busyId}) => FriendsState(
    friends: friends,
    incoming: incoming,
    outgoing: outgoing,
    online: online,
    busyId: busyId,
  );
}

class FriendsController extends AsyncNotifier<FriendsState> {
  @override
  Future<FriendsState> build() => _load();
  Future<FriendsState> _load() async {
    final repository = ref.read(friendsRepositoryProvider);
    final friends = await repository.friends();
    final incoming = await repository.incoming();
    final outgoing = await repository.outgoing();
    return FriendsState(
      friends: friends,
      incoming: incoming,
      outgoing: outgoing,
      online: await repository.presence(friends.map((item) => item.publicId)),
    );
  }

  Future<void> refresh() async => state = await AsyncValue.guard(_load);
  Future<void> accept(String id) =>
      _action(id, () => ref.read(friendsRepositoryProvider).accept(id));
  Future<void> decline(String id) =>
      _action(id, () => ref.read(friendsRepositoryProvider).decline(id));
  Future<void> cancel(String id) =>
      _action(id, () => ref.read(friendsRepositoryProvider).cancel(id));
  Future<void> remove(String id) =>
      _action(id, () => ref.read(friendsRepositoryProvider).remove(id));
  Future<void> _action(String id, Future<void> Function() action) async {
    final current = state.value;
    if (current != null) state = AsyncData(current.copyWith(busyId: id));
    try {
      await action();
      await refresh();
    } catch (error, stackTrace) {
      state = AsyncError(error, stackTrace);
    }
  }
}

final friendsControllerProvider =
    AsyncNotifierProvider<FriendsController, FriendsState>(
      FriendsController.new,
    );
