import 'package:flutter/material.dart';
import 'dart:convert';
import 'dart:io';

void main() => runApp(const SkyApp());

// ─── Config ───────────────────────────────────────────────────────────────────

const kApi = 'http://localhost:8001';
const sky   = Color(0xFF0EA5E9);

// ─── HTTP helper ──────────────────────────────────────────────────────────────

Future<dynamic> _req(
  String method,
  String path, {
  String? token,
  Map? body,
}) async {
  final client = HttpClient();
  final uri    = Uri.parse('$kApi$path');

  final HttpClientRequest req;
  if (method == 'POST')       req = await client.postUrl(uri);
  else if (method == 'PUT')   req = await client.putUrl(uri);
  else if (method == 'PATCH') req = await client.patchUrl(uri);
  else                        req = await client.getUrl(uri);

  req.headers.set(HttpHeaders.contentTypeHeader, 'application/json');
  if (token != null) req.headers.set(HttpHeaders.authorizationHeader, 'Bearer $token');
  if (body  != null) req.write(jsonEncode(body));

  final resp     = await req.close();
  final respBody = await resp.transform(utf8.decoder).join();

  if (resp.statusCode >= 400) {
    try {
      final err = jsonDecode(respBody);
      throw Exception(err['detail'] ?? 'Erreur ${resp.statusCode}');
    } catch (_) {
      throw Exception('Erreur ${resp.statusCode}');
    }
  }
  return jsonDecode(respBody);
}

// ─── Modèles ──────────────────────────────────────────────────────────────────

class UserProfile {
  int    id;
  String name, email, sportLevel, job, company, age, studies, school;
  List<String> sports;

  UserProfile({
    this.id          = 0,
    this.name        = '',
    this.email       = '',
    this.sports      = const [],
    this.sportLevel  = '',
    this.job         = '',
    this.company     = '',
    this.age         = '',
    this.studies     = '',
    this.school      = '',
  });
}

class ConvPreview {
  final int    id, otherUserId, unreadCount;
  final String otherUserName, lastMessage;
  const ConvPreview({
    required this.id,
    required this.otherUserId,
    required this.otherUserName,
    required this.lastMessage,
    required this.unreadCount,
  });
}

class ChatMsg {
  final int    senderId;
  final String senderName, content, time;
  const ChatMsg({
    required this.senderId,
    required this.senderName,
    required this.content,
    required this.time,
  });
}

// ─── État global ──────────────────────────────────────────────────────────────

class AppState extends InheritedWidget {
  final UserProfile user;
  final String      token;
  final VoidCallback onLogout;

  const AppState({
    super.key,
    required this.user,
    required this.token,
    required this.onLogout,
    required super.child,
  });

  static AppState of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AppState>()!;

  @override
  bool updateShouldNotify(AppState old) => true;
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

// ─── Écran racine ─────────────────────────────────────────────────────────────

class AuthScreen extends StatefulWidget {
  const AuthScreen({super.key});
  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> {
  UserProfile? _user;
  String?      _token;

  void _login(String token, UserProfile user) =>
      setState(() { _token = token; _user = user; });

  void _logout() => setState(() { _token = null; _user = null; });

  @override
  Widget build(BuildContext context) {
    if (_user == null) return LoginPage(onLogin: _login);

    return AppState(
      user:     _user!,
      token:    _token!,
      onLogout: _logout,
      child:    const HomeScreen(),
    );
  }
}

// ─── Page connexion / inscription ─────────────────────────────────────────────

class LoginPage extends StatefulWidget {
  final void Function(String token, UserProfile user) onLogin;
  const LoginPage({super.key, required this.onLogin});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  bool    _isLogin = true;
  bool    _loading = false;
  String? _error;

  final _nameCtrl  = TextEditingController();
  final _emailCtrl = TextEditingController();
  final _passCtrl  = TextEditingController();

