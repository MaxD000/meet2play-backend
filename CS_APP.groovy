import 'package:flutter/material.dart';

void main() => runApp(const SkyApp());

// ─── Couleur principale ───────────────────────────────────────────────────────

const sky = Color(0xFF0EA5E9);

// ─── App ─────────────────────────────────────────────────────────────────────

class SkyApp extends StatelessWidget {
  const SkyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Meet2Play',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: sky),
        useMaterial3: true,
        scaffoldBackgroundColor: Colors.white,
      ),
      home: const AuthScreen(),
    );
  }
}

// ─── Modèle : profil de l'utilisateur connecté ───────────────────────────────

class UserProfile {
  String name, email, sportLevel, job, company, age, studies, school;
  List<String> sports; // liste de sports pratiqués

  UserProfile({
    this.name = '',
    this.email = '',
    this.sports = const [],
    this.sportLevel = '',
    this.job = '',
    this.company = '',
    this.age = '',
    this.studies = '',
    this.school = '',
  });
}

// ─── État global partagé (utilisateur connecté) ───────────────────────────────
// On utilise un InheritedWidget pour passer le profil à tout l'arbre de widgets

class AppState extends InheritedWidget {
  final UserProfile user;
  final VoidCallback onLogout;

  const AppState({
    super.key,
    required this.user,
    required this.onLogout,
    required super.child,
  });

  static AppState of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AppState>()!;

  @override
  bool updateShouldNotify(AppState old) => true;
}

// ─── Écran racine : gère login / logout ──────────────────────────────────────

class AuthScreen extends StatefulWidget {
  const AuthScreen({super.key});

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> {
  UserProfile? _user; // null = pas connecté

  void _login(UserProfile user) => setState(() => _user = user);
  void _logout() => setState(() => _user = null);

  @override
  Widget build(BuildContext context) {
    if (_user == null) return LoginPage(onLogin: _login);

    return AppState(
      user: _user!,
      onLogout: _logout,
      child: const HomeScreen(),
    );
  }
}

// ─── Écran de connexion / inscription ────────────────────────────────────────

class LoginPage extends StatefulWidget {
  final void Function(UserProfile) onLogin;
  const LoginPage({super.key, required this.onLogin});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  bool _isLogin = true; // bascule entre "Connexion" et "Inscription"

  final _nameCtrl = TextEditingController();
  final _emailCtrl = TextEditingController();
  final _passCtrl = TextEditingController();

  void _submit() {
    if (_emailCtrl.text.trim().isEmpty || _passCtrl.text.trim().isEmpty) return;
    widget.onLogin(UserProfile(
      name: _nameCtrl.text.trim(),
      email: _emailCtrl.text.trim(),
    ));
  }

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;

    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        // Bouton "S'inscrire" en haut à droite, visible uniquement sur l'écran de login
        actions: [
          if (_isLogin)
            TextButton(
              onPressed: () => setState(() => _isLogin = false),
              child: const Text('S\'inscrire'),
            ),
          // Bouton "Se connecter" en haut à droite sur l'écran d'inscription
          if (!_isLogin)
            TextButton(
              onPressed: () => setState(() => _isLogin = true),
              child: const Text('Se connecter'),
            ),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Logo / titre
              Icon(Icons.sports, size: 64, color: primary),
              const SizedBox(height: 12),
              Text(
                'Meet2Play',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold, color: primary),
              ),
              const SizedBox(height: 8),
              Text(
                _isLogin ? 'Connexion' : 'Créer un compte',
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 16, color: Colors.grey),
              ),
              const SizedBox(height: 40),

              // Champ nom (inscription seulement)
              if (!_isLogin) ...[
                TextField(
                  controller: _nameCtrl,
                  decoration: _inputDeco('Nom complet', Icons.person_outline),
                ),
                const SizedBox(height: 14),
              ],

              // Email
              TextField(
                controller: _emailCtrl,
                keyboardType: TextInputType.emailAddress,
                decoration: _inputDeco('Email', Icons.email_outlined),
              ),
              const SizedBox(height: 14),

