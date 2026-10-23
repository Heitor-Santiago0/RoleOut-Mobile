import { PersonalizeScreen } from '../../PersonalizeScreen';
import { useRoutingPlan } from '../../RoutingPlanContext';
export default function CreateRoute() {
  const { draftId } = useRoutingPlan();
  return <PersonalizeScreen key={draftId} />;
}
