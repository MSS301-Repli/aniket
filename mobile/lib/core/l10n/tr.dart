import 'package:animal_locket/l10n/generated/app_localizations.dart';
import 'package:flutter/widgets.dart';

Locale _trLocale = const Locale('vi');

/// Cập nhật ngôn ngữ cho [tr] (gọi từ LocaleController mỗi khi đổi ngôn ngữ).
void setTrLocale(Locale locale) => _trLocale = locale;

/// Chuỗi theo ngôn ngữ đang chọn cho code không có BuildContext (model, controller).
/// Trong widget hãy dùng `context.l10n` để được dựng lại khi đổi ngôn ngữ.
AppLocalizations get tr => lookupAppLocalizations(_trLocale);

extension L10nContext on BuildContext {
  /// Dự phòng về [tr] khi chưa có Localizations (ví dụ test không khai báo).
  AppLocalizations get l10n => AppLocalizations.of(this) ?? tr;
}
