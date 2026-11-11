import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Nơi lưu theme đã chọn trên máy (không cần backend).
abstract interface class ThemeStore {
  Future<AlbumThemeId?> read();
  Future<void> write(AlbumThemeId id);
}

AlbumThemeId? parseAlbumThemeId(String? value) {
  for (final id in AlbumThemeId.values) {
    if (id.name == value) return id;
  }
  return null;
}

class SecureThemeStore implements ThemeStore {
  const SecureThemeStore(this._storage);
  static const _key = 'album_theme';
  final FlutterSecureStorage _storage;

  @override
  Future<AlbumThemeId?> read() async =>
      parseAlbumThemeId(await _storage.read(key: _key));

  @override
  Future<void> write(AlbumThemeId id) =>
      _storage.write(key: _key, value: id.name);
}

class MemoryThemeStore implements ThemeStore {
  MemoryThemeStore([this._value]);
  AlbumThemeId? _value;

  @override
  Future<AlbumThemeId?> read() async => _value;

  @override
  Future<void> write(AlbumThemeId id) async => _value = id;
}

final themeStoreProvider = Provider<ThemeStore>(
  (ref) => const SecureThemeStore(FlutterSecureStorage()),
);

/// Theme đọc sẵn trong main() trước runApp; null = chưa đọc được.
final initialAlbumThemeProvider = Provider<AlbumThemeId?>((ref) => null);

/// Theme đang chọn. Dùng theme đọc sẵn lúc khởi động nếu có (tránh nháy Shu);
/// nếu không, bắt đầu bằng Shu rồi thay bằng lựa chọn đã lưu.
class ThemeController extends Notifier<AlbumThemeId> {
  bool _userPicked = false;

  @override
  AlbumThemeId build() {
    final initial = ref.read(initialAlbumThemeProvider);
    if (initial != null) return initial;
    _restore();
    return AlbumThemeId.shu;
  }

  Future<void> _restore() async {
    try {
      final saved = await ref.read(themeStoreProvider).read();
      // Người dùng chọn trước khi đọc xong thì giữ lựa chọn mới.
      if (saved != null && !_userPicked) state = saved;
    } catch (_) {
      // Không đọc được (plugin thiếu, dữ liệu hỏng): giữ mặc định Shu.
    }
  }

  Future<void> select(AlbumThemeId id) async {
    _userPicked = true;
    state = id;
    try {
      await ref.read(themeStoreProvider).write(id);
    } catch (_) {
      // Không lưu được thì vẫn áp dụng cho phiên này.
    }
  }
}

final albumThemeProvider = NotifierProvider<ThemeController, AlbumThemeId>(
  ThemeController.new,
);