              // Mot de passe
              TextField(
                controller: _passCtrl,
                obscureText: true,
                decoration: _inputDeco('Mot de passe', Icons.lock_outline),
              ),
              const SizedBox(height: 28),

              // Bouton principal
              FilledButton(
                onPressed: _submit,
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  child: Text(
                    _isLogin ? 'Se connecter' : 'S\'inscrire',
                    style: const TextStyle(fontSize: 16), // Fix: fontSize must be in TextStyle
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  InputDecoration _inputDeco(String hint, IconData icon) => InputDecoration(
        hintText: hint,
        prefixIcon: Icon(icon),
        filled: true,
        fillColor: Colors.grey[100],
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
      );
}

// ─── Écran principal avec navigation bas ─────────────────────────────────────

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _index = 0;

  final _pages = const [SportsPage(), ProfilesPage(), JobsPage(), ConversationListPage(), MyProfilePage()];
  final _titles = const ['Sports', 'Profils', 'Offres', 'Messages', 'Mon profil'];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_titles[_index]),
        centerTitle: true,
        elevation: 0,
        backgroundColor: Colors.white,
        foregroundColor: Colors.black,
      ),
      body: _pages[_index],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (i) => setState(() => _index = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.sports_soccer_outlined), label: ''),
          NavigationDestination(icon: Icon(Icons.people_outline), label: ''),
          NavigationDestination(icon: Icon(Icons.work_outline), label: ''),
          NavigationDestination(icon: Icon(Icons.chat_bubble_outline), label: ''),
          NavigationDestination(icon: Icon(Icons.person_outline), label: ''),
        ],
      ),
    );
  }
}

// ─── Modèle : un événement sportif ───────────────────────────────────────────

class SportPreview {
  final String name, place, time;
  const SportPreview(this.name, this.place, this.time);
}

const _sports = <SportPreview>[
  SportPreview('Football match', 'Parc', '18:00'),
  SportPreview('Tennis training', 'Club', 'Tomorrow'),
  SportPreview('Golf session', 'Club de golf', 'Sat.'),
];

// ─── Page liste des sports ────────────────────────────────────────────────────

class SportsPage extends StatelessWidget {
  const SportsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      itemCount: _sports.length,
      separatorBuilder: (_, __) => const Divider(height: 1, indent: 70),
      itemBuilder: (context, i) {
        final s = _sports[i];
        return ListTile(
          leading: const CircleAvatar(child: Icon(Icons.sports)),
          title: Text(s.name, style: const TextStyle(fontWeight: FontWeight.bold)),
          subtitle: Text(s.place),
          trailing: Text(s.time, style: const TextStyle(fontSize: 12, color: Colors.grey)),
        );
      },
    );
  }
}

// ─── Modèle : un profil de joueur ────────────────────────────────────────────

class PlayerProfile {
  final String name, sport, job, bio, avatarUrl;
  const PlayerProfile(this.name, this.sport, this.job, this.bio, this.avatarUrl);
}

const _profiles = <PlayerProfile>[
  PlayerProfile(
    'Antoine Dupont',
    'Football',
    'Data Scientist',
    'Analyse les performances des joueurs via la data. Travaille chez SportMetrics. Passionné par le machine learning appliqué au sport.',
    'https://i.pravatar.cc/150?u=10',
  ),
  PlayerProfile(
    'Sarah Mbeki',
    'Tennis',
    'Analyste Financier',
    'Gère les investissements d\'un fonds spécialisé dans les franchises sportives. CFA charterholder. Ex-joueuse de tennis universitaire.',
    'https://i.pravatar.cc/150?u=11',
  ),
  PlayerProfile(
    'Marc Leroy',
    'Golf',
    'Ingénieur Logiciel',
    'Développe des applis de tracking de performance pour les athlètes. 8 ans d\'expérience en Flutter et Python. Golfeur le week-end.',
    'https://i.pravatar.cc/150?u=12',
  ),
  PlayerProfile(
    'Inès Moreau',
    'Basketball',
    'Avocate — Droit du sport',
    'Spécialisée dans les contrats de transfert et les droits à l\'image. Représente des joueurs pros en Europe et aux États-Unis.',
    'https://i.pravatar.cc/150?u=13',
  ),
  PlayerProfile(
    'Lucas Ferreira',
    'Football',
    'Médecin du sport',
    'Suivi médical et prévention des blessures pour des clubs de Ligue 2. Titulaire d\'un DU de médecine du sport. Chercheur à mi-temps.',
    'https://i.pravatar.cc/150?u=14',
  ),
  PlayerProfile(
    'Camille Renard',
    'Natation',
    'Chef de projet Marketing',
    'Développe les partenariats entre marques et athlètes. Spécialisée dans le sponsoring sportif et les stratégies de personal branding.',
    'https://i.pravatar.cc/150?u=15',
  ),
];

