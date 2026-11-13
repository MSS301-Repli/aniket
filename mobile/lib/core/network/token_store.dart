import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class TokenBundle {
  const TokenBundle({
    required this.accessToken,
    this.refreshToken,
    this.idToken,
    this.expiresAt,
  });

  final String accessToken;
  final String? refreshToken;
  final String? idToken;
  final DateTime? expiresAt;

  bool get shouldRefresh =>
      expiresAt != null &&
      DateTime.now().isAfter(expiresAt!.subtract(const Duration(seconds: 45)));
}

abstract interface class TokenStore {
  Future<TokenBundle?> read();
  Future<void> save(TokenBundle bundle);
  Future<void> clear();
}

class SecureTokenStore implements TokenStore {
  const SecureTokenStore(this._storage);

  static const _accessKey = 'access_token';
  static const _refreshKey = 'refresh_token';
  static const _idKey = 'id_token';
  static const _expiryKey = 'access_token_expiry';
  final FlutterSecureStorage _storage;

  @override
  Future<TokenBundle?> read() async {
    final accessToken = await _storage.read(key: _accessKey);
    if (accessToken == null || accessToken.isEmpty) return null;
    final expiresAtValue = await _storage.read(key: _expiryKey);
    return TokenBundle(
      accessToken: accessToken,
      refreshToken: await _storage.read(key: _refreshKey),
      idToken: await _storage.read(key: _idKey),
      expiresAt: expiresAtValue == null
          ? null
          : DateTime.tryParse(expiresAtValue)?.toUtc(),
    );
  }

  @override
  Future<void> save(TokenBundle bundle) async {
    await _storage.write(key: _accessKey, value: bundle.accessToken);
    await _storage.write(key: _refreshKey, value: bundle.refreshToken);
    await _storage.write(key: _idKey, value: bundle.idToken);
    await _storage.write(
      key: _expiryKey,
      value: bundle.expiresAt?.toUtc().toIso8601String(),
    );
  }

  @override
  Future<void> clear() => _storage.deleteAll();
}
