import 'dart:convert';
import 'dart:io';

import 'package:animal_locket/core/auth/auth_controller.dart';
import 'package:animal_locket/core/avatar/avatar_library.dart';
import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/theme/theme_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// AuthController giả cho các test màn vào app: ghi lại lời gọi, không chạm mạng.
class FakeAuth extends AuthController {
  FakeAuth(this.initial, {this.register});
  final AuthState initial;
  final Future<MeProfile> Function()? register;
  final calls = <String>[];

  @override
  AuthState build() => initial;

  @override
  Future<void> signIn() async => calls.add('signIn');

  @override
  Future<void> enterDemo() async => calls.add('enterDemo');

  @override
  Future<MeProfile> registerProfile({
    required String birthDate,
    String? alias,
  }) {
    calls.add('register $birthDate $alias');
    return register!();
  }

  @override
  void finishRegistration(MeProfile profile) => calls.add('finish');
}

/// Nạp thư viện avatar v2 đóng gói (test không đọc được rootBundle trước khi chạy app).
void loadAvatarLibrary() {
  AvatarLibraries.instance.resetForTest();
  AvatarLibraries.instance.register(
    jsonDecode(File('assets/avatar/avatar-lib.v2.json').readAsStringSync())
        as Map<String, dynamic>,
  );
}

Future<void> pumpOnboarding(
  WidgetTester tester,
  Widget screen,
  FakeAuth auth, {
  double width = 390,
  double height = 844,
  double textScale = 1,
}) async {
  tester.view.physicalSize = Size(width, height);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        themeStoreProvider.overrideWithValue(MemoryThemeStore()),
        authControllerProvider.overrideWith(() => auth),
      ],
      child: MaterialApp(
        theme: AppTheme.forPalette(AlbumPalette.of(AlbumThemeId.shu)),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: screen,
      ),
    ),
  );
  await tester.pump();
}
