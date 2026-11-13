import 'dart:io';

import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:path_provider/path_provider.dart';

class PreparedImage {
  const PreparedImage({
    required this.file,
    required this.contentType,
    required this.sizeBytes,
    required this.isTemporary,
  });

  final XFile file;
  final String contentType;
  final int sizeBytes;
  final bool isTemporary;

  Future<void> cleanup() async {
    if (!isTemporary) return;
    final target = File(file.path);
    if (await target.exists()) await target.delete();
  }
}

class ImagePreparationService {
  const ImagePreparationService();
  static const maxBytes = 10 * 1024 * 1024;
  static const maxEdge = 1440;

  Future<PreparedImage> prepare(XFile input) async {
    // Ảnh đã đúng loại/cỡ vẫn nén lại một lần (keepExif: false) để bỏ EXIF, nhất là toạ độ GPS.
    // Server không còn xoá EXIF, nên bước này là chỗ duy nhất làm việc đó.
    // 1440 px / q85: nhìn trên điện thoại gần như không khác 2048/q94 nhưng nhẹ ~3 lần,
    // nên nén và tải lên nhanh hơn rõ.
    final tempDir = await getTemporaryDirectory();
    for (final quality in const [85, 78, 70]) {
      final target =
          '${tempDir.path}/animal-locket-${DateTime.now().microsecondsSinceEpoch}-$quality.jpg';
      final compressed = await FlutterImageCompress.compressAndGetFile(
        input.path,
        target,
        minWidth: maxEdge,
        minHeight: maxEdge,
        quality: quality,
        format: CompressFormat.jpeg,
        keepExif: false,
      );
      if (compressed == null) continue;
      final compressedSize = await compressed.length();
      if (compressedSize <= maxBytes) {
        return PreparedImage(
          file: compressed,
          contentType: 'image/jpeg',
          sizeBytes: compressedSize,
          isTemporary: true,
        );
      }
      await File(compressed.path).delete();
    }
    throw const FileSystemException('FILE_TOO_LARGE');
  }
}
