import 'dart:math' as math;

import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/core/network/error_messages.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/double_tap_like.dart';
import 'package:animal_locket/core/widgets/album/hanko_stamp.dart';
import 'package:animal_locket/core/widgets/album/washi_photo_card.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/animal_avatar.dart';
import 'package:animal_locket/core/widgets/app_page.dart';
import 'package:animal_locket/features/feed/domain/post_repository.dart';
import 'package:animal_locket/features/feed/presentation/feed_controller.dart';
import 'package:animal_locket/features/feed/presentation/posts_grid.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:animal_locket/core/l10n/tr.dart';

class FeedScreen extends ConsumerWidget {
  const FeedScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final feed = ref.watch(feedControllerProvider);
    return AppPage(
      title: context.l10n.feedTitle,
      watermark: '瞬',
      // Danh sách tự cuộn; AppPage cuộn thêm một lớp sẽ nuốt thao tác vuốt.
      scrollable: false,
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
      actions: [
        IconButton(
          tooltip: context.l10n.friendsLabel,
          onPressed: () => context.push('/friends'),
          icon: const AlbumIcon(AlbumIcons.friends),
        ),
      ],
      child: feed.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _FeedError(error: error),
        data: (data) => RefreshIndicator(
          onRefresh: ref.read(feedControllerProvider.notifier).refresh,
          child: data.items.isEmpty
              ? const _EmptyFeed()
              : NotificationListener<ScrollNotification>(
                  onNotification: (notification) {
                    if (notification.metrics.extentAfter < 500) {
                      ref.read(feedControllerProvider.notifier).loadMore();
                    }
                    return false;
                  },
                  // Ẩn thanh cuộn (Flutter tự thêm trên web/desktop).
                  child: ScrollConfiguration(
                    behavior: ScrollConfiguration.of(context)
                        .copyWith(scrollbars: false),
                    // Mỗi lần vuốt chuyển đúng một bài, hiện trọn trong khung.
                    child: PageView.builder(
                      scrollDirection: Axis.vertical,
                      physics: const AlwaysScrollableScrollPhysics(),
                      itemCount: data.items.length + (data.loadingMore ? 1 : 0),
                      itemBuilder: (context, index) {
                        if (index == data.items.length) {
                          return const Center(
                            child: CircularProgressIndicator(),
                          );
                        }
                        return Padding(
                          // Chừa chỗ cho thanh điều hướng nổi ở đáy (extendBody).
                          padding: const EdgeInsets.only(top: 4, bottom: 88),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              if (index == 0 && data.degraded)
                                const _DegradedBanner(),
                              // Mỗi trang một bài nên trang nào cũng ghi ngày.
                              _DayLabel(_dayLabel(data.items[index])),
                              Flexible(
                                child: Padding(
                                  // Chừa lề cho góc nghiêng và băng washi.
                                  padding: const EdgeInsets.fromLTRB(
                                    4,
                                    10,
                                    4,
                                    0,
                                  ),
                                  child: _PostCard(
                                    post: data.items[index],
                                    index: index,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        );
                      },
                    ),
                  ),
                ),
        ),
      ),
    );
  }
}

class _DayLabel extends StatelessWidget {
  const _DayLabel(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 2, left: 4),
    child: Text(
      text,
      style: TextStyle(
        fontFamily: AppFonts.title,
        fontStyle: FontStyle.italic,
        fontWeight: FontWeight.w500,
        fontSize: 14,
        color: context.album.muted,
      ),
    ),
  );
}

class _FeedError extends ConsumerWidget {
  const _FeedError({required this.error});
  final Object error;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final message = error is ApiProblem
        ? messageForApiCode(
            (error as ApiProblem).code,
            status: (error as ApiProblem).status,
          )
        : context.l10n.feedLoadFailed;
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          AlbumIcon(AlbumIcons.cloudOff, size: 46, color: context.album.muted),
          const SizedBox(height: 12),
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: ref.read(feedControllerProvider.notifier).refresh,
            child: Text(context.l10n.retry),
          ),
        ],
      ),
    );
  }
}

class _EmptyFeed extends StatelessWidget {
  const _EmptyFeed();

  @override
  Widget build(BuildContext context) => ListView(
    physics: const AlwaysScrollableScrollPhysics(),
    children: [
      const SizedBox(height: 140),
      Center(
        child: AlbumIcon(
          AlbumIcons.emptyAlbum,
          size: 58,
          color: context.album.muted,
        ),
      ),
      const SizedBox(height: 14),
      Text(context.l10n.feedEmpty, textAlign: TextAlign.center),
    ],
  );
}

class _DegradedBanner extends StatelessWidget {
  const _DegradedBanner();

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: p.accentSoft,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: p.fold1),
      ),
      child: Text(context.l10n.feedDegraded, textAlign: TextAlign.center),
    );
  }
}