// ─── Page liste des profils ───────────────────────────────────────────────────

class ProfilesPage extends StatelessWidget {
  const ProfilesPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      itemCount: _profiles.length,
      separatorBuilder: (_, __) => const Divider(height: 1, indent: 70),
      itemBuilder: (context, i) {
        final p = _profiles[i];
        return ListTile(
          leading: CircleAvatar(backgroundImage: NetworkImage(p.avatarUrl)),
          title: Text(p.name, style: const TextStyle(fontWeight: FontWeight.bold)),
          subtitle: Text(p.job, maxLines: 1, overflow: TextOverflow.ellipsis),
          trailing: Chip(
            label: Text(p.sport, style: const TextStyle(fontSize: 11)),
            padding: EdgeInsets.zero,
            visualDensity: VisualDensity.compact,
          ),
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => ProfileDetailScreen(profile: p)),
          ),
        );
      },
    );
  }
}

// ─── Écran détail d'un profil ─────────────────────────────────────────────────

class ProfileDetailScreen extends StatelessWidget {
  final PlayerProfile profile;
  const ProfileDetailScreen({super.key, required this.profile});

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;

    return Scaffold(
      appBar: AppBar(title: Text(profile.name)),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            CircleAvatar(radius: 52, backgroundImage: NetworkImage(profile.avatarUrl)),
            const SizedBox(height: 16),
            Text(profile.name, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            Text(profile.job,
                style: TextStyle(fontSize: 15, color: primary, fontWeight: FontWeight.w500),
                textAlign: TextAlign.center),
            const SizedBox(height: 8),
            Chip(
              label: Text(profile.sport),
              backgroundColor: primary.withOpacity(0.12),
              labelStyle: TextStyle(color: primary, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 24),
            const Divider(),
            const SizedBox(height: 16),
            Text(profile.bio,
                style: const TextStyle(fontSize: 15, height: 1.6, color: Colors.black87),
                textAlign: TextAlign.center),
            const Spacer(),
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                icon: const Icon(Icons.chat_bubble_outline),
                label: const Text('Envoyer un message'),
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => ChatDetailScreen(contactName: profile.name)),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Page Mon Profil ──────────────────────────────────────────────────────────

class MyProfilePage extends StatefulWidget {
  const MyProfilePage({super.key});

  @override
  State<MyProfilePage> createState() => _MyProfilePageState();
}

class _MyProfilePageState extends State<MyProfilePage> {
  // Champs texte libres
  late final TextEditingController _nameCtrl;
  late final TextEditingController _jobCtrl;
  late final TextEditingController _companyCtrl;
  late final TextEditingController _schoolCtrl;

  // Liste dynamique : une valeur dropdown par sport + une par niveau
  final List<String?> _selectedSports = [null];
  final List<String?> _selectedSportLevels = [null];

  // Sports disponibles dans la dropdown
  static const _sportOptions = [
    'Football', 'Basketball', 'Tennis', 'Rugby', 'Golf', 'Natation',
    'Cyclisme', 'Athletisme', 'Volleyball', 'Handball', 'Boxe',
    'Judo', 'Escalade', 'Ski', 'Surf', 'Padel', 'Badminton', 'Autre'
  ];

  // Valeurs des dropdowns
  String? _age;
  String? _studies;

  // Options des dropdowns
  static const _ages = [
    '< 18 ans', '18-24 ans', '25-34 ans', '35-44 ans', '45-54 ans', '55 ans et +'
  ];
  static const _levels = [
    'Débutant', 'Intermédiaire', 'Confirmé', 'Amateur compétiteur', 'Semi-professionnel', 'Professionnel'
  ];
  static const _diplomas = [
    'Brevet', 'Bac', 'Bac +2 (BTS / DUT)', 'Bac +3 (Licence)',
    'Bac +4 (Master 1)', 'Bac +5 (Master / Grande école)', 'Doctorat', 'Autre'
  ];

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final u = AppState.of(context).user;
    _nameCtrl    = TextEditingController(text: u.name);
    _jobCtrl     = TextEditingController(text: u.job);
    // Initialise les sports depuis la liste sauvegardée
    if (u.sports.isNotEmpty) {
      _selectedSports.clear();
      _selectedSportLevels.clear();
      for (final s in u.sports) {
        _selectedSports.add(_sportOptions.contains(s) ? s : null);
        _selectedSportLevels.add(null);
      }
    }
    _companyCtrl = TextEditingController(text: u.company);
    _schoolCtrl  = TextEditingController(text: u.school);
    _age         = _ages.contains(u.age) ? u.age : null;
    _studies     = _diplomas.contains(u.studies) ? u.studies : null;
  }

  void _save() {
    final u = AppState.of(context).user;
    u.name       = _nameCtrl.text.trim();
    u.sports     = _selectedSports.whereType<String>().toList();
    u.job        = _jobCtrl.text.trim();
    u.company    = _companyCtrl.text.trim();
    u.school     = _schoolCtrl.text.trim();
    u.age        = _age ?? '';
    u.sportLevel = _selectedSportLevels.whereType<String>().join(', ');
    u.studies    = _studies ?? '';
    setState(() {});
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Profil sauvegardé ✓'), behavior: SnackBarBehavior.floating),
    );
  }

  @override
  Widget build(BuildContext context) {
    final onLogout = AppState.of(context).onLogout;
    final primary = Theme.of(context).colorScheme.primary;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Avatar placeholder
          Center(
            child: CircleAvatar(
              radius: 44,
              backgroundColor: primary.withOpacity(0.15),
              child: Icon(Icons.person, size: 44, color: primary),
            ),
          ),
          const SizedBox(height: 28),

          // ── Section : Infos personnelles ──
          _sectionTitle('Infos personnelles'),
          _field(_nameCtrl, 'Nom complet', Icons.person_outline),
          _dropdown(_age, 'Âge', Icons.cake_outlined, _ages, (v) => setState(() => _age = v)),

          // ── Section : Sport ──
          _sectionTitle('Sport'),
          // Une ligne par sport : dropdown sport + dropdown niveau + bouton supprimer
          ..._selectedSports.asMap().entries.map((entry) {
            final i = entry.key;
            return Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Dropdown sport
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      value: _selectedSports[i],
                      hint: const Text('Sport'),
                      decoration: InputDecoration(
                        prefixIcon: const Icon(Icons.sports_tennis_outlined),
                        filled: true,
                        fillColor: Colors.grey[100],
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide.none,
                        ),
                      ),
                      items: _sportOptions.map((o) => DropdownMenuItem(value: o, child: Text(o))).toList(),
                      onChanged: (v) => setState(() => _selectedSports[i] = v),
                    ),
                  ),
                  const SizedBox(width: 8),
                  // Dropdown niveau pour ce sport
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      value: _selectedSportLevels[i],
                      hint: const Text('Niveau'),
                      decoration: InputDecoration(
                        prefixIcon: const Icon(Icons.emoji_events_outlined),
                        filled: true,
                        fillColor: Colors.grey[100],
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide.none,
                        ),
                      ),
                      items: _levels.map((o) => DropdownMenuItem(value: o, child: Text(o))).toList(),
                      onChanged: (v) => setState(() => _selectedSportLevels[i] = v),
                    ),
                  ),
                  // Bouton supprimer (masque si une seule ligne)
                  if (_selectedSports.length > 1)
                    IconButton(
                      icon: const Icon(Icons.remove_circle_outline, color: Colors.red),
                      onPressed: () => setState(() {
                        _selectedSports.removeAt(i);
                        _selectedSportLevels.removeAt(i);
                      }),
                    ),
                ],
              ),
            );
          }),
          // Bouton ajouter un sport
          TextButton.icon(
            icon: const Icon(Icons.add),
            label: const Text('Ajouter un sport'),
            onPressed: () => setState(() {
              _selectedSports.add(null);
              _selectedSportLevels.add(null);
            }),
          ),

          // ── Section : Carrière ──
          _sectionTitle('Carrière'),
          _field(_jobCtrl, 'Poste  (ex: Data Scientist)', Icons.work_outline),
          _field(_companyCtrl, 'Entreprise', Icons.business_outlined),

          // ── Section : Études ──
          _sectionTitle('Études'),
          _dropdown(_studies, 'Diplôme', Icons.school_outlined, _diplomas, (v) => setState(() => _studies = v)),
          _field(_schoolCtrl, 'École / Université', Icons.account_balance_outlined),

          const SizedBox(height: 28),

          // Bouton sauvegarder
          FilledButton.icon(
            icon: const Icon(Icons.check),
            label: const Text('Sauvegarder'),
            onPressed: _save,
          ),
          const SizedBox(height: 12),

          // Bouton déconnexion
          OutlinedButton.icon(
            icon: const Icon(Icons.logout, color: Colors.red),
            label: const Text('Se déconnecter', style: TextStyle(color: Colors.red)),
            style: OutlinedButton.styleFrom(side: const BorderSide(color: Colors.red)),
            onPressed: onLogout,
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  // Titre de section
  Widget _sectionTitle(String title) => Padding(
        padding: const EdgeInsets.only(top: 20, bottom: 10),
        child: Text(title,
            style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.bold,
                color: Colors.grey[600],
                letterSpacing: 0.8)),
      );

  // Champ texte réutilisable
  Widget _field(TextEditingController ctrl, String hint, IconData icon) =>
      Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: TextField(
          controller: ctrl,
          decoration: InputDecoration(
            hintText: hint,
            prefixIcon: Icon(icon),
            filled: true,
            fillColor: Colors.grey[100],
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide.none,
            ),
          ),
        ),
      );

  // Dropdown réutilisable
  Widget _dropdown(
    String? value,
    String hint,
    IconData icon,
    List<String> options,
    void Function(String?) onChanged,
  ) =>
      Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: DropdownButtonFormField<String>(
          value: value,
          hint: Text(hint),
          decoration: InputDecoration(
            prefixIcon: Icon(icon),
            filled: true,
            fillColor: Colors.grey[100],
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide.none,
            ),
          ),
          items: options
              .map((o) => DropdownMenuItem(value: o, child: Text(o)))
              .toList(),
          onChanged: onChanged,
        ),
      );
}

