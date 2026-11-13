import 'dart:convert';
import 'dart:io';

import 'package:animal_locket/core/avatar/avatar_library.dart';
import 'package:animal_locket/core/widgets/animal_avatar.dart';
import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:flutter_test/flutter_test.dart';

Map<String, dynamic> _json(String path) =>
    jsonDecode(File(path).readAsStringSync()) as Map<String, dynamic>;

Set<String> _keys(AvatarLibraryData lib) => {
  for (final a in lib.animals.keys)
    for (var p = 0; p < lib.palettes.length; p++)
      for (final x in lib.accessories.keys) '$a:$p:$x',
};

void main() {
  // v1 chỉ còn ở libs/ (server và app cũ dùng); app đóng gói v2.
  final v1Json = _json('../libs/avatar-lib/avatar-lib.v1.json');
  final v2Json = _json('assets/avatar/avatar-lib.v2.json');
  final v1 = AvatarLibraryData.fromJson(v1Json);
  final v2 = AvatarLibraryData.fromJson(v2Json);

  test('thư viện v2 có 3000 tổ hợp, cùng bộ khoá với v1', () {
    expect(v2.version, 2);
    expect(_keys(v2), hasLength(3000));
    expect(_keys(v2), _keys(v1));
  });

  test('app chỉ đóng gói thư viện v2', () {
    expect(AvatarLibraries.bundledVersions, [2]);
    expect(File('assets/avatar/avatar-lib.v1.json').existsSync(), isFalse);
  });

  test('vẽ ra đúng chuỗi SVG như hàm chuẩn trong build-lib.mjs', () {
    for (final (lib, fixture) in [
      (v1, 'test/fixtures/avatar-lib.v1.samples.json'),
      (v2, 'test/fixtures/avatar-lib.v2.samples.json'),
    ]) {
      final samples = _json(fixture);
      expect(samples, isNotEmpty);
      samples.forEach((key, expected) {
        expect(
          lib.render(AvatarCode.tryParse('${lib.version}:$key')!),
          expected,
          reason: 'v${lib.version} $key',
        );
      });
    }
  });

  test('mã avatar: parse và từ chối mã sai', () {
    final c = AvatarCode.tryParse('1:fox:0:glasses')!;
    expect(c.version, 1);
    expect(c.key, 'fox:0:glasses');
    expect(c.toString(), '1:fox:0:glasses');
    expect(AvatarCode.tryParse(null), isNull);
    expect(AvatarCode.tryParse('fox:0:none'), isNull);
    expect(AvatarCode.tryParse('x:fox:0:none'), isNull);
    expect(v2.render(AvatarCode.tryParse('2:nope:0:none')!), isNull);
    expect(v2.render(AvatarCode.tryParse('2:fox:10:none')!), isNull);
  });

  group('chọn thư viện để vẽ', () {
    final libs = AvatarLibraries.instance;
    setUp(libs.resetForTest);

    test('mã đã cấp 1:… được vẽ bằng v2 mà không cần thư viện v1', () {
      libs.register(v2Json);
      expect(
        libs.svgFor('1:fox:0:none'),
        v2.render(AvatarCode.tryParse('2:fox:0:none')!),
      );
      expect(libs.has(1), isFalse);
      expect(libs.animalName('1:fox:0:none'), 'Cáo');
    });

    test('có cả v1 và v2 thì vẫn chọn v2', () {
      libs.register(v1Json);
      libs.register(v2Json);
      expect(
        libs.svgFor('1:cat:3:glasses'),
        v2.render(AvatarCode.tryParse('2:cat:3:glasses')!),
      );
    });

    test('khoá không có trong thư viện nào thì không vẽ', () {
      libs.register(v2Json);
      expect(libs.svgFor('1:nope:0:none'), isNull);
      expect(libs.svgFor('9:fox:0:hat'), isNull);
    });
  });

  group('AnimalAvatar', () {
    setUp(() {
      AvatarLibraries.instance.resetForTest();
      AvatarLibraries.instance.register(v2Json);
    });

    testWidgets('mã đã cấp thì vẽ SVG origami', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(home: AnimalAvatar(avatar: '1:fox:0:glasses')),
      );
      expect(find.byType(SvgPicture), findsOneWidget);
      expect(find.bySemanticsLabel('Ảnh đại diện: Cáo'), findsOneWidget);
    });

    testWidgets('khoá lạ thì dùng emoji', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(home: AnimalAvatar(avatar: '9:fox:0:hat')),
      );
      expect(find.byType(SvgPicture), findsNothing);
      expect(find.text('🦊'), findsOneWidget);
    });
  });
}
