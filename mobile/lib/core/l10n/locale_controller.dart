import 'package:animal_locket/core/l10n/tr.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Ngôn ngữ app hỗ trợ; tiếng Việt là mặc định.
const supportedLanguages = [Locale('vi'), Locale('en')];

Locale? parseLanguage(String? code) {
  for (final locale in supportedLanguages) {
    if (locale.languageCode == code) return locale;
  }
  return null;
}

/// Nơi lưu ngôn ngữ đã chọn trên máy (không cần backend).
abstract interface class LocaleStore {
  Future<Locale?> read();
  Future<void> write(Locale locale);
}

class SecureLocaleStore implements LocaleStore {
  const SecureLocaleStore(this._storage);
  static const _key = 'app_language';
  final FlutterSecureStorage _storage;

  @override
  Future<Locale?> read() async => parseLanguage(await _storage.read(key: _key));

  @override
  Future<void> write(Locale locale) =>
      _storage.write(key: _key, value: locale.languageCode);
}

class MemoryLocaleStore implements LocaleStore {
  MemoryLocaleStore([this._value]);
  Locale? _value;

  @override
  Future<Locale?> read() async => _value;

  @override
  Future<void> write(Locale locale) async => _value = locale;
}

final localeStoreProvider = Provider<LocaleStore>(
  (ref) => const SecureLocaleStore(FlutterSecureStorage()),
);

/// Ngôn ngữ đọc sẵn trong main() trước runApp; null = chưa đọc được.
final initialLocaleProvider = Provider<Locale?>((ref) => null);

class LocaleController extends Notifier<Locale> {
  bool _userPicked = false;

  @override
  Locale build() {
    final initial = ref.read(initialLocaleProvider);
    if (initial != null) {
      setTrLocale(initial);
      return initial;
    }
    _restore();
    return supportedLanguages.first;
  }

  void _apply(Locale locale) {
    setTrLocale(locale);
    state = locale;
  }

  Future<void> _restore() async {
    try {
      final saved = await ref.read(localeStoreProvider).read();
      if (saved != null && !_userPicked) _apply(saved);
    } catch (_) {
      // Không đọc được: giữ tiếng Việt.
    }
  }

  Future<void> select(Locale locale) async {
    _userPicked = true;
    _apply(locale);
    try {
      await ref.read(localeStoreProvider).write(locale);
    } catch (_) {
      // Không lưu được thì vẫn áp dụng cho phiên này.
    }
  }
}

final appLocaleProvider = NotifierProvider<LocaleController, Locale>(
  LocaleController.new,
);
