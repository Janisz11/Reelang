import { useEffect, useRef, useState } from "react";
import { createPlaybackTracker, type PlaybackTracker } from "../lib/reelPlayback";
import { loadYouTubeApi, type YTPlayer } from "../lib/youtube";
import { PauseIcon, PlayIcon } from "./Icons";

interface PlayerProps {
  isActive: boolean;
  onTimeUpdate: (ms: number) => void;
}

function PlayPauseFlash({ paused, visible }: { paused: boolean; visible: boolean }) {
  if (!visible) return null;
  return (
    <div
      style={{
        position: "absolute",
        inset: 0,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        pointerEvents: "none",
        zIndex: 4,
      }}
    >
      <div
        style={{
          width: 72,
          height: 72,
          borderRadius: "50%",
          background: "rgba(0,0,0,0.5)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        {paused ? <PlayIcon size={40} color="#fff" /> : <PauseIcon size={40} color="#fff" />}
      </div>
    </div>
  );
}

function durationMsOf(video: HTMLVideoElement): number {
  return Number.isFinite(video.duration) ? Math.round(video.duration * 1000) : 0;
}

/** Browsers block audible autoplay, so reels start muted with a tap-to-unmute affordance. */
function MutedHint({ onUnmute }: { onUnmute: () => void }) {
  return (
    <button
      onClick={(event) => {
        event.stopPropagation();
        onUnmute();
      }}
      style={{
        position: "absolute",
        top: 72,
        right: 16,
        zIndex: 6,
        padding: "7px 12px",
        borderRadius: 999,
        background: "rgba(0,0,0,0.55)",
        color: "#fff",
        fontSize: 12,
        fontWeight: 600,
      }}
    >
      🔇 Tap for sound
    </button>
  );
}

export function YouTubeReel({ youtubeId, isActive, onTimeUpdate }: PlayerProps & { youtubeId: string }) {
  const hostRef = useRef<HTMLDivElement>(null);
  const playerRef = useRef<YTPlayer | null>(null);
  const [ready, setReady] = useState(false);
  const [paused, setPaused] = useState(false);
  const [muted, setMuted] = useState(true);
  const [flash, setFlash] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const host = hostRef.current;
    if (!host) return;

    void loadYouTubeApi().then((YT) => {
      if (cancelled || !hostRef.current) return;
      playerRef.current = new YT.Player(host, {
        videoId: youtubeId,
        playerVars: {
          autoplay: 0,
          controls: 0,
          modestbranding: 1,
          rel: 0,
          playsinline: 1,
          loop: 1,
          playlist: youtubeId,
        },
        events: {
          onReady: () => {
            if (cancelled) return;
            playerRef.current?.mute();
            setReady(true);
          },
        },
      });
    });

    return () => {
      cancelled = true;
      playerRef.current?.destroy();
      playerRef.current = null;
    };
  }, [youtubeId]);

  useEffect(() => {
    if (!ready) return;
    const player = playerRef.current;
    if (!player) return;
    if (isActive && !paused) player.playVideo();
    else player.pauseVideo();
  }, [isActive, paused, ready]);

  useEffect(() => {
    if (!ready || !isActive) return;
    const id = window.setInterval(() => {
      const seconds = playerRef.current?.getCurrentTime() ?? 0;
      onTimeUpdate(Math.round(seconds * 1000));
    }, 250);
    return () => window.clearInterval(id);
  }, [ready, isActive, onTimeUpdate]);

  useEffect(() => {
    if (!flash) return;
    const id = window.setTimeout(() => setFlash(false), 700);
    return () => window.clearTimeout(id);
  }, [flash]);

  return (
    <div
      style={{ position: "absolute", inset: 0 }}
      onClick={() => {
        setPaused((value) => !value);
        setFlash(true);
      }}
    >
      <div style={{ position: "absolute", inset: 0, pointerEvents: "none" }}>
        <div ref={hostRef} style={{ width: "100%", height: "100%" }} />
      </div>
      {!ready && (
        <div style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center" }}>
          <div className="spinner spinner--light" />
        </div>
      )}
      {muted && ready && (
        <MutedHint
          onUnmute={() => {
            playerRef.current?.unMute();
            playerRef.current?.setVolume(100);
            setMuted(false);
          }}
        />
      )}
      <PlayPauseFlash paused={paused} visible={flash} />
    </div>
  );
}

export function VideoReel({
  streamUrl,
  isActive,
  onTimeUpdate,
  reelId,
}: PlayerProps & { streamUrl: string; reelId?: string }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [paused, setPaused] = useState(false);
  const [muted, setMuted] = useState(true);
  const [flash, setFlash] = useState(false);
  const [isImage, setIsImage] = useState(false);

  // Held in a ref, not state: a fresh tracker per mount keeps StrictMode's double-invoke
  // from retiring the tracker the remounted player still needs.
  const trackerRef = useRef<PlaybackTracker | null>(null);

  useEffect(() => {
    if (!reelId) return;
    trackerRef.current = createPlaybackTracker(reelId);
    // A skip is only known once the card is gone, so the report happens on teardown.
    return () => {
      trackerRef.current?.onLeft();
      trackerRef.current = null;
    };
  }, [reelId]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || isImage) return;
    if (isActive && !paused) {
      trackerRef.current?.onActivated();
      void video.play().catch(() => undefined);
    } else video.pause();
  }, [isActive, paused, isImage]);

  useEffect(() => {
    if (!flash) return;
    const id = window.setTimeout(() => setFlash(false), 700);
    return () => window.clearTimeout(id);
  }, [flash]);

  // Uploaded "reels" can be stills; /stream then serves an image and <video> fails.
  if (isImage) {
    return <img src={streamUrl} alt="" style={{ position: "absolute", inset: 0, width: "100%", height: "100%", objectFit: "contain" }} />;
  }

  return (
    <div
      style={{ position: "absolute", inset: 0 }}
      onClick={() => {
        setPaused((value) => !value);
        setFlash(true);
      }}
    >
      <video
        ref={videoRef}
        src={streamUrl}
        loop
        muted={muted}
        playsInline
        preload="metadata"
        onError={() => setIsImage(true)}
        onLoadStart={() => trackerRef.current?.onLoadStarted()}
        onWaiting={() => trackerRef.current?.onBufferingStarted()}
        onPlaying={() => {
          trackerRef.current?.onBufferingEnded();
          trackerRef.current?.onFirstFrameRendered();
        }}
        onLoadedData={() => trackerRef.current?.onFirstFrameRendered()}
        onEnded={() => trackerRef.current?.onPlaybackEnded()}
        onSeeked={(event) =>
          trackerRef.current?.onProgress(
            Math.round(event.currentTarget.currentTime * 1000),
            durationMsOf(event.currentTarget),
            true,
          )
        }
        onPause={(event) =>
          trackerRef.current?.onProgress(
            Math.round(event.currentTarget.currentTime * 1000),
            durationMsOf(event.currentTarget),
            true,
          )
        }
        onTimeUpdate={(event) => {
          onTimeUpdate(Math.round(event.currentTarget.currentTime * 1000));
          trackerRef.current?.onProgress(
            Math.round(event.currentTarget.currentTime * 1000),
            durationMsOf(event.currentTarget),
          );
        }}
        style={{ width: "100%", height: "100%", objectFit: "contain", background: "#000" }}
      />
      {muted && (
        <MutedHint
          onUnmute={() => {
            setMuted(false);
            const video = videoRef.current;
            if (video) {
              video.muted = false;
              video.volume = 1;
            }
          }}
        />
      )}
      <PlayPauseFlash paused={paused} visible={flash} />
    </div>
  );
}
