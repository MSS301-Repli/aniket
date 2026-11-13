import 'dart:async';
import 'dart:math' as math;

import 'package:animal_locket/core/auth/auth_controller.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/album/paper_list.dart';
import 'package:animal_locket/core/widgets/album/washi_photo_card.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/app_background.dart';
import 'package:animal_locket/features/chat/data/glyph_repository_impl.dart';
import 'package:animal_locket/features/chat/domain/chat_repository.dart';
import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:animal_locket/features/chat/presentation/chat_controllers.dart';
import 'package:animal_locket/features/chat/presentation/glyph_controllers.dart';
import 'package:animal_locket/features/chat/presentation/widgets/glyph_composer_panel.dart';
import 'package:animal_locket/features/chat/presentation/widgets/glyph_message_bubble.dart';
import 'package:animal_locket/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class ConversationScreen extends ConsumerStatefulWidget {
  const ConversationScreen({super.key, required this.conversationId});
  final String conversationId;
  @override
  ConsumerState<ConversationScreen> createState() => _ConversationScreenState();
}

class _ConversationScreenState extends ConsumerState<ConversationScreen>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      unawaited(
        ref
            .read(conversationGlyphsProvider(widget.conversationId).notifier)
            .refresh(),
      );
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final value = ref.watch(
      conversationControllerProvider(widget.conversationId),
    );
    final glyphs =
        ref
            .watch(conversationMessageGlyphsProvider(widget.conversationId))
            .value ??
        const <GlyphDefinition>[];
    final myId = ref.watch(authControllerProvider).profile?.publicId;
    return AppBackground(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        appBar: AppBar(
          backgroundColor: Colors.transparent,
          title: Text(l10n.conversationTitle),
        ),
        body: SafeArea(
          child: value.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (_, _) => Center(child: Text(l10n.messagesLoadError)),
            data: (data) {
              // Chat chỉ bằng ký hiệu: ẩn tin chữ/ảnh cũ (dữ liệu vẫn còn trên server).
              final messages = data.messages
                  .where((m) => m.type == ChatMessageType.glyph)
                  .toList();
              return Column(
                children: [
                  if (data.closedReason != null)
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                      child: TapedNote(
                        child: Text(
                          '${l10n.conversationClosed}: ${data.closedReason}',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: context.album.danger,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                  Expanded(
                    child: messages.isEmpty
                        ? Center(
                            child: Text(
                              l10n.glyphComposerEmpty,
                              style: TextStyle(color: context.album.muted),
                            ),
                          )
                        : ListView.builder(
                            padding: const EdgeInsets.fromLTRB(16, 18, 16, 8),
                            itemCount: messages.length,
                            itemBuilder: (_, index) => _MessageBubble(
                              message: messages[index],
                              mine: messages[index].senderId == myId,
                              index: index,
                              dictionary: glyphs,
                              onRetry: () => ref
                                  .read(
                                    conversationControllerProvider(
                                      widget.conversationId,
                                    ).notifier,
                                  )
                                  .retryMessage(messages[index]),
                            ),
                          ),
                  ),
                  if (data.actionError != null)
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      child: Text(
                        data.actionError is GlyphFeatureUnavailable
                            ? l10n.glyphFeatureWaitingBackend
                            : l10n.messageSendFailed,
                        style: TextStyle(color: context.album.danger),
                      ),
                    ),
                  GlyphComposerPanel(
                    conversationId: widget.conversationId,
                    enabled: data.closedReason == null,
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

/// Một tin nhắn là mẩu giấy ghi chú dán washi, nghiêng xen kẽ (spec §7.6).
class _MessageBubble extends StatelessWidget {
  const _MessageBubble({
    required this.message,
    required this.mine,
    required this.index,
    required this.dictionary,
    required this.onRetry,
  });
  final ChatMessage message;
  final bool mine;
  final int index;
  final List<GlyphDefinition> dictionary;
  final VoidCallback onRetry;
  @override
  Widget build(BuildContext context) {
    final p = context.album;
    final l10n = AppLocalizations.of(context)!;
    return Align(
      alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
      child: Opacity(
        opacity: message.pending ? .65 : 1,
        child: Transform.rotate(
          angle: (index.isEven ? -1 : 1) * math.pi / 180,
          child: Padding(
            padding: const EdgeInsets.only(top: 8, bottom: 14),
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                Container(
                  constraints: const BoxConstraints(maxWidth: 280),
                  padding: const EdgeInsets.fromLTRB(14, 14, 10, 4),
                  decoration: BoxDecoration(
                    color: mine ? p.accentSoft : p.paper,
                    borderRadius: BorderRadius.circular(3),
                    border: message.failed
                        ? Border.all(color: p.danger, width: 1.5)
                        : null,
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x265A3C1E),
                        blurRadius: 6,
                        offset: Offset(0, 2),
                      ),
                    ],
                  ),
                  child: Column(
                    crossAxisAlignment: mine
                        ? CrossAxisAlignment.end
                        : CrossAxisAlignment.start,
                    children: [
                      GlyphMessageBubble(
                        message: message,
                        dictionary: dictionary,
                        mine: mine,
                      ),
                      if (message.failed)
                        TextButton.icon(
                          style: TextButton.styleFrom(
                            foregroundColor: p.danger,
                            minimumSize: const Size(48, 48),
                          ),
                          onPressed: onRetry,
                          icon: AlbumIcon(
                            AlbumIcons.refresh,
                            size: 18,
                            color: p.danger,
                          ),
                          label: Text(l10n.retryMessage),
                        ),
                    ],
                  ),
                ),
                Positioned(
                  top: -7,
                  left: mine ? null : 18,
                  right: mine ? 18 : null,
                  child: _Tape(angle: index.isEven ? -6 : 5),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Tape extends StatelessWidget {
  const _Tape({required this.angle});
  final double angle;
  @override
  Widget build(BuildContext context) {
    final p = context.album;
    return Transform.rotate(
      angle: angle * math.pi / 180,
      child: Opacity(
        opacity: .85,
        child: CustomPaint(
          size: const Size(42, 14),
          painter: WashiTapePainter(p.tape1, p.tape2),
        ),
      ),
    );
  }
}
