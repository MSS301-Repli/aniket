import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/network/api_client.dart';
import 'package:animal_locket/features/billing/domain/billing_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class LiveBillingRepository implements BillingRepository {
  const LiveBillingRepository(this._api);
  final ApiClient _api;
  @override
  Future<List<BillingProduct>> products() async =>
      ((await _api.get<List<dynamic>>('/billing/products')).data ?? const [])
          .cast<Map<String, dynamic>>()
          .map(BillingProduct.fromJson)
          .toList();
  @override
  Future<BillingMe> me() async => BillingMe.fromJson(
    (await _api.get<Map<String, dynamic>>('/billing/me')).data ?? const {},
  );
  @override
  Future<CheckoutLink> createCheckout(String sku) async =>
      CheckoutLink.fromJson(
        (await _api.post<Map<String, dynamic>>(
          '/billing/checkout-sessions',
          data: {'sku': sku},
        )).data!,
      );
  @override
  Future<MeProfile> verify({
    required String store,
    required String sku,
    required String token,
  }) async => MeProfile.fromJson(
    (await _api.post<Map<String, dynamic>>(
      '/billing/purchases/verify',
      data: {'store': store, 'sku': sku, 'token': token},
    )).data!,
  );
}

final billingRepositoryProvider = Provider<BillingRepository>(
  (ref) => LiveBillingRepository(ref.watch(apiClientProvider)),
);
