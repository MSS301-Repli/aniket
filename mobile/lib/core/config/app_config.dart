import 'package:flutter_riverpod/flutter_riverpod.dart';

class AppConfig {
  const AppConfig({
    required this.apiBaseUrl,
    required this.webSocketUrl,
    required this.keycloakIssuer,
    required this.keycloakClientId,
    required this.oauthRedirectUrl,
    required this.enablePush,
    required this.enableBilling,
    required this.backendTests,
  });

  factory AppConfig.fromEnvironment() {
    return const AppConfig(
      apiBaseUrl: String.fromEnvironment(
        'API_BASE_URL',
        defaultValue: 'http://api.localhost:8080',
      ),
      webSocketUrl: String.fromEnvironment(
        'WS_URL',
        defaultValue: 'ws://api.localhost:8080/ws/chat',
      ),
      keycloakIssuer: String.fromEnvironment(
        'KEYCLOAK_ISSUER',
        defaultValue: 'http://auth.localhost:8080/realms/animal-locket',
      ),
      keycloakClientId: String.fromEnvironment(
        'KEYCLOAK_CLIENT_ID',
        defaultValue: 'mobile',
      ),
      oauthRedirectUrl: String.fromEnvironment(
        'OAUTH_REDIRECT_URL',
        defaultValue: 'com.animallocket.app:/oauth2redirect',
      ),
      enablePush: bool.fromEnvironment('ENABLE_PUSH'),
      enableBilling: bool.fromEnvironment('ENABLE_BILLING'),
      backendTests: bool.fromEnvironment('BACKEND_TESTS'),
    );
  }

  final String apiBaseUrl;
  final String webSocketUrl;
  final String keycloakIssuer;
  final String keycloakClientId;
  final String oauthRedirectUrl;
  final bool enablePush;
  final bool enableBilling;
  final bool backendTests;

  String get apiV1BaseUrl =>
      '${apiBaseUrl.replaceAll(RegExp(r'/$'), '')}/api/v1';
}

final appConfigProvider = Provider<AppConfig>(
  (ref) => AppConfig.fromEnvironment(),
);
