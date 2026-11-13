import 'package:animal_locket/core/l10n/tr.dart';

class DiscoveryCard {
  const DiscoveryCard({
    required this.publicId,
    required this.alias,
    this.leadAnimal,
    this.avatar,
    this.distanceBand,
    required this.online,
    required this.boosted,
  });
  factory DiscoveryCard.fromJson(Map<String, dynamic> json) => DiscoveryCard(
    publicId: json['publicId'] as String? ?? '',
    alias: json['alias'] as String? ?? tr.anonymousFriend,
    leadAnimal: json['leadAnimal'] as String?,
    avatar: json['avatar'] as String?,
    distanceBand: json['distanceBand'] as String?,
    online: json['online'] as bool? ?? false,
    boosted: json['boosted'] as bool? ?? false,
  );
  final String publicId;
  final String alias;
  final String? leadAnimal;
  final String? avatar;
  final String? distanceBand;
  final bool online;
  final bool boosted;
  String? get distanceLabel => switch (distanceBand) {
    'UNDER_1KM' => tr.distUnder1km,
    '1_TO_3KM' => tr.dist1to3km,
    'UNDER_3KM' => tr.distUnder3km,
    '3_TO_10KM' => tr.dist3to10km,
    'OVER_10KM' => tr.distOver10km,
    _ => null,
  };
}

class DiscoveryDeck {
  const DiscoveryDeck({
    required this.cards,
    required this.degraded,
    required this.swipesLeftToday,
  });
  final List<DiscoveryCard> cards;
  final bool degraded;
  final int swipesLeftToday;
}

class SwipeResult {
  const SwipeResult({required this.result, required this.swipesLeftToday});
  final String result;
  final int swipesLeftToday;
}

abstract interface class DiscoveryRepository {
  Future<void> updateLocation({required double lat, required double lon});
  Future<void> deleteLocation();
  Future<void> heartbeat();
  Future<DiscoveryDeck> cards({int limit = 20});
  Future<SwipeResult> swipe(String publicId, {required bool right});
  Future<void> boost();
}
