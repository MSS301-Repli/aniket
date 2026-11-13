import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/app_theme.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/paper_list.dart';
import 'package:animal_locket/core/widgets/album/paper_tile_button.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:animal_locket/features/chat/domain/glyph_recognizer.dart';
import 'package:animal_locket/features/chat/presentation/chat_controllers.dart';
import 'package:animal_locket/features/chat/presentation/glyph_controllers.dart';
import 'package:animal_locket/features/chat/presentation/widgets/glyph_drawing_pad.dart';
import 'package:animal_locket/features/chat/presentation/widgets/glyph_token.dart';
import 'package:animal_locket/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class GlyphComposerPanel extends ConsumerStatefulWidget {
  const GlyphComposerPanel({
    super.key,
    required this.conversationId,
    required this.enabled,
  });

  final String conversationId;
  final bool enabled;

  @override
  ConsumerState<GlyphComposerPanel> createState() => _GlyphComposerPanelState();
}

class _GlyphComposerPanelState extends ConsumerState<GlyphComposerPanel> {
  final _recognizer = const GlyphRecognizer();
  GlyphRecognition? _recognition;
  List<GlyphPoint> _stroke = const [];

  void _recognize(List<GlyphPoint> stroke, List<GlyphDefinition> dictionary) {
    setState(() {
      _stroke = stroke;
      _recognition = stroke.length < 5
          ? null
          : _recognizer.recognize(stroke, dictionary);
    });
  }

  Future<void> _send(List<GlyphDefinition> selected) async {
    if (selected.isEmpty) return;
    final composer = ref.read(
      glyphComposerProvider(widget.conversationId).notifier,
    );
    composer.setBusy(true);
    try {
      final sent = await ref
          .read(conversationControllerProvider(widget.conversationId).notifier)
          .sendGlyphs(selected);
      if (!mounted) return;
      if (sent) {
        composer.clear();
      } else {
        composer.setBusy(false);
      }
    } catch (_) {
      if (mounted) composer.setBusy(false);
    }
  }

