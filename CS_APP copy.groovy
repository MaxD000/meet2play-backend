import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:http/http.dart' as http;
import 'package:latlong2/latlong.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';

void main() => runApp(const SkyApp());

// ─── Couleur principale ───────────────────────────────────────────────────────

const sky = Color(0xFF0EA5E9);

// ─── Service API ──────────────────────────────────────────────────────────────

// En développement local : 'http://127.0.0.1:8000'
// En production (Render) : 'https://meet2play-backend.onrender.com'
const _apiBase = 'https://meet2play-backend.onrender.com';

class ApiService {
  static String? _token;

  // ── Token (persisté dans SharedPreferences) ──────────────────────────────
  static Future<void> saveToken(String token) async {
    _token = token;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('jwt', token);
  }

  static Future<bool> tryLoadToken() async {
    final prefs = await SharedPreferences.getInstance();
    final t = prefs.getString('jwt');
    if (t == null) return false;
    _token = t;
    return true;
  }

  static Future<void> clearToken() async {
    _token = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('jwt');
  }

  // ── Headers ──────────────────────────────────────────────────────────────
  static Map<String, String> get _headers => {
    'Content-Type': 'application/json; charset=utf-8',
    if (_token != null) 'Authorization': 'Bearer $_token',
  };

  // ── Helpers ──────────────────────────────────────────────────────────────
  static Map<String, dynamic> _decode(http.Response res) {
    final body = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    if (res.statusCode >= 400) {
      throw Exception(body['detail'] ?? 'Erreur ${res.statusCode}');
    }
    return body;
  }

  static UserProfile _profileFromJson(Map<String, dynamic> j) => UserProfile(
    name: j['name'] ?? '',
    email: j['email'] ?? '',
    sports: List<String>.from(j['sports'] ?? []),
    sportLevel: j['sport_level'] ?? '',
    job: j['job'] ?? '',
    company: j['company'] ?? '',
    companyCity: j['company_city'] ?? '',
    age: j['age'] ?? '',
    studies: j['studies'] ?? '',
    school: j['school'] ?? '',
    schoolCity: j['school_city'] ?? '',
  );

  // ── Auth ─────────────────────────────────────────────────────────────────
  static Future<String> register(String email, String password, String name) async {
    final res = await http.post(
      Uri.parse('$_apiBase/auth/register'),
      headers: {'Content-Type': 'application/json; charset=utf-8'},
      body: jsonEncode({'email': email, 'password': password, 'name': name}),
    );
    final body = _decode(res);
    return body['message'] as String;
  }

  static Future<UserProfile> login(String email, String password) async {
    final res = await http.post(
      Uri.parse('$_apiBase/auth/login'),
      headers: {'Content-Type': 'application/json; charset=utf-8'},
      body: jsonEncode({'email': email, 'password': password}),
    );
    final body = _decode(res);
    await saveToken(body['access_token'] as String);
    return getMe();
  }

  static Future<UserProfile> getMe() async {
    final res = await http.get(Uri.parse('$_apiBase/users/me'), headers: _headers);
    return _profileFromJson(_decode(res));
  }

