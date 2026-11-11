import 'package:animal_locket/features/camera/data/media_repository_impl.dart';
import 'package:animal_locket/features/feed/data/post_repository_impl.dart';
import 'package:animal_locket/features/feed/domain/post_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class FeedState {
  const FeedState({
    this.items = const [],
    this.nextBefore,
    this.degraded = false,
    this.loadingMore = false,
    this.actionError,
  });
  final List<Post> items;
  final String? nextBefore;
  final bool degraded;
  final bool loadingMore;
  final Object? actionError;

  FeedState copyWith({
    List<Post>? items,
    String? nextBefore,
    bool? degraded,
    bool? loadingMore,
    Object? actionError,
  }) => FeedState(
    items: items ?? this.items,
    nextBefore: nextBefore ?? this.nextBefore,
    degraded: degraded ?? this.degraded,
    loadingMore: loadingMore ?? this.loadingMore,
    actionError: actionError,
  );
}

class FeedController extends AsyncNotifier<FeedState> {
  @override
  Future<FeedState> build() => _firstPage();

  Future<FeedState> _firstPage() async {
    final page = await ref.read(postRepositoryProvider).feed();
    return FeedState(
      items: page.items,
      nextBefore: page.nextBefore,
      degraded: page.degraded,
    );
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(_firstPage);
  }

  Future<void> loadMore() async {
    final current = state.value;
    if (current == null || current.nextBefore == null || current.loadingMore) {
      return;
    }
    state = AsyncData(current.copyWith(loadingMore: true));
    try {
      final page = await ref
          .read(postRepositoryProvider)
          .feed(before: current.nextBefore);
      state = AsyncData(
        FeedState(
          items: [...current.items, ...page.items],
          nextBefore: page.nextBefore,
          degraded: current.degraded || page.degraded,
        ),
      );
    } catch (error) {
      state = AsyncData(
        current.copyWith(loadingMore: false, actionError: error),
      );
    }
  }

  Future<void> toggleLike(String postId) async {
    final current = state.value;
    if (current == null) return;
    final index = current.items.indexWhere((post) => post.id == postId);
    if (index < 0) return;
    final original = current.items[index];
    final liked = !original.likedByMe;
    final updated = [...current.items];
    updated[index] = original.copyWith(
      likedByMe: liked,
      likeCount: original.likeCount + (liked ? 1 : -1),
    );
    state = AsyncData(current.copyWith(items: updated));
    try {
      await ref.read(postRepositoryProvider).setLiked(postId, liked);
    } catch (error) {
      final rollback = [...current.items];
      rollback[index] = original;
      state = AsyncData(current.copyWith(items: rollback, actionError: error));
    }
  }

  Future<void> deletePost(String postId) async {
    await ref.read(postRepositoryProvider).deletePost(postId);
    final current = state.value;
    if (current != null) {
      state = AsyncData(
        current.copyWith(
          items: current.items.where((post) => post.id != postId).toList(),
        ),
      );
    }
  }
}

final feedControllerProvider = AsyncNotifierProvider<FeedController, FeedState>(
  FeedController.new,
);

final mediaUrlProvider = FutureProvider.family<String?, String>((
  ref,
  mediaId,
) async {
  final lease = await ref.watch(mediaRepositoryProvider).viewUrl(mediaId);
  return lease.url.isEmpty ? null : lease.url;
});
