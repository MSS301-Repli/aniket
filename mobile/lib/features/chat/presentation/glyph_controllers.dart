import 'dart:async';

import 'package:animal_locket/core/auth/auth_controller.dart';
import 'package:animal_locket/core/l10n/locale_controller.dart';
import 'package:animal_locket/features/chat/data/chat_realtime_service.dart';
import 'package:animal_locket/features/chat/data/glyph_repository_impl.dart';
import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

String glyphAccountId(Ref ref) =>
    ref.read(authControllerProvider).profile?.publicId ?? 'demo-account';

class PersonalGlyphsController extends AsyncNotifier<List<GlyphDefinition>> {
  @override
  Future<List<GlyphDefinition>> build() async {
    ref.watch(
      appLocaleProvider,
    ); // dịch lại nghĩa ký hiệu hệ thống khi đổi ngôn ngữ
    final accountId = glyphAccountId(ref);
    final repository = ref.watch(glyphRepositoryProvider);
    await repository.ensureSystemGlyphs();
    return repository.personalGlyphs(accountId);
  }

  Future<GlyphDefinition> save({
    required String meaning,
    required int colorArgb,
    required List<GlyphPoint> points,
    String? localId,
  }) async {
    final glyph = await ref
        .read(glyphRepositoryProvider)
        .savePersonalGlyph(
          accountPublicId: glyphAccountId(ref),
          meaning: meaning,
          colorArgb: colorArgb,
          points: points,
          localId: localId,
        );
    ref.invalidateSelf();
    return glyph;
  }

  Future<void> deactivate(String localId) async {
    await ref
        .read(glyphRepositoryProvider)
        .deactivatePersonalGlyph(glyphAccountId(ref), localId);
    ref.invalidateSelf();
  }
}

final personalGlyphsProvider =
    AsyncNotifierProvider<PersonalGlyphsController, List<GlyphDefinition>>(
      PersonalGlyphsController.new,
    );

class ConversationGlyphsController
    extends AsyncNotifier<List<GlyphDefinition>> {
  ConversationGlyphsController(this.conversationId);

  final String conversationId;
  StreamSubscription<Map<String, dynamic>>? _events;
  Future<void>? _refreshInFlight;

  @override
  Future<List<GlyphDefinition>> build() async {
    ref.watch(
      appLocaleProvider,
    ); // dịch lại nghĩa ký hiệu hệ thống khi đổi ngôn ngữ
    final repository = ref.watch(glyphRepositoryProvider);
    final accountId = glyphAccountId(ref);
    _events = ref.watch(chatRealtimeProvider).events.listen((frame) {
      final data = (frame['data'] as Map?)?.cast<String, dynamic>();
      final dictionaryChanged =
          frame['type'] == 'dictionary_changed' &&
          data?['conversationId']?.toString() == conversationId;
      final reconnected =
          frame['type'] == 'connection' && data?['state'] == 'connected';
      if (dictionaryChanged || reconnected) {
        unawaited(refresh());
      }
    });
    ref.onDispose(() => _events?.cancel());
    await repository.ensureSystemGlyphs();
    await repository.retryPendingMutations(accountId);
    await repository.syncConversation(accountId, conversationId);
    return repository.availableGlyphs(accountId, conversationId);
  }

  Future<void> refresh() async {
    final active = _refreshInFlight;
    if (active != null) {
      await active;
      return;
    }
    final work = _refresh();
    _refreshInFlight = work;
    try {
      await work;
    } finally {
      _refreshInFlight = null;
    }
  }

  Future<void> _refresh() async {
    final repository = ref.read(glyphRepositoryProvider);
    final accountId = glyphAccountId(ref);
    await repository.retryPendingMutations(accountId);
    await repository.syncConversation(accountId, conversationId);
    state = AsyncData(
      await repository.availableGlyphs(accountId, conversationId),
    );
  }

  Future<GlyphDefinition> publish(GlyphDefinition glyph) async {
    final repository = ref.read(glyphRepositoryProvider);
    final published = await repository.publishGlyph(
      accountPublicId: glyphAccountId(ref),
      conversationId: conversationId,
      personalGlyph: glyph,
    );
    await refresh();
    return published;
  }
}

final conversationGlyphsProvider =
    AsyncNotifierProvider.family<
      ConversationGlyphsController,
      List<GlyphDefinition>,
      String
    >(ConversationGlyphsController.new);

/// Glyphs used to render message bubbles (see
/// [GlyphRepositoryImpl.messageGlyphs]). Recomputed whenever the conversation
/// dictionary syncs; a failed sync still renders from the local cache.
final conversationMessageGlyphsProvider =
    FutureProvider.family<List<GlyphDefinition>, String>((ref, conversationId) {
      ref.watch(conversationGlyphsProvider(conversationId));
      return ref
          .watch(glyphRepositoryProvider)
          .messageGlyphs(glyphAccountId(ref), conversationId);
    });

class GlyphComposerState {
  const GlyphComposerState({
    this.selected = const [],
    this.busy = false,
    this.error,
  });

  final List<GlyphDefinition> selected;
  final bool busy;
  final Object? error;

  GlyphComposerState copyWith({
    List<GlyphDefinition>? selected,
    bool? busy,
    Object? error,
  }) => GlyphComposerState(
    selected: selected ?? this.selected,
    busy: busy ?? this.busy,
    error: error,
  );
}

class GlyphComposerController extends Notifier<GlyphComposerState> {
  GlyphComposerController(this.conversationId);

  final String conversationId;

  @override
  GlyphComposerState build() => const GlyphComposerState();

  void add(GlyphDefinition glyph) {
    if (state.selected.length >= 12 || state.busy) return;
    state = state.copyWith(selected: [...state.selected, glyph]);
  }

  void removeAt(int index) {
    if (state.busy || index < 0 || index >= state.selected.length) return;
    final next = [...state.selected]..removeAt(index);
    state = state.copyWith(selected: next);
  }

  void clear() => state = const GlyphComposerState();

  void setBusy(bool value) => state = state.copyWith(busy: value);

  void setError(Object error) => state = state.copyWith(error: error);
}

final glyphComposerProvider =
    NotifierProvider.family<
      GlyphComposerController,
      GlyphComposerState,
      String
    >(GlyphComposerController.new);
