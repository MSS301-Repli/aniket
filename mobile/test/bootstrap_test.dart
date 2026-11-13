import 'package:animal_locket/main.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('boots the Animal Locket application', (tester) async {
    await tester.pumpWidget(const AnimalLocketBootstrap());

    expect(find.text('Animal Locket'), findsOneWidget);
  });
}
