import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, Text, View } from 'react-native';
import { Redirect, router } from 'expo-router';
import { useAuth } from './AuthContext';
import { ApiError, request } from './api';
import { Button, Field, Message } from './ui';
import { colors, styles as s } from './theme';

type OrganizationInput = { nome: string; tipo: string; descricao: string; contatoEmail: string; contatoTelefone: string };
type Organization = OrganizationInput & { id: number; status: string; papel: string };
type VenueInput = { nome: string; categoria: string; endereco: string; cidade: string; uf: string; cep: string; descricao: string; acessibilidade: string };
type Venue = VenueInput & { id: number; status: string };
const types = [{ id: 'RESTAURANTE', label: 'Restaurante' }, { id: 'BAR', label: 'Bar' }, { id: 'MUSEU', label: 'Museu' }, { id: 'PREFEITURA', label: 'Prefeitura' }, { id: 'OUTRO', label: 'Outro' }];
const states = 'AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO'.split(' ');
const statusLabels: Record<string, string> = { PENDENTE: 'Aguardando análise', APROVADA: 'Aprovada', REJEITADA: 'Rejeitada', SUSPENSA: 'Suspensa', RASCUNHO: 'Rascunho', PUBLICADO: 'Publicado', REJEITADO: 'Rejeitado', SUSPENSO: 'Suspenso' };
const emptyVenue: VenueInput = { nome: '', categoria: '', endereco: '', cidade: '', uf: '', cep: '', descricao: '', acessibilidade: '' };

