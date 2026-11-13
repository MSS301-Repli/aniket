import 'dart:async';

import 'package:animal_locket/core/auth/auth_controller.dart';
import 'package:animal_locket/core/config/app_config.dart';
import 'package:animal_locket/core/config/backend_mode.dart';
import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/features/billing/data/billing_repository_impl.dart';
import 'package:animal_locket/features/billing/domain/billing_repository.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:in_app_purchase/in_app_purchase.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:animal_locket/core/l10n/tr.dart';

class BillingState {
  const BillingState({
    required this.enabled,
    this.storeEnabled = false,
    this.products = const [],
    this.details = const {},
    this.me,
    this.busy = false,
    this.message,
  });

  /// Có bán hàng không (tắt với tài khoản dưới 18 tuổi và chế độ demo).
  final bool enabled;

  /// In-App Purchase của Google Play / App Store (chỉ khi build với ENABLE_BILLING=true).
  final bool storeEnabled;
  final List<BillingProduct> products;
  final Map<String, ProductDetails> details;
  final BillingMe? me;
  final bool busy;
  final String? message;

  /// Sản phẩm mua được qua trang thanh toán web của chúng ta (/pay).
  /// Ẩn gói con vật (PACK): thuộc cơ chế con vật cũ, mua xong không hiện ở đâu (docs/19).
  List<BillingProduct> get webProducts =>
      products.where((p) => p.purchasableOnline && p.type != 'PACK').toList();

  BillingState copyWith({bool? busy, String? message, BillingMe? me}) =>
      BillingState(
        enabled: enabled,
        storeEnabled: storeEnabled,
        products: products,
        details: details,
        me: me ?? this.me,
        busy: busy ?? this.busy,
        message: message,
      );
}

class BillingController extends AsyncNotifier<BillingState> {
  StreamSubscription<List<PurchaseDetails>>? _subscription;

  @override
  Future<BillingState> build() async {
    final config = ref.watch(appConfigProvider);
    final profile = ref.watch(authControllerProvider).profile;
    if (profile?.ageGroup == AgeGroup.minor) {
      return const BillingState(enabled: false);
    }
    if (ref.watch(backendModeProvider) == BackendMode.demo) {
      return BillingState(enabled: false, message: tr.billingDemoDisabled);
    }
    final repository = ref.read(billingRepositoryProvider);
    final products = await repository.products();
    BillingMe? me;
    try {
      me = await repository.me();
    } catch (_) {}

    // Luồng chính: thanh toán web. In-App Purchase chỉ khi build bật ENABLE_BILLING.
    var details = <String, ProductDetails>{};
    var storeEnabled = false;
    String? message;
    if (config.enableBilling) {
      final store = InAppPurchase.instance;
      if (await store.isAvailable()) {
        storeEnabled = true;
        _subscription = store.purchaseStream.listen(
          _onPurchases,
          onError: (Object error) => _setMessage(tr.billingTransactionError),
        );
        ref.onDispose(() => _subscription?.cancel());
        final response = await store.queryProductDetails(
          products.map((item) => item.sku).toSet(),
        );
        details = {for (final item in response.productDetails) item.id: item};
        message = response.error?.message;
      }
    }
    return BillingState(
      enabled: true,
      storeEnabled: storeEnabled,
      products: products,
      details: details,
      me: me,
      message: message,
    );
  }

  /// Mở trang thanh toán web (/pay/{token}) bằng trình duyệt; xong sẽ quay lại app qua animallocket://checkout.
  Future<void> buyOnWeb(String sku) async {
    final current = state.value;
    if (current == null || current.busy) return;
    state = AsyncData(current.copyWith(busy: true));
    try {
      final link = await ref
          .read(billingRepositoryProvider)
          .createCheckout(sku);
      final opened = await launchUrl(
        Uri.parse(link.url),
        mode: LaunchMode.externalApplication,
      );
      _setMessage(opened ? tr.billingCheckoutOpened : tr.billingBrowserFailed);
    } on ApiProblem catch (problem) {
      _setMessage(switch (problem.code) {
        'PAYMENT_UNAVAILABLE' => tr.billingWebPaymentOff,
        'NOT_PURCHASABLE_ONLINE' => tr.billingNotOnWeb,
        _ => tr.billingSessionFailed,
      });
    } catch (_) {
      _setMessage(tr.billingSessionFailed);
    }
  }

  /// Gọi khi app quay lại foreground: tải lại gói/ví (webhook có thể đã ghi nhận thanh toán).
  Future<void> refreshAfterCheckout() async {
    final current = state.value;
    if (current == null || !current.enabled) return;
    try {
      final me = await ref.read(billingRepositoryProvider).me();
      final changed =
          me.plan != current.me?.plan ||
          !mapEquals(me.wallet, current.me?.wallet);
      state = AsyncData(
        current.copyWith(
          busy: false,
          me: me,
          message: changed ? tr.billingUpdated : current.message,
        ),
      );
      if (changed) {
        await ref.read(authControllerProvider.notifier).refreshProfile();
      }
    } catch (_) {
      state = AsyncData(
        current.copyWith(busy: false, message: current.message),
      );
    }
  }

  Future<void> buy(String sku) async {
    final current = state.value;
    final details = current?.details[sku];
    if (current == null || details == null || current.busy) return;
    state = AsyncData(current.copyWith(busy: true));
    await InAppPurchase.instance.buyNonConsumable(
      purchaseParam: PurchaseParam(productDetails: details),
    );
  }

  Future<void> restore() async {
    final current = state.value;
    if (current != null) state = AsyncData(current.copyWith(busy: true));
    await InAppPurchase.instance.restorePurchases();
  }

  void _setMessage(String message) {
    final current = state.value;
    if (current != null) {
      state = AsyncData(current.copyWith(busy: false, message: message));
    }
  }

  Future<void> _onPurchases(List<PurchaseDetails> purchases) async {
    for (final purchase in purchases) {
      if (purchase.status == PurchaseStatus.purchased ||
          purchase.status == PurchaseStatus.restored) {
        try {
          await ref
              .read(billingRepositoryProvider)
              .verify(
                store: defaultTargetPlatform == TargetPlatform.iOS
                    ? 'APPLE'
                    : 'GOOGLE',
                sku: purchase.productID,
                token: purchase.verificationData.serverVerificationData,
              );
          if (purchase.pendingCompletePurchase) {
            await InAppPurchase.instance.completePurchase(purchase);
          }
          _setMessage(tr.billingVerified);
        } catch (error) {
          _setMessage(tr.billingNotVerified);
        }
      } else if (purchase.status == PurchaseStatus.error ||
          purchase.status == PurchaseStatus.canceled) {
        _setMessage(purchase.error?.message ?? tr.billingCanceled);
      }
    }
  }
}

final billingControllerProvider =
    AsyncNotifierProvider<BillingController, BillingState>(
      BillingController.new,
    );
