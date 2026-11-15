#!/usr/bin/env python3
"""Makes the sound of the Zeus start-up film (assets/zeus/sounds/intro.ogg), 5.1 seconds, 44.1 kHz stereo, in time with the film:

   0.0 - 2.1 s   the ring draws itself: a low drone swells, a thin tone climbs three octaves with the ring, air rushes up
                 in pitch and tiny electric sparks come faster and faster
   2.05 - 2.20   the air is pulled in (a reversed rush) and cut off, one breath of silence
   2.20 s        the first bolt: a hard crack, a sub boom that falls in pitch, a bright zap and a long rolling thunder in a big room;
                 the ring closes with a bell
   2.70, 3.03, 3.40  three more strikes, each softer and further away
   2.4 - 4.9 s   a warm, open chord comes up under the thunder and fades with the film

Everything is synthesised (numpy, scipy); nothing is sampled. The film's strikes are at the times above (see VideoIntroScreen.STARTUP).

Usage: python3 tools/intro_sound.py OUT.wav      then:  ffmpeg -i OUT.wav -c:a libvorbis -q:a 6 intro.ogg
"""
import sys
import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
DUR = 5.1
N = int(SR * DUR)
RNG = np.random.default_rng(7)
T = np.arange(N) / SR


def tone_env(t0, t1, attack, release):
    e = np.zeros(N)
    a = (T >= t0) & (T < t1)
    x = T[a] - t0
    y = np.minimum(1.0, x / max(attack, 1e-6)) ** 2 * (3 - 2 * np.minimum(1.0, x / max(attack, 1e-6)))
    z = np.minimum(1.0, (t1 - T[a]) / max(release, 1e-6))
    e[a] = y * (z * z * (3 - 2 * z))
    return e


def noise():
    return RNG.normal(0, 1, N)


def lowpass(x, fc, order=2):
    return signal.sosfilt(signal.butter(order, fc, "low", fs=SR, output="sos"), x)


def highpass(x, fc, order=2):
    return signal.sosfilt(signal.butter(order, fc, "high", fs=SR, output="sos"), x)


def bandpass(x, lo, hi, order=2):
    return signal.sosfilt(signal.butter(order, [lo, hi], "band", fs=SR, output="sos"), x)


def tv_filter(x, cutoff, kind="low", order=2, block=2048):
    """A filter whose cutoff follows cutoff(t): blocks with their own filter, faded into each other."""
    out = np.zeros_like(x)
    win = np.hanning(block * 2)
    hop = block
    for s in range(-hop, N, hop):
        a, b = max(0, s), min(N, s + 2 * hop)
        if b <= a:
            continue
        mid = min(N - 1, max(0, s + hop))
        fc = float(np.clip(cutoff(mid / SR), 30, SR / 2 - 200))
        sos = signal.butter(order, fc, kind, fs=SR, output="sos")
        seg = x[a:b]
        y = signal.sosfilt(sos, seg)
        w = win[(a - s):(b - s)]
        out[a:b] += y * w
    return out


def stereo(x, pan):
    """pan -1 (left) .. 1 (right), constant power."""
    a = (pan + 1) * np.pi / 4
    return np.stack([x * np.cos(a), x * np.sin(a)], 1)


def add(mix, sig, t0=0.0, gain=1.0):
    i = int(t0 * SR)
    m = min(len(sig), N - i)
    if m > 0:
        mix[i:i + m] += sig[:m] * gain


def wide_noise(fn):
    """Two different noises through the same shaping: a wide stereo image."""
    return np.stack([fn(noise()), fn(noise())], 1)


def reverb_ir(length=2.6, tau=0.85, damp=5000.0):
    n = int(SR * length)
    t = np.arange(n) / SR
    ir = np.stack([RNG.normal(0, 1, n), RNG.normal(0, 1, n)], 1) * np.exp(-t / tau)[:, None]
    ir[:int(0.02 * SR)] *= np.linspace(0, 1, int(0.02 * SR))[:, None]
    for c in range(2):
        ir[:, c] = lowpass(ir[:, c], damp, 1)
    ir /= np.sqrt((ir ** 2).sum())
    return ir


def convolve(x, ir):
    return np.stack([signal.fftconvolve(x[:, c], ir[:, c])[:N] for c in range(2)], 1)


# ------------------------------------------------------------------ the layers

