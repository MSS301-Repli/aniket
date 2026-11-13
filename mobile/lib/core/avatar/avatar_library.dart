import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

/// Mã avatar "v:animal:palette:accessory" (docs/19-avatar-mechanism.md).
/// Duy nhất toàn hệ thống: server cấp, client chỉ vẽ.
@immutable
class AvatarCode {
  const AvatarCode({
    required this.version,
    required this.animal,
    required this.palette,
    required this.accessory,
  });

  final int version;
  final String animal;
  final int palette;
  final String accessory;

  /// Khoá nhận dạng (không gồm phiên bản): "fox:0:none".
  String get key => '$animal:$palette:$accessory';

  static AvatarCode? tryParse(String? code) {
    if (code == null) return null;
    final parts = code.split(':');
    if (parts.length != 4) return null;
    final version = int.tryParse(parts[0]);
    final palette = int.tryParse(parts[2]);
    if (version == null || palette == null) return null;
    return AvatarCode(
      version: version,
      animal: parts[1],
      palette: palette,
      accessory: parts[3],
    );
  }

  @override
  String toString() => '$version:$key';

  @override
  bool operator ==(Object other) =>
      other is AvatarCode && other.toString() == toString();

  @override
  int get hashCode => toString().hashCode;
}

/// Khung một con vật: SVG có chỗ trống màu, và cách đặt riêng từng phụ kiện (transform SVG).
typedef AvatarAnimal = ({String name, String svg, Map<String, String> fit});

/// Một phiên bản thư viện: 50 khung con vật (màu là chỗ trống), bảng màu, phụ kiện.
class AvatarLibraryData {
  AvatarLibraryData._(
    this.version,
    this.viewBox,
    this.animals,
    this.palettes,
    this.accessories,
  );

  factory AvatarLibraryData.fromJson(Map<String, dynamic> json) {
    final animals = <String, AvatarAnimal>{};
    (json['animals'] as Map<String, dynamic>).forEach((k, v) {
      final m = v as Map<String, dynamic>;
      animals[k] = (
        name: m['name'] as String? ?? k,
        svg: m['svg'] as String? ?? '',
        fit: ((m['fit'] as Map<String, dynamic>?) ?? const {}).map(
          (k, v) => MapEntry(k, v as String),
        ),
      );
    });
    final accessories = <String, ({String name, String svg})>{};
    (json['accessories'] as Map<String, dynamic>).forEach((k, v) {
      final m = v as Map<String, dynamic>;
      accessories[k] = (
        name: m['name'] as String? ?? k,
        svg: m['svg'] as String? ?? '',
      );
    });
    final palettes = (json['palettes'] as List<dynamic>)
        .cast<Map<String, dynamic>>()
        .map((p) => p.map((k, v) => MapEntry(k, v as String)))
        .toList();
    return AvatarLibraryData._(
      json['version'] as int,
      json['viewBox'] as String? ?? '0 0 100 100',
      animals,
      palettes,
      accessories,
    );
  }

  final int version;
  final String viewBox;
  final Map<String, AvatarAnimal> animals;
  final List<Map<String, String>> palettes;
  final Map<String, ({String name, String svg})> accessories;

  /// Thư viện có đủ con vật, bảng màu và phụ kiện của mã này không.
  bool covers(AvatarCode code) =>
      animals.containsKey(code.animal) &&
      accessories.containsKey(code.accessory) &&
      code.palette >= 0 &&
      code.palette < palettes.length;

  static final _slot = RegExp(r'\{\{(\w+)\}\}');

  /// Hàm vẽ chuẩn — phải ra đúng chuỗi như `render()` trong libs/avatar-lib/build-lib.mjs:
  /// mọi chỗ trống `{{tên}}` lấy màu cùng tên trong bảng màu; con vật có
  /// `fit[phụ kiện]` thì phụ kiện được bọc trong `<g transform="…">`.
  String? render(AvatarCode code) {
    if (!covers(code)) return null;
    final p = palettes[code.palette];
    String fill(String svg) =>
        svg.replaceAllMapped(_slot, (m) => p[m[1]!] ?? m[0]!);
    final animal = animals[code.animal]!;
    final accessory = fill(accessories[code.accessory]!.svg);
    final fit = animal.fit[code.accessory];
    final placed = fit != null && accessory.isNotEmpty
        ? '<g transform="$fit">$accessory</g>'
        : accessory;
    return '<svg viewBox="$viewBox" xmlns="http://www.w3.org/2000/svg">'
        '<rect width="100" height="100" fill="${p['bg']}"/>'
        '${fill(animal.svg)}$placed</svg>';
  }
}

