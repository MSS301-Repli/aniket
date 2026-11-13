import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/features/shell/presentation/washi_nav_bar.dart';
import 'package:animal_locket/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class AppShell extends StatelessWidget {
  const AppShell({super.key, required this.navigationShell});
  final StatefulNavigationShell navigationShell;

  /// Nhánh router theo thứ tự hiển thị: tab Ảnh (nhánh 0) nằm giữa thanh.
  /// Router giữ nguyên index nhánh, chỉ đổi chỗ trên thanh tab.
  static const _branchAt = [1, 2, 0, 3, 4];

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final current = navigationShell.currentIndex;
    return Scaffold(
      extendBody: true,
      body: navigationShell,
      bottomNavigationBar: WashiNavBar(
        currentIndex: _branchAt.indexOf(current),
        onSelected: (slot) {
          final branch = _branchAt[slot];
          navigationShell.goBranch(branch, initialLocation: branch == current);
        },
        items: [
          WashiNavItem(icon: AlbumIcons.feed, label: l10n.feedTab),
          WashiNavItem(icon: AlbumIcons.discover, label: l10n.discoverTab),
          WashiNavItem(icon: AlbumIcons.photos, label: l10n.photosTab),
          WashiNavItem(icon: AlbumIcons.chat, label: l10n.chatTab),
          WashiNavItem(icon: AlbumIcons.profile, label: l10n.profileTab),
        ],
      ),
    );
  }
}
