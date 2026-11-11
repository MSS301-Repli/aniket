import 'package:animal_locket/core/l10n/tr.dart';

/// Thông báo theo mã lỗi của server, theo ngôn ngữ đang chọn.
Map<String, String> _messages() => {
  'PROFILE_REQUIRED': tr.errProfileRequired,
  'INVALID_ALIAS': tr.errInvalidAlias,
  'INVALID_BIRTHDATE': tr.errInvalidBirthdate,
  'ALIAS_COOLDOWN': tr.errAliasCooldown,
  'PRO_REQUIRED': tr.errProRequired,
  'ANIMAL_NOT_OWNED': tr.errAnimalNotOwned,
  'SPIN_COOLDOWN': tr.errSpinCooldown,
  'NO_AVATAR_SPIN': tr.errNoAvatarSpin,
  'AVATAR_NOT_OWNED': tr.errAvatarNotOwned,
  'AVATAR_POOL_EXHAUSTED': tr.errAvatarPoolExhausted,
  'AVATAR_BUSY': tr.errAvatarBusy,
  'COLLECTION_COMPLETE': tr.errCollectionComplete,
  'FILE_TOO_LARGE': tr.errFileTooLarge,
  'UNSUPPORTED_MEDIA_TYPE': tr.errUnsupportedMediaType,
  'UPLOAD_LIMIT': tr.errUploadLimit,
  'PHOTO_LIMIT': tr.errPhotoLimit,
  'SWIPE_LIMIT': tr.errSwipeLimit,
  'TOO_MANY_SEARCHES': tr.errTooManySearches,
  'NO_BOOST': tr.errNoBoost,
  'INVITE_LIMIT': tr.errInviteLimit,
  'REPORT_LIMIT': tr.errReportLimit,
  'CHAT_RATE_LIMIT': tr.errChatRateLimit,
  'MEDIA_NOT_READY': tr.errMediaNotReady,
  'INVALID_MESSAGE': tr.errInvalidMessage,
  'GLYPH_INVALID_POINTS': tr.errGlyphInvalidPoints,
  'GLYPH_INVALID_COLOR': tr.errGlyphInvalidColor,
  'GLYPH_DUPLICATE': tr.errGlyphDuplicate,
  'GLYPH_LIMIT': tr.errGlyphLimit,
  'GLYPH_NOT_FOUND': tr.errGlyphNotFound,
  'GLYPH_NOT_ACTIVE': tr.errGlyphNotActive,
  'GLYPH_NOT_OWNER': tr.errGlyphNotOwner,
  'GLYPH_SEQUENCE_TOO_LONG': tr.errGlyphSequenceTooLong,
  'INVALID_GLYPH_MESSAGE': tr.errInvalidGlyphMessage,
  'GLYPH_CREATE_RATE_LIMIT': tr.errGlyphCreateRateLimit,
  'RECEIPT_ALREADY_USED': tr.errReceiptAlreadyUsed,
  'STORE_UNAVAILABLE': tr.errStoreUnavailable,
  'INVALID_RECEIPT': tr.errInvalidReceipt,
  'SERVICE_UNAVAILABLE': tr.errServiceUnavailable,
  'IDENTITY_UNAVAILABLE': tr.errIdentityUnavailable,
};

String messageForApiCode(String? code, {int? status}) {
  final messages = _messages();
  if (code != null && messages.containsKey(code)) return messages[code]!;
  if (status == 429) return tr.errTooFast;
  if (status == 503) return tr.errServiceRetry;
  return tr.genericError;
}
