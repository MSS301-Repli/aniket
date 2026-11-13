import 'package:animal_locket/core/config/app_config.dart';
import 'package:animal_locket/core/network/token_store.dart';
import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class SessionManager {
  SessionManager(this._config, this._tokenStore, {FlutterAppAuth? appAuth})
    : _appAuth = appAuth ?? const FlutterAppAuth();

  final AppConfig _config;
  final TokenStore _tokenStore;
  final FlutterAppAuth _appAuth;
  Future<bool>? _refreshInFlight;

  Future<bool> get hasSession async => await _tokenStore.read() != null;

  Future<String?> accessToken({bool refreshIfNeeded = true}) async {
    final bundle = await _tokenStore.read();
    if (bundle == null) return null;
    if (refreshIfNeeded && bundle.shouldRefresh && await refresh()) {
      return (await _tokenStore.read())?.accessToken;
    }
    return bundle.accessToken;
  }

  Future<bool> signIn() async {
    final response = await _appAuth.authorizeAndExchangeCode(
      AuthorizationTokenRequest(
        _config.keycloakClientId,
        _config.oauthRedirectUrl,
        issuer: _config.keycloakIssuer,
        scopes: const ['openid', 'profile', 'email', 'offline_access'],
        allowInsecureConnections: _config.keycloakIssuer.startsWith('http://'),
      ),
    );
    final accessToken = response.accessToken;
    if (accessToken == null || accessToken.isEmpty) return false;
    await _tokenStore.save(
      TokenBundle(
        accessToken: accessToken,
        refreshToken: response.refreshToken,
        idToken: response.idToken,
        expiresAt: response.accessTokenExpirationDateTime,
      ),
    );
    return true;
  }

  Future<bool> refresh() {
    final current = _refreshInFlight;
    if (current != null) return current;
    final future = _performRefresh();
    _refreshInFlight = future;
    return future.whenComplete(() => _refreshInFlight = null);
  }

  Future<bool> _performRefresh() async {
    final current = await _tokenStore.read();
    if (current?.refreshToken == null) return false;
    try {
      final response = await _appAuth.token(
        TokenRequest(
          _config.keycloakClientId,
          _config.oauthRedirectUrl,
          issuer: _config.keycloakIssuer,
          refreshToken: current!.refreshToken,
          scopes: const ['openid', 'profile', 'email', 'offline_access'],
          allowInsecureConnections: _config.keycloakIssuer.startsWith(
            'http://',
          ),
        ),
      );
      final accessToken = response.accessToken;
      if (accessToken == null || accessToken.isEmpty) return false;
      await _tokenStore.save(
        TokenBundle(
          accessToken: accessToken,
          refreshToken: response.refreshToken ?? current.refreshToken,
          idToken: response.idToken ?? current.idToken,
          expiresAt: response.accessTokenExpirationDateTime,
        ),
      );
      return true;
    } catch (_) {
      await _tokenStore.clear();
      return false;
    }
  }

  Future<void> signOut() => _tokenStore.clear();
}

final tokenStoreProvider = Provider<TokenStore>((ref) {
  return const SecureTokenStore(FlutterSecureStorage());
});

final sessionManagerProvider = Provider<SessionManager>((ref) {
  return SessionManager(
    ref.watch(appConfigProvider),
    ref.watch(tokenStoreProvider),
  );
});
