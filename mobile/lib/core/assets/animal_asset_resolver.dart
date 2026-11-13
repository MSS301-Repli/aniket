class AnimalAssetResolver {
  const AnimalAssetResolver();

  static const _fallbackEmoji = '🐾';
  static const _emojiByAnimal = <String, String>{
    'cat': '🐱',
    'fox': '🦊',
    'wolf': '🐺',
    'dog': '🐶',
    'bear': '🐻',
    'panda': '🐼',
    'polar': '🐻‍❄️',
    'koala': '🐨',
    'rabbit': '🐰',
    'mouse': '🐭',
    'hamster': '🐹',
    'squirrel': '🐿️',
    'raccoon': '🦝',
    'redpanda': '🦊',
    'tiger': '🐯',
    'lion': '🦁',
    'leopard': '🐆',
    'cow': '🐮',
    'sheep': '🐑',
    'goat': '🐐',
    'horse': '🐴',
    'unicorn': '🦄',
    'zebra': '🦓',
    'donkey': '🫏',
    'pig': '🐷',
    'boar': '🐗',
    'elephant': '🐘',
    'hippo': '🦛',
    'rhino': '🦏',
    'giraffe': '🦒',
    'deer': '🦌',
    'camel': '🐫',
    'llama': '🦙',
    'monkey': '🐵',
    'gorilla': '🦍',
    'sloth': '🦥',
    'kangaroo': '🦘',
    'otter': '🦦',
    'beaver': '🦫',
    'hedgehog': '🦔',
    'bat': '🦇',
    'owl': '🦉',
    'penguin': '🐧',
    'chicken': '🐔',
    'duck': '🦆',
    'parrot': '🦜',
    'frog': '🐸',
    'turtle': '🐢',
    'dino': '🦖',
    'seal': '🦭',
  };

  String emojiFor(String? animalCode) {
    final normalized = animalCode?.trim().toLowerCase();
    if (normalized == null || normalized.isEmpty) return _fallbackEmoji;

    final parts = normalized.split(':');
    final key = parts.length == 4 ? parts[1] : normalized;
    return _emojiByAnimal[key] ?? _fallbackEmoji;
  }

  static String emoji(String? animalCode) =>
      const AnimalAssetResolver().emojiFor(animalCode);
}
