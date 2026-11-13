import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/app_background.dart';
import 'package:flutter/material.dart';

class AppPage extends StatelessWidget {
  const AppPage({
    super.key,
    required this.child,
    this.title,
    this.actions,
    this.padding = const EdgeInsets.fromLTRB(20, 12, 20, 24),
    this.scrollable = true,
    this.watermark,
  });

  final Widget child;
  final String? title;

  /// Chữ trang trí in mờ sau thanh tiêu đề, góc phải (chỉ khi có [title]).
  final String? watermark;
  final List<Widget>? actions;
  final EdgeInsets padding;
  final bool scrollable;

  @override
  Widget build(BuildContext context) {
    final body = SafeArea(
      child: scrollable
          ? SingleChildScrollView(padding: padding, child: child)
          : Padding(padding: padding, child: child),
    );
    return AppBackground(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        appBar: title == null
            ? null
            : AppBar(
                backgroundColor: Colors.transparent,
                elevation: 0,
                title: Text(title!),
                foregroundColor: context.album.ink,
                actions: actions,
                flexibleSpace: watermark == null
                    ? null
                    : ExcludeSemantics(
                        child: ClipRect(
                          child: Align(
                            alignment: Alignment.bottomRight,
                            // Lùi sang trái chừa chỗ cho nút ở góc phải.
                            child: Transform.translate(
                              offset: Offset(
                                (actions?.isNotEmpty ?? false) ? -52 : 6,
                                10,
                              ),
                              child: Text(
                                watermark!,
                                textScaler: TextScaler.noScaling,
                                style: TextStyle(
                                  fontFamily: AppFonts.jp,
                                  fontSize: 64,
                                  height: 1,
                                  color: context.album.accent.withValues(
                                    alpha: .1,
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
              ),
        body: body,
      ),
    );
  }
}

class SectionLabel extends StatelessWidget {
  const SectionLabel(this.text, {super.key});
  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text.toUpperCase(),
      style: TextStyle(
        color: context.album.accent,
        fontSize: 12,
        fontWeight: FontWeight.w700,
        letterSpacing: 1,
      ),
    );
  }
}

class InfoPill extends StatelessWidget {
  const InfoPill({super.key, required this.label, this.icon, this.color});
  final String label;
  final IconData? icon;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: color ?? context.album.paper,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: context.album.fold1),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 15, color: context.album.accent),
            const SizedBox(width: 5),
          ],
          Text(
            label,
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700),
          ),
        ],
      ),
    );
  }
}
