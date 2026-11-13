import 'package:animal_locket/core/config/backend_mode.dart';
import 'package:animal_locket/core/cache/cache_database.dart';
import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/network/api_client.dart';
import 'package:animal_locket/features/feed/domain/post_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class LivePostRepository implements PostRepository {
  const LivePostRepository(this._api, this._cache);
  final ApiClient _api;
  final Future<CacheDatabase> _cache;

  @override
  Future<Post> create({required String mediaId, String? caption}) async {
    final normalizedCaption = caption?.trim();
    return Post.fromJson(
      (await _api.post<Map<String, dynamic>>(
        '/posts',
        data: {
          'mediaId': mediaId,
          'caption': ?(normalizedCaption?.isEmpty == false
              ? normalizedCaption
              : null),
        },
      )).data!,
    );
  }

  @override
  Future<List<Post>> mine({int limit = 50}) async =>
      ((await _api.get<List<dynamic>>(
                '/posts/mine',
                queryParameters: {'limit': limit},
              )).data ??
              const [])
          .cast<Map<String, dynamic>>()
          .map(Post.fromJson)
          .toList();
  @override
  Future<PagedResult<Post>> postsOf(
    String publicId, {
    String? before,
    int limit = 30,
  }) async {
    final data = (await _api.get<Map<String, dynamic>>(
      '/posts/by/$publicId',
      queryParameters: {'before': ?before, 'limit': limit},
    )).data!;
    return PagedResult(
      items: ((data['items'] as List?) ?? const [])
          .cast<Map<String, dynamic>>()
          .map(Post.fromJson)
          .toList(),
      nextBefore: data['nextBefore'] as String?,
    );
  }

  @override
  Future<PagedResult<Post>> feed({String? before, int limit = 20}) async {
    try {
      final data = (await _api.get<Map<String, dynamic>>(
        '/feed',
        queryParameters: {'before': ?before, 'limit': limit},
      )).data!;
      final result = PagedResult(
        items: ((data['items'] as List?) ?? const [])
            .cast<Map<String, dynamic>>()
            .map(Post.fromJson)
            .toList(),
        nextBefore: data['nextBefore'] as String?,
        degraded: data['degraded'] as bool? ?? false,
      );
      if (before == null) {
        await (await _cache).write('feed', {
          'items': result.items.map((item) => item.toJson()).toList(),
          'nextBefore': result.nextBefore,
        });
      }
      return result;
    } catch (_) {
      if (before != null) rethrow;
      final cached = await (await _cache).read('feed');
      if (cached is! Map) rethrow;
      final data = cached.cast<String, dynamic>();
      return PagedResult(
        items: ((data['items'] as List?) ?? const [])
            .cast<Map<String, dynamic>>()
            .map(Post.fromJson)
            .toList(),
        nextBefore: data['nextBefore'] as String?,
        degraded: true,
      );
    }
  }

  @override
  Future<void> deletePost(String postId) async =>
      _api.delete<void>('/posts/$postId');
  @override
  Future<void> setLiked(String postId, bool liked) async {
    if (liked) {
      await _api.put<void>('/posts/$postId/like');
    } else {
      await _api.delete<void>('/posts/$postId/like');
    }
  }

  @override
  Future<Comment> addComment(String postId, String text) async =>
      Comment.fromJson(
        (await _api.post<Map<String, dynamic>>(
          '/posts/$postId/comments',
          data: {'text': text},
        )).data!,
      );
  @override
  Future<PagedResult<Comment>> comments(
    String postId, {
    int? after,
    int limit = 30,
  }) async {
    final data = (await _api.get<Map<String, dynamic>>(
      '/posts/$postId/comments',
      queryParameters: {'after': ?after, 'limit': limit},
    )).data!;
    return PagedResult(
      items: ((data['items'] as List?) ?? const [])
          .cast<Map<String, dynamic>>()
          .map(Comment.fromJson)
          .toList(),
      nextAfter: (data['nextAfter'] as num?)?.toInt(),
    );
  }

  @override
  Future<void> deleteComment(String postId, int commentId) async =>
      _api.delete<void>('/posts/$postId/comments/$commentId');
}

class DemoPostRepository implements PostRepository {
  final List<Post> _posts = List.generate(
    3,
    (index) => Post(
      id: 'demo-$index',
      author: PublicProfile(
        publicId: 'demo-author-$index',
        alias: ['Mây Nhỏ', 'Nắng Lấp Lánh', 'Hổ Mơ Màng'][index],
        leadAnimal: ['panda', 'fox', 'tiger'][index],
      ),
      caption: [
        'Đi trốn một chút 🌿',
        'Nắng đẹp quá!',
        'Chiều nay vẽ gì?',
      ][index],
      mediaId: 'demo-media-$index',
      status: PostStatus.published,
      likeCount: 23 + index,
      commentCount: 4 + index,
      likedByMe: false,
      createdAt: DateTime.now().toUtc().subtract(Duration(hours: index + 1)),
    ),
  );
  @override
  Future<Post> create({required String mediaId, String? caption}) async {
    final post = Post(
      id: 'demo-created',
      author: const PublicProfile(
        publicId: 'demodemodemodemodemo',
        alias: 'Nắng Lấp Lánh',
        leadAnimal: 'fox',
      ),
      caption: caption,
      mediaId: mediaId,
      status: PostStatus.published,
      likeCount: 0,
      commentCount: 0,
      likedByMe: false,
      createdAt: DateTime.now().toUtc(),
    );
    _posts.insert(0, post);
    return post;
  }

  @override
  Future<List<Post>> mine({int limit = 50}) async =>
      _posts.take(limit).toList();
  @override
  Future<PagedResult<Post>> postsOf(
    String publicId, {
    String? before,
    int limit = 30,
  }) async => PagedResult(
    items: _posts
        .where((post) => post.author.publicId == publicId)
        .take(limit)
        .toList(),
  );
  @override
  Future<PagedResult<Post>> feed({String? before, int limit = 20}) async =>
      PagedResult(items: _posts.take(limit).toList());
  @override
  Future<void> deletePost(String postId) async =>
      _posts.removeWhere((post) => post.id == postId);
  @override
  Future<void> setLiked(String postId, bool liked) async {}
  @override
  Future<Comment> addComment(String postId, String text) async => Comment(
    id: 1,
    author: const PublicProfile(publicId: 'demodemodemodemodemo', alias: 'Bạn'),
    text: text,
    createdAt: DateTime.now().toUtc(),
  );
  @override
  Future<PagedResult<Comment>> comments(
    String postId, {
    int? after,
    int limit = 30,
  }) async => const PagedResult(items: []);
  @override
  Future<void> deleteComment(String postId, int commentId) async {}
}

final livePostRepositoryProvider = Provider<PostRepository>(
  (ref) => LivePostRepository(
    ref.watch(apiClientProvider),
    ref.watch(cacheDatabaseProvider.future),
  ),
);
final demoPostRepositoryProvider = Provider<PostRepository>(
  (ref) => DemoPostRepository(),
);
final postRepositoryProvider = Provider<PostRepository>(
  (ref) => ref.watch(backendModeProvider) == BackendMode.demo
      ? ref.watch(demoPostRepositoryProvider)
      : ref.watch(livePostRepositoryProvider),
);
