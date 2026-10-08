import { useLocalSearchParams } from 'expo-router';
import { SavedItinerariesScreen } from '../../SavedItinerariesScreen';
export default function SavedItineraryRoute() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return <SavedItinerariesScreen key={id} id={Number(id)} />;
}