def charge():
    """0 - 2.1 s: the ring draws itself."""
    out = np.zeros((N, 2))
    ring = np.clip(T / 2.1, 0, 1)
    live = tone_env(0.0, 2.2, 0.5, 0.06)
    # a low drone that swells, an open fifth with a little beating
    drone = np.zeros(N)
    for f, g in ((55.0, 1.0), (55.35, 0.8), (82.4, 0.45), (110.0, 0.25)):
        drone += g * np.sin(2 * np.pi * f * T)
    drone *= (0.04 + 0.30 * ring ** 2.2) * live * (1 + 0.06 * np.sin(2 * np.pi * 5.2 * T))
    out += stereo(drone, 0)
    # a thin tone that climbs three octaves with the ring (it is the ring's own voice), with a soft second voice a fifth above
    f = 196.0 * 2 ** (3.0 * ring ** 1.15)
    ph = 2 * np.pi * np.cumsum(f * (1 + 0.004 * np.sin(2 * np.pi * 6.0 * T))) / SR
    tone = (np.sin(ph) + 0.35 * np.sin(2 * ph) + 0.15 * np.sin(3 * ph)) * (0.020 + 0.10 * ring ** 1.5) * live
    ph2 = 2 * np.pi * np.cumsum(f * 1.5) / SR
    tone2 = np.sin(ph2) * (0.01 + 0.05 * ring ** 1.5) * live
    out += stereo(tone, -0.25) + stereo(tone2, 0.3)
    # air that rises in pitch
    air = tv_filter(noise(), lambda t: 300 * 2 ** (5.0 * min(1.0, t / 2.1)), "high", 2)
    air = tv_filter(air, lambda t: 500 * 2 ** (5.2 * min(1.0, t / 2.1)), "low", 2)
    air *= (0.01 + 0.28 * ring ** 2.4) * live
    air2 = tv_filter(noise(), lambda t: 300 * 2 ** (5.0 * min(1.0, t / 2.1)), "high", 2)
    air2 = tv_filter(air2, lambda t: 500 * 2 ** (5.2 * min(1.0, t / 2.1)), "low", 2)
    air2 *= (0.01 + 0.28 * ring ** 2.4) * live
    out += np.stack([air, air2], 1) * 0.55
    # sparks: tiny clicks that come faster as the ring grows
    t = 0.35
    while t < 2.1:
        rate = 6 + 120 * ((t - 0.35) / 1.75) ** 2
        t += RNG.exponential(1.0 / rate)
        if t >= 2.12:
            break
        n = int(SR * RNG.uniform(0.0008, 0.0026))
        burst = RNG.normal(0, 1, n) * np.exp(-np.arange(n) / (n * 0.35))
        burst = highpass(np.pad(burst, (0, 64)), RNG.uniform(2500, 6500), 1)
        g = RNG.uniform(0.03, 0.11) * (0.4 + 0.6 * (t / 2.1))
        add(out, stereo(burst, RNG.uniform(-0.9, 0.9)), t, g)
    return out


def pull_in():
    """2.04 - 2.20: a reversed rush that is cut off just before the bolt."""
    out = np.zeros((N, 2))
    t0, t1 = 2.04, 2.19
    x = lambda: tv_filter(noise(), lambda t: 1200 + 9000 * np.clip((t - t0) / (t1 - t0), 0, 1), "high", 2)
    e = tone_env(t0, t1, t1 - t0 - 0.002, 0.002) * np.clip((T - t0) / (t1 - t0), 0, 1) ** 2
    out += np.stack([x() * e, x() * e], 1) * 0.30
    return out


def strike(t0, level, dist):
    """One bolt. level 0..1 (how big), dist 0..1 (how far: less crack, more rumble, darker)."""
    out = np.zeros((N, 2))
    near = 1.0 - 0.75 * dist
    # the crack
    n = int(SR * 0.09)
    k = np.arange(n) / SR
    crack = RNG.normal(0, 1, n) * (np.exp(-k / 0.0035) + 0.35 * np.exp(-k / 0.025))
    crack2 = RNG.normal(0, 1, n) * (np.exp(-k / 0.0035) + 0.35 * np.exp(-k / 0.025))
    cut = 9000 - 6500 * dist
    crack, crack2 = [lowpass(highpass(c, 500, 1), cut, 1) for c in (crack, crack2)]
    add(out, np.stack([crack, crack2], 1), t0, 0.80 * level * near)
    # the zap: an electric sweep falling fast
    n = int(SR * 0.16)
    k = np.arange(n) / SR
    f = 300 + 5200 * np.exp(-k / 0.03)
    ph = 2 * np.pi * np.cumsum(f) / SR
    zap = np.sin(ph + 2.2 * np.sin(ph * 0.5)) * np.exp(-k / 0.05)
    add(out, stereo(zap, RNG.uniform(-0.4, 0.4)), t0, 0.22 * level * near)
    # the boom: a sine that falls in pitch
    n = int(SR * 1.6)
    k = np.arange(n) / SR
    f = 36 + 52 * np.exp(-k / 0.18)
    ph = 2 * np.pi * np.cumsum(f) / SR
    boom = (np.sin(ph) + 0.4 * np.sin(2 * ph)) * (1 - np.exp(-k / 0.004)) * np.exp(-k / 0.55)
    add(out, stereo(boom, 0), t0, 0.95 * level * (1 - 0.5 * dist))
    # the thunder: a rolling rumble in a big room
    length = 3.0 if dist < 0.5 else 2.6
    n = int(SR * length)
    k = np.arange(n) / SR
    roll = np.zeros(n)
    # uneven echoes of the bolt off the ground and the clouds
    env = np.exp(-k / (0.9 + 0.7 * dist)) * (1 - np.exp(-k / (0.02 + 0.08 * dist)))
    for _ in range(9):
        c = RNG.uniform(0.05, 1.4)
        w = RNG.uniform(0.08, 0.35)
        env += RNG.uniform(0.15, 0.55) * np.exp(-((k - c) / w) ** 2) * np.exp(-k / 1.4)
    env /= env.max()
    th = []
    for _ in range(2):
        x = RNG.normal(0, 1, n)
        fcs = lambda t: 380 - 250 * min(1.0, t / 2.2)
        # the cutoff falls as it fades
        y = np.zeros(n)
        blk = 2048
        w = np.hanning(blk * 2)
        for s in range(-blk, n, blk):
            a, b = max(0, s), min(n, s + 2 * blk)
            if b <= a:
                continue
            fc = max(60.0, fcs(max(0, s + blk) / SR)) * (1.0 - 0.35 * dist)
            sos = signal.butter(2, fc, "low", fs=SR, output="sos")
            y[a:b] += signal.sosfilt(sos, x[a:b]) * w[(a - s):(b - s)]
        th.append(y * env)
    thunder = np.stack(th, 1)
    thunder /= np.abs(thunder).max() + 1e-9
    add(out, thunder, t0, 0.80 * level)
    return out


