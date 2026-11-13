import 'dart:math' as math;

import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/hanko_stamp.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/washi_photo_card.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/app_page.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:animal_locket/core/l10n/tr.dart';

/// Ngày sinh + đồng ý điều khoản trên một phiếu giấy dán băng (spec §7.3).
class BirthConsentScreen extends StatefulWidget {
  const BirthConsentScreen({super.key});

  @override
  State<BirthConsentScreen> createState() => _BirthConsentScreenState();
}

class _BirthConsentScreenState extends State<BirthConsentScreen> {
  DateTime? _birthDate;
  bool _accepted = false;

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final selected = await showDatePicker(
      context: context,
      initialDate: _birthDate ?? DateTime(now.year - 18),
      firstDate: DateTime(now.year - 100),
      lastDate: now,
      helpText: context.l10n.pickBirthdate,
    );
    if (selected != null) setState(() => _birthDate = selected);
  }

  void _toggleConsent() => setState(() => _accepted = !_accepted);

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    final date = _birthDate;
    return AppPage(
      title: context.l10n.safetySetupTitle,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 14),
          _TapedSlip(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SectionLabel(context.l10n.birthdateSection),
                const SizedBox(height: 10),
                Text(
                  context.l10n.birthAgeSpace,
                  style: Theme.of(context).textTheme.headlineLarge,
                ),
                const SizedBox(height: 10),
                Text(
                  context.l10n.birthPrivacy,
                  style: TextStyle(color: p.muted, height: 1.5),
                ),
                const SizedBox(height: 18),
                Semantics(
                  key: const Key('birth-date'),
                  button: true,
                  label: date == null
                      ? context.l10n.pickBirthdate
                      : DateFormat('dd/MM/yyyy').format(date),
                  excludeSemantics: true,
                  onTap: _pickDate,
                  child: InkWell(
                    onTap: _pickDate,
                    child: Column(
                      children: [
                        Row(
                          children: [
                            _DateBox(
                              context.l10n.dayLabel,
                              date == null ? '--' : _two(date.day),
                            ),
                            const SizedBox(width: 10),
                            _DateBox(
                              context.l10n.monthLabel,
                              date == null ? '--' : _two(date.month),
                            ),
                            const SizedBox(width: 10),
                            _DateBox(
                              context.l10n.yearLabel,
                              date == null ? '----' : '${date.year}',
                              flex: 7,
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        Text(
                          context.l10n.tapToPickBirthdate,
                          textAlign: TextAlign.center,
                          style: TextStyle(color: p.muted, fontSize: 12),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 26),
          // Cả câu cũng bấm được; con dấu mang nhãn cho trình đọc màn hình.
          InkWell(
            onTap: _toggleConsent,
            // Chỉ con dấu (có nhãn) là điểm dừng của trình đọc màn hình/bàn phím.
            excludeFromSemantics: true,
            canRequestFocus: false,
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Row(
                children: [
                  HankoStamp(
                    stamped: _accepted,
                    tooltip: context.l10n.termsAgree,
                    mark: AlbumIcons.check,
                    size: 38,
                    onPressed: _toggleConsent,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ExcludeSemantics(
                      child: Text(
                        context.l10n.termsAgree,
                        style: TextStyle(height: 1.45),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 28),
          PaperButton(
            label: context.l10n.continueLabel,
            trailingIcon: AlbumIcons.arrowRight,
            onPressed: date != null && _accepted
                ? () => context.go(
                    '/reveal?birthDate=${DateFormat('yyyy-MM-dd').format(date)}',
                  )
                : null,
          ),
        ],
      ),
    );
  }
}

String _two(int n) => n.toString().padLeft(2, '0');

/// Phiếu giấy nghiêng nhẹ, mẩu washi dán ở mép trên.
class _TapedSlip extends StatelessWidget {
  const _TapedSlip({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    return Transform.rotate(
      angle: -.8 * math.pi / 180,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          DecoratedBox(
            decoration: BoxDecoration(
              color: p.paper,
              boxShadow: const [
                BoxShadow(
                  color: Color(0x245A3C1E),
                  blurRadius: 14,
                  offset: Offset(0, 6),
                ),
              ],
            ),
            child: Material(
              type: MaterialType.transparency,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(18, 22, 18, 16),
                child: child,
              ),
            ),
          ),
          Positioned(
            top: -10,
            left: 0,
            right: 0,
            child: Center(
              child: Transform.rotate(
                angle: 3 * math.pi / 180,
                child: Opacity(
                  opacity: .85,
                  child: CustomPaint(
                    size: const Size(78, 22),
                    painter: WashiTapePainter(p.tape1, p.tape2),
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

/// Một ô viết tay trong phiếu (Ngày / Tháng / Năm).
class _DateBox extends StatelessWidget {
  const _DateBox(this.label, this.value, {this.flex = 5});
  final String label;
  final String value;
  final int flex;

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    return Expanded(
      flex: flex,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
        decoration: BoxDecoration(
          color: p.background,
          border: Border.all(color: p.fold2),
        ),
        child: Column(
          children: [
            Text(label, style: TextStyle(color: p.muted, fontSize: 11)),
            Text(
              value,
              maxLines: 1,
              style: TextStyle(
                fontFamily: AppFonts.hand,
                fontSize: 24,
                color: p.ink,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