  Future<void> _submit() async {
    if (_emailCtrl.text.trim().isEmpty || _passCtrl.text.trim().isEmpty) return;
    setState(() { _loading = true; _error = null; });
    try {
      if (!_isLogin) {
        await _req('POST', '/auth/register', body: {
          'email':    _emailCtrl.text.trim(),
          'password': _passCtrl.text.trim(),
          'name':     _nameCtrl.text.trim(),
        });
      }
      final login = await _req('POST', '/auth/login', body: {
        'email':    _emailCtrl.text.trim(),
        'password': _passCtrl.text.trim(),
      });
      final token = login['access_token'] as String;
      final me    = await _req('GET', '/users/me', token: token);
      widget.onLogin(token, UserProfile(
        id:         me['id'],
        name:       me['name']        ?? '',
        email:      me['email'],
        sports:     (me['sports'] as List?)?.cast<String>() ?? [],
        sportLevel: me['sport_level'] ?? '',
        job:        me['job']         ?? '',
        company:    me['company']     ?? '',
        age:        me['age']         ?? '',
        studies:    me['studies']     ?? '',
        school:     me['school']      ?? '',
      ));
    } catch (e) {
      setState(() => _error = e.toString().replaceFirst('Exception: ', ''));
    } finally {
      setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        actions: [
          if (_isLogin)
            TextButton(
              onPressed: () => setState(() { _isLogin = false; _error = null; }),
              child: const Text('S\'inscrire'),
            ),
          if (!_isLogin)
            TextButton(
              onPressed: () => setState(() { _isLogin = true; _error = null; }),
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
              Icon(Icons.sports, size: 64, color: primary),
              const SizedBox(height: 12),
              Text('Meet2Play',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold, color: primary),
              ),
              const SizedBox(height: 8),
              Text(_isLogin ? 'Connexion' : 'Créer un compte',
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 16, color: Colors.grey),
              ),
              const SizedBox(height: 40),

              if (!_isLogin) ...[
                TextField(controller: _nameCtrl,  decoration: _deco('Nom complet', Icons.person_outline)),
                const SizedBox(height: 14),
              ],

              TextField(
                controller: _emailCtrl,
                keyboardType: TextInputType.emailAddress,
                decoration: _deco('Email', Icons.email_outlined),
              ),
              const SizedBox(height: 14),

              TextField(
                controller: _passCtrl,
                obscureText: true,
                decoration: _deco('Mot de passe', Icons.lock_outline),
                onSubmitted: (_) => _submit(),
              ),
              const SizedBox(height: 16),

              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Text(_error!, style: const TextStyle(color: Colors.red), textAlign: TextAlign.center),
                ),

              FilledButton(
                onPressed: _loading ? null : _submit,
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  child: _loading
                    ? const SizedBox(height: 20, width: 20,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                    : Text(_isLogin ? 'Se connecter' : 'S\'inscrire',
                        style: const TextStyle(fontSize: 16)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  InputDecoration _deco(String hint, IconData icon) => InputDecoration(
    hintText: hint,
    prefixIcon: Icon(icon),
    filled: true,
    fillColor: Colors.grey[100],
    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
  );
}

// ─── Écran principal ──────────────────────────────────────────────────────────

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});
  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _index = 0;

  final _pages  = const [SportsPage(), ProfilesPage(), JobsPage(), ConversationListPage(), MyProfilePage()];
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
          NavigationDestination(icon: Icon(Icons.sports_soccer_outlined),  label: ''),
          NavigationDestination(icon: Icon(Icons.people_outline),           label: ''),
          NavigationDestination(icon: Icon(Icons.work_outline),             label: ''),
          NavigationDestination(icon: Icon(Icons.chat_bubble_outline),      label: ''),
          NavigationDestination(icon: Icon(Icons.person_outline),           label: ''),
        ],
      ),
    );
  }
}

// ─── Sports ───────────────────────────────────────────────────────────────────

class SportPreview {
  final String name, place, time;
  const SportPreview(this.name, this.place, this.time);
}

const _sports = <SportPreview>[
  SportPreview('Football match',  'Parc',          '18:00'),
  SportPreview('Tennis training', 'Club',           'Tomorrow'),
  SportPreview('Golf session',    'Club de golf',   'Sat.'),
];

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
          title:    Text(s.name, style: const TextStyle(fontWeight: FontWeight.bold)),
          subtitle: Text(s.place),
          trailing: Text(s.time, style: const TextStyle(fontSize: 12, color: Colors.grey)),
        );
      },
    );
  }
}