  static Future<UserProfile> updateProfile(UserProfile p) async {
    final res = await http.put(
      Uri.parse('$_apiBase/users/me'),
      headers: _headers,
      body: jsonEncode({
        'name': p.name,
        'sports': p.sports,
        'sport_level': p.sportLevel,
        'job': p.job,
        'company': p.company,
        'company_city': p.companyCity,
        'age': p.age,
        'studies': p.studies,
        'school': p.school,
        'school_city': p.schoolCity,
      }),
    );
    return _profileFromJson(_decode(res));
  }
}

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
  String name, email, sportLevel, job, company, age, studies, school, schoolCity, companyCity;
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
    this.schoolCity = '',
    this.companyCity = '',
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
  UserProfile? _user;
  bool _loading = true; // true pendant la vérification du JWT stocké

  @override
  void initState() {
    super.initState();
    _tryAutoLogin();
  }

  Future<void> _tryAutoLogin() async {
    try {
      final hasToken = await ApiService.tryLoadToken();
      if (hasToken) {
        final profile = await ApiService.getMe();
        if (mounted) setState(() => _user = profile);
      }
    } catch (_) {
      // Token expiré ou invalide — on efface et on affiche le login
      await ApiService.clearToken();
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _login(UserProfile user) => setState(() => _user = user);

  Future<void> _logout() async {
    await ApiService.clearToken();
    if (mounted) setState(() => _user = null);
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }
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
  bool _isLogin = true;
  bool _loading = false;
  // Affiché après inscription réussie (en attente de vérification email)
  String? _pendingVerificationEmail;

  final _nameCtrl  = TextEditingController();
  final _emailCtrl = TextEditingController();
  final _passCtrl  = TextEditingController();

  @override
  void dispose() {
    _nameCtrl.dispose();
    _emailCtrl.dispose();
    _passCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final email = _emailCtrl.text.trim();
    final pass  = _passCtrl.text.trim();
    final name  = _nameCtrl.text.trim();

    if (email.isEmpty || pass.isEmpty) return;
    if (!_isLogin && name.isEmpty) return;

    setState(() => _loading = true);
    try {
      if (_isLogin) {
        final profile = await ApiService.login(email, pass);
        widget.onLogin(profile);
      } else {
        await ApiService.register(email, pass, name);
        if (mounted) setState(() => _pendingVerificationEmail = email);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(e.toString().replaceFirst('Exception: ', '')),
            backgroundColor: Colors.red[700],
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;

    // ── État : inscription réussie, vérification en attente ──
    if (_pendingVerificationEmail != null) {
      return Scaffold(
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.mark_email_unread_outlined, size: 72, color: primary),
                const SizedBox(height: 24),
                const Text('Vérifiez votre email',
                    style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
                const SizedBox(height: 12),
                Text(
                  'Un lien de confirmation a été envoyé à\n$_pendingVerificationEmail\n\nCliquez sur ce lien pour activer votre compte.',
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 15, color: Colors.grey, height: 1.6),
                ),
                const SizedBox(height: 32),
                OutlinedButton.icon(
                  icon: const Icon(Icons.arrow_back),
                  label: const Text('Retour à la connexion'),
                  onPressed: () => setState(() {
                    _pendingVerificationEmail = null;
                    _isLogin = true;
                    _passCtrl.clear();
                  }),
                ),
              ],
            ),
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        actions: [
          TextButton(
            onPressed: _loading ? null : () => setState(() => _isLogin = !_isLogin),
            child: Text(_isLogin ? 'S\'inscrire' : 'Se connecter'),
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
              Icon(Icons.sports, size: 64, color: primary),
              const SizedBox(height: 12),
              Text('Meet2Play',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold, color: primary)),
              const SizedBox(height: 8),
              Text(
                _isLogin ? 'Connexion' : 'Créer un compte',
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 16, color: Colors.grey),
              ),
              const SizedBox(height: 40),

              if (!_isLogin) ...[
                TextField(
                  controller: _nameCtrl,
                  decoration: _inputDeco('Nom complet', Icons.person_outline),
                ),
                const SizedBox(height: 14),
              ],

              TextField(
                controller: _emailCtrl,
                keyboardType: TextInputType.emailAddress,
                decoration: _inputDeco('Email', Icons.email_outlined),
              ),
              const SizedBox(height: 14),

              TextField(
                controller: _passCtrl,
                obscureText: true,
                decoration: _inputDeco(
                  _isLogin ? 'Mot de passe' : 'Mot de passe (min. 6 caractères)',
                  Icons.lock_outline,
                ),
              ),
              const SizedBox(height: 28),

              FilledButton(
                onPressed: _loading ? null : _submit,
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  child: _loading
                      ? const SizedBox(
                          width: 20, height: 20,
                          child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2),
                        )
                      : Text(
                          _isLogin ? 'Se connecter' : 'S\'inscrire',
                          style: const TextStyle(fontSize: 16),
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

  final _pages = const [SportsPage(), ClubsMapPage(), ProfilesPage(), JobsPage(), ConversationListPage(), MyProfilePage()];
  final _titles = const ['Sports', 'Clubs', 'Profils', 'Offres', 'Messages', 'Mon profil'];

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
          NavigationDestination(icon: Icon(Icons.map_outlined), label: ''),
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
  String? _schoolCity;
  String? _companyCity;

  // Options des dropdowns
  static const _cities = [
    'Paris', 'Lyon', 'Marseille', 'Toulouse', 'Bordeaux',
    'Lille', 'Nice', 'Nantes', 'Strasbourg', 'Montpellier',
    'Rennes', 'Grenoble',
  ];
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
    _schoolCity  = _cities.contains(u.schoolCity) ? u.schoolCity : null;
    _companyCity = _cities.contains(u.companyCity) ? u.companyCity : null;
  }

  Future<void> _save() async {
    final u = AppState.of(context).user;
    u.name        = _nameCtrl.text.trim();
    u.sports      = _selectedSports.whereType<String>().toList();
    u.job         = _jobCtrl.text.trim();
    u.company     = _companyCtrl.text.trim();
    u.school      = _schoolCtrl.text.trim();
    u.age         = _age ?? '';
    u.sportLevel  = _selectedSportLevels.whereType<String>().join(', ');
    u.studies     = _studies ?? '';
    u.schoolCity  = _schoolCity ?? '';
    u.companyCity = _companyCity ?? '';
    setState(() {});

    try {
      await ApiService.updateProfile(u);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Profil sauvegardé ✓'), behavior: SnackBarBehavior.floating),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Erreur : ${e.toString().replaceFirst("Exception: ", "")}'),
            backgroundColor: Colors.red[700],
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    }
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
          _dropdown(_companyCity, 'Ville de l\'entreprise', Icons.location_city_outlined, _cities, (v) => setState(() => _companyCity = v)),

          // ── Section : Études ──
          _sectionTitle('Études'),
          _dropdown(_studies, 'Diplôme', Icons.school_outlined, _diplomas, (v) => setState(() => _studies = v)),
          _field(_schoolCtrl, 'École / Université', Icons.account_balance_outlined),
          _dropdown(_schoolCity, 'Ville de l\'école', Icons.location_city_outlined, _cities, (v) => setState(() => _schoolCity = v)),

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

// ─── Modèle : terrain / lieu de pratique ─────────────────────────────────────

class SportVenue {
  final String name, city, address, sport, openingHours, phone, bookingUrl;
  final int count; // nb terrains / trous / couloirs
  final double lat, lng;

  const SportVenue({
    required this.name,
    required this.city,
    required this.address,
    required this.sport,
    required this.count,
    required this.openingHours,
    this.phone = '',
    this.bookingUrl = '',
    required this.lat,
    required this.lng,
  });
}

// ─── Centres des villes ───────────────────────────────────────────────────────

const _cityCenters = {
  'Paris':        LatLng(48.8566,  2.3522),
  'Lyon':         LatLng(45.7640,  4.8357),
  'Marseille':    LatLng(43.2965,  5.3698),
  'Bordeaux':     LatLng(44.8378, -0.5792),
  'Lille':        LatLng(50.6292,  3.0573),
  'Nice':         LatLng(43.7102,  7.2620),
  'Nantes':       LatLng(47.2184, -1.5536),
  'Toulouse':     LatLng(43.6047,  1.4442),
  'Strasbourg':   LatLng(48.5734,  7.7521),
  'Montpellier':  LatLng(43.6108,  3.8767),
  'Rennes':       LatLng(48.1173, -1.6778),
  'Grenoble':     LatLng(45.1885,  5.7245),
};

// ─── Couleurs par sport ───────────────────────────────────────────────────────

const _sportColors = <String, Color>{
  'Football':   Color(0xFF16A34A),
  'Tennis':     Color(0xFFB45309),
  'Golf':       Color(0xFF166534),
  'Natation':   Color(0xFF0369A1),
  'Basketball': Color(0xFFEA580C),
  'Padel':      Color(0xFF7C3AED),
  'Rugby':      Color(0xFF9F1239),
  'Volleyball': Color(0xFF0891B2),
  'Handball':   Color(0xFFD97706),
  'Cyclisme':   Color(0xFF0F766E),
  'Badminton':  Color(0xFFDB2777),
};

Color _colorOf(String sport) => _sportColors[sport] ?? sky;

// ─── Données des terrains ─────────────────────────────────────────────────────

const _allVenues = <SportVenue>[
  // ── Paris · Football ──
  SportVenue(name: 'Terrain synthétique Charléty', city: 'Paris', sport: 'Football',
    address: 'Stade Charléty, 99 Bd Kellermann, 75013 Paris', count: 3,
    openingHours: 'Lun–Sam 9h–22h', phone: '01 44 16 60 00',
    bookingUrl: 'https://www.paris.fr/equipements/stade-charlety-2776',
    lat: 48.8186, lng: 2.3615),
  SportVenue(name: 'Terrain synthétique Boutroux', city: 'Paris', sport: 'Football',
    address: '117 Av. d\'Ivry, 75013 Paris', count: 2,
    openingHours: 'Lun–Ven 14h–22h · Sam–Dim 9h–19h', phone: '01 45 86 57 57',
    bookingUrl: 'https://www.paris.fr/equipements/stade-boutroux-2756',
    lat: 48.8223, lng: 2.3686),
  SportVenue(name: 'Urban Soccer Paris Nation', city: 'Paris', sport: 'Football',
    address: '1 Rue de Lagny, 75020 Paris', count: 6,
    openingHours: 'Lun–Dim 8h–23h', phone: '01 43 79 84 84',
    bookingUrl: 'https://www.urban-soccer.com/paris-nation',
    lat: 48.8519, lng: 2.4031),
  // ── Paris · Tennis ──
  SportVenue(name: 'Courts de la Croix Catelan', city: 'Paris', sport: 'Tennis',
    address: 'Allée de la Croix Catelan, 75016 Paris', count: 18,
    openingHours: 'Lun–Dim 8h–21h (sur réservation)',
    bookingUrl: 'https://tennis.paris.fr',
    lat: 48.8646, lng: 2.2559),
  SportVenue(name: 'Courts du Jardin du Luxembourg', city: 'Paris', sport: 'Tennis',
    address: 'Bd Saint-Michel, 75006 Paris', count: 6,
    openingHours: 'Mar–Dim 9h–20h (saison estivale)',
    bookingUrl: 'https://tennis.paris.fr',
    lat: 48.8479, lng: 2.3366),
  SportVenue(name: 'Tennis Montmartre', city: 'Paris', sport: 'Tennis',
    address: '2 Allée de la Carrière, 75018 Paris', count: 4,
    openingHours: 'Lun–Dim 8h–22h', phone: '01 42 55 28 10',
    bookingUrl: 'https://tennis.paris.fr',
    lat: 48.8873, lng: 2.3414),
  // ── Paris · Golf ──
  SportVenue(name: 'Golf de Paris Longchamp', city: 'Paris', sport: 'Golf',
    address: 'Route du Champ d\'Entraînement, 75016 Paris', count: 18,
    openingHours: 'Lun–Dim 8h–20h (selon saison)', phone: '01 44 30 70 00',
    bookingUrl: 'https://www.golfnow.com/courses/65204',
    lat: 48.8575, lng: 2.2396),
  SportVenue(name: 'Golf du Bois de Vincennes', city: 'Paris', sport: 'Golf',
    address: 'Av. du Polygone, 75012 Paris', count: 18,
    openingHours: 'Lun–Dim 7h30–19h', phone: '01 43 28 04 26',
    bookingUrl: 'https://www.golfnow.com/courses/65239',
    lat: 48.8319, lng: 2.4356),
  // ── Paris · Natation ──
  SportVenue(name: 'Piscine de la Butte aux Cailles', city: 'Paris', sport: 'Natation',
    address: '5 Pl. Paul Verlaine, 75013 Paris', count: 3,
    openingHours: 'Mar–Sam 7h–21h · Dim 8h–17h', phone: '01 45 89 60 05',
    bookingUrl: 'https://www.paris.fr/lieux/piscine-de-la-butte-aux-cailles-2971',
    lat: 48.8243, lng: 2.3517),
  SportVenue(name: 'Piscine Georges Vallerey', city: 'Paris', sport: 'Natation',
    address: '148 Av. Gambetta, 75020 Paris', count: 2,
    openingHours: 'Lun 12h–21h · Mar–Ven 7h–21h · Sam–Dim 8h–19h', phone: '01 40 31 15 20',
    bookingUrl: 'https://www.paris.fr/lieux/piscine-georges-vallerey-2981',
    lat: 48.8757, lng: 2.4051),
  SportVenue(name: 'Aquaboulevard', city: 'Paris', sport: 'Natation',
    address: '4-6 Rue Louis Armand, 75015 Paris', count: 2,
    openingHours: 'Lun–Jeu 9h–23h · Ven 9h–00h · Sam 8h–00h · Dim 8h–23h', phone: '01 40 60 10 00',
    bookingUrl: 'https://www.aquaboulevard.fr/natation',
    lat: 48.8337, lng: 2.2812),
  // ── Paris · Basketball ──
  SportVenue(name: 'Terrain Duperré', city: 'Paris', sport: 'Basketball',
    address: '13 Rue Duperre, 75009 Paris', count: 1,
    openingHours: 'Lun–Dim 9h–22h (accès libre)',
    lat: 48.8829, lng: 2.3387),
  SportVenue(name: 'Terrain Pigalle Basketball', city: 'Paris', sport: 'Basketball',
    address: '22 Bd de Clichy, 75018 Paris', count: 1,
    openingHours: 'Lun–Dim 9h–22h (accès libre)',
    bookingUrl: 'https://www.pigallebasketball.com',
    lat: 48.8836, lng: 2.3405),
  // ── Paris · Padel ──
  SportVenue(name: 'Padl Paris 17', city: 'Paris', sport: 'Padel',
    address: '42 Rue Guersant, 75017 Paris', count: 4,
    openingHours: 'Lun–Dim 7h–23h', phone: '01 40 55 00 00',
    bookingUrl: 'https://www.padl.fr/clubs/paris-17',
    lat: 48.8855, lng: 3.1530),
  SportVenue(name: 'Padel Club Paris Est', city: 'Paris', sport: 'Padel',
    address: '38 Av. du Général de Gaulle, 94160 Saint-Mandé', count: 5,
    openingHours: 'Lun–Dim 7h–23h', phone: '01 43 65 15 15',
    bookingUrl: 'https://www.padel-paris-est.fr/reservation',
    lat: 48.8453, lng: 2.4213),
  // ── Lyon · Football ──
  SportVenue(name: 'Terrains annexes Gerland', city: 'Lyon', sport: 'Football',
    address: '353 Av. Jean Jaurès, 69007 Lyon', count: 4,
    openingHours: 'Lun–Sam 9h–21h', phone: '04 72 76 35 35',
    bookingUrl: 'https://www.lyon.fr/equipement/stade-de-gerland',
    lat: 45.7328, lng: 4.8272),
  SportVenue(name: 'Urban Soccer Lyon Gerland', city: 'Lyon', sport: 'Football',
    address: '165 Rue Challemel Lacour, 69007 Lyon', count: 6,
    openingHours: 'Lun–Dim 8h–23h', phone: '04 72 70 80 90',
    bookingUrl: 'https://www.urban-soccer.com/lyon',
    lat: 45.7261, lng: 4.8281),
  // ── Lyon · Tennis ──
  SportVenue(name: 'Courts du Parc de la Tête d\'Or', city: 'Lyon', sport: 'Tennis',
    address: 'Parc de la Tête d\'Or, 69006 Lyon', count: 12,
    openingHours: 'Mar–Dim 8h–21h (saison)', phone: '04 72 69 47 60',
    bookingUrl: 'https://www.tennis-lyon.fr',
    lat: 45.7735, lng: 4.8558),
  SportVenue(name: 'Lyon Lawn Tennis Club', city: 'Lyon', sport: 'Tennis',
    address: '16 Rue Waldeck Rousseau, 69006 Lyon', count: 14,
    openingHours: 'Lun–Ven 8h–22h · Sam–Dim 8h–20h', phone: '04 78 93 52 28',
    bookingUrl: 'https://www.lltc.fr/reservation',
    lat: 45.7684, lng: 4.8555),
  // ── Lyon · Golf ──
  SportVenue(name: 'Golf de Lyon Villette d\'Anthon', city: 'Lyon', sport: 'Golf',
    address: '38280 Villette-d\'Anthon', count: 27,
    openingHours: 'Lun–Dim 7h30–Coucher du soleil', phone: '04 78 31 11 33',
    bookingUrl: 'https://www.golflyon.com/reservation',
    lat: 45.8214, lng: 5.0578),
  // ── Lyon · Natation ──
  SportVenue(name: 'Centre Nautique Tony Bertrand', city: 'Lyon', sport: 'Natation',
    address: '19 Quai Claude Bernard, 69007 Lyon', count: 8,
    openingHours: 'Mar–Ven 7h–21h · Sam 8h–18h · Dim 8h–17h', phone: '04 78 72 54 42',
    bookingUrl: 'https://www.lyon.fr/lieux/centre-nautique-tony-bertrand',
    lat: 45.7485, lng: 4.8361),
  SportVenue(name: 'Piscine Garibaldi', city: 'Lyon', sport: 'Natation',
    address: '221 Rue Garibaldi, 69003 Lyon', count: 6,
    openingHours: 'Mar–Sam 7h–20h · Dim 8h–17h', phone: '04 78 54 36 78',
    bookingUrl: 'https://www.lyon.fr/lieux/piscine-garibaldi',
    lat: 45.7546, lng: 4.8527),
  // ── Lyon · Padel ──
  SportVenue(name: 'Padel Lyon Est', city: 'Lyon', sport: 'Padel',
    address: '8 Rue des Frères Lumière, 69120 Vaulx-en-Velin', count: 4,
    openingHours: 'Lun–Dim 7h–23h', phone: '04 72 05 45 45',
    bookingUrl: 'https://www.padellyon.fr/reservation',
    lat: 45.7754, lng: 4.9232),
  // ── Marseille · Football ──
  SportVenue(name: 'Terrain synthétique Vallier', city: 'Marseille', sport: 'Football',
    address: '49 Bd Vallier, 13004 Marseille', count: 2,
    openingHours: 'Lun–Sam 9h–22h', phone: '04 91 14 68 00',
    bookingUrl: 'https://www.marseille.fr/sports/terrains',
    lat: 43.3102, lng: 5.3883),
  SportVenue(name: 'Urban Soccer Marseille', city: 'Marseille', sport: 'Football',
    address: '30 Av. de Frais Vallon, 13013 Marseille', count: 5,
    openingHours: 'Lun–Dim 8h–23h', phone: '04 91 68 45 45',
    bookingUrl: 'https://www.urban-soccer.com/marseille',
    lat: 43.3394, lng: 5.4108),
  // ── Marseille · Tennis ──
  SportVenue(name: 'Courts de tennis Parc Borély', city: 'Marseille', sport: 'Tennis',
    address: 'Parc Borély, 13008 Marseille', count: 8,
    openingHours: 'Mar–Dim 9h–20h', phone: '04 91 55 93 22',
    bookingUrl: 'https://www.marseille.fr/sports/tennis',
    lat: 43.2635, lng: 5.3796),
  SportVenue(name: 'Tennis Club de Marseille', city: 'Marseille', sport: 'Tennis',
    address: '5 Av. Pierre Mendès France, 13008 Marseille', count: 16,
    openingHours: 'Lun–Ven 8h–21h · Sam–Dim 8h–19h', phone: '04 91 71 22 80',
    bookingUrl: 'https://www.tcmarseille.fr/reservation',
    lat: 43.2655, lng: 5.3814),
  // ── Marseille · Golf ──
  SportVenue(name: 'Golf de Marseille La Salette', city: 'Marseille', sport: 'Golf',
    address: '65 Imp. des Escadrilles, 13011 Marseille', count: 18,
    openingHours: 'Lun–Dim 7h30–Coucher du soleil', phone: '04 91 27 12 16',
    bookingUrl: 'https://www.golflasalette.com/reservation',
    lat: 43.3037, lng: 5.4481),
  // ── Marseille · Natation ──
  SportVenue(name: 'Piscine Paul Boyé', city: 'Marseille', sport: 'Natation',
    address: '11 Impasse Fleming, 13013 Marseille', count: 6,
    openingHours: 'Lun–Ven 12h–20h · Sam–Dim 9h–18h', phone: '04 91 70 10 50',
    bookingUrl: 'https://www.marseille.fr/sports/piscines',
    lat: 43.3378, lng: 5.4058),
  // ── Bordeaux · Football ──
  SportVenue(name: 'Terrain synthétique Bordeaux Lac', city: 'Bordeaux', sport: 'Football',
    address: 'Av. du Parc des Expositions, 33300 Bordeaux', count: 3,
    openingHours: 'Lun–Sam 9h–22h', phone: '05 56 43 17 82',
    bookingUrl: 'https://www.bordeaux.fr/p26065/sports',
    lat: 44.8891, lng: -0.5736),
  SportVenue(name: 'Urban Soccer Bordeaux', city: 'Bordeaux', sport: 'Football',
    address: '4 Rue Aristide Bergès, 33700 Mérignac', count: 6,
    openingHours: 'Lun–Dim 8h–23h', phone: '05 56 34 40 40',
    bookingUrl: 'https://www.urban-soccer.com/bordeaux',
    lat: 44.8397, lng: -0.6477),
  // ── Bordeaux · Tennis ──
  SportVenue(name: 'Courts de tennis Albert', city: 'Bordeaux', sport: 'Tennis',
    address: 'Parc Albert, 33000 Bordeaux', count: 6,
    openingHours: 'Mar–Dim 9h–20h', phone: '05 56 92 74 56',
    bookingUrl: 'https://www.bordeaux.fr/p26065/tennis',
    lat: 44.8404, lng: -0.5820),
  SportVenue(name: 'Tennis Club Bordelais', city: 'Bordeaux', sport: 'Tennis',
    address: '103 Cours du Maréchal Gallieni, 33000 Bordeaux', count: 18,
    openingHours: 'Lun–Ven 8h–22h · Sam–Dim 8h–20h', phone: '05 56 96 99 70',
    bookingUrl: 'https://www.tennisclubbordelais.com/reservation',
    lat: 44.8336, lng: -0.6023),
  // ── Bordeaux · Golf ──
  SportVenue(name: 'Golf de Bordeaux-Lac', city: 'Bordeaux', sport: 'Golf',
    address: 'Av. de Pernon, 33300 Bordeaux', count: 18,
    openingHours: 'Lun–Dim 8h–Coucher du soleil', phone: '05 56 50 92 72',
    bookingUrl: 'https://www.golfdebordeaux.fr/reservation',
    lat: 44.8880, lng: -0.5789),
  // ── Bordeaux · Natation ──
  SportVenue(name: 'Piscine Judaïque', city: 'Bordeaux', sport: 'Natation',
    address: '167 Rue Judaïque, 33000 Bordeaux', count: 4,
    openingHours: 'Lun–Ven 7h–21h · Sam 8h–18h · Dim 9h–17h', phone: '05 56 51 96 12',
    bookingUrl: 'https://www.bordeaux.fr/p26065/piscines',
    lat: 44.8448, lng: -0.5941),
  // ── Bordeaux · Padel ──
  SportVenue(name: 'Padel Bordeaux Mériadeck', city: 'Bordeaux', sport: 'Padel',
    address: '18 Rue du Cardinal Richaud, 33000 Bordeaux', count: 4,
    openingHours: 'Lun–Dim 7h30–23h', phone: '05 33 09 07 07',
    bookingUrl: 'https://www.padl.fr/clubs/bordeaux',
    lat: 44.8393, lng: -0.5857),
  // ── Lille · Football ──
  SportVenue(name: 'Terrain synthétique Lille Sud', city: 'Lille', sport: 'Football',
    address: '1 Av. du Bois Blancs, 59000 Lille', count: 3,
    openingHours: 'Lun–Sam 9h–22h', phone: '03 20 49 50 00',
    bookingUrl: 'https://www.lille.fr/Sports/Equipements-sportifs',
    lat: 50.6092, lng: 3.0455),
  SportVenue(name: 'Urban Soccer Lille', city: 'Lille', sport: 'Football',
    address: 'ZA du Château, 59290 Wasquehal', count: 6,
    openingHours: 'Lun–Dim 8h–23h', phone: '03 20 65 90 90',
    bookingUrl: 'https://www.urban-soccer.com/lille',
    lat: 50.6698, lng: 3.1487),
  // ── Lille · Tennis ──
  SportVenue(name: 'Courts de tennis Bourgogne', city: 'Lille', sport: 'Tennis',
    address: 'Rue de Bourgogne, 59000 Lille', count: 6,
    openingHours: 'Mar–Dim 9h–20h', phone: '03 20 05 42 00',
    bookingUrl: 'https://www.lille.fr/Sports/Tennis',
    lat: 50.6362, lng: 3.0634),
  SportVenue(name: 'Tennis Club de Lille Villeneuve d\'Ascq', city: 'Lille', sport: 'Tennis',
    address: '83 Av. Paul Langevin, 59650 Villeneuve-d\'Ascq', count: 12,
    openingHours: 'Lun–Ven 8h–22h · Sam–Dim 8h–20h', phone: '03 20 91 36 47',
    bookingUrl: 'https://www.tclille.fr/reservation',
    lat: 50.6116, lng: 3.1425),
  // ── Lille · Golf ──
  SportVenue(name: 'Golf de Bondues', city: 'Lille', sport: 'Golf',
    address: 'Château de la Vigne, 59910 Bondues', count: 36,
    openingHours: 'Lun–Dim 7h30–Coucher du soleil', phone: '03 20 23 20 62',
    bookingUrl: 'https://www.golfdebondues.com/reservation',
    lat: 50.7017, lng: 3.1033),
  // ── Lille · Natation ──
  SportVenue(name: 'Piscine Euralille', city: 'Lille', sport: 'Natation',
    address: '155 Av. Willy Brandt, 59777 Lille', count: 6,
    openingHours: 'Lun–Ven 7h–21h · Sam 8h–19h · Dim 9h–18h', phone: '03 20 30 38 00',
    bookingUrl: 'https://www.lille.fr/Sports/Piscines',
    lat: 50.6376, lng: 3.0762),
  // ── Lille · Basketball ──
  SportVenue(name: 'Salle omnisports Lille Métropole', city: 'Lille', sport: 'Basketball',
    address: '1 Bd des Cités Unies, 59000 Lille', count: 3,
    openingHours: 'Lun–Sam 9h–21h', phone: '03 20 14 15 00',
    bookingUrl: 'https://www.esbvl.fr',
    lat: 50.6183, lng: 3.0731),
  // ── Lille · Padel ──
  SportVenue(name: 'Padl Lille Lomme', city: 'Lille', sport: 'Padel',
    address: '65 Rue du Fg de Béthune, 59160 Lomme', count: 4,
    openingHours: 'Lun–Dim 7h–23h', phone: '03 20 22 33 44',
    bookingUrl: 'https://www.padl.fr/clubs/lille',
    lat: 50.6455, lng: 3.0155),
];

// ─── Page Carte des terrains ──────────────────────────────────────────────────

class ClubsMapPage extends StatefulWidget {
  const ClubsMapPage({super.key});

  @override
  State<ClubsMapPage> createState() => _ClubsMapPageState();
}

class _ClubsMapPageState extends State<ClubsMapPage> {
  final _mapController = MapController();

  LatLng _centerFor(Set<String> cities) {
    final cs = cities.where(_cityCenters.containsKey).map((c) => _cityCenters[c]!).toList();
    if (cs.isEmpty) return const LatLng(46.6, 2.3);
    if (cs.length == 1) return cs.first;
    return LatLng(
      cs.map((c) => c.latitude).reduce((a, b) => a + b) / cs.length,
      cs.map((c) => c.longitude).reduce((a, b) => a + b) / cs.length,
    );
  }

  double _zoomFor(Set<String> cities) {
    if (cities.length <= 1) return 12;
    final cs = cities.where(_cityCenters.containsKey).toList();
    if (cs.length < 2) return 12;
    final lats = cs.map((x) => _cityCenters[x]!.latitude);
    final lngs = cs.map((x) => _cityCenters[x]!.longitude);
    final diff = [
      lats.reduce((a, b) => a > b ? a : b) - lats.reduce((a, b) => a < b ? a : b),
      lngs.reduce((a, b) => a > b ? a : b) - lngs.reduce((a, b) => a < b ? a : b),
    ].reduce((a, b) => a > b ? a : b);
    if (diff > 5) return 5;
    if (diff > 2) return 7;
    return 9;
  }

  Widget _emptyState(IconData icon, String title, String msg) => Center(
    child: Padding(
      padding: const EdgeInsets.all(32),
      child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
        Icon(icon, size: 64, color: Colors.grey[400]),
        const SizedBox(height: 16),
        Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        Text(msg, textAlign: TextAlign.center, style: TextStyle(color: Colors.grey[600], height: 1.5)),
      ]),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final user = AppState.of(context).user;
    final cities = {user.schoolCity, user.companyCity}.where((c) => c.isNotEmpty).toSet();
    final userSports = user.sports.toSet();

    if (cities.isEmpty) return _emptyState(
      Icons.location_city_outlined, 'Configurez vos villes',
      'Rendez-vous dans Mon profil → Carrière et Études pour saisir la ville de votre entreprise et de votre école.',
    );

    if (userSports.isEmpty) return _emptyState(
      Icons.sports_outlined, 'Ajoutez vos sports',
      'Rendez-vous dans Mon profil → Sport pour sélectionner vos sports. Les terrains correspondants apparaîtront ici.',
    );

    final venues = _allVenues
        .where((v) => cities.contains(v.city) && userSports.contains(v.sport))
        .toList();

    // Sports distincts présents sur la carte (pour la légende)
    final visibleSports = venues.map((v) => v.sport).toSet().toList()..sort();

    return Stack(children: [
      FlutterMap(
        mapController: _mapController,
        options: MapOptions(initialCenter: _centerFor(cities), initialZoom: _zoomFor(cities)),
        children: [
          TileLayer(
            urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
            userAgentPackageName: 'com.meet2play.app',
          ),
          MarkerLayer(
            markers: venues.map((v) => Marker(
              point: LatLng(v.lat, v.lng),
              width: 44,
              height: 50,
              child: GestureDetector(
                onTap: () => showModalBottomSheet(
                  context: context,
                  isScrollControlled: true,
                  backgroundColor: Colors.transparent,
                  builder: (_) => _VenueDetailSheet(venue: v),
                ),
                child: _MapPin(color: _colorOf(v.sport), icon: _iconOf(v.sport)),
              ),
            )).toList(),
          ),
        ],
      ),
      // Légende par sport
      Positioned(
        top: 12, right: 12,
        child: _SportLegend(sports: visibleSports, count: venues.length),
      ),
    ]);
  }
}

// ─── Épingle de marker ────────────────────────────────────────────────────────

class _MapPin extends StatelessWidget {
  final Color color;
  final IconData icon;
  const _MapPin({required this.color, required this.icon});

  @override
  Widget build(BuildContext context) {
    return Stack(
      alignment: Alignment.topCenter,
      children: [
        Icon(Icons.place, color: color, size: 44,
            shadows: [Shadow(color: color.withOpacity(0.4), blurRadius: 6, offset: const Offset(0, 2))]),
        Padding(
          padding: const EdgeInsets.only(top: 5),
          child: Icon(icon, color: Colors.white, size: 17),
        ),
      ],
    );
  }
}

// ─── Légende par sport ────────────────────────────────────────────────────────

class _SportLegend extends StatelessWidget {
  final List<String> sports;
  final int count;
  const _SportLegend({required this.sports, required this.count});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.95),
        borderRadius: BorderRadius.circular(12),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.1), blurRadius: 8)],
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('$count terrain${count > 1 ? 's' : ''}',
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
        const SizedBox(height: 6),
        ...sports.map((s) => Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Icon(_iconOf(s), color: _colorOf(s), size: 14),
            const SizedBox(width: 6),
            Text(s, style: const TextStyle(fontSize: 12)),
          ]),
        )),
      ]),
    );
  }
}