/// Kho các phiên bản thư viện đang có trên máy. App đóng gói bản mới nhất (v2 origami);
/// phiên bản khác tải một lần rồi lưu file. Mọi mã được vẽ bằng thư viện mới nhất có
/// khoá đó (docs/19 §3.2): mã đã cấp `1:fox:0:none` ra con cáo origami của v2.
class AvatarLibraries {
  AvatarLibraries._();

  static final AvatarLibraries instance = AvatarLibraries._();

  static const bundledVersions = [2];

  final Map<int, AvatarLibraryData> _libs = {};
  final Map<String, String> _svgCache = {};
  final Set<int> _fetching = {};

  /// Tăng mỗi khi có thư viện mới để widget vẽ lại.
  final ValueNotifier<int> revision = ValueNotifier(0);

  /// Tải JSON của một phiên bản từ server (gắn ở app khi đã có ApiClient).
  Future<Map<String, dynamic>> Function(int version)? remote;

  Future<void> loadBundled() async {
    for (final v in bundledVersions) {
      try {
        final raw = await rootBundle.loadString(
          'assets/avatar/avatar-lib.v$v.json',
        );
        register(jsonDecode(raw) as Map<String, dynamic>);
      } catch (e) {
        debugPrint('Không nạp được avatar-lib v$v: $e');
      }
    }
  }

  void register(Map<String, dynamic> json) {
    final lib = AvatarLibraryData.fromJson(json);
    _libs[lib.version] = lib;
    // Thư viện mới có thể vẽ lại mã đã có trong bộ nhớ đệm.
    _svgCache.clear();
    revision.value++;
  }

  /// Thư viện số lớn nhất có đủ khoá của mã, hoặc null.
  AvatarLibraryData? _libFor(AvatarCode c) {
    final newestFirst = _libs.keys.toList()..sort((a, b) => b - a);
    for (final v in newestFirst) {
      if (_libs[v]!.covers(c)) return _libs[v];
    }
    return null;
  }

  bool has(int version) => _libs.containsKey(version);

  String? animalName(String? code) {
    final c = AvatarCode.tryParse(code);
    if (c == null) return null;
    return (_libFor(c) ?? _libs[c.version] ?? _latest)?.animals[c.animal]?.name;
  }

  /// Một mã ngẫu nhiên chỉ để chạy hiệu ứng "đang quay" (kết quả thật luôn do server cấp).
  String? randomCodeForAnimation(Random random) {
    final lib = _latest;
    if (lib == null || lib.animals.isEmpty) return null;
    final animals = lib.animals.keys.toList();
    final accessories = lib.accessories.keys.toList();
    return '${lib.version}:${animals[random.nextInt(animals.length)]}:'
        '${random.nextInt(lib.palettes.length)}:${accessories[random.nextInt(accessories.length)]}';
  }

  AvatarLibraryData? get _latest =>
      _libs.isEmpty ? null : _libs[_libs.keys.reduce((a, b) => a > b ? a : b)];

  /// Chuỗi SVG của avatar, hoặc null nếu mã sai / chưa có thư viện (khi đó bắt đầu tải nền).
  String? svgFor(String? code) {
    final c = AvatarCode.tryParse(code);
    if (c == null) return null;
    final cached = _svgCache[code!];
    if (cached != null) return cached;
    final lib = _libFor(c);
    if (lib == null) {
      // Không thư viện nào trên máy có khoá này: thử tải đúng phiên bản ghi trong mã.
      if (!_libs.containsKey(c.version)) _ensure(c.version);
      return null;
    }
    final svg = lib.render(c);
    if (svg != null) _svgCache[code] = svg;
    return svg;
  }

  Future<void> _ensure(int version) async {
    if (_fetching.contains(version)) return;
    _fetching.add(version);
    try {
      final file = await _file(version);
      if (file != null && await file.exists()) {
        register(jsonDecode(await file.readAsString()) as Map<String, dynamic>);
        return;
      }
      final fetch = remote;
      if (fetch == null) return;
      final json = await fetch(version);
      register(json);
      await file?.writeAsString(jsonEncode(json));
    } catch (e) {
      debugPrint('Không tải được avatar-lib v$version: $e');
    } finally {
      _fetching.remove(version);
    }
  }

  Future<File?> _file(int version) async {
    if (kIsWeb) return null;
    try {
      final dir = await getApplicationSupportDirectory();
      return File('${dir.path}/avatar-lib.v$version.json');
    } catch (_) {
      return null;
    }
  }

  @visibleForTesting
  void resetForTest() {
    _libs.clear();
    _svgCache.clear();
  }
}
