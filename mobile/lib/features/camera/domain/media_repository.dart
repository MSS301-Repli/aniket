import 'package:cross_file/cross_file.dart';

enum MediaContext { post, chat }

enum MediaStatus { pendingUpload, uploaded, ready, rejected, unknown }

class UploadTicket {
  const UploadTicket({
    required this.mediaId,
    required this.uploadUrl,
    required this.headers,
    required this.expiresInSeconds,
  });

  final String mediaId;
  final String uploadUrl;

  /// Header phải gửi kèm khi PUT file (Content-Type).
  final Map<String, String> headers;
  final int expiresInSeconds;
}

class MediaState {
  const MediaState({
    required this.mediaId,
    required this.status,
    this.method,
    this.rejectReason,
  });

  factory MediaState.fromJson(Map<String, dynamic> json) => MediaState(
    mediaId: json['mediaId'] as String? ?? '',
    status: switch (json['status']) {
      'PENDING_UPLOAD' => MediaStatus.pendingUpload,
      'UPLOADED' => MediaStatus.uploaded,
      'READY' => MediaStatus.ready,
      'REJECTED' => MediaStatus.rejected,
      _ => MediaStatus.unknown,
    },
    method: json['method'] as String?,
    rejectReason: json['rejectReason'] as String?,
  );

  final String mediaId;
  final MediaStatus status;
  final String? method;
  final String? rejectReason;
}

class MediaUrlLease {
  const MediaUrlLease({required this.url, required this.expiresAt});
  final String url;
  final DateTime expiresAt;

  bool get isValid => DateTime.now().toUtc().isBefore(expiresAt);
}

class UploadProgress {
  const UploadProgress({required this.value, required this.label});
  final double value;
  final String label;
}

abstract interface class MediaRepository {
  Future<UploadTicket> createUpload({
    required MediaContext context,
    required String contentType,
    required int sizeBytes,
    String? refId,
  });
  Future<void> uploadFile({
    required UploadTicket ticket,
    required XFile file,
    required String contentType,
    void Function(int sent, int total)? onProgress,
  });
  Future<MediaState> complete(String mediaId);
  Future<MediaState> status(String mediaId);
  Future<MediaUrlLease> viewUrl(String mediaId);
  Future<MediaState> waitUntilProcessed(
    String mediaId, {
    Duration timeout = const Duration(seconds: 60),
  });
}