  Future<void> _openBuilder(List<GlyphDefinition> dictionary) async {
    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) =>
          _GlyphBuilderSheet(initialStroke: _stroke, dictionary: dictionary),
    );
    if (saved == true) {
      ref.invalidate(personalGlyphsProvider);
      ref.invalidate(conversationGlyphsProvider(widget.conversationId));
    }
  }

  Future<void> _openDictionary(List<GlyphDefinition> dictionary) =>
      showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        useSafeArea: true,
        builder: (_) => _GlyphDictionarySheet(
          conversationId: widget.conversationId,
          glyphs: dictionary,
        ),
      );

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final p = context.album;
    final dictionary = ref.watch(
      conversationGlyphsProvider(widget.conversationId),
    );
    final composer = ref.watch(glyphComposerProvider(widget.conversationId));
    final notifier = ref.read(
      glyphComposerProvider(widget.conversationId).notifier,
    );
    final canEdit = widget.enabled && !composer.busy;
    // Tờ giấy đặt dưới đáy, mép trên răng cưa như giấy xé (spec §7.6).
    return ClipPath(
      clipper: const TornPaperClipper(),
      child: Material(
        color: p.paper,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 16, 12, 12),
          child: dictionary.when(
            loading: () => const SizedBox(
              height: 180,
              child: Center(child: CircularProgressIndicator()),
            ),
            error: (_, _) => SizedBox(
              height: 140,
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(l10n.glyphDictionaryError),
                    const SizedBox(height: 8),
                    PaperButton.tape(
                      label: l10n.retry,
                      onPressed: () => ref.invalidate(
                        conversationGlyphsProvider(widget.conversationId),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            data: (glyphs) => Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                GlyphDrawingPad(
                  semanticsLabel: l10n.glyphDrawLabel,
                  hint: l10n.glyphDrawHint,
                  clearLabel: l10n.glyphClearStroke,
                  onStrokeComplete: (stroke) => _recognize(stroke, glyphs),
                ),
                const SizedBox(height: 8),
                _RecognitionResult(
                  recognition: _recognition,
                  onAdd: notifier.add,
                ),
                if (glyphs.isEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 10),
                    child: Text(l10n.glyphDictionaryEmpty),
                  )
                else ...[
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Text(
                      l10n.glyphQuickPick,
                      style: TextStyle(
                        color: p.muted,
                        fontWeight: FontWeight.w600,
                        fontSize: 13,
                      ),
                    ),
                  ),
                  const SizedBox(height: 4),
                  SizedBox(
                    height: 60,
                    child: ListView.separated(
                      scrollDirection: Axis.horizontal,
                      padding: const EdgeInsets.symmetric(vertical: 4),
                      itemCount: glyphs.take(12).length,
                      separatorBuilder: (_, _) => const SizedBox(width: 8),
                      itemBuilder: (_, index) {
                        final glyph = glyphs[index];
                        return PaperTileButton(
                          label: glyph.meaning,
                          size: 52,
                          onPressed: canEdit ? () => notifier.add(glyph) : null,
                          child: GlyphIcon(glyph: glyph, size: 30),
                        );
                      },
                    ),
                  ),
                ],
                if (composer.selected.isEmpty)
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      child: Text(
                        l10n.glyphComposerEmpty,
                        style: TextStyle(color: p.muted, fontSize: 13),
                      ),
                    ),
                  )
                else
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 6),
                      child: Wrap(
                        spacing: 6,
                        runSpacing: 6,
                        children: [
                          for (var i = 0; i < composer.selected.length; i++)
                            GlyphToken(
                              glyph: composer.selected[i],
                              removeLabel: l10n.glyphRemoveToken(
                                composer.selected[i].meaning,
                              ),
                              onRemove: canEdit
                                  ? () => notifier.removeAt(i)
                                  : null,
                            ),
                        ],
                      ),
                    ),
                  ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    PaperTileButton(
                      icon: AlbumIcons.book,
                      label: l10n.glyphDictionary,
                      size: 48,
                      onPressed: () => _openDictionary(glyphs),
                    ),
                    const SizedBox(width: 8),
                    PaperTileButton(
                      icon: AlbumIcons.plus,
                      label: l10n.glyphCreate,
                      size: 48,
                      onPressed: widget.enabled
                          ? () => _openBuilder(glyphs)
                          : null,
                    ),
                    if (composer.selected.isNotEmpty) ...[
                      const SizedBox(width: 8),
                      PaperTileButton(
                        icon: AlbumIcons.trash,
                        label: l10n.glyphClearComposer,
                        size: 48,
                        onPressed: composer.busy ? null : notifier.clear,
                      ),
                    ],
                    const SizedBox(width: 12),
                    Expanded(
                      child: PaperButton(
                        label: l10n.glyphSend,
                        // Đang gửi: đồng hồ cát thay icon gửi.
                        icon: composer.busy
                            ? AlbumIcons.hourglass
                            : AlbumIcons.send,
                        onPressed: canEdit && composer.selected.isNotEmpty
                            ? () => _send(composer.selected)
                            : null,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _RecognitionResult extends StatelessWidget {
  const _RecognitionResult({required this.recognition, required this.onAdd});

  final GlyphRecognition? recognition;
  final ValueChanged<GlyphDefinition> onAdd;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    if (recognition == null) return const SizedBox.shrink();
    if (recognition!.ambiguous) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(l10n.glyphAmbiguous),
          const SizedBox(height: 6),
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: recognition!.alternatives
                .map(
                  (candidate) => _GlyphLabel(
                    glyph: candidate.glyph,
                    onPressed: () => onAdd(candidate.glyph),
                  ),
                )
                .toList(growable: false),
          ),
        ],
      );
    }
    if (!recognition!.matched || recognition!.best == null) {
      return Align(
        alignment: Alignment.centerLeft,
        child: Text(l10n.glyphNoMatch),
      );
    }
    final glyph = recognition!.best!;
    // Wrap: chữ to thì nút "Thêm" xuống dòng thay vì bóp chữ.
    return Wrap(
      spacing: 8,
      runSpacing: 6,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        Text(l10n.glyphMatched(glyph.meaning)),
        PaperButton.tape(
          label: l10n.glyphAddToComposer,
          icon: AlbumIcons.plus,
          onPressed: () => onAdd(glyph),
        ),
      ],
    );
  }
}

