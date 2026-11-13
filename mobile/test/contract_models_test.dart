import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/core/network/api_problem.dart';
import 'package:animal_locket/features/camera/domain/media_repository.dart';
import 'package:animal_locket/features/chat/domain/chat_repository.dart';
import 'package:animal_locket/features/feed/domain/post_repository.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('contract DTO parsing', () {
    test('maps nullable user fields and UTC timestamps', () {
      final profile = MeProfile.fromJson({
        'publicId': 'abc',
        'alias': null,
        'leadAnimal': null,
        'ageGroup': 'MINOR',
        'discoverable': true,
        'plan': 'FREE',
        'createdAt': '2026-01-02T03:04:05+07:00',
      });
      expect(profile.ageGroup, AgeGroup.minor);
      expect(profile.alias, isNull);
      expect(profile.createdAt.isUtc, isTrue);
      expect(profile.createdAt.hour, 20);
      // Server cũ chưa trả avatarSpins: coi như không còn lượt, không được crash.
      expect(profile.avatarSpins, 0);
    });

    test('reads the avatar spin balance and spin result', () {
      expect(
        MeProfile.fromJson({
          'publicId': 'abc',
          'ageGroup': 'ADULT',
          'plan': 'PRO',
          'avatarSpins': 2,
        }).avatarSpins,
        2,
      );
      final spin = AvatarSpinResult.fromJson({
        'avatar': '1:panda:3:bow',
        'remaining': 1,
      });
      expect(spin.avatar, '1:panda:3:bow');
      expect(spin.remaining, 1);
    });

    test('maps unknown enum values without crashing', () {
      expect(
        Animal.fromJson({'rarity': 'LEGENDARY'}).rarity,
        AnimalRarity.unknown,
      );
      expect(
        MediaState.fromJson({'status': 'NEW', 'mediaId': 'm'}).status,
        MediaStatus.unknown,
      );
      expect(
        Post.fromJson({'id': 'p', 'author': {}, 'status': 'NEW'}).status,
        PostStatus.unknown,
      );
    });

    test('chat message accepts nullable text and media', () {
      final message = ChatMessage.fromJson({
        'id': '1',
        'conversationId': 'c',
        'senderId': 'u',
        'type': 'IMAGE',
        'text': null,
        'mediaId': 'm',
        'createdAt': '2026-01-01T00:00:00Z',
      });
      expect(message.type, ChatMessageType.image);
      expect(message.text, isNull);
      expect(message.mediaId, 'm');
    });

    test('chat message maps glyph ids and fallback text', () {
      final message = ChatMessage.fromJson({
        'id': 'g1',
        'conversationId': 'c',
        'senderId': 'u',
        'type': 'GLYPH',
        'text': 'Đồng ý · Đang tới',
        'glyphIds': ['sys-ok', 'sys-coming'],
        'dictionaryRevision': 3,
        'createdAt': '2026-01-01T00:00:00Z',
      });
      expect(message.type, ChatMessageType.glyph);
      expect(message.glyphIds, ['sys-ok', 'sys-coming']);
      expect(message.dictionaryRevision, 3);
      expect(message.text, contains('Đồng ý'));
    });
  });

  test('RFC 7807 problem keeps code, status and retry-after', () {
    final request = RequestOptions(path: '/feed');
    final problem = ApiProblem.fromDioException(
      DioException(
        requestOptions: request,
        response: Response<dynamic>(
          requestOptions: request,
          statusCode: 429,
          data: {'code': 'SWIPE_LIMIT'},
          headers: Headers.fromMap({
            'retry-after': ['30'],
          }),
        ),
        type: DioExceptionType.badResponse,
      ),
    );
    expect(problem.status, 429);
    expect(problem.code, 'SWIPE_LIMIT');
    expect(problem.retryAfter, 30);
  });

  test('media URL lease expires by its adjusted deadline', () {
    expect(
      MediaUrlLease(
        url: 'signed',
        expiresAt: DateTime.now().toUtc().add(const Duration(seconds: 1)),
      ).isValid,
      isTrue,
    );
    expect(
      MediaUrlLease(
        url: 'signed',
        expiresAt: DateTime.now().toUtc().subtract(const Duration(seconds: 1)),
      ).isValid,
      isFalse,
    );
  });
}
