import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

/// Bản đệm trên máy cho avatar của chính người dùng (server là nguồn sự thật).
/// Ghi đè mỗi lần đồng bộ hồ sơ; cài lại app thì lấy lại từ server.
class MyAvatarStore {
  const MyAvatarStore();

  Future<File?> _file() async {
    if (kIsWeb) return null;
    try {
      final dir = await getApplicationSupportDirectory();
      return File('${dir.path}/my_avatar.json');
    } catch (_) {
      return null;
    }
  }

  Future<void> save({
    required String publicId,
    String? avatar,
    List<String>? collection,
  }) async {
    if (avatar == null) return;
    try {
      final f = await _file();
      if (f == null) return;
      final previous = await read();
      final keep = previous != null && previous['publicId'] == publicId
          ? previous['collection']
          : null;
      await f.writeAsString(
        jsonEncode({
          'publicId': publicId,
          'avatar': avatar,
          'collection': collection ?? keep ?? [avatar],
        }),
      );
    } catch (e) {
      debugPrint('Không lưu được avatar: $e');
    }
  }

  Future<Map<String, dynamic>?> read() async {
    try {
      final f = await _file();
      if (f == null || !await f.exists()) return null;
      return jsonDecode(await f.readAsString()) as Map<String, dynamic>;
    } catch (_) {
      return null;
    }
  }

  Future<void> clear() async {
    try {
      final f = await _file();
      if (f != null && await f.exists()) await f.delete();
    } catch (_) {}
  }
}
