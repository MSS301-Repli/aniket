import 'package:animal_locket/core/models/common_models.dart';

enum PostStatus { pendingMedia, published, rejected, deleted, unknown }

class Post {
  const Post({
    required this.id,
    required this.author,
    required this.mediaId,
    required this.status,
    required this.likeCount,
    required this.commentCount,
    required this.likedByMe,
    required this.createdAt,
    this.caption,
    this.publishedAt,
  });

  factory Post.fromJson(Map<String, dynamic> json) => Post(
    id: json['id'].toString(),
    author: PublicProfile.fromJson(
      (json['author'] as Map?)?.cast<String, dynamic>() ?? const {},
    ),
    caption: json['caption'] as String?,
    mediaId: json['mediaId'] as String? ?? '',
    status: switch (json['status']) {
      'PENDING_MEDIA' => PostStatus.pendingMedia,
      'PUBLISHED' => PostStatus.published,
      'REJECTED' => PostStatus.rejected,
      'DELETED' => PostStatus.deleted,
      _ => PostStatus.unknown,
    },
    likeCount: (json['likeCount'] as num?)?.toInt() ?? 0,
    commentCount: (json['commentCount'] as num?)?.toInt() ?? 0,
    likedByMe: json['likedByMe'] as bool? ?? false,
    createdAt:
        DateTime.tryParse(json['createdAt'] as String? ?? '')?.toUtc() ??
        DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
    publishedAt: DateTime.tryParse(json['publishedAt'] as String? ?? '')
        ?.toUtc(),
  );

  final String id;
  final PublicProfile author;
  final String? caption;
  final String mediaId;
  final PostStatus status;
  final int likeCount;
  final int commentCount;
  final bool likedByMe;
  final DateTime createdAt;
  final DateTime? publishedAt;

  Map<String, dynamic> toJson() => {
    'id': id,
    'author': {
      'publicId': author.publicId,
      'alias': ?author.alias,
      'leadAnimal': ?author.leadAnimal,
      'avatar': ?author.avatar,
    },
    'caption': ?caption,
    'mediaId': mediaId,
    'status': switch (status) {
      PostStatus.pendingMedia => 'PENDING_MEDIA',
      PostStatus.published => 'PUBLISHED',
      PostStatus.rejected => 'REJECTED',
      PostStatus.deleted => 'DELETED',
      PostStatus.unknown => 'UNKNOWN',
    },
    'likeCount': likeCount,
    'commentCount': commentCount,
    'likedByMe': likedByMe,
    'createdAt': createdAt.toUtc().toIso8601String(),
    'publishedAt': ?publishedAt?.toUtc().toIso8601String(),
  };

  Post copyWith({int? likeCount, int? commentCount, bool? likedByMe}) => Post(
    id: id,
    author: author,
    caption: caption,
    mediaId: mediaId,
    status: status,
    likeCount: likeCount ?? this.likeCount,
    commentCount: commentCount ?? this.commentCount,
    likedByMe: likedByMe ?? this.likedByMe,
    createdAt: createdAt,
    publishedAt: publishedAt,
  );
}

class Comment {
  const Comment({
    required this.id,
    required this.author,
    required this.text,
    required this.createdAt,
  });
  factory Comment.fromJson(Map<String, dynamic> json) => Comment(
    id: (json['id'] as num?)?.toInt() ?? 0,
    author: PublicProfile.fromJson(
      (json['author'] as Map?)?.cast<String, dynamic>() ?? const {},
    ),
    text: json['text'] as String? ?? '',
    createdAt:
        DateTime.tryParse(json['createdAt'] as String? ?? '')?.toUtc() ??
        DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
  );
  final int id;
  final PublicProfile author;
  final String text;
  final DateTime createdAt;
}

abstract interface class PostRepository {
  Future<Post> create({required String mediaId, String? caption});
  Future<List<Post>> mine({int limit = 50});

  /// Bài của một người (chính mình: mọi trạng thái; bạn bè: chỉ đã đăng). Người lạ: ApiProblem 404.
  Future<PagedResult<Post>> postsOf(
    String publicId, {
    String? before,
    int limit = 30,
  });
  Future<PagedResult<Post>> feed({String? before, int limit = 20});
  Future<void> deletePost(String postId);
  Future<void> setLiked(String postId, bool liked);
  Future<Comment> addComment(String postId, String text);
  Future<PagedResult<Comment>> comments(
    String postId, {
    int? after,
    int limit = 30,
  });
  Future<void> deleteComment(String postId, int commentId);
}
