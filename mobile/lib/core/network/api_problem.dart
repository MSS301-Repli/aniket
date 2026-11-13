import 'package:dio/dio.dart';

class ApiProblem implements Exception {
  const ApiProblem({
    required this.status,
    this.code,
    this.retryAfter,
    this.isNetworkFailure = false,
  });

  factory ApiProblem.fromDioException(DioException error) {
    final data = error.response?.data;
    final map = data is Map<String, dynamic> ? data : const <String, dynamic>{};
    return ApiProblem(
      status: error.response?.statusCode,
      code: map['code'] as String?,
      retryAfter: int.tryParse(
        error.response?.headers.value('retry-after') ?? '',
      ),
      isNetworkFailure:
          error.type == DioExceptionType.connectionError ||
          error.type == DioExceptionType.connectionTimeout ||
          error.type == DioExceptionType.receiveTimeout,
    );
  }

  final int? status;
  final String? code;
  final int? retryAfter;
  final bool isNetworkFailure;
}
