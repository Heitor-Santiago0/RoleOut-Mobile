import { useState } from 'react';
import { Text, View, type TextInputProps } from 'react-native';
import { Button, Field, Message } from './ui';
import { styles as s } from './theme';
import { formatDateInput, isoDate } from './itinerary';
import { weekDays, type HoursRow } from './openingHours';

export function OpeningHoursEditor({ rows, onChange, disabled, onFieldFocus }: { onFieldFocus?: TextInputProps["onFocus"]; rows: HoursRow[]; onChange: (rows: HoursRow[]) => void; disabled: boolean }) {
  const [expanded, setExpanded] = useState(false);
  const [date, setDate] = useState(''); const [error, setError] = useState('');
  const update = (dia: string, value?: string) => onChange([...rows.filter(r => r.dia !== dia), ...(value === undefined ? [] : [{ dia, value }])]);
  function row(dia: string, title: string) {
    const current = rows.find(r => r.dia === dia);
    return <View key={dia} style={{ gap: 8 }}>
      <Text style={s.label}>{title} · {current ? current.value === 'fechado' ? 'Fechado' : 'Com horários' : 'Não informado'}</Text>
      <Button secondary disabled={disabled} title={current ? current.value === 'fechado' ? `Informar horários de ${title}` : `Marcar ${title} como fechado` : `Informar horários de ${title}`} onPress={() => update(dia, current && current.value !== 'fechado' ? 'fechado' : '')} />
      {current && current.value !== 'fechado' && <Field onFocus={onFieldFocus} label={`Intervalos de ${title}`} placeholder="09:00-12:00, 14:00-18:00" value={current.value} setValue={value => { if (!disabled) update(dia, value); }} />}
      {current && <Button secondary disabled={disabled} title={dia.length === 1 ? `Remover informação de ${title}` : `Remover exceção de ${title}`} onPress={() => update(dia)} />}
    </View>;
  }
  return <View style={{ gap: 12 }}>
    <Button secondary title={expanded ? 'Recolher horários de funcionamento' : 'Configurar funcionamento e exceções'} disabled={disabled} onPress={() => setExpanded(!expanded)} />
    {expanded && <>
      <Text style={s.small}>Horários desta atividade no local. Até quatro intervalos por dia. Para atravessar a meia-noite, termine em 24:00 e informe a continuação a partir de 00:00 no dia seguinte. Dias sem informação precisam ser confirmados.</Text>
      {weekDays.map((title, i) => row(String(i + 1), title))}
      <Text style={s.cardTitle}>Exceções por data</Text>
      <Text style={s.small}>Uma exceção substitui todos os horários daquele dia, inclusive em feriados.</Text>
      {rows.filter(r => r.dia.length !== 1).sort((a,b) => a.dia.localeCompare(b.dia)).map(r => row(r.dia, r.dia.split('-').reverse().join('/')))}
      <Field onFocus={onFieldFocus} label="Data da exceção" placeholder="DD/MM/AAAA" keyboardType="number-pad" value={date} setValue={v => setDate(formatDateInput(v))} />
      <Message text={error} />
      <Button secondary disabled={disabled} title="Adicionar exceção" onPress={() => {
        const day = isoDate(date);
        if (!day || rows.some(r => r.dia === day) || rows.filter(r => r.dia.length !== 1).length >= 60) { setError('Use uma data válida ainda não cadastrada (até 60 exceções).'); return; }
        update(day, 'fechado'); setDate(''); setError('');
      }} />
    </>}
  </View>;
}
