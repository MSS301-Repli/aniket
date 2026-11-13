import 'package:animal_locket/core/l10n/locale_controller.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/app_page.dart';
import 'package:animal_locket/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Tên mỗi ngôn ngữ luôn viết bằng chính ngôn ngữ đó để ai cũng tìm thấy.
const _languageNames = {'vi': 'Tiếng Việt', 'en': 'English'};

/// Chọn ngôn ngữ app (Việt / Anh); bấm là áp dụng ngay và lưu trên máy.
class LanguagePicker extends ConsumerWidget {
  const LanguagePicker({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final current = ref.watch(appLocaleProvider);
    final p = context.album;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionLabel(AppLocalizations.of(context)!.languageSection),
        const SizedBox(height: 10),
        Row(
          children: [
            for (final (i, locale) in supportedLanguages.indexed) ...[
              if (i > 0) const SizedBox(width: 12),
              Expanded(
                child: MergeSemantics(
                  child: Semantics(
                    button: true,
                    selected: locale == current,
                    child: InkWell(
                      onTap: () =>
                          ref.read(appLocaleProvider.notifier).select(locale),
                      borderRadius: BorderRadius.circular(14),
                      child: Ink(
                        padding: const EdgeInsets.symmetric(
                          vertical: 16,
                          horizontal: 12,
                        ),
                        decoration: BoxDecoration(
                          color: p.paper,
                          borderRadius: BorderRadius.circular(14),
                          border: Border.all(
                            color: locale == current ? p.accent : p.fold1,
                            width: locale == current ? 2 : 1,
                          ),
                        ),
                        child: Center(
                          child: Text(
                            _languageNames[locale.languageCode]!,
                            style: TextStyle(
                              fontWeight: FontWeight.w600,
                              color: locale == current ? p.accent : p.ink,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ],
        ),
      ],
    );
  }
}