class _PostCard extends ConsumerWidget {
  const _PostCard({required this.post, required this.index});
  final Post post;
  final int index;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = context.album;
    final image = ref.watch(mediaUrlProvider(post.mediaId));
    final tilt = WashiPhotoCard.tiltFor(index);
    Widget placeholder(Widget child) => ColoredBox(
      color: p.fold2,
      child: Center(child: child),
    );
    final caption = post.caption?.trim();
    Widget card(double t) => WashiPhotoCard(
      tiltDegrees: tilt * t,
      photo: DoubleTapLike(
        liked: post.likedByMe,
        onLike: () =>
            ref.read(feedControllerProvider.notifier).toggleLike(post.id),
        child: image.when(
          loading: () => placeholder(const CircularProgressIndicator()),
          error: (_, _) =>
              placeholder(AlbumIcon(AlbumIcons.imageBroken, color: p.muted)),
          data: (url) => url == null
              ? placeholder(AlbumIcon(AlbumIcons.image, color: p.muted))
              : Image.network(
                  url,
                  fit: BoxFit.cover,
                  errorBuilder: (_, _, _) => placeholder(
                    AlbumIcon(AlbumIcons.imageBroken, color: p.muted),
                  ),
                ),
        ),
      ),
      // Một hàng: tác giả bên trái, thích/bình luận bên phải (gọn hơn hai hàng).
      header: Row(
        children: [
          Expanded(
            child: InkWell(
              onTap: () => context.push('/user/${post.author.publicId}'),
              borderRadius: BorderRadius.circular(10),
              // Vùng chạm tối thiểu 48pt (hướng dẫn Android).
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 48),
                child: Row(
                  children: [
                    AnimalAvatar(
                      avatar: post.author.avatar,
                      leadAnimal: post.author.leadAnimal,
                      size: 36,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            post.author.displayAlias,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontWeight: FontWeight.w600,
                              fontSize: 14,
                            ),
                          ),
                          Text(
                            _relativeTime(post.createdAt),
                            style: TextStyle(fontSize: 12, color: p.muted),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          HankoStamp(
            stamped: post.likedByMe,
            tooltip: post.likedByMe ? context.l10n.unlike : context.l10n.like,
            onPressed: () =>
                ref.read(feedControllerProvider.notifier).toggleLike(post.id),
          ),
          Text('${post.likeCount}', style: TextStyle(color: p.muted)),
          const SizedBox(width: 12),
          AlbumIcon(AlbumIcons.comment, size: 21, color: p.muted),
          const SizedBox(width: 4),
          Text('${post.commentCount}', style: TextStyle(color: p.muted)),
          const SizedBox(width: 4),
        ],
      ),
      caption: caption?.isNotEmpty == true
          ? Text(
              post.caption!,
              style: TextStyle(
                fontFamily: AppFonts.hand,
                fontSize: 19,
                height: 1.25,
                color: p.ink,
              ),
            )
          : null,
    );

    return _Entrance(postId: post.id, builder: card);
  }
}

/// Bài đã chạy hiệu ứng xuất hiện trong phiên (theo ProviderScope), để thẻ
/// cuộn ra khỏi màn rồi quay lại không chạy lại hiệu ứng.
/// ponytail: Set lớn dần theo số bài đã xem trong phiên; vài nghìn id chuỗi là
/// không đáng kể, cần thì giới hạn kích thước (LRU).
final _shownEntrancesProvider = Provider<Set<String>>((ref) => <String>{});

/// Thẻ hiện ra lần đầu: trượt lên và nghiêng dần về góc của nó.
class _Entrance extends ConsumerStatefulWidget {
  const _Entrance({required this.postId, required this.builder});
  final String postId;
  final Widget Function(double t) builder;

  @override
  ConsumerState<_Entrance> createState() => _EntranceState();
}

class _EntranceState extends ConsumerState<_Entrance> {
  // Quyết định một lần cho mỗi phần tử, để rebuild giữa chừng không cắt hiệu ứng.
  late final bool _animate =
      ref.read(_shownEntrancesProvider).add(widget.postId) &&
      !MediaQuery.disableAnimationsOf(context);

  @override
  Widget build(BuildContext context) {
    if (!_animate) return widget.builder(1);
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: const Duration(milliseconds: 250),
      curve: Curves.easeOutCubic,
      builder: (context, t, _) => Transform.translate(
        offset: Offset(0, 12 * (1 - t)),
        child: Opacity(opacity: math.min(1, .4 + t), child: widget.builder(t)),
      ),
    );
  }
}

String _dayLabel(Post post) =>
    formatPostDay((post.publishedAt ?? post.createdAt).toLocal());

String _relativeTime(DateTime value) {
  final difference = DateTime.now().toUtc().difference(value.toUtc());
  if (difference.inMinutes < 1) return tr.timeJustNow;
  if (difference.inHours < 1) return tr.timeMinutesAgo(difference.inMinutes);
  if (difference.inDays < 1) return tr.timeHoursAgo(difference.inHours);
  return tr.timeDaysAgo(difference.inDays);
}
