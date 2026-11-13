import 'dart:math' as math;

import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/double_tap_like.dart';
import 'package:animal_locket/core/widgets/album/hanko_stamp.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/features/feed/data/post_repository_impl.dart';
import 'package:animal_locket/features/feed/domain/post_repository.dart';
import 'package:animal_locket/features/feed/presentation/feed_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:animal_locket/core/l10n/tr.dart';

class UserPostsState {
  const UserPostsState({
    this.items = const [],
    this.nextBefore,
    this.loadingMore = false,
  });
  final List<Post> items;
  final String? nextBefore;
  final bool loadingMore;
}

/// Bài của một người, phân trang theo `before`. Tham số family là publicId.
class UserPostsController extends AsyncNotifier<UserPostsState> {
  UserPostsController(this.publicId);
  final String publicId;

  @override
  Future<UserPostsState> build() async {
    final page = await ref.read(postRepositoryProvider).postsOf(publicId);
    return UserPostsState(items: page.items, nextBefore: page.nextBefore);
  }

  Future<void> loadMore() async {
    final current = state.value;
    if (current == null || current.nextBefore == null || current.loadingMore) {
      return;
    }
    state = AsyncData(
      UserPostsState(
        items: current.items,
        nextBefore: current.nextBefore,
        loadingMore: true,
      ),
    );
    try {
      final page = await ref
          .read(postRepositoryProvider)
          .postsOf(publicId, before: current.nextBefore);
      state = AsyncData(
        UserPostsState(
          items: [...current.items, ...page.items],
          nextBefore: page.nextBefore,
        ),
      );
    } catch (_) {
      state = AsyncData(current);
    }
  }
}

/// `ref.invalidate(userPostsProvider)` làm mới lưới ảnh của mọi người (sau khi đăng/xoá).
final userPostsProvider =
    AsyncNotifierProvider.family<UserPostsController, UserPostsState, String>(
      UserPostsController.new,
    );

/// Lưới 3 cột ảnh in nhỏ dán nghiêng của một người (spec §7.5). [mine]: hiện cả bài đang xử lý/bị từ chối và cho xoá.
class PostsGrid extends ConsumerWidget {
  const PostsGrid({
    super.key,
    required this.publicId,
    this.mine = false,
    this.emptyText,
  });
  final String publicId;
  final bool mine;
  final String? emptyText;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final posts = ref.watch(userPostsProvider(publicId));
    return posts.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(24),
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (error, _) => error is ApiProblem && error.status == 404
          ? _Note(context.l10n.befriendToSee)
          : TextButton(
              onPressed: () => ref.invalidate(userPostsProvider(publicId)),
              child: Text(context.l10n.photosLoadFailed),
            ),
      data: (data) {
        final visible = data.items
            .where((post) => post.status != PostStatus.deleted)
            .toList();
        if (visible.isEmpty) {
          return _Note(emptyText ?? context.l10n.noPhotosYet);
        }
        return Column(
          children: [
            GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              padding: EdgeInsets.zero,
              itemCount: visible.length,
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 3,
                mainAxisSpacing: 12,
                crossAxisSpacing: 12,
                childAspectRatio: 4 / 5,
              ),
              itemBuilder: (context, index) =>
                  _Thumbnail(post: visible[index], mine: mine, index: index),
            ),
            if (data.nextBefore != null)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: data.loadingMore
                    ? const CircularProgressIndicator()
                    : TextButton(
                        onPressed: ref
                            .read(userPostsProvider(publicId).notifier)
                            .loadMore,
                        child: Text(context.l10n.morePhotos),
                      ),
              ),
          ],
        );
      },
    );
  }
}

class _Note extends StatelessWidget {
  const _Note(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 20),
    child: Text(
      text,
      textAlign: TextAlign.center,
      style: TextStyle(color: context.album.muted),
    ),
  );
}

/// Ảnh in nhỏ: viền giấy, bóng mềm, nghiêng xen kẽ theo vị trí.
class _Thumbnail extends StatelessWidget {
  const _Thumbnail({
    required this.post,
    required this.mine,
    required this.index,
  });
  final Post post;
  final bool mine;
  final int index;

