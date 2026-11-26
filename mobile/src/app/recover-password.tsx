import { useCallback, useRef, useState } from 'react';
import { ScrollView, Text, TextInput } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { request } from '../api';
import { Button, Field, Message } from '../ui';
import { styles as s } from '../theme';
import { FormKeyboardView } from '../FormKeyboardView';
import { useFormKeyboard } from '../useFormKeyboard';
import { recoveryCode, recoveryError } from '../recovery';

export default function RecoverPassword() {
  const [step, setStep] = useState<'email' | 'code' | 'done'>('email');
  const [email, setEmail] = useState(''), [code, setCode] = useState('');
  const [password, setPassword] = useState(''), [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const scroll = useRef<ScrollView>(null), focus = useFormKeyboard(scroll), generation = useRef(0), pending = useRef(false);
  useFocusEffect(useCallback(() => { generation.current++; setBusy(false); return () => { generation.current++; setCode(''); setPassword(''); setConfirmation(''); }; }, []));
  async function submit() {
    if (pending.current) return;
    const invalid = step === 'email' ? (!email.trim() ? 'Informe seu e-mail.' : '') : recoveryError(code, password, confirmation);
    if (invalid) { setError(invalid); return; }
    const current = generation.current; pending.current = true; setBusy(true); setError('');
    try {
      if (step === 'email') {
        const result = await request<{ mensagem: string }>('/auth/recuperacao', { method: 'POST', body: { email: email.trim() } });
        if (current === generation.current) { setNotice(result.mensagem); setStep('code'); }
      } else {
        await request('/auth/nova-senha', { method: 'POST', body: { codigo: recoveryCode(code), senha: password } });
        if (current === generation.current) { setCode(''); setPassword(''); setConfirmation(''); setNotice('Senha alterada. Entre novamente com sua nova senha.'); setStep('done'); }
      }
    } catch (e) { if (current === generation.current) setError(e instanceof Error ? e.message : 'Não foi possível concluir. Tente novamente.'); }
    finally { pending.current = false; if (current === generation.current) setBusy(false); }
  }
  return <FormKeyboardView><ScrollView ref={scroll} contentContainerStyle={s.content} keyboardShouldPersistTaps="handled">
    <Text style={s.title}>{step === 'done' ? 'Senha atualizada' : 'Recuperar senha'}</Text>
    {step === 'email' && <><Text style={s.subtitle}>Receba um código no e-mail da sua conta.</Text><Field label="E-mail" value={email} setValue={setEmail} email onFocus={focus} /></>}
    {!!notice && <Text accessibilityRole="alert" style={s.text}>{notice}</Text>}
    {step === 'code' && <>
      <Text style={s.small}>O código vale por 15 minutos. Cole o código do e-mail mais recente.</Text>
      <Text style={s.label}>Código de recuperação</Text><TextInput accessibilityLabel="Código de recuperação" value={code} onChangeText={setCode} maxLength={100} autoCapitalize="none" autoCorrect={false} onFocus={focus} style={s.field} />
      <Field label="Nova senha · mínimo 6 caracteres" value={password} setValue={setPassword} password onFocus={focus} />
      <Field label="Confirmar nova senha" value={confirmation} setValue={setConfirmation} password onFocus={focus} />
    </>}
    <Message text={error} />
    {step !== 'done' && <Button title={step === 'email' ? 'Enviar código' : 'Salvar nova senha'} busy={busy} onPress={() => { void submit(); }} />}
    {step === 'email' && <Button title="Já tenho um código" secondary disabled={busy} onPress={() => { setError(''); setNotice(''); setStep('code'); }} />}
    {step === 'code' && <Button title="Pedir outro código" secondary disabled={busy} onPress={() => { setCode(''); setError(''); setNotice('Aguarde um minuto entre pedidos. São permitidos até três por hora.'); setStep('email'); }} />}
    <Button title="Voltar para entrar" secondary disabled={busy} onPress={() => router.replace('/auth')} />
  </ScrollView></FormKeyboardView>;
}
