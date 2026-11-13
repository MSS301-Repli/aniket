import 'package:animal_locket/core/auth/auth_controller.dart';
import 'package:animal_locket/core/widgets/app_page.dart';
import 'package:animal_locket/features/auth/presentation/birth_consent_screen.dart';
import 'package:animal_locket/features/auth/presentation/reveal_screen.dart';
import 'package:animal_locket/features/auth/presentation/sign_in_screen.dart';
import 'package:animal_locket/features/billing/presentation/pro_screen.dart';
import 'package:animal_locket/features/camera/presentation/camera_screen.dart';
import 'package:animal_locket/features/camera/presentation/preview_screen.dart';
import 'package:animal_locket/features/chat/presentation/chat_list_screen.dart';
import 'package:animal_locket/features/chat/presentation/conversation_screen.dart';
import 'package:animal_locket/features/discovery/presentation/discovery_screen.dart';
import 'package:animal_locket/features/feed/presentation/feed_screen.dart';
import 'package:animal_locket/features/friends/presentation/friends_screen.dart';
import 'package:animal_locket/features/profile/presentation/avatar_spin_screen.dart';
import 'package:animal_locket/features/profile/presentation/profile_screen.dart';
import 'package:animal_locket/features/profile/presentation/public_profile_screen.dart';
import 'package:animal_locket/features/profile/presentation/settings_screen.dart';
import 'package:animal_locket/features/shell/presentation/app_shell.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

class _AuthRefreshNotifier extends ChangeNotifier {
  _AuthRefreshNotifier(this.state);
  AuthState state;

  void update(AuthState value) {
    state = value;
    notifyListeners();
  }
}

final appRouterProvider = Provider<GoRouter>((ref) {
  final rootNavigatorKey = GlobalKey<NavigatorState>();
  final refresh = _AuthRefreshNotifier(ref.read(authControllerProvider));
  ref.listen(authControllerProvider, (_, next) => refresh.update(next));
  ref.onDispose(refresh.dispose);

  return GoRouter(
    navigatorKey: rootNavigatorKey,
    initialLocation: '/splash',
    refreshListenable: refresh,
    redirect: (context, state) {
      final status = refresh.state.status;
      final location = state.matchedLocation;
      final isAuthRoute =
          location == '/sign-in' ||
          location == '/birth-consent' ||
          location == '/reveal' ||
          location == '/splash';
      return switch (status) {
        AuthStatus.checking => location == '/splash' ? null : '/splash',
        AuthStatus.unauthenticated =>
          location == '/sign-in' ? null : '/sign-in',
        AuthStatus.needsProfile =>
          location == '/birth-consent' || location == '/reveal'
              ? null
              : '/birth-consent',
        AuthStatus.ready => isAuthRoute ? '/camera' : null,
      };
    },
    routes: [
      GoRoute(
        path: '/splash',
        builder: (_, _) => const AppPage(
          scrollable: false,
          child: Center(child: CircularProgressIndicator()),
        ),
      ),
      GoRoute(path: '/sign-in', builder: (_, _) => const SignInScreen()),
      GoRoute(
        path: '/birth-consent',
        builder: (_, _) => const BirthConsentScreen(),
      ),
      GoRoute(
        path: '/reveal',
        builder: (_, state) => RevealScreen(
          birthDate: state.uri.queryParameters['birthDate'] ?? '',
        ),
      ),
      StatefulShellRoute.indexedStack(
        builder: (_, _, navigationShell) =>
            AppShell(navigationShell: navigationShell),
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(path: '/camera', builder: (_, _) => const CameraScreen()),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(path: '/feed', builder: (_, _) => const FeedScreen()),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/discover',
                builder: (_, _) => const DiscoveryScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(path: '/chat', builder: (_, _) => const ChatListScreen()),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/profile',
                builder: (_, _) => const ProfileScreen(),
              ),
            ],
          ),
        ],
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/preview',
        builder: (_, state) =>
            PreviewScreen(imagePath: state.uri.queryParameters['imagePath']),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/friends',
        builder: (_, _) => const FriendsScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/chat/:id',
        builder: (_, state) =>
            ConversationScreen(conversationId: state.pathParameters['id']!),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/settings',
        builder: (_, _) => const SettingsScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/pro',
        builder: (_, _) => const ProScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/avatar-spin',
        builder: (_, _) => const AvatarSpinScreen(),
      ),
      GoRoute(
        parentNavigatorKey: rootNavigatorKey,
        path: '/user/:id',
        builder: (_, state) =>
            PublicProfileScreen(publicId: state.pathParameters['id']!),
      ),
    ],
  );
});