  static const _tilts = [-2.0, 1.5, -1.0, 2.0, -1.5, 1.0];

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    final overlay = switch (post.status) {
      PostStatus.pendingMedia => (
        AlbumIcons.hourglass,
        context.l10n.postProcessing,
      ),
      PostStatus.rejected => (AlbumIcons.block, context.l10n.postRejected),
      _ => null,
    };
    return Transform.rotate(
      key: const ValueKey('post-print'),
      angle: _tilts[index % _tilts.length] * math.pi / 180,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: p.paper,
          boxShadow: const [
            BoxShadow(
              color: Color(0x2E5A3C1E),
              blurRadius: 6,
              offset: Offset(0, 2),
            ),
          ],
        ),
        child: Padding(
          padding: const EdgeInsets.all(4),
          child: Material(
            type: MaterialType.transparency,
            child: InkWell(
              onTap: () => showDialog<void>(
                context: context,
                builder: (_) => _PostDialog(post: post, mine: mine),
              ),
              child: Stack(
                fit: StackFit.expand,
                children: [
                  post.status == PostStatus.published
                      ? _PostImage(mediaId: post.mediaId)
                      : ColoredBox(color: p.fold2),
                  if (overlay != null)
                    Center(
                      child: Padding(
                        padding: const EdgeInsets.all(4),
                        child: DecoratedBox(
                          decoration: BoxDecoration(color: p.paper),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 6,
                              vertical: 4,
                            ),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                AlbumIcon(overlay.$1, size: 18, color: p.ink),
                                const SizedBox(height: 2),
                                Text(
                                  overlay.$2,
                                  textAlign: TextAlign.center,
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    color: p.ink,
                                    fontSize: 11,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _PostImage extends ConsumerWidget {
  const _PostImage({required this.mediaId});
  final String mediaId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = context.album;
    Widget placeholder(Widget child) => ColoredBox(
      color: p.fold2,
      child: Center(child: child),
    );
    final url = ref.watch(mediaUrlProvider(mediaId));
    return url.when(
      loading: () => placeholder(
        const SizedBox(
          width: 20,
          height: 20,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      ),
      error: (_, _) =>
          placeholder(AlbumIcon(AlbumIcons.imageBroken, color: p.muted)),
      data: (value) => value == null
          ? placeholder(AlbumIcon(AlbumIcons.image, color: p.muted))
          : Image.network(
              value,
              fit: BoxFit.cover,
              errorBuilder: (_, _, _) => placeholder(
                AlbumIcon(AlbumIcons.imageBroken, color: p.muted),
              ),
            ),
    );
  }
}

class _PostDialog extends ConsumerStatefulWidget {
  const _PostDialog({required this.post, required this.mine});
  final Post post;
  final bool mine;

  @override
  ConsumerState<_PostDialog> createState() => _PostDialogState();
}

class _PostDialogState extends ConsumerState<_PostDialog> {
  late bool _liked = widget.post.likedByMe;
  late int _likes = widget.post.likeCount;

  Post get post => widget.post;

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    final time = (post.publishedAt ?? post.createdAt).toLocal();
    return Dialog(
      clipBehavior: Clip.antiAlias,
      backgroundColor: p.paper,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(12, 12, 12, 6),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            AspectRatio(
              aspectRatio: 4 / 5,
              child: post.status == PostStatus.published
                  ? DoubleTapLike(
                      liked: _liked,
                      onLike: _toggleLike,
                      child: _PostImage(mediaId: post.mediaId),
                    )
                  : ColoredBox(
                      color: p.fold2,
                      child: Center(
                        child: Text(
                          post.status == PostStatus.rejected
                              ? context.l10n.postRejectedPhoto
                              : context.l10n.postProcessingPhoto,
                        ),
                      ),
                    ),
            ),
            if (post.caption?.trim().isNotEmpty == true)
              Padding(
                padding: const EdgeInsets.fromLTRB(4, 10, 4, 0),
                child: Text(
                  post.caption!,
                  style: TextStyle(
                    fontFamily: AppFonts.hand,
                    fontSize: 19,
                    height: 1.25,
                    color: p.ink,
                  ),
                ),
              ),
            const SizedBox(height: 4),
            Row(
              children: [
                HankoStamp(
                  stamped: _liked,
                  tooltip: _liked ? context.l10n.unlike : context.l10n.like,
                  onPressed: post.status == PostStatus.published
                      ? _toggleLike
                      : null,
                ),
                Text('$_likes', style: TextStyle(color: p.muted)),
                const SizedBox(width: 12),
                AlbumIcon(AlbumIcons.comment, size: 18, color: p.muted),
                const SizedBox(width: 4),
                Text('${post.commentCount}', style: TextStyle(color: p.muted)),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    formatPostDay(time),
                    style: TextStyle(color: p.muted, fontSize: 12),
                  ),
                ),
                if (widget.mine)
                  Semantics(
                    label: context.l10n.deletePhoto,
                    button: true,
                    excludeSemantics: true,
                    onTap: _delete,
                    child: IconButton(
                      tooltip: context.l10n.deletePhoto,
                      icon: AlbumIcon(AlbumIcons.trash, color: p.danger),
                      onPressed: _delete,
                    ),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _toggleLike() async {
    final liked = !_liked;
    setState(() {
      _liked = liked;
      _likes += liked ? 1 : -1;
    });
    try {
      await ref.read(postRepositoryProvider).setLiked(post.id, liked);
      ref
        ..invalidate(userPostsProvider(post.author.publicId))
        ..invalidate(feedControllerProvider);
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _liked = !liked;
        _likes += liked ? -1 : 1;
      });
    }
  }

  Future<void> _delete() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(context.l10n.deletePhotoQ),
        content: Text(context.l10n.deletePhotoBody),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: Text(context.l10n.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(context.l10n.delete),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(postRepositoryProvider).deletePost(post.id);
      ref
        ..invalidate(userPostsProvider)
        ..invalidate(feedControllerProvider);
      if (mounted) Navigator.pop(context);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(context.l10n.deletePhotoFailed)));
      }
    }
  }
}

/// "Hôm nay", "Hôm qua" hoặc "dd/MM/yyyy" theo giờ máy.
String formatPostDay(DateTime local) {
  final now = DateTime.now();
  final today = DateTime(now.year, now.month, now.day);
  final day = DateTime(local.year, local.month, local.day);
  final diff = today.difference(day).inDays;
  if (diff == 0) return tr.dayToday;
  if (diff == 1) return tr.dayYesterday;
  String two(int v) => v.toString().padLeft(2, '0');
  return '${two(local.day)}/${two(local.month)}/${local.year}';
}