// ─── Fiche détail d'un terrain ────────────────────────────────────────────────

class _VenueDetailSheet extends StatelessWidget {
  final SportVenue venue;
  const _VenueDetailSheet({required this.venue});

  String get _countLabel {
    final n = venue.count;
    switch (venue.sport) {
      case 'Golf':     return '$n trou${n > 1 ? 's' : ''}';
      case 'Natation': return '$n couloir${n > 1 ? 's' : ''}';
      case 'Cyclisme': return '$n piste${n > 1 ? 's' : ''}';
      default:         return '$n terrain${n > 1 ? 's' : ''}';
    }
  }

  @override
  Widget build(BuildContext context) {
    final color = _colorOf(venue.sport);

    return DraggableScrollableSheet(
      initialChildSize: 0.5,
      maxChildSize: 0.85,
      minChildSize: 0.3,
      expand: false,
      builder: (_, ctrl) => Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
        child: SingleChildScrollView(
          controller: ctrl,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(24, 12, 24, 36),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              // Poignée
              Center(child: Container(
                width: 40, height: 4,
                decoration: BoxDecoration(color: Colors.grey[300], borderRadius: BorderRadius.circular(2)),
              )),
              const SizedBox(height: 16),

              // En-tête : icône sport colorée + nom + badge sport
              Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Container(
                  width: 48, height: 48,
                  decoration: BoxDecoration(color: color.withOpacity(0.12), borderRadius: BorderRadius.circular(12)),
                  child: Icon(_iconOf(venue.sport), color: color, size: 26),
                ),
                const SizedBox(width: 12),
                Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(venue.name, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 4),
                  Wrap(spacing: 6, children: [
                    _Chip(label: venue.sport, color: color),
                    _Chip(label: venue.city, color: Colors.grey[700]!),
                  ]),
                ])),
              ]),
              const SizedBox(height: 18),

              // Infos de base
              _InfoRow(icon: Icons.location_on_outlined, text: venue.address),
              const SizedBox(height: 8),
              _InfoRow(icon: Icons.access_time_outlined, text: venue.openingHours),
              if (venue.phone.isNotEmpty) ...[
                const SizedBox(height: 8),
                _InfoRow(icon: Icons.phone_outlined, text: venue.phone),
              ],

              const SizedBox(height: 20),
              const Divider(),
              const SizedBox(height: 16),

              // Compteur de terrains
              Row(children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                  decoration: BoxDecoration(
                    color: color.withOpacity(0.10),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(mainAxisSize: MainAxisSize.min, children: [
                    Icon(_iconOf(venue.sport), color: color, size: 18),
                    const SizedBox(width: 8),
                    Text(_countLabel,
                        style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 15)),
                  ]),
                ),
              ]),
              const SizedBox(height: 20),

              // Bouton réserver
              if (venue.bookingUrl.isNotEmpty)
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    style: FilledButton.styleFrom(backgroundColor: color),
                    icon: const Icon(Icons.calendar_month_outlined),
                    label: const Text('Réserver un terrain'),
                    onPressed: () async {
                      final uri = Uri.parse(venue.bookingUrl);
                      if (!await launchUrl(uri, mode: LaunchMode.externalApplication)) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Impossible d\'ouvrir le lien')),
                          );
                        }
                      }
                    },
                  ),
                ),
            ]),
          ),
        ),
      ),
    );
  }
}

