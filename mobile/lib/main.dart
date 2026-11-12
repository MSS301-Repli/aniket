import 'package:flutter/material.dart';

void main() => runApp(const AnimalLocketBootstrap());

class AnimalLocketBootstrap extends StatelessWidget {
  const AnimalLocketBootstrap({super.key});

  @override
  Widget build(BuildContext context) => const MaterialApp(
    title: 'Animal Locket',
    home: Scaffold(body: Center(child: Text('Animal Locket'))),
  );
}
