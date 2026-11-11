import 'package:animal_locket/core/avatar/avatar_library.dart';
import 'package:animal_locket/core/avatar/avatar_spin_reel.dart';
import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/core/network/error_messages.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/paper_list.dart';
import 'package:animal_locket/core/widgets/album/polaroid.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/animal_avatar.dart';
import 'package:animal_locket/core/widgets/app_page.dart';
import 'package:animal_locket/features/profile/presentation/profile_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// Quay một avatar mới bằng lượt tặng kèm gói Pro (docs/19 §8).
///
/// Server quay trước, hiệu ứng chỉ chạy trên kết quả đã có. Avatar mới vào bộ sưu tập; đổi sang nó là một bước
/// riêng người dùng tự chọn, nên avatar đang dùng không bị thay sau lưng.
class AvatarSpinScreen extends ConsumerStatefulWidget {
  const AvatarSpinScreen({super.key});

  @override
  ConsumerState<AvatarSpinScreen> createState() => _AvatarSpinScreenState();
}

class _AvatarSpinScreenState extends ConsumerState<AvatarSpinScreen> {
  final AvatarSpinReel _reel = AvatarSpinReel();
  AvatarSpinResult? _won;
  bool _busy = false;
  bool _applied = false;
  String? _error;

  @override
  void dispose() {
    _reel.dispose();
    super.dispose();
  }

  Future<void> _spin() async {
    setState(() {
      _busy = true;
      _error = null;
      _won = null;
      _applied = false;
    });
    try {
      final result = await ref
          .read(profileControllerProvider.notifier)
          .spinAvatar();
      if (!mounted) return;
      setState(() => _won = result);
      if (!MediaQuery.disableAnimationsOf(context)) {
        _reel.play(onFrame: () {
          if (mounted) setState(() {});
        });
      }
    } on ApiProblem catch (problem) {
      if (!mounted) return;
      // Lượt có thể đã bị tiêu trước khi phản hồi tới (mạng đứt). Tải lại để avatar mới hiện trong bộ sưu tập
      // thay vì để người dùng tưởng mất lượt mà không được gì.
      await _resyncQuietly();
      if (!mounted) return;
      setState(() {
        _error = messageForApiCode(problem.code, status: problem.status);
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _resyncQuietly() async {
    try {
      await ref.read(profileControllerProvider.notifier).resync();
    } catch (_) {
      // Có chủ ý bỏ qua: đây chỉ là cố gắng cứu lượt quay có thể đã bị tiêu. Không được ghi đè thông báo
      // lỗi gốc của lần quay, và lần mở lại màn hồ sơ sẽ tải lại dữ liệu.
    }
  }

  Future<void> _useWonAvatar() async {
    final won = _won;
    if (won == null) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(profileControllerProvider.notifier).setAvatar(won.avatar);
      if (mounted) setState(() => _applied = true);
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
    final libs = AvatarLibraries.instance;
    final data = ref.watch(profileControllerProvider).value;
    if (data == null) {
      // Mở trực tiếp bằng deep link: chờ hồ sơ tải xong trước khi nói còn bao nhiêu lượt.
      return const AppPage(
        title: 'Quay avatar',
        child: Center(child: CircularProgressIndicator()),
      );
    }
    final spins = data.profile.avatarSpins;
    final spinning = _reel.spinning;
    final won = _won;
    final shown = _reel.current ?? won?.avatar;
    const photo = 196.0;

    return AppPage(
      title: 'Quay avatar',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 12),
          const Center(child: SectionLabel('Một avatar không ai khác có')),
          const SizedBox(height: 26),
          Center(
            child: Polaroid(
              width: photo,
              caption: shown == null
                  ? 'ảnh chưa rửa…'
                  : (libs.animalName(shown) ?? ''),
              child: shown == null
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
                  : AnimalAvatar(avatar: shown, size: photo, square: true),
            ),
          ),
          const SizedBox(height: 26),
          Text(
            _statusTitle(spinning: spinning, won: won, spins: spins),
            style: Theme.of(context).textTheme.headlineLarge,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 8),
          Text(
            _statusDetail(spinning: spinning, won: won, spins: spins),
            textAlign: TextAlign.center,
            style: TextStyle(color: p.muted),
          ),
          if (_error != null) ...[
            const SizedBox(height: 14),
            TapedNote(child: Text(_error!, textAlign: TextAlign.center)),
          ],
          const SizedBox(height: 26),
          if (won == null)
            spins > 0
                ? PaperButton(
                    label: _busy ? 'Đang quay…' : 'Quay (còn $spins lượt)',
                    icon: AlbumIcons.dice,
                    onPressed: _busy ? null : _spin,
                  )
                : PaperButton(
                    label: 'Nhận lượt quay với gói Pro',
                    icon: AlbumIcons.bolt,
                    onPressed: () => context.push('/pro'),
                  )
          else ...[
            if (!_applied)
              PaperButton(
                label: 'Dùng avatar này',
                icon: AlbumIcons.check,
                onPressed: _busy || spinning ? null : _useWonAvatar,
              ),
            const SizedBox(height: 12),
            if (spins > 0 && !_applied)
              PaperButton.tape(
                label: 'Quay tiếp (còn $spins lượt)',
                onPressed: _busy || spinning ? null : _spin,
              ),
            const SizedBox(height: 12),
            PaperButton.tape(
              label: _applied ? 'Xong' : 'Để trong bộ sưu tập',
              onPressed: spinning ? null : () => context.pop(),
            ),
          ],
        ],
      ),
    );
  }

  String _statusTitle({
    required bool spinning,
    required AvatarSpinResult? won,
    required int spins,
  }) {
    if (spinning) return 'Đang rửa ảnh…';
    if (_applied) return 'Đã đổi avatar';
    if (won != null) return 'Của bạn rồi!';
    return spins > 0 ? 'Sẵn sàng quay?' : 'Bạn chưa có lượt quay';
  }

  String _statusDetail({
    required bool spinning,
    required AvatarSpinResult? won,
    required int spins,
  }) {
    if (spinning) return 'Đang quay…';
    if (_applied) {
      return 'Bạn bè sẽ thấy avatar mới sau vài phút.';
    }
    if (won != null) {
      final left = won.remaining;
      return 'Đã vào bộ sưu tập của bạn. '
          '${left > 0 ? 'Còn $left lượt.' : 'Hết lượt quay.'}';
    }
    return spins > 0
        ? 'Mỗi lượt cho một avatar mới, không ai khác có được nó.'
        : 'Mỗi kỳ thuê bao Pro tặng một lượt quay.';
  }
}
