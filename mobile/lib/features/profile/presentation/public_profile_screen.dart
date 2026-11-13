import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/polaroid.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/animal_avatar.dart';
import 'package:animal_locket/core/widgets/app_page.dart';
import 'package:animal_locket/features/chat/data/chat_repository_impl.dart';
import 'package:animal_locket/features/feed/presentation/feed_controller.dart';
import 'package:animal_locket/features/feed/presentation/posts_grid.dart';
import 'package:animal_locket/features/friends/data/friends_repository_impl.dart';
import 'package:animal_locket/features/friends/domain/friends_repository.dart';
import 'package:animal_locket/features/profile/data/user_repository_impl.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:animal_locket/core/l10n/tr.dart';

final publicProfileProvider = FutureProvider.family<PublicProfile, String>(
  (ref, id) => ref.watch(userRepositoryProvider).publicProfile(id),
);

/// Có đang là bạn không; lỗi mạng coi như không (ẩn nút huỷ kết bạn).
final isFriendProvider = FutureProvider.family<bool, String>((ref, id) async {
  try {
    final friends = await ref.watch(friendsRepositoryProvider).friends();
    return friends.any((friend) => friend.publicId == id);
  } catch (_) {
    return false;
  }
});

class PublicProfileScreen extends ConsumerWidget {
  const PublicProfileScreen({super.key, required this.publicId});
  final String publicId;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final value = ref.watch(publicProfileProvider(publicId));
    final isFriend = ref.watch(isFriendProvider(publicId)).value ?? false;
    return AppPage(
      title: context.l10n.profileTab,
      child: value.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(context.l10n.profileLoadFailed)),
        data: (profile) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _header(context, ref, profile, isFriend),
            const SizedBox(height: 24),
            Text(
              context.l10n.photosOf(profile.displayAlias),
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: 12),
            PostsGrid(
              publicId: publicId,
              emptyText: context.l10n.noPhotosOf(profile.displayAlias),
            ),
            // Chừa chỗ cho thanh điều hướng.
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  /// Avatar polaroid dán băng cạnh biệt danh; nút giấy; báo cáo/chặn là chữ nhỏ (spec §7.5).
  Widget _header(
    BuildContext context,
    WidgetRef ref,
    PublicProfile profile,
    bool isFriend,
  ) {
    final p = context.album;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: 10),
        Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Polaroid(
              width: 116,
              tiltDegrees: -4,
              caption: '',
              child: AnimalAvatar(
                avatar: profile.avatar,
                leadAnimal: profile.leadAnimal,
                size: 116,
                square: true,
              ),
            ),
            const SizedBox(width: 18),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    profile.displayAlias,
                    style: Theme.of(context).textTheme.headlineLarge,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    context.l10n.anonymousProfile,
                    style: TextStyle(color: p.muted),
                  ),
                  const SizedBox(height: 14),
                  PaperButton(
                    label: context.l10n.message,
                    icon: AlbumIcons.chat,
                    onPressed: () => _chat(context, ref),
                  ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 18),
        Wrap(
          crossAxisAlignment: WrapCrossAlignment.center,
          spacing: 8,
          runSpacing: 8,
          children: [
            if (isFriend)
              PaperButton.tape(
                label: context.l10n.unfriend,
                onPressed: () async {
                  await ref.read(friendsRepositoryProvider).remove(publicId);
                  ref
                    ..invalidate(isFriendProvider(publicId))
                    ..invalidate(userPostsProvider(publicId))
                    ..invalidate(feedControllerProvider);
                  if (context.mounted) context.pop();
                },
              ),
            TextButton.icon(
              style: TextButton.styleFrom(foregroundColor: p.muted),
              onPressed: () => _report(context, ref),
              icon: AlbumIcon(AlbumIcons.flag, size: 18, color: p.muted),
              label: Text(context.l10n.report),
            ),
            TextButton.icon(
              style: TextButton.styleFrom(foregroundColor: p.danger),
              onPressed: () => _block(context, ref),
              icon: AlbumIcon(AlbumIcons.block, size: 18, color: p.danger),
              label: Text(context.l10n.block),
            ),
          ],
        ),
      ],
    );
  }

  Future<void> _chat(BuildContext context, WidgetRef ref) async {
    final conversation = await ref
        .read(chatRepositoryProvider)
        .createConversation(publicId);
    if (context.mounted) context.push('/chat/${conversation.id}');
  }

  Future<void> _block(BuildContext context, WidgetRef ref) async {
    final accepted = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(context.l10n.blockQ),
        content: Text(context.l10n.blockBody),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: Text(context.l10n.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(context.l10n.block),
          ),
        ],
      ),
    );
    if (accepted == true) {
      await ref.read(safetyRepositoryProvider).block(publicId);
      if (context.mounted) context.pop();
    }
  }

  Future<void> _report(BuildContext context, WidgetRef ref) async {
    final details = TextEditingController();
    final submit = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(context.l10n.report),
        content: TextField(
          controller: details,
          maxLength: 500,
          maxLines: 4,
          decoration: InputDecoration(labelText: context.l10n.reportDetails),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: Text(context.l10n.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(context.l10n.sendReport),
          ),
        ],
      ),
    );
    final text = details.text.trim();
    details.dispose();
    if (submit == true) {
      await ref
          .read(safetyRepositoryProvider)
          .report(
            targetType: ReportTarget.user,
            targetPublicId: publicId,
            reason: 'USER_REPORT',
            details: text.isEmpty ? null : text,
          );
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(context.l10n.reportSent)));
      }
    }
  }
}