// ─── Profils ──────────────────────────────────────────────────────────────────

class PlayerProfile {
  final String name, sport, job, bio, avatarUrl;
  const PlayerProfile(this.name, this.sport, this.job, this.bio, this.avatarUrl);
}

const _profiles = <PlayerProfile>[
  PlayerProfile('Antoine Dupont', 'Football', 'Data Scientist',
    'Analyse les performances des joueurs via la data. Travaille chez SportMetrics. Passionné par le machine learning appliqué au sport.',
    'https://i.pravatar.cc/150?u=10'),
  PlayerProfile('Sarah Mbeki', 'Tennis', 'Analyste Financier',
    'Gère les investissements d\'un fonds spécialisé dans les franchises sportives. CFA charterholder. Ex-joueuse de tennis universitaire.',
    'https://i.pravatar.cc/150?u=11'),
  PlayerProfile('Marc Leroy', 'Golf', 'Ingénieur Logiciel',
    'Développe des applis de tracking de performance pour les athlètes. 8 ans d\'expérience en Flutter et Python. Golfeur le week-end.',
    'https://i.pravatar.cc/150?u=12'),
  PlayerProfile('Inès Moreau', 'Basketball', 'Avocate — Droit du sport',
    'Spécialisée dans les contrats de transfert et les droits à l\'image. Représente des joueurs pros en Europe et aux États-Unis.',
    'https://i.pravatar.cc/150?u=13'),
  PlayerProfile('Lucas Ferreira', 'Football', 'Médecin du sport',
    'Suivi médical et prévention des blessures pour des clubs de Ligue 2. Titulaire d\'un DU de médecine du sport. Chercheur à mi-temps.',
    'https://i.pravatar.cc/150?u=14'),
  PlayerProfile('Camille Renard', 'Natation', 'Chef de projet Marketing',
    'Développe les partenariats entre marques et athlètes. Spécialisée dans le sponsoring sportif et les stratégies de personal branding.',
    'https://i.pravatar.cc/150?u=15'),
];

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
          leading:  CircleAvatar(backgroundImage: NetworkImage(p.avatarUrl)),
          title:    Text(p.name, style: const TextStyle(fontWeight: FontWeight.bold)),
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
                icon:  const Icon(Icons.chat_bubble_outline),
                label: const Text('Envoyer un message'),
                onPressed: () => _askUserId(context),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // Demande l'ID de l'utilisateur (les profils sont statiques pour l'instant)
  void _askUserId(BuildContext context) {
    final ctrl = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Contacter ${profile.name}'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Entrez l\'ID utilisateur Meet2Play :', style: TextStyle(fontSize: 13)),
            const SizedBox(height: 10),
            TextField(
              controller: ctrl,
              keyboardType: TextInputType.number,
              autofocus: true,
              decoration: const InputDecoration(labelText: 'ID utilisateur'),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Annuler')),
          FilledButton(
            onPressed: () async {
              final otherId = int.tryParse(ctrl.text);
              if (otherId == null) return;
              Navigator.pop(ctx);
              final token = AppState.of(context).token;
              try {
                final conv = await _req('POST', '/chat/conversations',
                    token: token, body: {'other_user_id': otherId});
                if (context.mounted) {
                  Navigator.push(context, MaterialPageRoute(
                    builder: (_) => ChatDetailScreen(convId: conv['id'], peerName: profile.name),
                  ));
                }
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                    content: Text(e.toString().replaceFirst('Exception: ', '')),
                  ));
                }
              }
            },
            child: const Text('Démarrer'),
          ),
        ],
      ),
    );
  }
}

// ─── Messagerie — liste des conversations ─────────────────────────────────────

class ConversationListPage extends StatefulWidget {
  const ConversationListPage({super.key});
  @override
  State<ConversationListPage> createState() => _ConversationListPageState();
}