def bell(t0, f, gain, tau=0.9, pan=0.0):
    k = np.arange(int(SR * 3.0)) / SR
    s = (np.sin(2 * np.pi * f * k) + 0.45 * np.sin(2 * np.pi * f * 2.76 * k) * np.exp(-k / (tau * 0.4))
         + 0.25 * np.sin(2 * np.pi * f * 5.4 * k) * np.exp(-k / (tau * 0.2))) * np.exp(-k / tau) * (1 - np.exp(-k / 0.002))
    out = np.zeros((N, 2))
    add(out, stereo(s, pan), t0, gain)
    return out


def pad():
    """2.4 - 4.95 s: a warm open chord (D, A, D, E, A) that comes up under the thunder and fades with the film."""
    out = np.zeros((N, 2))
    env = tone_env(2.35, 5.0, 1.1, 1.3)
    notes = [(73.4, 0.9, -0.2), (110.0, 0.8, 0.2), (146.8, 0.7, -0.4), (220.0, 0.6, 0.4), (293.7, 0.45, -0.1), (329.6, 0.35, 0.3)]
    for f, g, pan in notes:
        for det in (-0.12, 0.0, 0.12):
            ph = 2 * np.pi * np.cumsum(np.full(N, f * 2 ** (det / 12.0))) / SR
            saw = signal.sawtooth(ph + RNG.uniform(0, 6.28))
            out += stereo(saw, pan) * g * 0.04
    # the chord opens up as it rises
    for c in range(2):
        out[:, c] = tv_filter(out[:, c], lambda t: 350 + 2200 * np.clip((t - 2.3) / 1.6, 0, 1) ** 1.4, "low", 2)
    return out * env[:, None] * (1 + 0.04 * np.sin(2 * np.pi * 0.35 * T))[:, None]


def build():
    mix = charge()
    mix += pull_in()
    mix += strike(2.20, 1.00, 0.0)
    mix += strike(2.70, 0.62, 0.30)
    mix += strike(3.03, 0.50, 0.45)
    mix += strike(3.40, 0.38, 0.6)
    mix += pad()
    # the ring closes: a bell with a fifth above, and a few faint ones after
    mix += bell(2.20, 587.3, 0.20, 1.5, -0.15) + bell(2.20, 880.0, 0.11, 1.2, 0.2)
    for t0, f, g, p in ((2.95, 1174.7, 0.05, 0.4), (3.55, 880.0, 0.045, -0.5), (4.05, 1174.7, 0.04, 0.2), (4.45, 1318.5, 0.03, -0.2)):
        mix += bell(t0, f, g, 1.1, p)
    # a room round everything (the crack stays dry because it is far louder than its own tail)
    wet = convolve(mix, reverb_ir(2.6, 0.85, 4500))
    mix = mix * 0.82 + wet * 0.55
    # a gentle start and the end that goes with the film's fade to black
    mix *= np.minimum(1.0, T / 0.04)[:, None]
    mix *= (1.0 - np.clip((T - 4.55) / 0.55, 0, 1) ** 1.5)[:, None]
    # no DC or rumble below hearing, then a soft limiter
    mix = np.stack([highpass(mix[:, c], 22, 2) for c in range(2)], 1)
    peak = np.abs(mix).max()
    mix = mix / peak * 1.35
    mix = np.tanh(mix) / np.tanh(1.35)
    mix *= 0.89
    return mix


if __name__ == "__main__":
    m = build()
    wavfile.write(sys.argv[1], SR, (np.clip(m, -1, 1) * 32767).astype(np.int16))
    rms = 20 * np.log10(np.sqrt((m ** 2).mean()) + 1e-9)
    print("peak %.2f  rms %.1f dBFS" % (np.abs(m).max(), rms))
