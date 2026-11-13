import 'package:animal_locket/core/avatar/avatar_library.dart';
import 'package:animal_locket/core/l10n/locale_controller.dart';
import 'package:animal_locket/core/config/backend_mode.dart';
import 'package:animal_locket/core/network/api_client.dart';
import 'package:animal_locket/core/router/app_router.dart';
import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/theme/theme_controller.dart';
import 'package:animal_locket/features/devices/data/push_service.dart';
import 'package:animal_locket/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class AnimalLocketApp extends ConsumerWidget {
  const AnimalLocketApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    ref.watch(pushBootstrapProvider);
    ref.watch(avatarRemoteBootstrapProvider);
    return MaterialApp.router(
      debugShowCheckedModeBanner: false,
      onGenerateTitle: (context) => AppLocalizations.of(context)!.appName,
      theme: AppTheme.forPalette(
        AlbumPalette.of(ref.watch(albumThemeProvider)),
      ),
      locale: ref.watch(appLocaleProvider),
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      routerConfig: ref.watch(appRouterProvider),
    );
  }
}

/// Cho phép tải thư viện avatar phiên bản mới (app chỉ đóng gói sẵn v1).
final avatarRemoteBootstrapProvider = Provider<void>((ref) {
  if (ref.watch(backendModeProvider) == BackendMode.demo) return;
  final api = ref.watch(apiClientProvider);
  AvatarLibraries.instance.remote = (version) async =>
      (await api.get<Map<String, dynamic>>('/animals/avatar-lib/$version'))
          .data!;
});
