import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from 'react-native';
import { apiUrl, request, type Session } from './api';
import { saveToken } from './session';
import { Button, Field, Message } from './ui';
import { styles as s } from './theme';
import { router } from 'expo-router';
export function AuthScreen({ onLogin, onDemo, message = '' }: { onLogin: (session: Session) => void; onDemo: () => void; message?: string }) {
  const [signup, setSignup] = useState(false);
  const [nome, setNome] = useState(''); const [email, setEmail] = useState(''); const [senha, setSenha] = useState('');
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState('');
  async function submit() {
    setError(''); setNotice('');
    if (!apiUrl) { setError('Esta versão está sem conexão configurada com o servidor. Atualize o aplicativo ou reinicie o ambiente de desenvolvimento.'); return; }
    if (!email.trim() || senha.length < 6 || senha.length > 128 || (signup && !nome.trim())) { setError('Preencha os campos. Use uma senha com pelo menos 6 caracteres.'); return; }
    setBusy(true);
    try {
      if (signup) {
        await request('/auth/cadastro', { method: 'POST', body: { nome, email, senha } });
        setSignup(false); setSenha(''); setNotice('Conta criada. Entre para continuar.');
      } else {
        const session = await request<Session>('/auth/login', { method: 'POST', body: { email, senha } });
        try { await saveToken(session.token); }
        catch { await request('/auth/logout', { method: 'POST', token: session.token }).catch(() => {}); throw new Error('Não foi possível guardar a sessão com segurança.'); }
        setSenha(''); onLogin(session);
      }
    } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível entrar.'); }
    finally { setBusy(false); }
  }
  return <KeyboardAvoidingView style={s.root} behavior={Platform.OS === 'ios' ? 'padding' : undefined}><ScrollView contentContainerStyle={[s.content, { justifyContent: 'center' }]} keyboardShouldPersistTaps="handled">
    <Text style={s.title}>RolêOut</Text><Text style={s.subtitle}>{signup ? 'Crie sua conta e encontre seu próximo rolê.' : 'Entre para descobrir rolês que combinam com você.'}</Text>
    {signup && <Field label="Nome" value={nome} setValue={setNome} />}
    <Field label="E-mail" value={email} setValue={setEmail} email /><Field label={signup ? 'Senha · mínimo 6 caracteres' : 'Senha'} value={senha} setValue={setSenha} password />
    {notice ? <Text style={s.text}>{notice}</Text> : null}<Message text={error || message} />
    <Button title={signup ? 'Criar conta' : 'Entrar'} onPress={submit} busy={busy} />
    <Button title={signup ? 'Já tenho uma conta' : 'Criar minha conta'} secondary busy={busy} onPress={() => { setSignup(!signup); setError(''); setNotice(''); setSenha(''); }} />
    {!signup && <Button title="Esqueci minha senha" secondary disabled={busy} onPress={() => { setSenha(''); router.push('/recover-password'); }} />}
    {__DEV__ && <View><Button title="Conhecer a demonstração" secondary onPress={onDemo} busy={busy} /><Text style={[s.small, { marginTop: 8 }]}>Explore exemplos fictícios sem criar uma conta.</Text></View>}
  </ScrollView></KeyboardAvoidingView>;
}