// ─── Modèle : aperçu d'une conversation ──────────────────────────────────────

class ChatPreview {
  final String name, lastMsg, time;
  const ChatPreview(this.name, this.lastMsg, this.time);
}

const _chats = <ChatPreview>[
  ChatPreview('Thomas', 'Tu es où ?', '12:03'),
  ChatPreview('Léa', 'On se voit ce soir ?', 'Hier'),
  ChatPreview('Club de golf', 'Entraînement demain à 18h', 'Lun.'),
];

// ─── Page liste des conversations ────────────────────────────────────────────

class ConversationListPage extends StatelessWidget {
  const ConversationListPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      itemCount: _chats.length,
      separatorBuilder: (_, __) => const Divider(height: 1, indent: 70),
      itemBuilder: (context, i) {
        final c = _chats[i];
        return ListTile(
          leading: CircleAvatar(child: Text(c.name[0])),
          title: Text(c.name, style: const TextStyle(fontWeight: FontWeight.bold)),
          subtitle: Text(c.lastMsg, maxLines: 1, overflow: TextOverflow.ellipsis),
          trailing: Text(c.time, style: const TextStyle(fontSize: 12, color: Colors.grey)),
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => ChatDetailScreen(contactName: c.name)),
          ),
        );
      },
    );
  }
}