export function OrganizationsScreen() {
  const { session } = useAuth();
  if (!session) return <Redirect href="/auth" />;
  return <Manager key={session.token} token={session.token} email={session.conta.email} />;
}
function Manager({ token, email }: { token: string; email: string }) {
  const { expire } = useAuth(); const active = useRef(true);
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [organization, setOrganization] = useState<Organization | null>(null);
  const [venues, setVenues] = useState<Venue[]>([]);
  const [mode, setMode] = useState<'list' | 'organization' | 'orgForm' | 'venueForm'>('list');
  const [orgInput, setOrgInput] = useState<OrganizationInput>({ nome: '', tipo: 'RESTAURANTE', descricao: '', contatoEmail: email, contatoTelefone: '' });
  const [venueInput, setVenueInput] = useState<VenueInput>(emptyVenue);
  const [editing, setEditing] = useState<number | null>(null);
  const [busy, setBusy] = useState(true); const [error, setError] = useState(''); const [notice, setNotice] = useState('');
  async function failure(e: unknown) {
    if (!active.current) return;
    if (e instanceof ApiError && e.status === 401) await expire();
    else setError(e instanceof Error ? e.message : 'Não foi possível concluir.');
  }
  useEffect(() => {
    active.current = true;
    request<{ itens: Organization[] }>('/organizacoes', { token }).then(data => { if (active.current) setOrganizations(data.itens); })
      .catch(e => { if (active.current) { if (e instanceof ApiError && e.status === 401) void expire(); else setError(e instanceof Error ? e.message : 'Não foi possível carregar.'); } })
      .finally(() => { if (active.current) setBusy(false); });
    return () => { active.current = false; };
  }, [token, expire]);
  async function refresh(org?: Organization) {
    setBusy(true); setError('');
    try {
      if (org) {
        const [current, data] = await Promise.all([request<Organization>(`/organizacoes/${org.id}`, { token }), request<{ itens: Venue[] }>(`/organizacoes/${org.id}/locais`, { token })]);
        if (active.current) { setOrganization(current); setVenues(data.itens); setMode('organization'); }
      } else {
        const data = await request<{ itens: Organization[] }>('/organizacoes', { token });
        if (active.current) { setOrganizations(data.itens); setOrganization(null); setMode('list'); }
      }
    } catch (e) { await failure(e); } finally { if (active.current) setBusy(false); }
  }
  function openOrgForm(org?: Organization) {
    setEditing(org?.id ?? null); setError(''); setNotice('');
    setOrgInput(org ? { nome: org.nome, tipo: org.tipo, descricao: org.descricao ?? '', contatoEmail: org.contatoEmail, contatoTelefone: org.contatoTelefone ?? '' } : { nome: '', tipo: 'RESTAURANTE', descricao: '', contatoEmail: email, contatoTelefone: '' }); setMode('orgForm');
  }
  function openVenueForm(venue?: Venue) {
    setEditing(venue?.id ?? null); setError(''); setNotice('');
    setVenueInput(venue ? { nome: venue.nome, categoria: venue.categoria, endereco: venue.endereco, cidade: venue.cidade, uf: venue.uf, cep: venue.cep ?? '', descricao: venue.descricao ?? '', acessibilidade: venue.acessibilidade ?? '' } : { ...emptyVenue }); setMode('venueForm');
  }
  async function save() {
    setError(''); setNotice('');
    if (mode === 'orgForm' && (!orgInput.nome.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(orgInput.contatoEmail.trim()))) { setError('Informe o nome e um e-mail de contato válido.'); return; }
    if (mode === 'venueForm' && (!venueInput.nome.trim() || !venueInput.categoria.trim() || !venueInput.endereco.trim() || !venueInput.cidade.trim() || !states.includes(venueInput.uf) || (venueInput.cep && !/^\d{5}-?\d{3}$/.test(venueInput.cep)))) { setError('Preencha nome, categoria, endereço, cidade e UF. Se informar CEP, use oito números.'); return; }
    setBusy(true);
    try {
      if (mode === 'orgForm') {
        const saved = await request<Organization>(`/organizacoes${editing ? `/${editing}` : ''}`, { token, method: editing ? 'PUT' : 'POST', body: orgInput });
        if (!active.current) return;
        setOrganization(saved); setVenues([]); setMode('organization');
        setNotice('Organização salva. A publicação depende de aprovação.');
        await refresh(saved);
      } else if (organization) {
        const saved = await request<Venue>(`/organizacoes/${organization.id}/locais${editing ? `/${editing}` : ''}`, { token, method: editing ? 'PUT' : 'POST', body: venueInput });
        if (!active.current) return;
        setVenues(previous => [...previous.filter(v => v.id !== saved.id), saved]); setMode('organization');
        setNotice('Local salvo como rascunho. Ainda não está publicado.');
      }
    } catch (e) {
      await failure(e);
      if (active.current && !(e instanceof ApiError)) setError('Não foi possível confirmar a gravação. Volte à lista e atualize antes de tentar novamente.');
    } finally { if (active.current) setBusy(false); }
  }
  const editableOrg = organization?.papel === 'RESPONSAVEL' && ['PENDENTE', 'REJEITADA'].includes(organization.status);
  return <ScrollView contentContainerStyle={[s.content, { maxWidth: 680, width: '100%', alignSelf: 'center' }]} keyboardShouldPersistTaps="handled">
    <Button title={mode === 'list' ? '← Minha conta' : '← Voltar'} secondary disabled={busy} onPress={() => { setError(''); setNotice(''); if (mode === 'list') router.replace('/account'); else if (mode === 'venueForm' || (mode === 'orgForm' && organization)) setMode('organization'); else void refresh(); }} />
    <Text style={s.title}>{mode === 'list' ? 'Meus locais' : mode === 'orgForm' ? (editing ? 'Editar organização' : 'Cadastrar organização') : mode === 'venueForm' ? (editing ? 'Editar local' : 'Cadastrar local') : organization?.nome}</Text>
    <Message text={error} />{!!notice && <Text accessibilityRole="alert" style={s.text}>{notice}</Text>}
    {busy && <ActivityIndicator color={colors.blue} accessibilityLabel="Carregando cadastro" />}
    {mode === 'list' && <>
      <Text style={s.subtitle}>Cadastre sua organização e os locais sob sua responsabilidade. Os cadastros precisam de aprovação antes de aparecer para o público.</Text>
      <Button title="Cadastrar organização" disabled={busy} onPress={() => openOrgForm()} />
      <Button title="Atualizar organizações" secondary disabled={busy} onPress={() => { void refresh(); }} />
      {!busy && !error && organizations.length === 0 && <Text style={s.subtitle}>Você ainda não possui organizações cadastradas.</Text>}
      {organizations.map(org => <View key={org.id} style={[s.secondary, { padding: 16, borderRadius: 14, gap: 10 }]}><Text style={s.cardTitle}>{org.nome}</Text><Text style={s.subtitle}>{statusLabels[org.status] ?? org.status}</Text><Button title={`Gerenciar ${org.nome}`} secondary disabled={busy} onPress={() => { setNotice(''); void refresh(org); }} /></View>)}
      {organizations.length === 100 && <Text style={s.small}>Exibindo as primeiras 100 organizações.</Text>}
    </>}
    {mode === 'organization' && organization && <>
      <Text style={s.subtitle}>Organização: {statusLabels[organization.status] ?? organization.status}</Text>
      <Text style={s.small}>Nesta etapa, você pode preparar os cadastros. O envio para análise e o painel de aprovação ainda serão disponibilizados.</Text>
      {editableOrg && <Button title="Editar organização" secondary disabled={busy} onPress={() => openOrgForm(organization)} />}
      {organization.status !== 'SUSPENSA' && <Button title="Cadastrar local" disabled={busy} onPress={() => openVenueForm()} />}
      <Button title="Atualizar locais e status" secondary disabled={busy} onPress={() => { void refresh(organization); }} />
      {!busy && !error && venues.length === 0 && <Text style={s.subtitle}>Nenhum local cadastrado nesta organização.</Text>}
      {venues.map(venue => <View key={venue.id} style={[s.secondary, { padding: 16, borderRadius: 14, gap: 10 }]}><Text style={s.cardTitle}>{venue.nome}</Text><Text style={s.subtitle}>{venue.cidade}/{venue.uf} • {statusLabels[venue.status] ?? venue.status}</Text><Text style={s.text}>{venue.endereco}</Text>{organization.status !== 'SUSPENSA' && ['RASCUNHO', 'REJEITADO'].includes(venue.status) && <Button title={`Editar ${venue.nome}`} secondary disabled={busy} onPress={() => openVenueForm(venue)} />}</View>)}
      {venues.length === 100 && <Text style={s.small}>Exibindo os primeiros 100 locais.</Text>}
    </>}
    {mode === 'orgForm' && <>
      <Field label="Nome da organização" value={orgInput.nome} setValue={nome => setOrgInput(v => ({ ...v, nome }))} />
      <Text style={s.label}>Tipo de organização</Text><View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>{types.map(type => <Pressable key={type.id} disabled={busy} accessibilityRole="radio" accessibilityState={{ checked: orgInput.tipo === type.id }} style={[s.chip, orgInput.tipo === type.id && s.selected]} onPress={() => setOrgInput(v => ({ ...v, tipo: type.id }))}><Text style={[s.chipText, orgInput.tipo === type.id && s.selectedText]}>{type.label}</Text></Pressable>)}</View>
      <Field label="E-mail de contato" email value={orgInput.contatoEmail} setValue={contatoEmail => setOrgInput(v => ({ ...v, contatoEmail }))} />
      <Field label="Telefone (opcional)" keyboardType="phone-pad" value={orgInput.contatoTelefone} setValue={contatoTelefone => setOrgInput(v => ({ ...v, contatoTelefone }))} />
      <Field label="Descrição (opcional)" value={orgInput.descricao} setValue={descricao => setOrgInput(v => ({ ...v, descricao }))} />
      <Button title="Salvar organização" busy={busy} onPress={() => { void save(); }} />
    </>}
    {mode === 'venueForm' && <>
      {([{ key: 'nome', label: 'Nome do local' }, { key: 'categoria', label: 'Categoria (ex.: Cultura, Música, Gastronomia)' }, { key: 'endereco', label: 'Endereço completo' }, { key: 'cidade', label: 'Cidade' }, { key: 'descricao', label: 'Descrição (opcional)' }, { key: 'acessibilidade', label: 'Acessibilidade (opcional)' }] as const).map(field => <Field key={field.key} label={field.label} value={venueInput[field.key]} setValue={value => setVenueInput(v => ({ ...v, [field.key]: value }))} />)}
      <Field label="UF (ex.: MG)" value={venueInput.uf} setValue={uf => setVenueInput(v => ({ ...v, uf: uf.replace(/[^a-z]/gi, '').slice(0, 2).toUpperCase() }))} />
      <Field label="CEP (opcional)" keyboardType="number-pad" value={venueInput.cep} setValue={value => { const digits = value.replace(/\D/g, '').slice(0, 8); setVenueInput(v => ({ ...v, cep: digits.length > 5 ? `${digits.slice(0, 5)}-${digits.slice(5)}` : digits })); }} />
      <Text style={s.small}>Informe somente recursos de acessibilidade existentes. O local será salvo como rascunho.</Text>
      <Button title="Salvar rascunho do local" busy={busy} onPress={() => { void save(); }} />
    </>}
  </ScrollView>;
}
