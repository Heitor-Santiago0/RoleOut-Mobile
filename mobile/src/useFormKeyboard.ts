import { useEffect, useRef, type RefObject } from 'react';
import { Keyboard, type ScrollView, type TextInputProps } from 'react-native';

export function useFormKeyboard(scroll: RefObject<ScrollView | null>) {
  const focused = useRef<number | null>(null);
  useEffect(() => {
    const subscription = Keyboard.addListener('keyboardDidShow', () => {
      if (focused.current !== null) scroll.current?.scrollResponderScrollNativeHandleToKeyboard(focused.current, 100, true);
    });
    return () => subscription.remove();
  }, [scroll]);
  const onFocus: TextInputProps['onFocus'] = event => {
    focused.current = event.nativeEvent.target;
    scroll.current?.scrollResponderScrollNativeHandleToKeyboard(event.nativeEvent.target, 100, true);
  };
  return onFocus;
}
