import 'package:animal_locket/core/avatar/avatar_library.dart';
import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/core/network/error_messages.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/paper_list.dart';
import 'package:animal_locket/core/widgets/album/polaroid.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/animal_avatar.dart';
import 'package:animal_locket/core/widgets/app_page.dart';
import 'package:animal_locket/core/widgets/text_input_dialog.dart';
import 'package:animal_locket/features/feed/presentation/posts_grid.dart';
import 'package:animal_locket/features/friends/presentation/friends_controller.dart';
import 'package:animal_locket/features/profile/presentation/profile_controller.dart';
import 'package:animal_locket/features/profile/presentation/upgrade_pro_button.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:animal_locket/core/l10n/tr.dart';

/// Hồ sơ của tôi như một trang album (spec §7.7).
class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final value = ref.watch(profileControllerProvider);
    return AppPage(
      child: value.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(
          child: PaperButton(
            label: context.l10n.loadFailedRetry,
            onPressed: ref.read(profileControllerProvider.notifier).refresh,
          ),
        ),
        data: (data) => _ProfileContent(data: data),
      ),
    );
  }
}

class _ProfileContent extends ConsumerWidget {
  const _ProfileContent({required this.data});
  final ProfileData data;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = context.album;
    final heading = Theme.of(context).textTheme.headlineMedium;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Align(
          alignment: Alignment.centerRight,
          child: IconButton(
            tooltip: context.l10n.settings,
            onPressed: () => context.push('/settings'),
            icon: AlbumIcon(AlbumIcons.settings, color: context.album.ink),
          ),
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Polaroid(
              width: 124,
              tiltDegrees: -4,
              caption: '',
              child: AnimalAvatar(
                avatar: data.profile.avatar,
                leadAnimal: data.profile.leadAnimal,
                size: 124,
                square: true,
              ),
            ),
            const SizedBox(width: 18),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          data.profile.displayAlias,
                          style: Theme.of(context).textTheme.headlineLarge,
                        ),
                      ),
                      IconButton(
                        tooltip: context.l10n.changeNickname,
                        visualDensity: VisualDensity.compact,
                        onPressed: () => _editAlias(context, ref),
                        icon: AlbumIcon(
                          AlbumIcons.edit,
                          size: 20,
                          color: p.muted,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    data.profile.plan == AccountPlan.pro
                        ? context.l10n.memberPro
                        : context.l10n.memberFree,
                    style: TextStyle(color: p.muted),
                  ),
                  const SizedBox(height: 16),
                  TapedNote(
                    child: Text.rich(
                      TextSpan(
                        children: [
                          TextSpan(
                            text: '${data.avatars.length}',
                            style: TextStyle(
                              color: p.accent,
                              fontWeight: FontWeight.w700,
                              fontSize: 17,
                            ),
                          ),
                          TextSpan(
                            text: '  ${context.l10n.ownedAvatars}',
                            style: TextStyle(color: p.muted, fontSize: 12),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 28),
        const _FriendsStrip(),
        const SizedBox(height: 24),
        Text(context.l10n.myPhotos, style: heading),
        const SizedBox(height: 12),
        PostsGrid(
          publicId: data.profile.publicId,
          mine: true,
          emptyText: context.l10n.noMyPhotos,
        ),
        const SizedBox(height: 24),
        Row(
          children: [
            Expanded(child: Text(context.l10n.collection, style: heading)),
            if (data.profile.avatarSpins > 0)
              PaperButton.tape(
                label: context.l10n.spinAvatarButton(data.profile.avatarSpins),
                onPressed: () => context.push('/avatar-spin'),
              ),
          ],
        ),
        const SizedBox(height: 6),
        Text(
          data.avatars.length > 1
              ? context.l10n.collectionNoteMulti
              : context.l10n.collectionNote,
          style: TextStyle(color: p.muted, fontSize: 12),
        ),
        const SizedBox(height: 18),
        // Album sticker: mỗi avatar là một polaroid nhỏ có tên con vật; avatar đang dùng được đánh dấu.
        Wrap(
          spacing: 14,
          runSpacing: 18,
          children: [
            for (final (i, owned) in data.avatars.indexed)
              _CollectionAvatar(
                owned: owned,
                index: i,
                inUse: owned.avatar == data.profile.avatar,
              ),
          ],
        ),
        const SizedBox(height: 24),
        if (data.profile.ageGroup == AgeGroup.adult &&
            data.profile.plan != AccountPlan.pro)
          UpgradeProButton(
            label: context.l10n.upgradePro,
            onPressed: () => context.push('/pro'),
          ),
      ],
    );
  }

  Future<void> _editAlias(BuildContext context, WidgetRef ref) async {
    final alias = await showDialog<String>(
      context: context,
      builder: (_) => TextInputDialog(
        title: context.l10n.changeNickname,
        label: context.l10n.nicknameLabel,
        confirmLabel: context.l10n.save,
        initialText: data.profile.alias ?? '',
        maxLength: 20, // server: 3–20 ký tự
      ),
    );
    if (alias == null ||
        alias.trim().isEmpty ||
        alias.trim() == data.profile.alias) {
      return;
    }
    try {
      await ref.read(profileControllerProvider.notifier).updateAlias(alias);
    } on ApiProblem catch (problem) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              messageForApiCode(problem.code, status: problem.status),
            ),
          ),
        );
      }
    }
  }
}

