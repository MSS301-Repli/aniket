import 'package:animal_locket/core/models/common_models.dart';

abstract interface class UserRepository {
  Future<MeProfile> me();
  Future<MeProfile> register({required String birthDate, String? alias});
  Future<MeProfile> update({String? alias, bool? discoverable});
  Future<void> deleteAccount();
  Future<PublicProfile> publicProfile(String publicId);

  /// Bộ sưu tập avatar (mỗi avatar chỉ thuộc về một người).
  Future<List<OwnedAvatar>> myAvatars();

  /// Dùng một lượt quay để lấy một avatar mới vào bộ sưu tập. Avatar đang dùng KHÔNG đổi.
  /// Hết lượt: ApiProblem mã `NO_AVATAR_SPIN`.
  Future<AvatarSpinResult> spinAvatar();

  /// Đổi avatar đang dùng sang [avatar] trong bộ sưu tập. Không sở hữu: mã `AVATAR_NOT_OWNED`.
  Future<MeProfile> setAvatar(String avatar);
}