// ─── Modèle : un message ─────────────────────────────────────────────────────

class ChatMessage {
  final String text;
  final bool mine;
  const ChatMessage(this.text, this.mine);
}

// ─── Écran de conversation ────────────────────────────────────────────────────

class ChatDetailScreen extends StatefulWidget {
  final String contactName;
  const ChatDetailScreen({super.key, required this.contactName});

  @override
  State<ChatDetailScreen> createState() => _ChatDetailScreenState();
}

class _ChatDetailScreenState extends State<ChatDetailScreen> {
  final _controller = TextEditingController();
  final _messages = <ChatMessage>[
    const ChatMessage('Salut !', false),
    const ChatMessage('Ça va ?', false),
    const ChatMessage('Oui et toi ?', true),
  ];

  void _send() {
    final text = _controller.text.trim();
    if (text.isEmpty) return;
    setState(() {
      _messages.add(ChatMessage(text, true));
      _controller.clear();
    });
  }

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;

    return Scaffold(
      appBar: AppBar(title: Text(widget.contactName)),
      body: Column(
        children: [
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: _messages.length,
              itemBuilder: (context, i) => _buildBubble(_messages[i], primary),
            ),
          ),
          _buildInputBar(primary),
        ],
      ),
    );
  }

  Widget _buildBubble(ChatMessage m, Color primary) {
    return Align(
      alignment: m.mine ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: m.mine ? primary : Colors.grey[200],
          borderRadius: BorderRadius.circular(18),
        ),
        child: Text(m.text, style: TextStyle(color: m.mine ? Colors.white : Colors.black)),
      ),
    );
  }

  Widget _buildInputBar(Color primary) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: Row(
          children: [
            Expanded(
              child: TextField(
                controller: _controller,
                decoration: InputDecoration(
                  hintText: 'Message...',
                  filled: true,
                  fillColor: Colors.grey[100],
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(25),
                    borderSide: BorderSide.none,
                  ),
                  contentPadding: const EdgeInsets.symmetric(horizontal: 20),
                ),
              ),
            ),
            IconButton(icon: const Icon(Icons.send), color: primary, onPressed: _send),
          ],
        ),
      ),
    );
  }
}