/// Một avatar trong bộ sưu tập. Chạm để dùng nó làm avatar chính; cái đang dùng thì không chạm được.
class _CollectionAvatar extends ConsumerWidget {
  const _CollectionAvatar({
    required this.owned,
    required this.index,
    required this.inUse,
  });

  final OwnedAvatar owned;
  final int index;
  final bool inUse;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final name = AvatarLibraries.instance.animalName(owned.avatar) ?? '';
    final card = Opacity(
      opacity: inUse ? 1 : 0.9,
      child: Polaroid(
        width: 92,
        tiltDegrees: index.isEven ? -2.5 : 2,
        caption: inUse ? '$name · đang dùng' : name,
        child: AnimalAvatar(avatar: owned.avatar, size: 92, square: true),
      ),
    );
    if (inUse) {
      return Semantics(label: '$name, avatar đang dùng', child: card);
    }
    return Semantics(
      button: true,
      label: 'Dùng avatar $name',
      excludeSemantics: true,
      child: InkWell(onTap: () => _use(context, ref), child: card),
    );
  }

  Future<void> _use(BuildContext context, WidgetRef ref) async {
    try {
      await ref.read(profileControllerProvider.notifier).setAvatar(owned.avatar);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Đã đổi avatar. Bạn bè sẽ thấy sau vài phút.'),
          ),
        );
      }
    } on ApiProblem catch (problem) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              messageForApiCode(problem.code, status: problem.status),
            ),
          ),
        );
      }
    }
  }
}

/// Hàng polaroid bạn bè cuộn ngang; chạm một người để mở trang của họ.
class _FriendsStrip extends ConsumerWidget {
  const _FriendsStrip();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = context.album;
    final friends = ref.watch(friendsControllerProvider);
    final count = friends.value?.friends.length;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                count == null
                    ? context.l10n.friendsLabel
                    : context.l10n.friendsCount(count),
                style: Theme.of(context).textTheme.headlineMedium,
              ),
            ),
            PaperButton.tape(
              label: context.l10n.seeAll,
              onPressed: () => context.push('/friends'),
            ),
          ],
        ),
        const SizedBox(height: 12),
        friends.when(
          loading: () => const SizedBox(
            height: 96,
            child: Center(child: CircularProgressIndicator()),
          ),
          error: (_, _) => PaperButton.tape(
            label: context.l10n.friendsLoadFailed,
            onPressed: () => ref.invalidate(friendsControllerProvider),
          ),
          data: (data) => data.friends.isEmpty
              ? Row(
                  children: [
                    Expanded(
                      child: Text(
                        context.l10n.noFriendsYet,
                        style: TextStyle(color: p.muted),
                      ),
                    ),
                    PaperButton.tape(
                      label: context.l10n.findFriends,
                      onPressed: () => context.go('/discover'),
                    ),
                  ],
                )
              : SizedBox(
                  height: 108,
                  child: ListView.separated(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    itemCount: data.friends.length,
                    separatorBuilder: (_, _) => const SizedBox(width: 10),
                    itemBuilder: (context, index) {
                      final person = data.friends[index];
                      return Semantics(
                        button: true,
                        label: context.l10n.openProfileOf(person.displayAlias),
                        excludeSemantics: true,
                        child: InkWell(
                          onTap: () => context.push('/user/${person.publicId}'),
                          child: SizedBox(
                            width: 72,
                            child: Column(
                              children: [
                                Stack(
                                  clipBehavior: Clip.none,
                                  children: [
                                    MiniPolaroidAvatar(
                                      avatar: person.avatar,
                                      leadAnimal: person.leadAnimal,
                                      index: index,
                                    ),
                                    if (data.online[person.publicId] == true)
                                      Positioned(
                                        right: -3,
                                        bottom: 4,
                                        child: Container(
                                          width: 13,
                                          height: 13,
                                          decoration: BoxDecoration(
                                            color: onlineGreen,
                                            shape: BoxShape.circle,
                                            border: Border.all(
                                              color: p.paper,
                                              width: 2,
                                            ),
                                          ),
                                        ),
                                      ),
                                  ],
                                ),
                                const SizedBox(height: 8),
                                Text(
                                  person.displayAlias,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  textAlign: TextAlign.center,
                                  style: const TextStyle(fontSize: 12),
                                ),
                              ],
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                ),
        ),
      ],
    );
  }
}
