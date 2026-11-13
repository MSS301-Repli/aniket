import 'package:animal_locket/core/config/app_config.dart';
import 'package:animal_locket/core/auth/session_manager.dart';
import 'package:animal_locket/core/network/api_problem.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class ApiClient {
  ApiClient({required String baseUrl, required this.sessionManager})
    : dio = Dio(
        BaseOptions(
          baseUrl: baseUrl,
          connectTimeout: const Duration(seconds: 12),
          receiveTimeout: const Duration(seconds: 18),
          headers: const {'Accept': 'application/json'},
        ),
      ) {
    dio.interceptors.add(
      InterceptorsWrapper(onRequest: _onRequest, onError: _onError),
    );
  }

  final Dio dio;
  final SessionManager sessionManager;

  Future<Response<T>> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
  }) => _guard(() => dio.get<T>(path, queryParameters: queryParameters));

  Future<Response<T>> post<T>(String path, {Object? data}) =>
      _guard(() => dio.post<T>(path, data: data));

  Future<Response<T>> put<T>(String path, {Object? data}) =>
      _guard(() => dio.put<T>(path, data: data));

  Future<Response<T>> patch<T>(String path, {Object? data}) =>
      _guard(() => dio.patch<T>(path, data: data));

  Future<Response<T>> delete<T>(String path, {Object? data}) =>
      _guard(() => dio.delete<T>(path, data: data));

  Future<Response<T>> _guard<T>(Future<Response<T>> Function() action) async {
    try {
      return await action();
    } on DioException catch (error) {
      final problem = error.error;
      if (problem is ApiProblem) throw problem;
      throw ApiProblem.fromDioException(error);
    }
  }

  Future<void> _onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    options.headers.remove('X-Public-Id');
    final token = await sessionManager.accessToken();
    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  Future<void> _onError(
    DioException error,
    ErrorInterceptorHandler handler,
  ) async {
    final request = error.requestOptions;
    if (error.response?.statusCode == 401 &&
        request.extra['authRetried'] != true &&
        await sessionManager.refresh()) {
      request.extra['authRetried'] = true;
      final token = await sessionManager.accessToken(refreshIfNeeded: false);
      if (token != null) request.headers['Authorization'] = 'Bearer $token';
      try {
        handler.resolve(await dio.fetch<Object?>(request));
        return;
      } on DioException catch (retryError) {
        handler.reject(retryError);
        return;
      }
    }
    final retryCount = request.extra['getRetryCount'] as int? ?? 0;
    final retryable =
        request.method.toUpperCase() == 'GET' &&
        retryCount < 2 &&
        (error.response?.statusCode == 503 ||
            error.type == DioExceptionType.connectionError ||
            error.type == DioExceptionType.connectionTimeout ||
            error.type == DioExceptionType.receiveTimeout);
    if (retryable) {
      request.extra['getRetryCount'] = retryCount + 1;
      await Future<void>.delayed(
        retryCount == 0
            ? const Duration(milliseconds: 400)
            : const Duration(milliseconds: 1200),
      );
      try {
        handler.resolve(await dio.fetch<Object?>(request));
        return;
      } on DioException catch (retryError) {
        handler.reject(retryError);
        return;
      }
    }
    handler.reject(
      DioException(
        requestOptions: request,
        response: error.response,
        type: error.type,
        error: ApiProblem.fromDioException(error),
      ),
    );
  }
}

final apiClientProvider = Provider<ApiClient>((ref) {
  return ApiClient(
    baseUrl: ref.watch(appConfigProvider).apiV1BaseUrl,
    sessionManager: ref.watch(sessionManagerProvider),
  );
});
