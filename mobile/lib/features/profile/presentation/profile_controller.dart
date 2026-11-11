import 'package:animal_locket/core/models/common_models.dart';
import 'package:animal_locket/features/profile/data/user_repository_impl.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class ProfileData {
  const ProfileData({required this.profile, required this.avatars});
  final MeProfile profile;

  /// Bộ sưu tập avatar; mỗi avatar chỉ thuộc về mình, không ai khác có.
  final List<OwnedAvatar> avatars;

  ProfileData copyWith({MeProfile? profile, List<OwnedAvatar>? avatars}) =>
      ProfileData(
        profile: profile ?? this.profile,
        avatars: avatars ?? this.avatars,
      );
}

class ProfileController extends AsyncNotifier<ProfileData> {
  @override
  Future<ProfileData> build() => _load();
  Future<ProfileData> _load() async {
    final repository = ref.read(userRepositoryProvider);
    final profile = await repository.me();
    final avatars = await repository.myAvatars();
    return ProfileData(profile: profile, avatars: avatars);
  }

  Future<void> refresh() async => state = await AsyncValue.guard(_load);

  Future<void> updateAlias(String alias) async {
    final profile = await ref
        .read(userRepositoryProvider)
        .update(alias: alias.trim());
    final current = state.value;
    if (current != null) state = AsyncData(current.copyWith(profile: profile));
  }

  /// Dùng một lượt quay. Trả về avatar mới để màn hình chạy hiệu ứng, rồi đồng bộ lại hồ sơ và bộ sưu tập.
  ///
  /// Nếu lỗi xảy ra sau khi server đã quay (mạng đứt lúc nhận phản hồi), người dùng mất lượt mà không thấy
  /// avatar. Vì vậy màn hình gọi [resync] khi gặp lỗi: avatar mới sẽ xuất hiện trong bộ sưu tập.
  Future<AvatarSpinResult> spinAvatar() async {
    final result = await ref.read(userRepositoryProvider).spinAvatar();
    await resync();
    return result;
  }

  /// Tải lại hồ sơ và bộ sưu tập mà không đặt state về loading (tránh nháy màn hình).
  Future<void> resync() async {
    final repository = ref.read(userRepositoryProvider);
    final profile = await repository.me();
    final avatars = await repository.myAvatars();
    state = AsyncData(ProfileData(profile: profile, avatars: avatars));
  }

  /// Đổi avatar đang dùng sang một avatar trong bộ sưu tập.
  Future<void> setAvatar(String avatar) async {
    final profile = await ref.read(userRepositoryProvider).setAvatar(avatar);
    final current = state.value;
    if (current != null) state = AsyncData(current.copyWith(profile: profile));
  }
}

final profileControllerProvider =
    AsyncNotifierProvider<ProfileController, ProfileData>(
      ProfileController.new,
    );
