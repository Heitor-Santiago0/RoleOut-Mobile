import { createContext, useContext, useState, type ReactNode, type Dispatch, type SetStateAction } from 'react';
import { useAuth } from './AuthContext';
import type { Plan, Stop } from './itinerary';
import type { ItinerarySummary } from './api';

type SavedDraft = { summary: ItinerarySummary; signature: string };
type Draft = {
  draftId: number; startNew: () => void;
  plan: Plan; setPlan: Dispatch<SetStateAction<Plan>>;
  stops: Stop[]; setStops: Dispatch<SetStateAction<Stop[]>>;
  saved: SavedDraft | null; setSaved: Dispatch<SetStateAction<SavedDraft | null>>;
};
const Context = createContext<Draft | null>(null);
function DraftProvider({ children }: { children: ReactNode }) {
  const [plan, setPlan] = useState<Plan>({ city: '', date: '', start: '10:00', hours: '4', people: '1', budget: '', budgetMode: 'group' });
  const [stops, setStops] = useState<Stop[]>([]);
  const [saved, setSaved] = useState<SavedDraft | null>(null);
  const [draftId, setDraftId] = useState(0);
  function startNew() {
    setPlan({ city: '', date: '', start: '10:00', hours: '4', people: '1', budget: '', budgetMode: 'group' });
    setStops([]); setSaved(null); setDraftId(value => value + 1);
  }
  return <Context.Provider value={{ plan, setPlan, stops, setStops, saved, setSaved, draftId, startNew }}>{children}</Context.Provider>;
}
export function RoutingPlanProvider({ children }: { children: ReactNode }) {
  const { session, demo } = useAuth();
  // Unmount session-local choices when signing out or changing accounts.
  return <DraftProvider key={session?.token ?? (demo ? 'demo' : 'guest')}>{children}</DraftProvider>;
}
export function useRoutingPlan() {
  const value = useContext(Context);
  if (!value) throw new Error('Missing RoutingPlanProvider');
  return value;
}
