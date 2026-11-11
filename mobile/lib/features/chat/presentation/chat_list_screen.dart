import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/paper_list.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/app_page.dart';
import 'package:animal_locket/features/chat/domain/chat_repository.dart';
import 'package:animal_locket/features/chat/presentation/chat_controllers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:animal_locket/core/l10n/tr.dart';

/// Danh sách chat trên một trang giấy kẻ, cùng kiểu sổ bạn bè (spec §7.6).
class ChatListScreen extends ConsumerWidget {
  const ChatListScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = context.album;
    final value = ref.watch(conversationsProvider);
    return AppPage(
      title: context.l10n.chatListTitle,
      scrollable: false,
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(context.l10n.chatOnlyFriends, style: TextStyle(color: p.muted)),
          const SizedBox(height: 14),
          Expanded(
            child: value.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (_, _) => Center(
                child: PaperButton(
                  label: context.l10n.loadFailedRetry,
                  onPressed: () => ref.invalidate(conversationsProvider),
                ),
              ),
              data: (items) => PaperSheet(
                child: items.isEmpty
                    ? Center(
                        child: Padding(
                          padding: const EdgeInsets.all(24),
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              AlbumIcon(
                                AlbumIcons.chat,
                                size: 40,
                                color: p.muted,
                              ),
                              const SizedBox(height: 10),
                              Text(
                                context.l10n.chatEmpty,
                                textAlign: TextAlign.center,
                                style: TextStyle(color: p.muted),
                              ),
                            ],
                          ),
                        ),
                      )
                    : ListView.separated(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 6,
                        ),
                        itemCount: items.length,
                        separatorBuilder: (_, _) => const DashedDivider(),
                        itemBuilder: (_, index) => _ConversationTile(
                          conversation: items[index],
                          index: index,
                        ),
                      ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ConversationTile extends ConsumerWidget {
  const _ConversationTile({required this.conversation, required this.index});
  final Conversation conversation;
  final int index;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = context.album;
    final profile = ref.watch(chatProfileProvider(conversation.withPublicId));
    final person = profile.value;
    // Đang tải hồ sơ thì để trống tên; lỗi thì hiện mã như cũ.
    final alias =
        person?.displayAlias ??
        (profile.hasError ? conversation.withPublicId : '');
    final unread = conversation.unread;
    return InkWell(
      onTap: () => context.push('/chat/${conversation.id}'),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 4),
        child: Row(
          children: [
            MiniPolaroidAvatar(
              avatar: person?.avatar,
              leadAnimal: person?.leadAnimal,
              index: index,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    alias,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontWeight: FontWeight.w700,
                      fontSize: 14,
                    ),
                  ),
                  Text(
                    conversation.lastMessageAt == null
                        ? context.l10n.chatStart
                        : context.l10n.chatRecent,
                    style: TextStyle(color: p.muted, fontSize: 12),
                  ),
                ],
              ),
            ),
            if (unread > 0) ...[
              const SizedBox(width: 8),
              // Con dấu tròn son ghi số tin chưa đọc.
              Semantics(
                container: true,
                label: context.l10n.unreadCount(unread),
                excludeSemantics: true,
                child: Container(
                  constraints: const BoxConstraints(
                    minWidth: 26,
                    minHeight: 26,
                  ),
                  padding: const EdgeInsets.symmetric(horizontal: 6),
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: p.accent,
                    borderRadius: BorderRadius.circular(13),
                    border: Border.all(color: p.accentDeep, width: 1.5),
                  ),
                  child: Text(
                    '$unread',
                    style: const TextStyle(
                      color: Color(0xFFFFFFFF),
                      fontWeight: FontWeight.w700,
                      fontSize: 12,
                    ),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
