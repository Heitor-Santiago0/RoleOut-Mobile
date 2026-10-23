import { useCallback, useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, Animated, Image, StyleSheet, View } from 'react-native';
import { useEventListener } from 'expo';
import * as SplashScreen from 'expo-splash-screen';
import { useVideoPlayer, VideoView } from 'expo-video';

// Run before the first React render so the native screen covers startup work.
void SplashScreen.preventAutoHideAsync().catch(() => {});

function hideNativeSplash() {
  void SplashScreen.hideAsync().catch(() => {});
}

export function StartupSplash({ onComplete }: { onComplete: () => void }) {
  const [reduceMotion, setReduceMotion] = useState<boolean | null>(null);
  const [opacity] = useState(() => new Animated.Value(1));
  const finished = useRef(false);
  const finish = useCallback(() => {
    if (finished.current) return;
    finished.current = true;
    hideNativeSplash();
    if (reduceMotion) onComplete();
    else Animated.timing(opacity, { toValue: 0, duration: 180, useNativeDriver: true })
      .start(({ finished: didFinish }) => { if (didFinish) onComplete(); });
  }, [onComplete, opacity, reduceMotion]);

  useEffect(() => {
    let active = true;
    void AccessibilityInfo.isReduceMotionEnabled().then(value => {
      if (active) setReduceMotion(value);
    }).catch(() => { if (active) setReduceMotion(false); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (reduceMotion) finish();
    // Loading/playback failure must never prevent entry into the app.
    const timeout = setTimeout(finish, 5500);
    return () => clearTimeout(timeout);
  }, [finish, reduceMotion]);

  useEffect(() => () => opacity.stopAnimation(), [opacity]);

  return <Animated.View style={[styles.overlay, { opacity }]} accessibilityViewIsModal accessibilityLabel="Abrindo RolêOut">
    <Image source={require('../assets/roleout-splash-poster.png')} style={styles.fill} resizeMode="contain" onLoadEnd={hideNativeSplash} />
    {reduceMotion === false && <SplashVideo onComplete={finish} />}
  </Animated.View>;
}

function SplashVideo({ onComplete }: { onComplete: () => void }) {
  const [firstFrame, setFirstFrame] = useState(false);
  const player = useVideoPlayer(require('../assets/roleout-splash.mp4'), video => {
    video.loop = false;
    video.muted = true;
    video.audioMixingMode = 'mixWithOthers';
  });
  useEventListener(player, 'playToEnd', onComplete);
  useEventListener(player, 'statusChange', ({ status }) => {
    if (status === 'error') onComplete();
  });
  useEffect(() => {
    if (player.status === 'error') onComplete();
    else player.play();
  }, [onComplete, player]);

  return <View style={[styles.fill, { opacity: firstFrame ? 1 : 0 }]} pointerEvents="none">
    <VideoView
      player={player}
      style={styles.fill}
      contentFit="contain"
      nativeControls={false}
      fullscreenOptions={{ enable: false }}
      allowsPictureInPicture={false}
      playsInline
      surfaceType="textureView"
      onFirstFrameRender={() => { setFirstFrame(true); hideNativeSplash(); }}
    />
  </View>;
}

const styles = StyleSheet.create({
  overlay: { ...StyleSheet.absoluteFill, backgroundColor: '#06122F', zIndex: 100, elevation: 100 },
  fill: { ...StyleSheet.absoluteFill, backgroundColor: '#06122F' },
});
