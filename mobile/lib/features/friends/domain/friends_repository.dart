import 'package:animal_locket/core/models/common_models.dart';

class FriendRequest {
  const FriendRequest({
    required this.id,
    required this.user,
    required this.createdAt,
  });
  factory FriendRequest.fromJson(Map<String, dynamic> json) => FriendRequest(
    id: json['id'].toString(),
    user: PublicProfile.fromJson(
      (json['user'] as Map?)?.cast<String, dynamic>() ?? const {},
    ),
    createdAt:
        DateTime.tryParse(json['createdAt'] as String? ?? '')?.toUtc() ??
        DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
  );
  final String id;
  final PublicProfile user;
  final DateTime createdAt;
}

abstract interface class FriendsRepository {
  Future<List<PublicProfile>> friends();
  Future<List<FriendRequest>> incoming();
  Future<List<FriendRequest>> outgoing();
  Future<String> request(String publicId);
  Future<void> accept(String requestId);
  Future<void> decline(String requestId);
  Future<void> cancel(String requestId);
  Future<void> remove(String publicId);
  Future<Map<String, bool>> presence(Iterable<String> publicIds);
}

enum ReportTarget { user, post, comment, message }

abstract interface class SafetyRepository {
  Future<List<PublicProfile>> blocked();
  Future<void> block(String publicId);
  Future<void> unblock(String publicId);
  Future<void> report({
    required ReportTarget targetType,
    required String targetPublicId,
    String? targetRef,
    required String reason,
    String? details,
  });
}
