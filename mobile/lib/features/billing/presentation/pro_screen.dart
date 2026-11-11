import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/album/gold_badge.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/paper_list.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/app_page.dart';
import 'package:animal_locket/features/billing/domain/billing_repository.dart';
import 'package:animal_locket/features/billing/presentation/billing_controller.dart';
import 'package:animal_locket/features/profile/presentation/profile_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:animal_locket/core/l10n/tr.dart';
import 'package:go_router/go_router.dart';

class ProScreen extends ConsumerStatefulWidget {
  const ProScreen({super.key});

  @override
  ConsumerState<ProScreen> createState() => _ProScreenState();
}

class _ProScreenState extends ConsumerState<ProScreen> {
  // Thanh toán diễn ra trong trình duyệt; quay lại app (nút "Quay lại app" hoặc tự chuyển) thì tải lại gói/ví.
  late final AppLifecycleListener _lifecycle = AppLifecycleListener(
    onResume: () {
      ref.read(billingControllerProvider.notifier).refreshAfterCheckout();
      // Lượt quay avatar tới user-service qua Kafka nên có thể trễ vài giây; tải lại để nút quay hiện ra.
      ref.invalidate(profileControllerProvider);
    },
  );

  @override
  void initState() {
    super.initState();
    _lifecycle; // khởi tạo listener
  }

  @override
  void dispose() {
    _lifecycle.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final value = ref.watch(billingControllerProvider);
    final p = context.album;
    final controller = ref.read(billingControllerProvider.notifier);
    return AppPage(
      title: 'Animal Locket Pro',
      child: value.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(
          child: PaperButton(
            label: context.l10n.proLoadFailed,
            onPressed: () => ref.invalidate(billingControllerProvider),
          ),
        ),
        data: (data) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 8),
            const Center(child: GoldBadge(label: 'Pro', size: 104)),
            const SizedBox(height: 20),
            Text(
              context.l10n.proHeadline,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.headlineLarge,
            ),
            const SizedBox(height: 18),
            PaperSheet(
              child: Padding(
                padding: EdgeInsets.symmetric(horizontal: 12),
                child: Column(
                  children: [
                    for (final (i, (icon, text)) in [
                      (AlbumIcons.bolt, context.l10n.proPerkBoost),
                      (AlbumIcons.mail, context.l10n.proPerkInvite),
                    ].indexed) ...[
                      if (i > 0) const DashedDivider(),
                      ListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 4,
                        ),
                        leading: AlbumIcon(icon, color: p.accent),
                        title: Text(text),
                      ),
                    ],
                  ],
                ),
              ),
            ),
            if (data.me != null) ...[
              const SizedBox(height: 22),
              _CurrentPlan(me: data.me!),
            ],
            const _AvatarSpinsNote(),
            const SizedBox(height: 22),
            if (!data.enabled)
              Text(
                data.message ?? context.l10n.proMinorNotice,
                textAlign: TextAlign.center,
                style: TextStyle(color: p.muted),
              )
            else ...[
              if (data.webProducts.isEmpty)
                Text(
                  context.l10n.proNoWebProducts,
                  textAlign: TextAlign.center,
                  style: TextStyle(color: p.muted),
                ),
              for (final product in data.webProducts)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: PaperButton(
                    label: '${product.label} · ${product.priceLabel ?? ''}',
                    onPressed: data.busy
                        ? null
                        : () => controller.buyOnWeb(product.sku),
                  ),
                ),
              Text(
                context.l10n.proWebPaymentNote,
                textAlign: TextAlign.center,
                style: TextStyle(color: p.muted, fontSize: 12),
              ),
              if (data.storeEnabled) ...[
                const SizedBox(height: 22),
                for (final product in data.products)
                  if (data.details[product.sku] != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 14),
                      child: Center(
                        child: PaperButton.tape(
                          label: context.l10n.proViaStore(
                            product.label,
                            data.details[product.sku]!.price,
                          ),
                          onPressed: data.busy
                              ? null
                              : () => controller.buy(product.sku),
                        ),
                      ),
                    ),
                TextButton(
                  onPressed: data.busy ? null : controller.restore,
                  child: Text(context.l10n.proRestore),
                ),
              ],
            ],
            if (data.enabled && data.message != null)
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Text(data.message!, textAlign: TextAlign.center),
              ),
          ],
        ),
      ),
    );
  }
}

/// Lượt quay avatar tặng kèm mỗi kỳ thuê bao (docs/19 §8). Hiện ngay sau khi mua, lúc người dùng còn ở đây.
class _AvatarSpinsNote extends ConsumerWidget {
  const _AvatarSpinsNote();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final spins =
        ref.watch(profileControllerProvider).value?.profile.avatarSpins ?? 0;
    if (spins == 0) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 14),
      child: PaperButton(
        label: 'Quay avatar mới ($spins lượt)',
        icon: AlbumIcons.dice,
        onPressed: () => context.push('/avatar-spin'),
      ),
    );
  }
}

class _CurrentPlan extends StatelessWidget {
  const _CurrentPlan({required this.me});
  final BillingMe me;

  @override
  Widget build(BuildContext context) {
    final expires = me.planExpiresAt?.toLocal();
    final boosts = me.wallet['BOOST'] ?? 0;
    final invites = me.wallet['PRIORITY_INVITE'] ?? 0;
    return TapedNote(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            me.plan == 'PRO'
                ? (expires == null
                      ? context.l10n.proCurrentPro
                      : context.l10n.proCurrentProUntil(
                          '${expires.day}/${expires.month}/${expires.year}',
                        ))
                : context.l10n.proCurrentFree,
            style: const TextStyle(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 4),
          Text(
            context.l10n.proWallet(boosts, invites),
            style: TextStyle(color: context.album.muted),
          ),
        ],
      ),
    );
  }
}
