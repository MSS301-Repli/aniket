import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/l10n/tr.dart';

class BillingProduct {
  const BillingProduct({
    required this.sku,
    required this.type,
    required this.item,
    required this.quantity,
    this.priceVnd,
    this.priceUsdCents,
    this.periodDays,
  });
  factory BillingProduct.fromJson(Map<String, dynamic> json) => BillingProduct(
    sku: json['sku'] as String? ?? '',
    type: json['type'] as String? ?? '',
    item: json['item'] as String? ?? '',
    quantity: (json['quantity'] as num?)?.toInt() ?? 0,
    priceVnd: (json['priceVnd'] as num?)?.toInt(),
    priceUsdCents: (json['priceUsdCents'] as num?)?.toInt(),
    periodDays: (json['periodDays'] as num?)?.toInt(),
  );
  final String sku;
  final String type;
  final String item;
  final int quantity;
  final int? priceVnd;
  final int? priceUsdCents;
  final int? periodDays;

  /// Server chỉ bán qua web sản phẩm có giá (gói định kỳ còn cần số ngày), xem CheckoutService.purchasableOnline.
  bool get purchasableOnline {
    final hasPrice = (priceVnd ?? 0) > 0 || (priceUsdCents ?? 0) > 0;
    return hasPrice && (type != 'SUBSCRIPTION' || (periodDays ?? 0) > 0);
  }

  String get label => switch (item) {
    'PRO' =>
      periodDays == null
          ? tr.billingProPlan
          : tr.billingProPlanDays(periodDays!),
    'BOOST' => tr.billingBoost(quantity),
    'PRIORITY_INVITE' => tr.billingPriorityInvite(quantity),
    _ => type == 'PACK' ? tr.billingAnimalPack(item) : '$item × $quantity',
  };

  /// Giá hiển thị: ưu tiên VND (payOS), không có thì USD (PayPal).
  String? get priceLabel {
    final vnd = priceVnd;
    if (vnd != null && vnd > 0) return '${_groupThousands(vnd)} đ';
    final usd = priceUsdCents;
    if (usd != null && usd > 0) return '\$${(usd / 100).toStringAsFixed(2)}';
    return null;
  }

  static String _groupThousands(int n) {
    final s = n.toString();
    final out = StringBuffer();
    for (var i = 0; i < s.length; i++) {
      if (i > 0 && (s.length - i) % 3 == 0) out.write('.');
      out.write(s[i]);
    }
    return out.toString();
  }
}

/// Liên kết tới trang thanh toán web của chúng ta (/pay/{token}), hết hạn sau [expiresAt].
class CheckoutLink {
  const CheckoutLink({required this.url, this.expiresAt});
  factory CheckoutLink.fromJson(Map<String, dynamic> json) => CheckoutLink(
    url: json['url'] as String? ?? '',
    expiresAt: DateTime.tryParse(json['expiresAt'] as String? ?? '')?.toUtc(),
  );
  final String url;
  final DateTime? expiresAt;
}

/// GET /billing/me: gói hiện tại và số dư ví (BOOST, PRIORITY_INVITE...).
class BillingMe {
  const BillingMe({
    required this.plan,
    this.planExpiresAt,
    this.wallet = const {},
  });
  factory BillingMe.fromJson(Map<String, dynamic> json) => BillingMe(
    plan: json['plan'] as String? ?? 'FREE',
    planExpiresAt: DateTime.tryParse(json['planExpiresAt'] as String? ?? '')
        ?.toUtc(),
    wallet: ((json['wallet'] as Map?) ?? const {}).map(
      (k, v) => MapEntry(k as String, (v as num).toInt()),
    ),
  );
  final String plan;
  final DateTime? planExpiresAt;
  final Map<String, int> wallet;
}

abstract interface class BillingRepository {
  Future<List<BillingProduct>> products();
  Future<BillingMe> me();

  /// Tạo phiên thanh toán web cho [sku]; app mở [CheckoutLink.url] bằng trình duyệt.
  Future<CheckoutLink> createCheckout(String sku);
  Future<MeProfile> verify({
    required String store,
    required String sku,
    required String token,
  });
}
