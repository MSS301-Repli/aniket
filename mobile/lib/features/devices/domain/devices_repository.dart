abstract interface class DevicesRepository {
  Future<void> register({required String token, required String platform});
  Future<void> unregister(String token);
}
