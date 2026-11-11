import 'package:animal_locket/app.dart';
import 'package:animal_locket/core/auth/session_manager.dart';
import 'package:animal_locket/core/network/token_store.dart';
import 'package:animal_locket/core/theme/theme_controller.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('shows the Vietnamese sign in screen', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          tokenStoreProvider.overrideWithValue(_MemoryTokenStore()),
          themeStoreProvider.overrideWithValue(MemoryThemeStore()),
        ],
        child: const AnimalLocketApp(),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('Đăng nhập bằng email'), findsOneWidget);
    expect(find.text('Mở tài khoản demo'), findsOneWidget);
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
