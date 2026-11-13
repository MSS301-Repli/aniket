import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/theme/theme_controller.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  _startupThemeTests();
  ProviderContainer containerWith(ThemeStore store) {
    final c = ProviderContainer(
      overrides: [themeStoreProvider.overrideWithValue(store)],
    );
    addTearDown(c.dispose);
    return c;
  }

  // Cho phép lần đọc store bất đồng bộ trong build() chạy xong.
  Future<void> settle() => Future<void>.delayed(Duration.zero);

  test('empty store starts and stays on Shu', () async {
    final c = containerWith(MemoryThemeStore());
    expect(c.read(albumThemeProvider), AlbumThemeId.shu);
    await settle();
    expect(c.read(albumThemeProvider), AlbumThemeId.shu);
  });

  test('restores the saved theme', () async {
    final c = containerWith(MemoryThemeStore(AlbumThemeId.sakura));
    c.read(albumThemeProvider);
    await settle();
    expect(c.read(albumThemeProvider), AlbumThemeId.sakura);
  });

  test('select applies at once and persists', () async {
    final store = MemoryThemeStore();
    final c = containerWith(store);
    await c.read(albumThemeProvider.notifier).select(AlbumThemeId.sora);
    expect(c.read(albumThemeProvider), AlbumThemeId.sora);
    expect(await store.read(), AlbumThemeId.sora);
  });

  test('a failing store falls back to Shu without throwing', () async {
    final c = containerWith(_ThrowingStore());
    expect(c.read(albumThemeProvider), AlbumThemeId.shu);
    await settle();
    expect(c.read(albumThemeProvider), AlbumThemeId.shu);
    await c.read(albumThemeProvider.notifier).select(AlbumThemeId.yuzu);
    expect(c.read(albumThemeProvider), AlbumThemeId.yuzu);
  });

  test('an unknown stored value parses to null', () {
    expect(parseAlbumThemeId('neon'), isNull);
    expect(parseAlbumThemeId(null), isNull);
    expect(parseAlbumThemeId('sora'), AlbumThemeId.sora);
  });

}

class _ThrowingStore implements ThemeStore {
  @override
  Future<AlbumThemeId?> read() async =>
      throw MissingPluginException('no secure storage in tests');
  @override
  Future<void> write(AlbumThemeId id) async =>
      throw MissingPluginException('no secure storage in tests');
}

void _startupThemeTests() {
  test('a theme read before runApp is used from the very first frame', () {
    final c = ProviderContainer(
      overrides: [
        themeStoreProvider.overrideWithValue(MemoryThemeStore()),
        initialAlbumThemeProvider.overrideWithValue(AlbumThemeId.sora),
      ],
    );
    addTearDown(c.dispose);
    expect(c.read(albumThemeProvider), AlbumThemeId.sora);
  });
}