// ─── Modèle : une offre d'emploi ─────────────────────────────────────────────

class JobOffer {
  final String title, company, location, type, sport, description;
  const JobOffer(this.title, this.company, this.location, this.type, this.sport, this.description);
}

const _jobs = <JobOffer>[
  JobOffer(
    'Data Scientist Sport',
    'PSG Analytics',
    'Paris · Présentiel',
    'CDI',
    'Football',
    'Analyse des données de performance des joueurs, modélisation prédictive des blessures, visualisation pour le staff technique.',
  ),
  JobOffer(
    'Analyste Financier',
    'SportInvest Group',
    'Lyon · Hybride',
    'CDI',
    'Tous sports',
    'Évaluation des franchises sportives, gestion de portefeuilles d\'actifs liés au sport, reporting investisseurs.',
  ),
  JobOffer(
    'Développeur Flutter',
    'TrackPerf',
    'Remote',
    'Freelance',
    'Tous sports',
    'Développement d\'une app mobile de suivi de performance pour athlètes amateurs. Stack : Flutter + Firebase.',
  ),
  JobOffer(
    'Responsable Marketing',
    'Nike France',
    'Paris · Hybride',
    'CDI',
    'Tous sports',
    'Stratégie de sponsoring, gestion des partenariats athlètes, campagnes digitales et activation terrain.',
  ),
  JobOffer(
    'Médecin du Sport',
    'Olympique de Marseille',
    'Marseille · Présentiel',
    'CDI',
    'Football',
    'Suivi médical de l\'équipe première, prévention et traitement des blessures, collaboration avec le staff technique.',
  ),
  JobOffer(
    'Agent Sportif Junior',
    'ProAthletes Agency',
    'Paris · Présentiel',
    'Stage',
    'Tennis',
    'Accompagnement des joueurs dans leur développement de carrière, négociation de contrats, prospection de sponsors.',
  ),
  JobOffer(
    'Ingénieur Biomécanique',
    'Decathlon Lab',
    'Lille · Hybride',
    'CDI',
    'Tous sports',
    'R&D sur l\'équipement sportif, analyse du mouvement humain, tests de performance produit en laboratoire.',
  ),
  JobOffer(
    'Chef de Projet Événementiel',
    'Roland Garros',
    'Paris · Présentiel',
    'CDD',
    'Tennis',
    'Organisation des tournois et événements annexes, coordination des prestataires, gestion des accréditations.',
  ),
];

