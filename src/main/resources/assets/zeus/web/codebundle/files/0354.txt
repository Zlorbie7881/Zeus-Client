"""Makes the sounds of the Zeus main menu (all synthesised, nothing sampled):

  menu_rumble.ogg   0.9 s soft low thunder swell, played when the pointer reaches a stone plate (mono)
  menu_crack.ogg    0.8 s sharp electric crack with a low thump and a crackling tail, played on a click (mono)
  menu_wind.ogg     8 s of wind that joins its own end without a seam, so the game can loop it (stereo)
  menu_rain.ogg     8 s of soft rain, built the same way (stereo)

Usage: python3 tools/menu_sounds.py <output folder>      (needs numpy and ffmpeg with libvorbis)
"""
import sys, os, subprocess, wave
import numpy as np

SR = 44100
RNG = np.random.default_rng(7)


def lowpass(x, fc):
    """One-pole low-pass, applied twice for a steeper edge."""
    a = np.exp(-2 * np.pi * fc / SR)
    for _ in range(2):
        y = np.empty_like(x)
        acc = 0.0
        for i in range(len(x)):
            acc = (1 - a) * x[i] + a * acc
            y[i] = acc
        x = y
    return x


def fast_lowpass(x, fc):
    """FFT brick-wall with a soft edge, quick for long noise."""
    n = len(x)
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    spec *= 1.0 / (1.0 + (f / fc) ** 4)
    return np.fft.irfft(spec, n)


def fast_highpass(x, fc):
    n = len(x)
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    spec *= 1.0 - 1.0 / (1.0 + (f / fc) ** 4)
    return np.fft.irfft(spec, n)


def bandpass(x, lo, hi):
    return fast_lowpass(fast_highpass(x, lo), hi)


def norm(x, peak):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def rumble():
    n = int(0.9 * SR)
    t = np.arange(n) / SR
    noise = fast_lowpass(RNG.standard_normal(n), 140)
    noise2 = fast_lowpass(RNG.standard_normal(n), 420) * 0.35
    body = noise + noise2
    # a slow swell: rises over 0.3 s and trails away
    env = np.minimum(1, t / 0.28) ** 1.6 * np.exp(-np.maximum(0, t - 0.28) * 3.4)
    sub = np.sin(2 * np.pi * (46 + 10 * np.exp(-t * 3)) * t) * 0.55
    y = (body * 1.0 + sub) * env
    y[:200] *= np.linspace(0, 1, 200)
    y[-400:] *= np.linspace(1, 0, 400)
    return norm(y, 0.55)


def crack():
    n = int(0.8 * SR)
    t = np.arange(n) / SR
    # the crack: a very short burst of bright noise
    bright = fast_highpass(RNG.standard_normal(n), 1800) * np.exp(-t * 95)
    mid = bandpass(RNG.standard_normal(n), 300, 3200) * np.exp(-t * 26) * 0.7
    # the thump under it
    thump = np.sin(2 * np.pi * (78 * np.exp(-t * 7) + 38) * t) * np.exp(-t * 9) * 0.95
    # the crackling tail: a few clicks that thin out
    tail = np.zeros(n)
    for _ in range(46):
        pos = int(RNG.exponential(0.16) * SR + 0.02 * SR)
        if pos < n - 600:
            amp = RNG.uniform(0.15, 0.6) * np.exp(-pos / SR * 4.5)
            k = RNG.integers(14, 80)
            burst = RNG.standard_normal(k) * np.hanning(k)
            tail[pos:pos + k] += burst * amp
    tail = fast_highpass(tail, 900)
    # a short wash behind it
    wash = fast_lowpass(RNG.standard_normal(n), 2400) * np.exp(-t * 7) * 0.22
    y = bright * 1.1 + mid + thump + tail * 1.4 + wash
    y[:60] *= np.linspace(0, 1, 60)
    y[-600:] *= np.linspace(1, 0, 600)
    return norm(y, 0.85)


def wrap_loop(x, fade):
    """Turns a piece of noise that is {fade} samples longer than wanted into a loop with no seam: the extra samples are mixed
    into the start with an equal-power cross-fade, so the end flows straight into the start (the game loops the file itself)."""
    n = len(x) - fade
    w = np.linspace(0, np.pi / 2, fade)
    out = x[:n].copy()
    out[:fade] = x[:fade] * np.sin(w) + x[n:n + fade] * np.cos(w)
    return out


def slow_noise(n, rate, seed):
    r = np.random.default_rng(seed)
    pts = r.standard_normal(int(n / SR * rate) + 3)
    xs = np.linspace(0, len(pts) - 3, n)
    i = xs.astype(int)
    f = xs - i
    f = f * f * (3 - 2 * f)
    return pts[i] + (pts[i + 1] - pts[i]) * f


def wind():
    fade = int(0.8 * SR)
    n = 8 * SR + fade
    out = []
    for ch in range(2):
        base = RNG.standard_normal(n)
        low = bandpass(base, 90, 420)
        hi = bandpass(RNG.standard_normal(n), 500, 1700)
        gust = np.clip(0.55 + 0.45 * slow_noise(n, 0.32, 3 + ch), 0.05, 1.6)
        gust2 = np.clip(0.6 + 0.4 * slow_noise(n, 0.9, 11 + ch), 0.1, 1.5)
        y = low * gust + hi * 0.45 * gust * gust2
        out.append(y)
    y = np.stack([wrap_loop(c, fade) for c in out], 1)
    return norm(y, 0.5)


def rain():
    fade = int(0.8 * SR)
    n = 8 * SR + fade
    out = []
    for ch in range(2):
        hiss = bandpass(RNG.standard_normal(n), 2200, 9500)
        body = bandpass(RNG.standard_normal(n), 600, 2200) * 0.5
        density = np.clip(0.75 + 0.25 * slow_noise(n, 0.5, 21 + ch), 0.3, 1.2)
        drops = np.zeros(n)
        for _ in range(int(8 * 70)):
            pos = RNG.integers(0, n - 400)
            k = RNG.integers(20, 150)
            burst = RNG.standard_normal(k) * np.exp(-np.arange(k) / (k * 0.28))
            drops[pos:pos + k] += burst * RNG.uniform(0.1, 0.6)
        drops = fast_highpass(drops, 1800)
        y = (hiss * 0.8 + body) * density + drops * 0.55
        out.append(y)
    y = np.stack([wrap_loop(c, fade) for c in out], 1)
    return norm(y, 0.5)


def write_ogg(path, data):
    wav = path[:-4] + ".wav"
    pcm = (np.clip(data, -1, 1) * 32767).astype(np.int16)
    ch = 1 if pcm.ndim == 1 else pcm.shape[1]
    with wave.open(wav, "wb") as w:
        w.setnchannels(ch)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", path], check=True)
    os.remove(wav)


def main(out):
    os.makedirs(out, exist_ok=True)
    write_ogg(os.path.join(out, "menu_rumble.ogg"), rumble())
    write_ogg(os.path.join(out, "menu_crack.ogg"), crack())
    write_ogg(os.path.join(out, "menu_wind.ogg"), wind())
    write_ogg(os.path.join(out, "menu_rain.ogg"), rain())
    print("menu sounds written to", out)


if __name__ == "__main__":
    main(sys.argv[1])
