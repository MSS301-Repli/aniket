import 'package:animal_locket/app.dart';
import 'package:animal_locket/core/network/token_store.dart';
import 'package:animal_locket/core/theme/theme_controller.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('demo entry point is available', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          tokenStoreProvider.overrideWithValue(_MemoryTokenStore()),
          themeStoreProvider.overrideWithValue(MemoryThemeStore()),
        ],
        child: const AnimalLocketApp(),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Mở tài khoản demo'));
    await tester.pumpAndSettle();
  });
}

class _MemoryTokenStore implements TokenStore {
  TokenBundle? value;

  @override
  Future<void> clear() async => value = null;

  @override
  Future<TokenBundle?> read() async => value;

  @override
  Future<void> save(TokenBundle bundle) async => value = bundle;
}