class _ConversationListPageState extends State<ConversationListPage> {
  List<ConvPreview> _convs   = [];
  bool              _loading = true;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _load();
  }

  Future<void> _load() async {
    final token = AppState.of(context).token;
    setState(() => _loading = true);
    try {
      final data = await _req('GET', '/chat/conversations', token: token) as List;
      setState(() {
        _convs = data.map((c) => ConvPreview(
          id:            c['id'],
          otherUserId:   c['other_user_id'],
          otherUserName: c['other_user_name'],
          lastMessage:   c['last_message'] ?? '',
          unreadCount:   c['unread_count'] ?? 0,
        )).toList();
      });
    } catch (_) {}
    setState(() => _loading = false);
  }

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;

    if (_loading) return const Center(child: CircularProgressIndicator());

    return Stack(
      children: [
        if (_convs.isEmpty)
          const Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.chat_bubble_outline, size: 48, color: Colors.grey),
                SizedBox(height: 12),
                Text('Aucune conversation', style: TextStyle(color: Colors.grey)),
                SizedBox(height: 4),
                Text('Appuyez sur + pour commencer', style: TextStyle(color: Colors.grey, fontSize: 12)),
              ],
            ),
          )
        else
          RefreshIndicator(
            onRefresh: _load,
            child: ListView.separated(
              itemCount: _convs.length,
              separatorBuilder: (_, __) => const Divider(height: 1, indent: 70),
              itemBuilder: (context, i) {
                final c = _convs[i];
                return ListTile(
                  leading: CircleAvatar(
                    backgroundColor: primary.withOpacity(0.15),
                    child: Text(
                      c.otherUserName.isNotEmpty ? c.otherUserName[0].toUpperCase() : '?',
                      style: TextStyle(color: primary, fontWeight: FontWeight.bold),
                    ),
                  ),
                  title:    Text(c.otherUserName, style: const TextStyle(fontWeight: FontWeight.bold)),
                  subtitle: Text(
                    c.lastMessage.isNotEmpty ? c.lastMessage : 'Aucun message',
                    maxLines: 1, overflow: TextOverflow.ellipsis,
                  ),
                  trailing: c.unreadCount > 0
                    ? CircleAvatar(
                        radius: 10,
                        backgroundColor: primary,
                        child: Text('${c.unreadCount}',
                            style: const TextStyle(color: Colors.white, fontSize: 10)),
                      )
                    : null,
                  onTap: () async {
                    await Navigator.push(context, MaterialPageRoute(
                      builder: (_) => ChatDetailScreen(convId: c.id, peerName: c.otherUserName),
                    ));
                    _load();
                  },
                );
              },
            ),
          ),

        // Bouton nouvelle conversation
        Positioned(
          bottom: 16, right: 16,
          child: FloatingActionButton(
            onPressed: _showNewConvDialog,
            child: const Icon(Icons.edit),
          ),
        ),
      ],
    );
  }

  void _showNewConvDialog() {
    final ctrl = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Nouvelle conversation'),
        content: TextField(
          controller: ctrl,
          keyboardType: TextInputType.number,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: 'ID de l\'utilisateur',
            hintText:  'Ex : 2',
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Annuler')),
          FilledButton(
            onPressed: () async {
              final otherId = int.tryParse(ctrl.text);
              if (otherId == null) return;
              Navigator.pop(ctx);
              final token = AppState.of(context).token;
              try {
                final conv = await _req('POST', '/chat/conversations',
                    token: token, body: {'other_user_id': otherId});
                if (mounted) {
                  await Navigator.push(context, MaterialPageRoute(
                    builder: (_) => ChatDetailScreen(
                      convId:   conv['id'],
                      peerName: conv['other_user_name'],
                    ),
                  ));
                  _load();
                }
              } catch (e) {
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                    content: Text(e.toString().replaceFirst('Exception: ', '')),
                  ));
                }
              }
            },
            child: const Text('Démarrer'),
          ),
        ],
      ),
    );
  }
}

// ─── Messagerie — écran de conversation ──────────────────────────────────────

