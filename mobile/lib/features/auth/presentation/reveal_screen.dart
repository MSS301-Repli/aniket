import 'dart:math';

import 'package:animal_locket/core/avatar/avatar_library.dart';
import 'package:animal_locket/core/avatar/avatar_spin_reel.dart';
import 'package:animal_locket/core/auth/auth_controller.dart';
import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/core/network/error_messages.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/polaroid.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/animal_avatar.dart';
import 'package:animal_locket/core/widgets/app_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:animal_locket/core/l10n/tr.dart';

/// Nhận avatar như một tấm polaroid đang rửa (spec §7.3).
class RevealScreen extends ConsumerStatefulWidget {
  const RevealScreen({super.key, required this.birthDate});
  final String birthDate;

  @override
  ConsumerState<RevealScreen> createState() => _RevealScreenState();
}

class _RevealScreenState extends ConsumerState<RevealScreen> {
  static List<String> get aliases => [
    tr.aliasSuggestion1,
    tr.aliasSuggestion2,
    tr.aliasSuggestion3,
    tr.aliasSuggestion4,
  ];
  late final TextEditingController _alias = TextEditingController(
    text: aliases.first,
  );
  MeProfile? _profile;
  bool _busy = false;
  String? _error;

  /// Hiệu ứng quay ~1,2 giây rồi dừng ở avatar server đã cấp (kết quả có trước khi hiệu ứng chạy).
  final AvatarSpinReel _reel = AvatarSpinReel();

  @override
  void dispose() {
    _reel.dispose();
    _alias.dispose();
    super.dispose();
  }

  Future<void> _register() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final profile = await ref
          .read(authControllerProvider.notifier)
          .registerProfile(birthDate: widget.birthDate, alias: _alias.text);
      if (mounted) {
        setState(() => _profile = profile);
        // Giảm chuyển động: hiện ngay avatar thật, không quay.
        if (!MediaQuery.disableAnimationsOf(context)) {
          _reel.play(onFrame: () {
            if (mounted) setState(() {});
          });
        }
      }
    } on ApiProblem catch (problem) {
      if (mounted) {
        setState(() {
          _error = messageForApiCode(problem.code, status: problem.status);
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    final profile = _profile;
    final spinning = _reel.spinning;
    final libs = AvatarLibraries.instance;
    final animalName = libs.animalName(profile?.avatar) ?? profile?.leadAnimal;
    final shown = _reel.current ?? profile?.avatar;
    const photo = 196.0;
    return AppPage(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 20),
          Center(child: SectionLabel(context.l10n.revealSection)),
          const SizedBox(height: 30),
          Center(
            child: Polaroid(
              width: photo,
              caption: profile == null
                  ? context.l10n.photoDeveloping
                  : (libs.animalName(shown) ?? animalName ?? ''),
              child: profile == null
                  ? ColoredBox(
                      color: p.fold1,
                      child: Center(
                        child: Text(
                          '?',
                          style: TextStyle(
                            fontFamily: AppFonts.title,
                            fontWeight: FontWeight.w700,
                            fontSize: 60,
                            color: p.muted,
                          ),
                        ),
                      ),
                    )
                  : AnimalAvatar(
                      avatar: shown,
                      leadAnimal: profile.leadAnimal,
                      size: photo,
                      square: true,
                    ),
            ),
          ),
          const SizedBox(height: 26),
          Text(
            profile == null
                ? context.l10n.revealReady
                : context.l10n.revealGreeting(profile.displayAlias),
            style: Theme.of(context).textTheme.headlineLarge,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 8),
          Text(
            profile == null
                ? context.l10n.aliasNotRealName
                : spinning
                ? context.l10n.spinning
                : context.l10n.animalUniqueAvatar(
                    animalName ?? context.l10n.unknownAnimal,
                  ),
            textAlign: TextAlign.center,
            style: TextStyle(color: p.muted),
          ),
          const SizedBox(height: 26),
          if (profile == null)
            // Ô biệt danh như một dòng kẻ trên giấy, chữ viết tay.
            TextField(
              controller: _alias,
              maxLength: 20,
              style: TextStyle(
                fontFamily: AppFonts.hand,
                fontSize: 22,
                color: p.ink,
              ),
              decoration: InputDecoration(
                labelText: context.l10n.nicknameLabel,
                errorText: _error,
                filled: true,
                fillColor: p.paper,
                border: UnderlineInputBorder(
                  borderSide: BorderSide(color: p.fold2, width: 2),
                ),
                enabledBorder: UnderlineInputBorder(
                  borderSide: BorderSide(color: p.fold2, width: 2),
                ),
                focusedBorder: UnderlineInputBorder(
                  borderSide: BorderSide(color: p.accent, width: 2),
                ),
                suffixIcon: Semantics(
                  label: context.l10n.randomNickname,
                  button: true,
                  child: IconButton(
                    onPressed: () => setState(() {
                      _alias.text = aliases[Random().nextInt(aliases.length)];
                    }),
                    icon: AlbumIcon(AlbumIcons.dice, color: p.ink),
                  ),
                ),
              ),
            ),
          const SizedBox(height: 18),
          PaperButton(
            label: profile == null
                ? (_busy ? context.l10n.registering : context.l10n.spinAvatar)
                : context.l10n.enterApp,
            trailingIcon: AlbumIcons.arrowRight,
            onPressed: _busy || spinning
                ? null
                : profile == null
                ? _register
                : () => ref
                      .read(authControllerProvider.notifier)
                      .finishRegistration(profile),
          ),
        ],
      ),
    );
  }
}
