import 'dart:async';
import 'dart:math';

import 'package:animal_locket/core/avatar/avatar_library.dart';
import 'package:flutter/foundation.dart';

/// Hiệu ứng "đang quay" cho tấm polaroid avatar: đổi mã ngẫu nhiên vài khung rồi dừng.
///
/// Chỉ là phần hình ảnh. Kết quả thật luôn do server cấp **trước** khi hiệu ứng chạy, nên hiệu ứng không bao giờ
/// quyết định avatar nào. Dùng ở `reveal_screen` (quay lúc đăng ký) và `avatar_spin_screen` (quay bằng lượt đã mua).
///
/// Màn hình giữ một thể hiện, gọi [play] khi có kết quả và [dispose] trong `dispose()` của mình.
class AvatarSpinReel {
  AvatarSpinReel({
    this.tickCount = 15,
    this.interval = const Duration(milliseconds: 80),
  });

  /// Số khung hình trước khi dừng (15 × 80ms ≈ 1,2 giây).
  final int tickCount;
  final Duration interval;

  final Random _random = Random();
  Timer? _timer;
  String? _current;

  /// Mã avatar đang hiện trong lúc quay; `null` nghĩa là đã dừng, màn hình hiện avatar thật.
  String? get current => _current;

  bool get spinning => _current != null;

  /// Bắt đầu quay. [onFrame] được gọi sau mỗi khung để màn hình `setState`.
  void play({required VoidCallback onFrame}) {
    _timer?.cancel();
    var ticks = 0;
    // Đặt khung đầu ngay, trước khi timer chạy: nút "xong" không được bật trong một khung hình đầu.
    _current = AvatarLibraries.instance.randomCodeForAnimation(_random);
    _timer = Timer.periodic(interval, (timer) {
      ticks++;
      final done = ticks >= tickCount;
      _current = done
          ? null
          : AvatarLibraries.instance.randomCodeForAnimation(_random);
      if (done) {
        timer.cancel();
        _timer = null;
      }
      onFrame();
    });
  }

  /// Dừng ngay và hiện avatar thật (dùng khi người dùng bật giảm chuyển động, hoặc khi có lỗi).
  void stop() {
    _timer?.cancel();
    _timer = null;
    _current = null;
  }

  void dispose() {
    _timer?.cancel();
    _timer = null;
  }
}
