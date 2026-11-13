import 'dart:math' as math;

import 'package:animal_locket/features/chat/domain/glyph.dart';
import 'package:animal_locket/features/chat/domain/glyph_point_codec.dart';

class GlyphRecognizer {
  const GlyphRecognizer({
    this.matchThreshold = 0.75,
    this.ambiguityMargin = 0.05,
    this.collisionThreshold = 0.85,
  });

  final double matchThreshold;
  final double ambiguityMargin;
  final double collisionThreshold;

  static const _angleRange = 22 * math.pi / 180;
  static const _anglePrecision = 2 * math.pi / 180;
  static const _phi = 0.5 * (-1 + 2.23606797749979);
  static const _halfDiagonal = 1.4142135623730951;

  List<GlyphPoint> normalize(List<GlyphPoint> raw) {
    if (raw.length < 2) return const [];
    final sampled = _resample(raw, GlyphPointCodec.pointCount);
    final bounds = _bounds(sampled);
    final scale = math.max(bounds.width, bounds.height);
    if (scale <= 0) {
      return List<GlyphPoint>.filled(
        GlyphPointCodec.pointCount,
        const GlyphPoint(0, 0),
      );
    }
    final scaled = sampled
        .map(
          (point) => GlyphPoint(
            ((point.x - bounds.left) / scale) * 2 - 1,
            ((point.y - bounds.top) / scale) * 2 - 1,
          ),
        )
        .toList(growable: false);
    final center = _centroid(scaled);
    return scaled
        .map((point) => GlyphPoint(point.x - center.x, point.y - center.y))
        .toList(growable: false);
  }

  GlyphRecognition recognize(
    List<GlyphPoint> raw,
    Iterable<GlyphDefinition> dictionary,
  ) {
    final candidate = normalize(raw);
    if (candidate.length != GlyphPointCodec.pointCount) {
      return const GlyphRecognition(
        best: null,
        score: 0,
        alternatives: [],
        ambiguous: false,
      );
    }
    final reversed = candidate.reversed.toList(growable: false);
    final matches = <GlyphCandidate>[];
    for (final glyph in dictionary.where((item) => item.isAvailable)) {
      if (glyph.points.length != GlyphPointCodec.pointCount) continue;
      final forward = _distanceAtBestAngle(candidate, glyph.points);
      final backward = _distanceAtBestAngle(reversed, glyph.points);
      final distance = math.min(forward, backward);
      final score = math.max(0.0, 1 - distance / _halfDiagonal);
      matches.add(GlyphCandidate(glyph, score));
    }
    matches.sort((a, b) => b.score.compareTo(a.score));
    if (matches.isEmpty || matches.first.score < matchThreshold) {
      return GlyphRecognition(
        best: matches.firstOrNull?.glyph,
        score: matches.firstOrNull?.score ?? 0,
        alternatives: matches.take(3).toList(growable: false),
        ambiguous: false,
      );
    }
    final ambiguous =
        matches.length > 1 &&
        matches.first.score - matches[1].score < ambiguityMargin;
    return GlyphRecognition(
      best: matches.first.glyph,
      score: matches.first.score,
      alternatives: matches.take(3).toList(growable: false),
      ambiguous: ambiguous,
    );
  }

  double similarity(List<GlyphPoint> raw, GlyphDefinition template) =>
      recognize(raw, [template]).score;

  List<GlyphPoint> _resample(List<GlyphPoint> source, int count) {
    final points = source.toList(growable: true);
    final total = _pathLength(points);
    if (total == 0) return List<GlyphPoint>.filled(count, points.first);
    final interval = total / (count - 1);
    var carried = 0.0;
    final result = <GlyphPoint>[points.first];
    var index = 1;
    while (index < points.length && result.length < count) {
      final previous = points[index - 1];
      final current = points[index];
      final segment = previous.distanceTo(current);
      if (segment > 0 && carried + segment >= interval) {
        final ratio = (interval - carried) / segment;
        final inserted = GlyphPoint(
          previous.x + ratio * (current.x - previous.x),
          previous.y + ratio * (current.y - previous.y),
        );
        result.add(inserted);
        points.insert(index, inserted);
        carried = 0;
        index++;
      } else {
        carried += segment;
        index++;
      }
    }
    while (result.length < count) {
      result.add(points.last);
    }
    return result.take(count).toList(growable: false);
  }

  double _pathLength(List<GlyphPoint> points) {
    var length = 0.0;
    for (var i = 1; i < points.length; i++) {
      length += points[i - 1].distanceTo(points[i]);
    }
    return length;
  }

  _GlyphBounds _bounds(List<GlyphPoint> points) {
    var minX = double.infinity;
    var minY = double.infinity;
    var maxX = double.negativeInfinity;
    var maxY = double.negativeInfinity;
    for (final point in points) {
      minX = math.min(minX, point.x);
      minY = math.min(minY, point.y);
      maxX = math.max(maxX, point.x);
      maxY = math.max(maxY, point.y);
    }
    return _GlyphBounds(minX, minY, maxX - minX, maxY - minY);
  }

  GlyphPoint _centroid(List<GlyphPoint> points) {
    var x = 0.0;
    var y = 0.0;
    for (final point in points) {
      x += point.x;
      y += point.y;
    }
    return GlyphPoint(x / points.length, y / points.length);
  }

  double _distanceAtBestAngle(
    List<GlyphPoint> candidate,
    List<GlyphPoint> template,
  ) {
    final unrotated = _distanceAtAngle(candidate, template, 0);
    var left = -_angleRange;
    var right = _angleRange;
    var x1 = _phi * left + (1 - _phi) * right;
    var f1 = _distanceAtAngle(candidate, template, x1);
    var x2 = (1 - _phi) * left + _phi * right;
    var f2 = _distanceAtAngle(candidate, template, x2);
    while ((right - left).abs() > _anglePrecision) {
      if (f1 < f2) {
        right = x2;
        x2 = x1;
        f2 = f1;
        x1 = _phi * left + (1 - _phi) * right;
        f1 = _distanceAtAngle(candidate, template, x1);
      } else {
        left = x1;
        x1 = x2;
        f1 = f2;
        x2 = (1 - _phi) * left + _phi * right;
        f2 = _distanceAtAngle(candidate, template, x2);
      }
    }
    return math.min(unrotated, math.min(f1, f2));
  }

  double _distanceAtAngle(
    List<GlyphPoint> candidate,
    List<GlyphPoint> template,
    double angle,
  ) {
    final center = _centroid(candidate);
    final cosine = math.cos(angle);
    final sine = math.sin(angle);
    var distance = 0.0;
    for (var i = 0; i < candidate.length; i++) {
      final point = candidate[i];
      final dx = point.x - center.x;
      final dy = point.y - center.y;
      final rotated = GlyphPoint(
        dx * cosine - dy * sine + center.x,
        dx * sine + dy * cosine + center.y,
      );
      distance += rotated.distanceTo(template[i]);
    }
    return distance / candidate.length;
  }
}

class _GlyphBounds {
  const _GlyphBounds(this.left, this.top, this.width, this.height);

  final double left;
  final double top;
  final double width;
  final double height;
}

extension<T> on List<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