class ChatDetailScreen extends StatefulWidget {
  final int    convId;
  final String peerName;
  const ChatDetailScreen({super.key, required this.convId, required this.peerName});

  @override
  State<ChatDetailScreen> createState() => _ChatDetailScreenState();
}

class _ChatDetailScreenState extends State<ChatDetailScreen> {
  final _controller = TextEditingController();
  final _scrollCtrl = ScrollController();
  final List<ChatMsg> _messages = [];

  WebSocket? _ws;
  bool       _wsConnected = false;
  late String _token;
  late int    _myId;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final state = AppState.of(context);
    _token = state.token;
    _myId  = state.user.id;
    _loadHistory();
    _connectWS();
  }

  Future<void> _loadHistory() async {
    try {
      final data = await _req(
        'GET', '/chat/conversations/${widget.convId}/messages?limit=60',
        token: _token,
      ) as List;
      if (!mounted) return;
      setState(() {
        _messages.clear();
        for (final m in data) _messages.add(_toMsg(m));
      });
      _markRead();
      _scrollToBottom();
    } catch (_) {}
  }

  ChatMsg _toMsg(Map m) {
    final dt   = DateTime.tryParse(m['created_at'] ?? '') ?? DateTime.now();
    final time = '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
    return ChatMsg(
      senderId:   m['sender_id'],
      senderName: m['sender_name'] ?? '?',
      content:    m['content'],
      time:       time,
    );
  }

  Future<void> _connectWS() async {
    try {
      _ws = await WebSocket.connect(
        'ws://localhost:8001/chat/ws/${widget.convId}?token=${Uri.encodeComponent(_token)}',
      );
      if (!mounted) return;
      setState(() => _wsConnected = true);

      _ws!.listen(
        (data) {
          if (!mounted) return;
          setState(() => _messages.add(_toMsg(jsonDecode(data as String))));
          _markRead();
          _scrollToBottom();
        },
        onDone: () {
          if (mounted) setState(() => _wsConnected = false);
          Future.delayed(const Duration(seconds: 3), () { if (mounted) _connectWS(); });
        },
        onError: (_) {
          if (mounted) setState(() => _wsConnected = false);
        },
      );
    } catch (_) {
      if (mounted) setState(() => _wsConnected = false);
      Future.delayed(const Duration(seconds: 3), () { if (mounted) _connectWS(); });
    }
  }

  void _markRead() {
    _req('PATCH', '/chat/conversations/${widget.convId}/read', token: _token).catchError((_) {});
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollCtrl.hasClients) {
        _scrollCtrl.animateTo(
          _scrollCtrl.position.maxScrollExtent,
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOut,
        );
      }
    });
  }

  void _send() {
    final text = _controller.text.trim();
    if (text.isEmpty || _ws == null || !_wsConnected) return;
    _ws!.add(jsonEncode({'content': text}));
    _controller.clear();
  }

  @override
  void dispose() {
    _ws?.close();
    _controller.dispose();
    _scrollCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(widget.peerName),
            Text(
              _wsConnected ? '● Connecté' : '⏳ Connexion…',
              style: TextStyle(
                fontSize: 11,
                color: _wsConnected ? Colors.green : Colors.orange,
              ),
            ),
          ],
        ),
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView.builder(
              controller: _scrollCtrl,
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

  Widget _buildBubble(ChatMsg m, Color primary) {
    final mine = m.senderId == _myId;
    return Align(
      alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.75),
        child: Column(
          crossAxisAlignment: mine ? CrossAxisAlignment.end : CrossAxisAlignment.start,
          children: [
            if (!mine)
              Padding(
                padding: const EdgeInsets.only(left: 4, bottom: 2),
                child: Text(m.senderName, style: TextStyle(fontSize: 11, color: Colors.grey[600])),
              ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: mine ? primary : Colors.grey[200],
                borderRadius: BorderRadius.only(
                  topLeft:     const Radius.circular(18),
                  topRight:    const Radius.circular(18),
                  bottomLeft:  Radius.circular(mine ? 18 : 4),
                  bottomRight: Radius.circular(mine ? 4 : 18),
                ),
              ),
              child: Text(m.content, style: TextStyle(color: mine ? Colors.white : Colors.black)),
            ),
            Padding(
              padding: const EdgeInsets.only(top: 2, left: 4, right: 4),
              child: Text(m.time, style: TextStyle(fontSize: 10, color: Colors.grey[500])),
            ),
          ],
        ),
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
                enabled: _wsConnected,
                onSubmitted: (_) => _send(),
                decoration: InputDecoration(
                  hintText: _wsConnected ? 'Message...' : 'Connexion en cours…',
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
            IconButton(
              icon:      const Icon(Icons.send),
              color:     _wsConnected ? primary : Colors.grey,
              onPressed: _wsConnected ? _send : null,
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Mon Profil ───────────────────────────────────────────────────────────────

class MyProfilePage extends StatefulWidget {
  const MyProfilePage({super.key});
  @override
  State<MyProfilePage> createState() => _MyProfilePageState();
}

class _MyProfilePageState extends State<MyProfilePage> {
  late final TextEditingController _nameCtrl;
  late final TextEditingController _jobCtrl;
  late final TextEditingController _companyCtrl;
  late final TextEditingController _schoolCtrl;

  final List<String?> _selectedSports      = [null];
  final List<String?> _selectedSportLevels = [null];

  static const _sportOptions = [
    'Football', 'Basketball', 'Tennis', 'Rugby', 'Golf', 'Natation',
    'Cyclisme', 'Athletisme', 'Volleyball', 'Handball', 'Boxe',
    'Judo', 'Escalade', 'Ski', 'Surf', 'Padel', 'Badminton', 'Autre'
  ];
  static const _ages     = ['< 18 ans', '18-24 ans', '25-34 ans', '35-44 ans', '45-54 ans', '55 ans et +'];
  static const _levels   = ['Débutant', 'Intermédiaire', 'Confirmé', 'Amateur compétiteur', 'Semi-professionnel', 'Professionnel'];
  static const _diplomas = ['Brevet', 'Bac', 'Bac +2 (BTS / DUT)', 'Bac +3 (Licence)', 'Bac +4 (Master 1)', 'Bac +5 (Master / Grande école)', 'Doctorat', 'Autre'];

  String? _age;
  String? _studies;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final u       = AppState.of(context).user;
    _nameCtrl    = TextEditingController(text: u.name);
    _jobCtrl     = TextEditingController(text: u.job);
    _companyCtrl = TextEditingController(text: u.company);
    _schoolCtrl  = TextEditingController(text: u.school);
    if (u.sports.isNotEmpty) {
      _selectedSports.clear();
      _selectedSportLevels.clear();
      for (final s in u.sports) {
        _selectedSports.add(_sportOptions.contains(s) ? s : null);
        _selectedSportLevels.add(null);
      }
    }
    _age     = _ages.contains(u.age)         ? u.age     : null;
    _studies = _diplomas.contains(u.studies) ? u.studies : null;
  }

  Future<void> _save() async {
    final state = AppState.of(context);
    final u     = state.user;
    final token = state.token;

    u.name       = _nameCtrl.text.trim();
    u.sports     = _selectedSports.whereType<String>().toList();
    u.job        = _jobCtrl.text.trim();
    u.company    = _companyCtrl.text.trim();
    u.school     = _schoolCtrl.text.trim();
    u.age        = _age ?? '';
    u.sportLevel = _selectedSportLevels.whereType<String>().join(', ');
    u.studies    = _studies ?? '';

    try {
      await _req('PUT', '/users/me', token: token, body: {
        'name':        u.name,
        'sports':      u.sports,
        'sport_level': u.sportLevel,
        'job':         u.job,
        'company':     u.company,
        'age':         u.age,
        'studies':     u.studies,
        'school':      u.school,
      });
      if (mounted) {
        setState(() {});
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Profil sauvegardé ✓'), behavior: SnackBarBehavior.floating),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Erreur : $e'), behavior: SnackBarBehavior.floating),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final onLogout = AppState.of(context).onLogout;
    final primary  = Theme.of(context).colorScheme.primary;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Center(
            child: CircleAvatar(
              radius: 44,
              backgroundColor: primary.withOpacity(0.15),
              child: Icon(Icons.person, size: 44, color: primary),
            ),
          ),
          const SizedBox(height: 28),

          _sectionTitle('Infos personnelles'),
          _field(_nameCtrl, 'Nom complet', Icons.person_outline),
          _dropdown(_age, 'Âge', Icons.cake_outlined, _ages, (v) => setState(() => _age = v)),

          _sectionTitle('Sport'),
          ..._selectedSports.asMap().entries.map((entry) {
            final i = entry.key;
            return Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      value: _selectedSports[i],
                      hint: const Text('Sport'),
                      decoration: InputDecoration(
                        prefixIcon: const Icon(Icons.sports_tennis_outlined),
                        filled: true, fillColor: Colors.grey[100],
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                      ),
                      items: _sportOptions.map((o) => DropdownMenuItem(value: o, child: Text(o))).toList(),
                      onChanged: (v) => setState(() => _selectedSports[i] = v),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      value: _selectedSportLevels[i],
                      hint: const Text('Niveau'),
                      decoration: InputDecoration(
                        prefixIcon: const Icon(Icons.emoji_events_outlined),
                        filled: true, fillColor: Colors.grey[100],
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                      ),
                      items: _levels.map((o) => DropdownMenuItem(value: o, child: Text(o))).toList(),
                      onChanged: (v) => setState(() => _selectedSportLevels[i] = v),
                    ),
                  ),
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
          TextButton.icon(
            icon: const Icon(Icons.add),
            label: const Text('Ajouter un sport'),
            onPressed: () => setState(() { _selectedSports.add(null); _selectedSportLevels.add(null); }),
          ),

          _sectionTitle('Carrière'),
          _field(_jobCtrl,     'Poste (ex: Data Scientist)',  Icons.work_outline),
          _field(_companyCtrl, 'Entreprise',                  Icons.business_outlined),

          _sectionTitle('Études'),
          _dropdown(_studies, 'Diplôme', Icons.school_outlined, _diplomas, (v) => setState(() => _studies = v)),
          _field(_schoolCtrl, 'École / Université', Icons.account_balance_outlined),

          const SizedBox(height: 28),
          FilledButton.icon(icon: const Icon(Icons.check), label: const Text('Sauvegarder'), onPressed: _save),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            icon:  const Icon(Icons.logout, color: Colors.red),
            label: const Text('Se déconnecter', style: TextStyle(color: Colors.red)),
            style: OutlinedButton.styleFrom(side: const BorderSide(color: Colors.red)),
            onPressed: onLogout,
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  Widget _sectionTitle(String title) => Padding(
    padding: const EdgeInsets.only(top: 20, bottom: 10),
    child: Text(title, style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: Colors.grey[600], letterSpacing: 0.8)),
  );

  Widget _field(TextEditingController ctrl, String hint, IconData icon) =>
    Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextField(
        controller: ctrl,
        decoration: InputDecoration(
          hintText: hint,
          prefixIcon: Icon(icon),
          filled: true, fillColor: Colors.grey[100],
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
        ),
      ),
    );

  Widget _dropdown(String? value, String hint, IconData icon, List<String> options, void Function(String?) onChanged) =>
    Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: DropdownButtonFormField<String>(
        value: value,
        hint: Text(hint),
        decoration: InputDecoration(
          prefixIcon: Icon(icon),
          filled: true, fillColor: Colors.grey[100],
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
        ),
        items: options.map((o) => DropdownMenuItem(value: o, child: Text(o))).toList(),
        onChanged: onChanged,
      ),
    );
}

