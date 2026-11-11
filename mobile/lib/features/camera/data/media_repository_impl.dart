import 'package:animal_locket/core/config/backend_mode.dart';
import 'package:animal_locket/core/network/api_client.dart';
import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/features/camera/domain/media_repository.dart';
import 'package:cross_file/cross_file.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class LiveMediaRepository implements MediaRepository {
  LiveMediaRepository(this._api) : _unsignedDio = Dio();
  final ApiClient _api;
  final Dio _unsignedDio;
  final Map<String, MediaUrlLease> _urlCache = {};

  @override
  Future<UploadTicket> createUpload({
    required MediaContext context,
    required String contentType,
    required int sizeBytes,
    String? refId,
  }) async {
    final data = (await _api.post<Map<String, dynamic>>(
      '/media/uploads',
      data: {
        'context': context == MediaContext.post ? 'POST' : 'CHAT',
        'refId': ?refId,
        'contentType': contentType,
        'sizeBytes': sizeBytes,
      },
    )).data!;
    return UploadTicket(
      mediaId: data['mediaId'] as String,
      uploadUrl: data['uploadUrl'] as String,
      headers: (data['headers'] as Map<String, dynamic>).map(
        (key, value) => MapEntry(key, value.toString()),
      ),
      expiresInSeconds: (data['expiresInSeconds'] as num?)?.toInt() ?? 300,
    );
  }

  @override
  Future<void> uploadFile({
    required UploadTicket ticket,
    required XFile file,
    required String contentType,
    void Function(int sent, int total)? onProgress,
  }) async {
    // R2 không hỗ trợ POST form nên upload bằng PUT thẳng nội dung file tới URL đã ký.
    final length = await file.length();
    try {
      final response = await _unsignedDio.put<Object?>(
        ticket.uploadUrl,
        data: file.openRead(),
        onSendProgress: onProgress,
        options: Options(
          followRedirects: false,
          headers: {...ticket.headers, Headers.contentLengthHeader: length},
          validateStatus: (status) =>
              status != null && status >= 200 && status < 300,
        ),
      );
      if (response.statusCode != 200 && response.statusCode != 204) {
        throw ApiProblem(status: response.statusCode);
      }
    } on DioException catch (error) {
      throw ApiProblem.fromDioException(error);
    }
  }

  @override
  Future<MediaState> complete(String mediaId) async => MediaState.fromJson(
    (await _api.post<Map<String, dynamic>>('/media/$mediaId/complete')).data!,
  );

  @override
  Future<MediaState> status(String mediaId) async => MediaState.fromJson(
    (await _api.get<Map<String, dynamic>>('/media/$mediaId')).data!,
  );

  @override
  Future<MediaUrlLease> viewUrl(String mediaId) async {
    final cached = _urlCache[mediaId];
    if (cached?.isValid == true) return cached!;
    final data = (await _api.get<Map<String, dynamic>>('/media/$mediaId/url'))
        .data!;
    final seconds = (data['expiresInSeconds'] as num?)?.toInt() ?? 600;
    final lease = MediaUrlLease(
      url: data['url'] as String,
      expiresAt: DateTime.now().toUtc().add(
        Duration(seconds: seconds > 30 ? seconds - 30 : seconds),
      ),
    );
    _urlCache[mediaId] = lease;
    return lease;
  }

  @override
  Future<MediaState> waitUntilProcessed(
    String mediaId, {
    Duration timeout = const Duration(seconds: 60),
  }) async {
    final deadline = DateTime.now().add(timeout);
    while (DateTime.now().isBefore(deadline)) {
      final current = await status(mediaId);
      if (current.status == MediaStatus.ready ||
          current.status == MediaStatus.rejected) {
        return current;
      }
      await Future<void>.delayed(const Duration(seconds: 2));
    }
    throw const ApiProblem(status: 408, code: 'MEDIA_PROCESSING_TIMEOUT');
  }
}

class DemoMediaRepository implements MediaRepository {
  @override
  Future<UploadTicket> createUpload({
    required MediaContext context,
    required String contentType,
    required int sizeBytes,
    String? refId,
  }) async => const UploadTicket(
    mediaId: 'demo-media',
    uploadUrl: '',
    headers: {},
    expiresInSeconds: 300,
  );
  @override
  Future<void> uploadFile({
    required UploadTicket ticket,
    required XFile file,
    required String contentType,
    void Function(int sent, int total)? onProgress,
  }) async {
    onProgress?.call(1, 1);
  }

  @override
  Future<MediaState> complete(String mediaId) async =>
      MediaState(mediaId: mediaId, status: MediaStatus.uploaded);
  @override
  Future<MediaState> status(String mediaId) async => MediaState(
    mediaId: mediaId,
    status: MediaStatus.ready,
    method: 'STICKER',
  );
  @override
  Future<MediaUrlLease> viewUrl(String mediaId) async => MediaUrlLease(
    url: '',
    expiresAt: DateTime.now().toUtc().add(const Duration(minutes: 10)),
  );
  @override
  Future<MediaState> waitUntilProcessed(
    String mediaId, {
    Duration timeout = const Duration(seconds: 60),
  }) async {
    await Future<void>.delayed(const Duration(milliseconds: 800));
    return MediaState(
      mediaId: mediaId,
      status: MediaStatus.ready,
      method: 'STICKER',
    );
  }
}

final liveMediaRepositoryProvider = Provider<MediaRepository>(
  (ref) => LiveMediaRepository(ref.watch(apiClientProvider)),
);
final demoMediaRepositoryProvider = Provider<MediaRepository>(
  (ref) => DemoMediaRepository(),
);
final mediaRepositoryProvider = Provider<MediaRepository>(
  (ref) => ref.watch(backendModeProvider) == BackendMode.demo
      ? ref.watch(demoMediaRepositoryProvider)
      : ref.watch(liveMediaRepositoryProvider),
);
