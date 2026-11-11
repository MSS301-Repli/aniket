import 'package:animal_locket/core/auth/auth_controller.dart';
import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/paper_list.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/app_page.dart';
import 'package:animal_locket/features/discovery/data/discovery_repository_impl.dart';
import 'package:animal_locket/features/friends/data/friends_repository_impl.dart';
import 'package:animal_locket/features/profile/data/user_repository_impl.dart';
import 'package:animal_locket/features/profile/presentation/profile_controller.dart';
import 'package:animal_locket/features/profile/presentation/language_picker.dart';
import 'package:animal_locket/features/profile/presentation/theme_picker.dart';
import 'package:animal_locket/core/widgets/text_input_dialog.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:animal_locket/core/l10n/tr.dart';

final blockedUsersProvider = FutureProvider<List<PublicProfile>>(
  (ref) => ref.watch(safetyRepositoryProvider).blocked(),
);

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});
  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  bool? _discoverable;
  bool _busy = false;
  @override
  Widget build(BuildContext context) {
    final profile = ref.watch(profileControllerProvider).value?.profile;
    final discoverable = _discoverable ?? profile?.discoverable ?? false;
    final blocked = ref.watch(blockedUsersProvider);
    final p = context.album;
    return AppPage(
      title: context.l10n.settingsTitle,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const ThemePicker(),
          const SizedBox(height: 24),
          const LanguagePicker(),
          const SizedBox(height: 24),
          PaperSheet(
            child: SwitchListTile(
              contentPadding: const EdgeInsets.symmetric(horizontal: 16),
              value: discoverable,
              onChanged: _busy ? null : _setDiscovery,
              title: Text(
                context.l10n.allowDiscovery,
                style: TextStyle(fontWeight: FontWeight.w700),
              ),
              subtitle: Text(context.l10n.discoveryOffNote),
            ),
          ),
          const SizedBox(height: 24),
          SectionLabel(context.l10n.blockedSection),
          const SizedBox(height: 10),
          blocked.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (_, _) => Align(
              alignment: Alignment.centerLeft,
              child: PaperButton.tape(
                label: context.l10n.loadFailedRetry,
                onPressed: () => ref.invalidate(blockedUsersProvider),
              ),
            ),
            data: (items) => items.isEmpty
                ? Text(
                    context.l10n.nobodyBlocked,
                    style: TextStyle(color: p.muted),
                  )
                : PaperSheet(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 12),
                      child: Column(
                        children: [
                          for (final (i, person) in items.indexed) ...[
                            if (i > 0) const DashedDivider(),
                            Padding(
                              padding: const EdgeInsets.symmetric(vertical: 12),
                              child: Row(
                                children: [
                                  MiniPolaroidAvatar(
                                    avatar: person.avatar,
                                    leadAnimal: person.leadAnimal,
                                    index: i,
                                  ),
                                  const SizedBox(width: 12),
                                  Expanded(
                                    child: Text(
                                      person.displayAlias,
                                      style: const TextStyle(
                                        fontWeight: FontWeight.w700,
                                      ),
                                    ),
                                  ),
                                  PaperButton.tape(
                                    label: context.l10n.unblock,
                                    onPressed: () => _unblock(person.publicId),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
          ),
          const SizedBox(height: 32),
          Align(
            alignment: Alignment.center,
            child: PaperButton.tape(
              label: context.l10n.signOut,
              icon: AlbumIcons.logout,
              onPressed: _busy
                  ? null
                  : () => ref.read(authControllerProvider.notifier).signOut(),
            ),
          ),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.center,
            child: TextButton.icon(
              style: TextButton.styleFrom(foregroundColor: p.danger),
              onPressed: _busy ? null : _confirmDelete,
              icon: AlbumIcon(
                AlbumIcons.trash,
                size: 18,
                color: _busy ? p.muted : p.danger,
              ),
              label: Text(context.l10n.deleteAccount),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _setDiscovery(bool enabled) async {
    setState(() {
      _busy = true;
      _discoverable = enabled;
    });
    try {
      await ref.read(userRepositoryProvider).update(discoverable: enabled);
      if (!enabled) {
        await ref.read(discoveryRepositoryProvider).deleteLocation();
      }
      ref.invalidate(profileControllerProvider);
    } catch (_) {
      if (mounted) setState(() => _discoverable = !enabled);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _unblock(String publicId) async {
    await ref.read(safetyRepositoryProvider).unblock(publicId);
    ref.invalidate(blockedUsersProvider);
  }

  Future<void> _confirmDelete() async {
    final deleteWord = context.l10n.deleteWord;
    final first = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(context.l10n.deleteAccountQ),
        content: Text(context.l10n.deleteAccountBody),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: Text(context.l10n.keepAccount),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: context.album.danger,
            ),
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(context.l10n.continueLabel),
          ),
        ],
      ),
    );
    if (first != true || !mounted) return;
    final typed = await showDialog<String>(
      context: context,
      builder: (_) => TextInputDialog(
        title: context.l10n.finalConfirm,
        label: context.l10n.typeDeleteWord,
        confirmLabel: context.l10n.deleteForever,
      ),
    );
    final second = typed?.trim().toUpperCase() == deleteWord;
    if (!second || !mounted) return;
    setState(() => _busy = true);
    try {
      await ref.read(userRepositoryProvider).deleteAccount();
      await ref.read(authControllerProvider.notifier).signOut();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }
}