// ─── Page recommandations / offres d'emploi ───────────────────────────────────

class JobsPage extends StatelessWidget {
  const JobsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: _jobs.length,
      itemBuilder: (context, i) => _JobCard(job: _jobs[i]),
    );
  }
}

class _JobCard extends StatelessWidget {
  final JobOffer job;
  const _JobCard({required this.job});

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;

    return Card(
      margin: const EdgeInsets.only(bottom: 14),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(color: Colors.grey.shade200),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => JobDetailScreen(job: job)),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Icône entreprise
                  CircleAvatar(
                    radius: 22,
                    backgroundColor: primary.withOpacity(0.12),
                    child: Icon(Icons.business, color: primary),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(job.title,
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                        const SizedBox(height: 2),
                        Text(job.company,
                            style: TextStyle(color: Colors.grey[600], fontSize: 13)),
                      ],
                    ),
                  ),
                  // Badge type de contrat
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: primary.withOpacity(0.12),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(job.type,
                        style: TextStyle(
                            color: primary, fontSize: 11, fontWeight: FontWeight.bold)),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              // Infos lieu + sport
              Row(
                children: [
                  Icon(Icons.location_on_outlined, size: 14, color: Colors.grey[500]),
                  const SizedBox(width: 4),
                  Text(job.location, style: TextStyle(fontSize: 12, color: Colors.grey[500])),
                  const SizedBox(width: 12),
                  Icon(Icons.sports_outlined, size: 14, color: Colors.grey[500]),
                  const SizedBox(width: 4),
                  Text(job.sport, style: TextStyle(fontSize: 12, color: Colors.grey[500])),
                ],
              ),
              const SizedBox(height: 10),
              // Description tronquée
              Text(job.description,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 13, color: Colors.grey[700], height: 1.4)),
            ],
          ),
        ),
      ),
    );
  }
}

// ─── Écran détail d'une offre ─────────────────────────────────────────────────

class JobDetailScreen extends StatelessWidget {
  final JobOffer job;
  const JobDetailScreen({super.key, required this.job});

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;

    return Scaffold(
      appBar: AppBar(title: Text(job.company)),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // En-tête
            Center(
              child: CircleAvatar(
                radius: 36,
                backgroundColor: primary.withOpacity(0.12),
                child: Icon(Icons.business, size: 36, color: primary),
              ),
            ),
            const SizedBox(height: 16),
            Center(
              child: Text(job.title,
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
            ),
            const SizedBox(height: 4),
            Center(
              child: Text(job.company,
                  style: TextStyle(fontSize: 15, color: primary, fontWeight: FontWeight.w500)),
            ),
            const SizedBox(height: 16),
            // Badges
            Wrap(
              spacing: 8,
              runSpacing: 8,
              alignment: WrapAlignment.center,
              children: [
                _badge(job.type, Icons.work_outline, primary),
                _badge(job.location, Icons.location_on_outlined, primary),
                _badge(job.sport, Icons.sports_outlined, primary),
              ],
            ),
            const SizedBox(height: 24),
            const Divider(),
            const SizedBox(height: 16),
            const Text('Description du poste',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
            const SizedBox(height: 8),
            Text(job.description,
                style: const TextStyle(fontSize: 15, height: 1.7, color: Colors.black87)),
            const SizedBox(height: 32),
            // Bouton postuler
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                icon: const Icon(Icons.send),
                label: const Text('Postuler'),
                onPressed: () => ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Candidature envoyée ! ✓'),
                    behavior: SnackBarBehavior.floating,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _badge(String label, IconData icon, Color color) => Container(
       padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: color.withOpacity(0.10),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 13, color: color),
            const SizedBox(width: 4),
            Text(label, style: TextStyle(fontSize: 12, color: color, fontWeight: FontWeight.w500)),
          ],
        ),
      );
}