// ─── Offres d'emploi ──────────────────────────────────────────────────────────

class JobOffer {
  final String title, company, location, type, sport, description;
  const JobOffer(this.title, this.company, this.location, this.type, this.sport, this.description);
}

const _jobs = <JobOffer>[
  JobOffer('Data Scientist Sport', 'PSG Analytics', 'Paris · Présentiel', 'CDI', 'Football',
    'Analyse des données de performance des joueurs, modélisation prédictive des blessures, visualisation pour le staff technique.'),
  JobOffer('Analyste Financier', 'SportInvest Group', 'Lyon · Hybride', 'CDI', 'Tous sports',
    'Évaluation des franchises sportives, gestion de portefeuilles d\'actifs liés au sport, reporting investisseurs.'),
  JobOffer('Développeur Flutter', 'TrackPerf', 'Remote', 'Freelance', 'Tous sports',
    'Développement d\'une app mobile de suivi de performance pour athlètes amateurs. Stack : Flutter + Firebase.'),
  JobOffer('Responsable Marketing', 'Nike France', 'Paris · Hybride', 'CDI', 'Tous sports',
    'Stratégie de sponsoring, gestion des partenariats athlètes, campagnes digitales et activation terrain.'),
  JobOffer('Médecin du Sport', 'Olympique de Marseille', 'Marseille · Présentiel', 'CDI', 'Football',
    'Suivi médical de l\'équipe première, prévention et traitement des blessures, collaboration avec le staff technique.'),
  JobOffer('Agent Sportif Junior', 'ProAthletes Agency', 'Paris · Présentiel', 'Stage', 'Tennis',
    'Accompagnement des joueurs dans leur développement de carrière, négociation de contrats, prospection de sponsors.'),
  JobOffer('Ingénieur Biomécanique', 'Decathlon Lab', 'Lille · Hybride', 'CDI', 'Tous sports',
    'R&D sur l\'équipement sportif, analyse du mouvement humain, tests de performance produit en laboratoire.'),
  JobOffer('Chef de Projet Événementiel', 'Roland Garros', 'Paris · Présentiel', 'CDD', 'Tennis',
    'Organisation des tournois et événements annexes, coordination des prestataires, gestion des accréditations.'),
];

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
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => JobDetailScreen(job: job))),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  CircleAvatar(radius: 22, backgroundColor: primary.withOpacity(0.12), child: Icon(Icons.business, color: primary)),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(job.title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                        const SizedBox(height: 2),
                        Text(job.company, style: TextStyle(color: Colors.grey[600], fontSize: 13)),
                      ],
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(color: primary.withOpacity(0.12), borderRadius: BorderRadius.circular(8)),
                    child: Text(job.type, style: TextStyle(color: primary, fontSize: 11, fontWeight: FontWeight.bold)),
                  ),
                ],
              ),
              const SizedBox(height: 12),
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
              Text(job.description, maxLines: 2, overflow: TextOverflow.ellipsis,
                style: TextStyle(fontSize: 13, color: Colors.grey[700], height: 1.4)),
            ],
          ),
        ),
      ),
    );
  }
}

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
            Center(child: CircleAvatar(radius: 36, backgroundColor: primary.withOpacity(0.12), child: Icon(Icons.business, size: 36, color: primary))),
            const SizedBox(height: 16),
            Center(child: Text(job.title, textAlign: TextAlign.center, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold))),
            const SizedBox(height: 4),
            Center(child: Text(job.company, style: TextStyle(fontSize: 15, color: primary, fontWeight: FontWeight.w500))),
            const SizedBox(height: 16),
            Wrap(
              spacing: 8, runSpacing: 8, alignment: WrapAlignment.center,
              children: [
                _badge(job.type,     Icons.work_outline,         primary),
                _badge(job.location, Icons.location_on_outlined, primary),
                _badge(job.sport,    Icons.sports_outlined,      primary),
              ],
            ),
            const SizedBox(height: 24),
            const Divider(),
            const SizedBox(height: 16),
            const Text('Description du poste', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
            const SizedBox(height: 8),
            Text(job.description, style: const TextStyle(fontSize: 15, height: 1.7, color: Colors.black87)),
            const SizedBox(height: 32),
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                icon: const Icon(Icons.send),
                label: const Text('Postuler'),
                onPressed: () => ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Candidature envoyée ! ✓'), behavior: SnackBarBehavior.floating),
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
    decoration: BoxDecoration(color: color.withOpacity(0.10), borderRadius: BorderRadius.circular(20)),
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
