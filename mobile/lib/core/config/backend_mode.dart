import 'package:flutter_riverpod/flutter_riverpod.dart';

enum BackendMode { demo, live }

class BackendModeController extends Notifier<BackendMode> {
  @override
  BackendMode build() => BackendMode.live;

  void useDemo() => state = BackendMode.demo;

  void useLive() => state = BackendMode.live;
}

final backendModeProvider =
    NotifierProvider<BackendModeController, BackendMode>(
      BackendModeController.new,
    );
