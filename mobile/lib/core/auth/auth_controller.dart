import 'dart:async';

import 'package:animal_locket/core/auth/session_manager.dart';
import 'package:animal_locket/core/cache/cache_database.dart';
import 'package:animal_locket/core/config/backend_mode.dart';
import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/features/profile/data/user_repository_impl.dart';
import 'package:animal_locket/features/devices/data/push_service.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

enum AuthStatus { checking, unauthenticated, needsProfile, ready }

class AuthState {
  const AuthState({required this.status, this.profile, this.error});

  const AuthState.checking() : this(status: AuthStatus.checking);
  const AuthState.unauthenticated({Object? error})
    : this(status: AuthStatus.unauthenticated, error: error);
  const AuthState.needsProfile() : this(status: AuthStatus.needsProfile);
  const AuthState.ready(MeProfile profile)
    : this(status: AuthStatus.ready, profile: profile);

  final AuthStatus status;
  final MeProfile? profile;
  final Object? error;
}

class AuthController extends Notifier<AuthState> {
  @override
  AuthState build() {
    scheduleMicrotask(bootstrap);
    return const AuthState.checking();
  }

  Future<void> bootstrap() async {
    if (ref.read(backendModeProvider) == BackendMode.demo) {
      state = AuthState.ready(await ref.read(userRepositoryProvider).me());
      return;
    }
    final session = ref.read(sessionManagerProvider);
    if (!await session.hasSession) {
      state = const AuthState.unauthenticated();
      return;
    }
    await _resolveProfile();
  }

  Future<void> signIn() async {
    state = const AuthState.checking();
    try {
      ref.read(backendModeProvider.notifier).useLive();
      final signedIn = await ref.read(sessionManagerProvider).signIn();
      if (!signedIn) {
        state = const AuthState.unauthenticated();
        return;
      }
      await _resolveProfile();
    } catch (error, stack) {
      debugPrint('signIn thất bại: $error\n$stack');
      state = AuthState.unauthenticated(error: error);
    }
  }

  Future<void> enterDemo() async {
    ref.read(backendModeProvider.notifier).useDemo();
    state = AuthState.ready(await ref.read(userRepositoryProvider).me());
  }

  Future<MeProfile> registerProfile({
    required String birthDate,
    String? alias,
  }) {
    return ref
        .read(userRepositoryProvider)
        .register(birthDate: birthDate, alias: alias);
  }

  void finishRegistration(MeProfile profile) =>
      state = AuthState.ready(profile);

  /// Tải lại hồ sơ (ví dụ quay về từ trang thanh toán web). Lỗi mạng thì giữ nguyên trạng thái.
  Future<void> refreshProfile() async {
    if (state.status != AuthStatus.ready) return;
    try {
      state = AuthState.ready(await ref.read(userRepositoryProvider).me());
    } catch (_) {}
  }

  Future<void> signOut() async {
    await ref.read(pushServiceProvider)?.unregister();
    await ref.read(sessionManagerProvider).signOut();
    await (await ref.read(cacheDatabaseProvider.future)).clear();
    ref.read(backendModeProvider.notifier).useLive();
    state = const AuthState.unauthenticated();
  }

  Future<void> _resolveProfile() async {
    try {
      state = AuthState.ready(await ref.read(userRepositoryProvider).me());
    } on ApiProblem catch (problem) {
      if (problem.code == 'PROFILE_REQUIRED' || problem.status == 404) {
        state = const AuthState.needsProfile();
      } else if (problem.status == 401) {
        await ref.read(sessionManagerProvider).signOut();
        state = const AuthState.unauthenticated();
      } else {
        state = AuthState.unauthenticated(error: problem);
      }
    }
  }
}

final authControllerProvider = NotifierProvider<AuthController, AuthState>(
  AuthController.new,
);