class _Chip extends StatelessWidget {
  final String label;
  final Color color;
  const _Chip({required this.label, required this.color});

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
    decoration: BoxDecoration(
      color: color.withOpacity(0.12),
      borderRadius: BorderRadius.circular(20),
    ),
    child: Text(label, style: TextStyle(fontSize: 11, color: color, fontWeight: FontWeight.w600)),
  );
}

// ─── Helpers sport ────────────────────────────────────────────────────────────

IconData _iconOf(String sport) {
  switch (sport) {
    case 'Football':   return Icons.sports_soccer;
    case 'Basketball': return Icons.sports_basketball;
    case 'Tennis':     return Icons.sports_tennis;
    case 'Golf':       return Icons.golf_course;
    case 'Natation':   return Icons.pool;
    case 'Padel':      return Icons.sports_tennis;
    case 'Rugby':      return Icons.sports_rugby;
    case 'Volleyball': return Icons.sports_volleyball;
    case 'Handball':   return Icons.sports_handball;
    case 'Cyclisme':   return Icons.directions_bike;
    case 'Badminton':  return Icons.sports_tennis;
    default:           return Icons.sports;
  }
}

// ─── Ligne d'info générique ───────────────────────────────────────────────────

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String text;
  const _InfoRow({required this.icon, required this.text});

  @override
  Widget build(BuildContext context) {
    return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Icon(icon, size: 18, color: Colors.grey[600]),
      const SizedBox(width: 8),
      Expanded(child: Text(text, style: const TextStyle(fontSize: 13, height: 1.4))),
    ]);
  }
}