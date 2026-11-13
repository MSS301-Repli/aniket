import 'package:animal_locket/core/theme/album_palette.dart';
import 'package:animal_locket/core/widgets/album/paper_button.dart';
import 'package:animal_locket/core/widgets/album/paper_tile_button.dart';
import 'package:animal_locket/core/widgets/album_icon.dart';
import 'package:animal_locket/core/widgets/app_page.dart';
import 'package:animal_locket/features/camera/presentation/polaroid_viewfinder.dart';
import 'package:animal_locket/features/camera/presentation/send_status_pill.dart';
import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:animal_locket/core/l10n/tr.dart';

/// Camera: khung ngắm là tấm polaroid đang hiện hình (spec §7.4).
class CameraScreen extends StatefulWidget {
  const CameraScreen({super.key});

  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen>
    with WidgetsBindingObserver {
  CameraController? _controller;
  List<CameraDescription> _cameras = const [];
  int _cameraIndex = 0;
  FlashMode _flashMode = FlashMode.auto;
  bool _permissionDenied = false;
  bool _initializing = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkPermission();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _controller?.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused) {
      _controller?.dispose();
      _controller = null;
    } else if (state == AppLifecycleState.resumed && !_permissionDenied) {
      _initializeCamera();
    }
  }

  Future<void> _checkPermission() async {
    final status = await Permission.camera.status;
    if (status.isGranted) {
      await _initializeCamera();
    } else if (mounted) {
      setState(() => _permissionDenied = true);
    }
  }

  Future<void> _requestPermission() async {
    final status = await Permission.camera.request();
    if (!mounted) return;
    setState(() => _permissionDenied = !status.isGranted);
    if (status.isGranted) await _initializeCamera();
  }

  Future<void> _initializeCamera() async {
    if (_initializing) return;
    _initializing = true;
    try {
      _cameras = await availableCameras();
      if (_cameras.isEmpty) return;
      _cameraIndex = _cameraIndex.clamp(0, _cameras.length - 1);
      final old = _controller;
      final controller = CameraController(
        _cameras[_cameraIndex],
        ResolutionPreset.max,
        enableAudio: false,
        imageFormatGroup: ImageFormatGroup.jpeg,
      );
      _controller = controller;
      await old?.dispose();
      await controller.initialize();
      await controller.setFlashMode(_flashMode);
      if (mounted) setState(() {});
    } on CameraException {
      if (mounted) setState(() => _permissionDenied = true);
    } finally {
      _initializing = false;
    }
  }

  Future<void> _capture() async {
    final controller = _controller;
    if (controller == null ||
        !controller.value.isInitialized ||
        controller.value.isTakingPicture) {
      return;
    }
    final file = await controller.takePicture();
    if (!mounted) return;
    context.push(
      Uri(
        path: '/preview',
        queryParameters: {'imagePath': file.path},
      ).toString(),
    );
  }

  Future<void> _pickImage() async {
    final file = await ImagePicker().pickImage(
      source: ImageSource.gallery,
      requestFullMetadata: false,
    );
    if (file == null || !mounted) return;
    context.push(
      Uri(
        path: '/preview',
        queryParameters: {'imagePath': file.path},
      ).toString(),
    );
  }

  Future<void> _switchCamera() async {
    if (_cameras.length < 2) return;
    _cameraIndex = (_cameraIndex + 1) % _cameras.length;
    await _initializeCamera();
  }

  Future<void> _cycleFlash() async {
    _flashMode = switch (_flashMode) {
      FlashMode.auto => FlashMode.always,
      FlashMode.always => FlashMode.off,
      _ => FlashMode.auto,
    };
    await _controller?.setFlashMode(_flashMode);
    if (mounted) setState(() {});
  }

  Future<void> _focus(
    TapDownDetails details,
    BoxConstraints constraints,
  ) async {
    final point = Offset(
      (details.localPosition.dx / constraints.maxWidth).clamp(0, 1),
      (details.localPosition.dy / constraints.maxHeight).clamp(0, 1),
    );
    await _controller?.setFocusPoint(point);
    await _controller?.setExposurePoint(point);
  }

  Widget _preview(bool initialized, CameraController? controller) {
    final p = context.album;
    if (_permissionDenied) {
      return ColoredBox(
        color: p.fold1,
        child: _PermissionExplainer(onAllow: _requestPermission),
      );
    }
    if (!initialized) {
      return ColoredBox(
        color: p.ink,
        child: Center(child: CircularProgressIndicator(color: p.paper)),
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) => GestureDetector(
        onTapDown: (details) => _focus(details, constraints),
        child: SizedBox.expand(child: CameraPreview(controller!)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    final controller = _controller;
    final initialized = controller?.value.isInitialized == true;
    return AppPage(
      scrollable: false,
      // Chừa chỗ cho tấm polaroid "Ảnh" nhô lên khỏi thanh tab nổi.
      padding: const EdgeInsets.fromLTRB(18, 14, 18, 26),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Flexible(
                child: Text(
                  context.l10n.newMoment,
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
              ),
              Semantics(
                label: context.l10n.flashMode,
                button: true,
                enabled: initialized,
                excludeSemantics: true,
                onTap: initialized ? _cycleFlash : null,
                child: IconButton(
                  onPressed: initialized ? _cycleFlash : null,
                  icon: AlbumIcon(switch (_flashMode) {
                    FlashMode.always => AlbumIcons.flashOn,
                    FlashMode.off => AlbumIcons.flashOff,
                    _ => AlbumIcons.flashAuto,
                  }, color: initialized ? p.ink : p.muted),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Expanded(
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                Positioned.fill(
                  child: PolaroidViewfinder(
                    date: DateTime.now(),
                    child: _preview(initialized, controller),
                  ),
                ),
                const Positioned(
                  top: 22,
                  left: 22,
                  right: 22,
                  child: Center(child: SendStatusPill()),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              PaperTileButton(
                icon: AlbumIcons.gallery,
                label: context.l10n.pickFromGallery,
                onPressed: _pickImage,
              ),
              _Shutter(onPressed: initialized ? _capture : null),
              PaperTileButton(
                icon: AlbumIcons.flipCamera,
                label: context.l10n.flipCamera,
                onPressed: _switchCamera,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// Nút chụp: vòng son trên đế giấy tròn.
class _Shutter extends StatelessWidget {
  const _Shutter({required this.onPressed});
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    final color = onPressed == null ? p.muted : p.accent;
    return Semantics(
      label: context.l10n.takePhoto,
      button: true,
      enabled: onPressed != null,
      excludeSemantics: true,
      onTap: onPressed,
      child: Material(
        color: p.paper,
        shape: const CircleBorder(),
        elevation: 3,
        shadowColor: const Color(0x665A3C1E),
        child: InkWell(
          customBorder: const CircleBorder(),
          onTap: onPressed,
          child: SizedBox.square(
            dimension: 80,
            child: Center(
              child: Container(
                width: 62,
                height: 62,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(color: color, width: 4),
                ),
                child: Center(
                  child: Container(
                    width: 42,
                    height: 42,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: color,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _PermissionExplainer extends StatelessWidget {
  const _PermissionExplainer({required this.onAllow});
  final VoidCallback onAllow;

  @override
  Widget build(BuildContext context) {
    final p = context.album;
    // Nằm giữa khung ảnh; cuộn được khi khung thấp hoặc chữ to.
    return LayoutBuilder(
      builder: (context, box) => SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: box.maxHeight - 48),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              AlbumIcon(AlbumIcons.camera, color: p.muted, size: 56),
              const SizedBox(height: 16),
              Text(
                context.l10n.cameraPermission,
                textAlign: TextAlign.center,
                style: TextStyle(height: 1.5),
              ),
              const SizedBox(height: 18),
              PaperButton(label: context.l10n.allowCamera, onPressed: onAllow),
            ],
          ),
        ),
      ),
    );
  }
}
