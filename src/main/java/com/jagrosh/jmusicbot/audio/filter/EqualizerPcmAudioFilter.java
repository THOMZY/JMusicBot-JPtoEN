package com.jagrosh.jmusicbot.audio.filter;

import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;

/**
 * A 15-band parametric equalizer implemented as a FloatPcmAudioFilter.
 * Uses biquad peak filters at standard frequencies matching Lavalink's EQ bands.
 * Band frequencies: 25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000 Hz.
 */
public class EqualizerPcmAudioFilter implements FloatPcmAudioFilter {

    public static final int BAND_COUNT = 15;
    public static final float[] BAND_FREQUENCIES = {
            25f, 40f, 63f, 100f, 160f, 250f, 400f, 630f,
            1000f, 1600f, 2500f, 4000f, 6300f, 10000f, 16000f
    };

    private final FloatPcmAudioFilter downstream;
    private final int channelCount;
    private final int sampleRate;
    private final float[] gains;

    // Biquad coefficients per band
    private final double[][] b0, b1, b2, a1, a2;
    // Biquad state per channel per band: x[n-1], x[n-2], y[n-1], y[n-2]
    private final double[][][] state; // [channel][band][4]

    public EqualizerPcmAudioFilter(FloatPcmAudioFilter downstream, int channelCount, int sampleRate) {
        this.downstream = downstream;
        this.channelCount = channelCount;
        this.sampleRate = sampleRate;
        this.gains = new float[BAND_COUNT];

        // We store coefficients as [1][band] since they're same for all channels
        b0 = new double[1][BAND_COUNT];
        b1 = new double[1][BAND_COUNT];
        b2 = new double[1][BAND_COUNT];
        a1 = new double[1][BAND_COUNT];
        a2 = new double[1][BAND_COUNT];

        state = new double[channelCount][BAND_COUNT][4];

        // Initialize all bands with 0 gain (no effect)
        for (int i = 0; i < BAND_COUNT; i++) {
            gains[i] = 0f;
            computeCoefficients(i);
        }
    }

    /**
     * Get the gain for a specific band.
     * @param band Band index (0-14)
     * @return Gain value (-0.25 to 1.0)
     */
    public float getGain(int band) {
        if (band < 0 || band >= BAND_COUNT) return 0f;
        return gains[band];
    }

    /**
     * Set the gain for a specific band.
     * @param band Band index (0-14)
     * @param gain Gain value (-0.25 to 1.0). 0 = no change, negative = cut, positive = boost
     */
    public void setGain(int band, float gain) {
        if (band < 0 || band >= BAND_COUNT) return;
        gains[band] = Math.max(-0.25f, Math.min(gain, 1.0f));
        computeCoefficients(band);
    }

    /**
     * Set all band gains at once.
     * @param bandGains Array of 15 gain values
     */
    public void setGains(float[] bandGains) {
        for (int i = 0; i < Math.min(bandGains.length, BAND_COUNT); i++) {
            gains[i] = Math.max(-0.25f, Math.min(bandGains[i], 1.0f));
            computeCoefficients(i);
        }
    }

    /**
     * Computes biquad peak EQ coefficients for a given band.
     * Uses the Audio EQ Cookbook formula for peakingEQ filter.
     */
    private void computeCoefficients(int band) {
        float freq = BAND_FREQUENCIES[band];
        float gain = gains[band];

        // Convert gain to dB scale: gain of 0.25 = +6dB, gain of 1.0 = +24dB, gain of -0.25 = -6dB
        double dbGain = gain * 24.0;
        double A = Math.pow(10.0, dbGain / 40.0);
        double w0 = 2.0 * Math.PI * freq / sampleRate;
        double Q = 1.0; // Quality factor - moderate bandwidth
        double alpha = Math.sin(w0) / (2.0 * Q);

        double norm;

        if (Math.abs(gain) < 0.001) {
            // Passthrough - unity coefficients
            b0[0][band] = 1.0;
            b1[0][band] = 0.0;
            b2[0][band] = 0.0;
            a1[0][band] = 0.0;
            a2[0][band] = 0.0;
        } else {
            // Peaking EQ
            double b0val = 1.0 + alpha * A;
            double b1val = -2.0 * Math.cos(w0);
            double b2val = 1.0 - alpha * A;
            double a0val = 1.0 + alpha / A;
            double a1val = -2.0 * Math.cos(w0);
            double a2val = 1.0 - alpha / A;

            // Normalize
            norm = 1.0 / a0val;
            b0[0][band] = b0val * norm;
            b1[0][band] = b1val * norm;
            b2[0][band] = b2val * norm;
            a1[0][band] = a1val * norm;
            a2[0][band] = a2val * norm;
        }
    }

    @Override
    public void process(float[][] input, int offset, int length) throws InterruptedException {
        float[][] output = new float[channelCount][length];

        for (int ch = 0; ch < channelCount; ch++) {
            // Copy input to output first
            System.arraycopy(input[ch], offset, output[ch], 0, length);

            // Apply each band's biquad filter in series
            for (int band = 0; band < BAND_COUNT; band++) {
                if (Math.abs(gains[band]) < 0.001) continue; // Skip inactive bands

                double cb0 = b0[0][band], cb1 = b1[0][band], cb2 = b2[0][band];
                double ca1 = a1[0][band], ca2 = a2[0][band];
                double x1 = state[ch][band][0], x2 = state[ch][band][1];
                double y1 = state[ch][band][2], y2 = state[ch][band][3];

                for (int i = 0; i < length; i++) {
                    double x = output[ch][i];
                    double y = cb0 * x + cb1 * x1 + cb2 * x2 - ca1 * y1 - ca2 * y2;
                    x2 = x1;
                    x1 = x;
                    y2 = y1;
                    y1 = y;
                    output[ch][i] = (float) y;
                }

                state[ch][band][0] = x1;
                state[ch][band][1] = x2;
                state[ch][band][2] = y1;
                state[ch][band][3] = y2;
            }
        }

        downstream.process(output, 0, length);
    }

    @Override
    public void seekPerformed(long requestedTime, long providedTime) {
        // Reset biquad state on seek
        for (int ch = 0; ch < channelCount; ch++) {
            for (int band = 0; band < BAND_COUNT; band++) {
                state[ch][band][0] = 0;
                state[ch][band][1] = 0;
                state[ch][band][2] = 0;
                state[ch][band][3] = 0;
            }
        }
        // Do NOT forward to downstream - lavaplayer manages lifecycle for all filters
    }

    @Override
    public void flush() throws InterruptedException {
        // nothing to do - lavaplayer manages downstream lifecycle
    }

    @Override
    public void close() {
        // nothing to do - lavaplayer manages downstream lifecycle
    }
}
