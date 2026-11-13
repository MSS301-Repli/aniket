import 'dart:math' as math;

import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/theme/theme_controller.dart';
import 'package:animal_locket/core/widgets/app_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:animal_locket/core/l10n/tr.dart';

String _themeName(BuildContext context, AlbumThemeId id) => switch (id) {
  AlbumThemeId.shu => context.l10n.themeShu,
  AlbumThemeId.yuzu => context.l10n.themeYuzu,
  AlbumThemeId.sakura => context.l10n.themeSakura,
  AlbumThemeId.sora => context.l10n.themeSora,
};

/// Chữ Hán gốc của tên theme, chỉ để trang trí.
const _themeKanji = {
  AlbumThemeId.shu: '朱',
  AlbumThemeId.yuzu: '柚',
  AlbumThemeId.sakura: '桜',
  AlbumThemeId.sora: '空',
};

/// Chọn một trong bốn bảng màu; bấm là áp dụng ngay và lưu trên máy.
class ThemePicker extends ConsumerWidget {
  const ThemePicker({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final current = ref.watch(albumThemeProvider);
    Widget tile(AlbumThemeId id) => Expanded(
      child: _ThemeTile(
        id: id,
        selected: id == current,
        onTap: () => ref.read(albumThemeProvider.notifier).select(id),
      ),
    );
    // Lưới 2×2 tự cao theo nội dung để tên theme không bị cắt khi chữ to.
    Widget row(AlbumThemeId a, AlbumThemeId b) => IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [tile(a), const SizedBox(width: 12), tile(b)],
      ),
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionLabel(context.l10n.themeSection),
        const SizedBox(height: 10),
        row(AlbumThemeId.shu, AlbumThemeId.yuzu),
        const SizedBox(height: 12),
        row(AlbumThemeId.sakura, AlbumThemeId.sora),
      ],
    );
  }
}

class _ThemeTile extends StatelessWidget {
  const _ThemeTile({
    required this.id,
    required this.selected,
    required this.onTap,
  });

  final AlbumThemeId id;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final own = AlbumPalette.of(id);
    final active = context.album;
    return MergeSemantics(
      child: Semantics(
        button: true,
        selected: selected,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(14),
          child: Stack(
            clipBehavior: Clip.none,
            children: [
              // Ink (không phải Container) để hiệu ứng gợn của InkWell hiện
              // trên nền giấy của ô.
              ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 64),
                child: Ink(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: active.paper,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(
                      color: selected ? active.accent : active.fold1,
                      width: selected ? 2 : 1,
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Row(
                        children: [
                          for (final c in [
                            own.background,
                            own.fold1,
                            own.tape1,
                            own.accent,
                          ])
                            Container(
                              width: 16,
                              height: 16,
                              margin: const EdgeInsets.only(right: 4),
                              decoration: BoxDecoration(
                                color: c,
                                borderRadius: BorderRadius.circular(4),
                                border: Border.all(color: active.fold1),
                              ),
                            ),
                          const Spacer(),
                          ExcludeSemantics(
                            child: Container(
                              width: 28,
                              height: 28,
                              alignment: Alignment.center,
                              decoration: BoxDecoration(
                                color: own.accent,
                                shape: BoxShape.circle,
                                border: Border.all(color: own.accentDeep),
                              ),
                              child: Text(
                                _themeKanji[id]!,
                                textScaler: TextScaler.noScaling,
                                style: const TextStyle(
                                  fontFamily: AppFonts.jp,
                                  fontSize: 15,
                                  height: 1,
                                  color: Colors.white,
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 6),
                      Text(
                        _themeName(context, id),
                        style: const TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              if (selected)
                Positioned(
                  top: -8,
                  right: 14,
                  child: Transform.rotate(
                    angle: 6 * math.pi / 180,
                    child: Opacity(
                      opacity: .8,
                      child: Container(width: 34, height: 14, color: own.tape1),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