/// Nhãn giấy gồm glyph và nghĩa (lựa chọn khi nhận dạng mơ hồ).
class _GlyphLabel extends StatelessWidget {
  const _GlyphLabel({required this.glyph, required this.onPressed});
  final GlyphDefinition glyph;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    return Semantics(
      button: true,
      label: glyph.meaning,
      excludeSemantics: true,
      onTap: onPressed,
      child: Material(
        color: p.paper,
        elevation: 1,
        shadowColor: const Color(0x665A3C1E),
        borderRadius: BorderRadius.circular(3),
        child: InkWell(
          onTap: onPressed,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 48),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(6, 4, 12, 4),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  GlyphIcon(glyph: glyph, size: 28),
                  const SizedBox(width: 6),
                  Flexible(
                    child: Text(
                      glyph.meaning,
                      style: TextStyle(color: p.ink, fontSize: 13),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _GlyphBuilderSheet extends ConsumerStatefulWidget {
  const _GlyphBuilderSheet({
    required this.initialStroke,
    required this.dictionary,
    this.editing,
  });

  final List<GlyphPoint> initialStroke;
  final List<GlyphDefinition> dictionary;
  final GlyphDefinition? editing;

  @override
  ConsumerState<_GlyphBuilderSheet> createState() => _GlyphBuilderSheetState();
}

class _GlyphBuilderSheetState extends ConsumerState<_GlyphBuilderSheet> {
  late final TextEditingController _meaning;
  final _recognizer = const GlyphRecognizer();
  late List<GlyphPoint> _stroke;
  late int _color;
  var _saving = false;

  static const _palette = [
    0xFF818CF8,
    0xFF34D399,
    0xFFFBBF24,
    0xFFF472B6,
    0xFF38BDF8,
    0xFFFB923C,
    0xFFC084FC,
  ];

  @override
  void initState() {
    super.initState();
    _meaning = TextEditingController(text: widget.editing?.meaning ?? '');
    _stroke = widget.editing?.points ?? widget.initialStroke;
    _color = widget.editing?.colorArgb ?? 0xFFF472B6;
  }

  @override
  void dispose() {
    _meaning.dispose();
    super.dispose();
  }

  double get _collision => widget.dictionary
      .where((glyph) => glyph.id != widget.editing?.id)
      .fold<double>(0, (value, glyph) {
        if (_stroke.length < 5) return value;
        final similarity = _recognizer.similarity(_stroke, glyph);
        return similarity > value ? similarity : value;
      });

  Future<void> _save() async {
    if (_stroke.length < 5 || _meaning.text.trim().isEmpty) return;
    setState(() => _saving = true);
    await ref
        .read(personalGlyphsProvider.notifier)
        .save(
          meaning: _meaning.text.trim(),
          colorArgb: _color,
          points: _stroke,
          localId: widget.editing?.id,
        );
    if (mounted) Navigator.of(context).pop(true);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final p = context.album;
    UnderlineInputBorder line(Color color) =>
        UnderlineInputBorder(borderSide: BorderSide(color: color, width: 1.5));
    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        top: 16,
        bottom: 16 + MediaQuery.viewInsetsOf(context).bottom,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.glyphCreate,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 12),
            GlyphDrawingPad(
              semanticsLabel: l10n.glyphDrawLabel,
              hint: l10n.glyphDrawHint,
              clearLabel: l10n.glyphClearStroke,
              initialPoints: _stroke,
              color: Color(_color),
              onStrokeComplete: (points) => setState(() => _stroke = points),
            ),
            if (_collision >= .85)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(
                  l10n.glyphCollisionWarning,
                  style: TextStyle(color: p.danger),
                ),
              ),
            const SizedBox(height: 12),
            // Ô nghĩa như dòng kẻ viết tay.
            TextField(
              controller: _meaning,
              maxLength: 64,
              style: TextStyle(
                fontFamily: AppFonts.hand,
                fontSize: 20,
                color: p.ink,
              ),
              decoration: InputDecoration(
                labelText: l10n.glyphMeaningLabel,
                hintText: l10n.glyphMeaningHint,
                hintStyle: TextStyle(
                  fontFamily: AppFonts.hand,
                  fontSize: 20,
                  color: p.muted,
                ),
                filled: false,
                isDense: true,
                border: line(p.fold2),
                enabledBorder: line(p.fold2),
                focusedBorder: line(p.accent),
              ),
              onChanged: (_) => setState(() {}),
            ),
            Text(l10n.glyphColorLabel),
            const SizedBox(height: 6),
            // Chấm mực: màu glyph là dữ liệu người dùng nên bảng màu giữ nguyên.
            Wrap(
              spacing: 4,
              children: _palette
                  .map(
                    (color) => Semantics(
                      button: true,
                      selected: color == _color,
                      label:
                          '${l10n.glyphColorLabel} ${_palette.indexOf(color) + 1}',
                      child: InkResponse(
                        onTap: () => setState(() => _color = color),
                        radius: 24,
                        child: SizedBox.square(
                          dimension: 48,
                          child: Center(
                            child: Container(
                              width: 36,
                              height: 36,
                              decoration: BoxDecoration(
                                color: Color(color),
                                shape: BoxShape.circle,
                                border: Border.all(color: p.paper, width: 3),
                                boxShadow: [
                                  BoxShadow(
                                    color: color == _color
                                        ? p.ink
                                        : const Color(0x00000000),
                                    spreadRadius: 2,
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  )
                  .toList(growable: false),
            ),
            const SizedBox(height: 16),
            PaperButton(
              label: l10n.glyphSave,
              onPressed:
                  !_saving &&
                      _stroke.length >= 5 &&
                      _meaning.text.trim().isNotEmpty &&
                      _collision < .85
                  ? _save
                  : null,
            ),
          ],
        ),
      ),
    );
  }
}

class _GlyphDictionarySheet extends ConsumerWidget {
  const _GlyphDictionarySheet({
    required this.conversationId,
    required this.glyphs,
  });

  final String conversationId;
  final List<GlyphDefinition> glyphs;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final p = context.album;
    final groups = [
      glyphs.where((item) => item.kind == GlyphKind.system).toList(),
      glyphs.where((item) => item.kind == GlyphKind.personal).toList(),
      glyphs.where((item) => item.kind == GlyphKind.conversation).toList(),
    ];

    Future<void> edit(GlyphDefinition glyph) async {
      final saved = await showModalBottomSheet<bool>(
        context: context,
        isScrollControlled: true,
        useSafeArea: true,
        builder: (_) => _GlyphBuilderSheet(
          initialStroke: glyph.points,
          dictionary: glyphs,
          editing: glyph,
        ),
      );
      if (saved == true) {
        ref.invalidate(personalGlyphsProvider);
        ref.invalidate(conversationGlyphsProvider(conversationId));
      }
    }

    Future<void> delete(GlyphDefinition glyph) async {
      await ref.read(personalGlyphsProvider.notifier).deactivate(glyph.id);
      ref.invalidate(conversationGlyphsProvider(conversationId));
    }

    return DefaultTabController(
      length: 3,
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * .72,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 16),
                child: Text(
                  l10n.glyphDictionary,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
              ),
              PaperTabBar(
                tabs: [
                  l10n.glyphSystemTab,
                  l10n.glyphPersonalTab,
                  l10n.glyphConversationTab,
                ],
              ),
              Expanded(
                child: PaperSheet(
                  child: TabBarView(
                    children: [
                      for (final group in groups)
                        group.isEmpty
                            ? Center(
                                child: Text(
                                  l10n.glyphDictionaryEmpty,
                                  style: TextStyle(color: p.muted),
                                ),
                              )
                            : ListView.separated(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 12,
                                  vertical: 6,
                                ),
                                itemCount: group.length,
                                separatorBuilder: (_, _) =>
                                    const DashedDivider(),
                                itemBuilder: (_, index) {
                                  final glyph = group[index];
                                  final personal =
                                      glyph.kind == GlyphKind.personal;
                                  return ListTile(
                                    minTileHeight: 60,
                                    contentPadding: const EdgeInsets.symmetric(
                                      horizontal: 4,
                                    ),
                                    leading: DecoratedBox(
                                      decoration: BoxDecoration(
                                        color: p.paper,
                                        border: Border.all(color: p.fold1),
                                        boxShadow: const [
                                          BoxShadow(
                                            color: Color(0x1F5A3C1E),
                                            blurRadius: 4,
                                            offset: Offset(0, 2),
                                          ),
                                        ],
                                      ),
                                      child: GlyphIcon(glyph: glyph, size: 40),
                                    ),
                                    title: Text(
                                      glyph.meaning,
                                      style: TextStyle(color: p.ink),
                                    ),
                                    onTap: personal ? () => edit(glyph) : null,
                                    trailing: personal
                                        ? PaperTileButton(
                                            icon: AlbumIcons.trash,
                                            label: l10n.glyphDelete,
                                            size: 48,
                                            onPressed: () => delete(glyph),
                                          )
                                        : null,
                                  );
                                },
                              ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
