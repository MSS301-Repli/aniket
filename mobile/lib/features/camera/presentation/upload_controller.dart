import 'dart:async';

import 'package:animal_locket/features/camera/data/image_preparation_service.dart';
import 'package:animal_locket/features/camera/data/media_repository_impl.dart';
import 'package:animal_locket/features/camera/domain/media_repository.dart';
import 'package:animal_locket/features/feed/data/post_repository_impl.dart';
import 'package:animal_locket/features/feed/domain/post_repository.dart';
import 'package:animal_locket/features/feed/presentation/feed_controller.dart';
import 'package:animal_locket/features/feed/presentation/posts_grid.dart';
import 'package:cross_file/cross_file.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// idle → sending → sent | rejected | failed. Gửi chạy nền: người dùng quay về camera ngay sau khi bấm gửi.
enum UploadStage { idle, sending, sent, rejected, failed }

class UploadFlowState {
  const UploadFlowState({
    this.stage = UploadStage.idle,
    this.post,
    this.rejectReason,
    this.error,
    this.input,
    this.caption,
  });

  final UploadStage stage;
  final Post? post;
  final String? rejectReason;
  final Object? error;

  /// Giữ lại để "Thử lại" khi gửi lỗi.
  final XFile? input;
  final String? caption;

  bool get sending => stage == UploadStage.sending;
}

class UploadController extends Notifier<UploadFlowState> {
  Timer? _autoHide;

  @override
  UploadFlowState build() {
    ref.onDispose(() => _autoHide?.cancel());
    return const UploadFlowState();
  }

  /// Bắt đầu gửi; trả về false nếu đang có ảnh khác đang gửi.
  bool publish({required XFile input, String? caption}) {
    if (state.sending) return false;
    _autoHide?.cancel();
    state = UploadFlowState(
      stage: UploadStage.sending,
      input: input,
      caption: caption,
    );
    unawaited(_send(input, caption));
    return true;
  }

  Future<void> retry() async {
    final input = state.input;
    if (input == null || state.sending) return;
    publish(input: input, caption: state.caption);
  }

  void dismiss() {
    _autoHide?.cancel();
    if (!state.sending) state = const UploadFlowState();
  }

  Future<void> _send(XFile input, String? caption) async {
    PreparedImage? prepared;
    try {
      prepared = await const ImagePreparationService().prepare(input);
      final media = ref.read(mediaRepositoryProvider);
      final ticket = await media.createUpload(
        context: MediaContext.post,
        contentType: prepared.contentType,
        sizeBytes: prepared.sizeBytes,
      );
      await media.uploadFile(
        ticket: ticket,
        file: prepared.file,
        contentType: prepared.contentType,
      );
      // Server kiểm tra xong là READY ngay trong lời gọi này; chỉ hỏi lại khi
      // backend cũ còn trả trạng thái trung gian.
      var result = await media.complete(ticket.mediaId);
      if (result.status != MediaStatus.ready &&
          result.status != MediaStatus.rejected) {
        result = await media.waitUntilProcessed(ticket.mediaId);
      }
      if (result.status == MediaStatus.rejected) {
        state = UploadFlowState(
          stage: UploadStage.rejected,
          rejectReason: result.rejectReason,
        );
        return;
      }
      final post = await ref
          .read(postRepositoryProvider)
          .create(mediaId: ticket.mediaId, caption: caption);
      ref
        ..invalidate(userPostsProvider)
        ..invalidate(feedControllerProvider);
      state = UploadFlowState(stage: UploadStage.sent, post: post);
      _autoHide = Timer(const Duration(seconds: 4), dismiss);
    } catch (error) {
      state = UploadFlowState(
        stage: UploadStage.failed,
        error: error,
        input: input,
        caption: caption,
      );
    } finally {
      await prepared?.cleanup();
    }
  }
}

final uploadControllerProvider =
    NotifierProvider<UploadController, UploadFlowState>(UploadController.new);
