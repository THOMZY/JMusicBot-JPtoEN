package com.jagrosh.jmusicbot.audio.filter;

import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;

import java.util.Arrays;

/**
 * Freeverb (Schroeder) reverb implementation as a FloatPcmAudioFilter.
 * 8 parallel comb filters followed by 4 series allpass filters per channel.
 * Lifecycle (seekPerformed/flush/close) is NOT forwarded to downstream;
 * lavaplayer's CompositeAudioFilter handles that for all filters in the chain.
 */
public class ReverbPcmAudioFilter implements FloatPcmAudioFilter {

    // Freeverb standard: 8 comb filters, 4 allpass filters
    private static final int[] COMB_DELAYS = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
    private static final int[] ALLPASS_DELAYS = {556, 441, 341, 225};
    private static final float ALLPASS_FEEDBACK = 0.5f;
    private static final float FIXED_GAIN = 0.015f;
    private static final float SCALE_ROOM = 0.28f;
    private static final float OFFSET_ROOM = 0.7f;
    private static final float SCALE_DAMP = 0.4f;
    private static final int STEREO_SPREAD = 23;

    private final FloatPcmAudioFilter downstream;
    private final int channelCount;

    // Per-channel state
    private final float[][][] combBuffers;    // [channel][combIndex][bufferLen]
    private final int[][] combPositions;       // [channel][combIndex]
    private final float[][] combFilterStore;   // [channel][combIndex] - for damping

    private final float[][][] allpassBuffers;  // [channel][apIndex][bufferLen]
    private final int[][] allpassPositions;    // [channel][apIndex]

    private volatile float roomSize = 0.7f;
    private volatile float damping = 0.5f;
    private volatile float wetLevel = 0.3f;

    public ReverbPcmAudioFilter(FloatPcmAudioFilter downstream, int channelCount, int sampleRate) {
        this.downstream = downstream;
        this.channelCount = channelCount;

        // Scale delays based on sample rate (base delays assume 44100 Hz)
        float scaleFactor = sampleRate / 44100.0f;

        combBuffers = new float[channelCount][COMB_DELAYS.length][];
        combPositions = new int[channelCount][COMB_DELAYS.length];
        combFilterStore = new float[channelCount][COMB_DELAYS.length];

        allpassBuffers = new float[channelCount][ALLPASS_DELAYS.length][];
        allpassPositions = new int[channelCount][ALLPASS_DELAYS.length];

        for (int ch = 0; ch < channelCount; ch++) {
            for (int i = 0; i < COMB_DELAYS.length; i++) {
                int spread = ch * STEREO_SPREAD;
                int bufLen = Math.max(1, (int) ((COMB_DELAYS[i] + spread) * scaleFactor));
                combBuffers[ch][i] = new float[bufLen];
                combPositions[ch][i] = 0;
                combFilterStore[ch][i] = 0f;
            }
            for (int i = 0; i < ALLPASS_DELAYS.length; i++) {
                int spread = ch * STEREO_SPREAD;
                int bufLen = Math.max(1, (int) ((ALLPASS_DELAYS[i] + spread) * scaleFactor));
                allpassBuffers[ch][i] = new float[bufLen];
                allpassPositions[ch][i] = 0;
            }
        }
    }

    public float getRoomSize() { return roomSize; }
    public void setRoomSize(float roomSize) { this.roomSize = Math.max(0.0f, Math.min(roomSize, 1.0f)); }

    public float getDamping() { return damping; }
    public void setDamping(float damping) { this.damping = Math.max(0.0f, Math.min(damping, 1.0f)); }

    public float getWetLevel() { return wetLevel; }
    public void setWetLevel(float wetLevel) { this.wetLevel = Math.max(0.0f, Math.min(wetLevel, 1.0f)); }

    @Override
    public void process(float[][] input, int offset, int length) throws InterruptedException {
        float wet = this.wetLevel;
        float dry = 1.0f - wet;
        // Freeverb feedback formula: roomsize * scaleRoom + offsetRoom
        float feedback = this.roomSize * SCALE_ROOM + OFFSET_ROOM;
        float damp1 = this.damping * SCALE_DAMP;
        float damp2 = 1.0f - damp1;

        float[][] output = new float[channelCount][length];

        for (int ch = 0; ch < channelCount; ch++) {
            for (int i = 0; i < length; i++) {
                float sample = input[ch][offset + i] * FIXED_GAIN;
                float reverbSample = 0f;

                // Process 8 parallel comb filters
                for (int c = 0; c < COMB_DELAYS.length; c++) {
                    float[] buf = combBuffers[ch][c];
                    int pos = combPositions[ch][c];

                    float bufOut = buf[pos];
                    // Low-pass filter in the feedback loop (damping)
                    combFilterStore[ch][c] = bufOut * damp2 + combFilterStore[ch][c] * damp1;
                    buf[pos] = sample + combFilterStore[ch][c] * feedback;

                    combPositions[ch][c] = (pos + 1) % buf.length;
                    reverbSample += bufOut;
                }

                // Process 4 series allpass filters
                for (int a = 0; a < ALLPASS_DELAYS.length; a++) {
                    float[] buf = allpassBuffers[ch][a];
                    int pos = allpassPositions[ch][a];

                    float bufOut = buf[pos];
                    buf[pos] = reverbSample + bufOut * ALLPASS_FEEDBACK;
                    reverbSample = bufOut - reverbSample * ALLPASS_FEEDBACK;

                    allpassPositions[ch][a] = (pos + 1) % buf.length;
                }

                output[ch][i] = input[ch][offset + i] * dry + reverbSample * wet;
            }
        }

        downstream.process(output, 0, length);
    }

    @Override
    public void seekPerformed(long requestedTime, long providedTime) {
        // Clear all buffers on seek - do NOT forward to downstream
        for (int ch = 0; ch < channelCount; ch++) {
            for (int c = 0; c < COMB_DELAYS.length; c++) {
                Arrays.fill(combBuffers[ch][c], 0f);
                combPositions[ch][c] = 0;
                combFilterStore[ch][c] = 0f;
            }
            for (int a = 0; a < ALLPASS_DELAYS.length; a++) {
                Arrays.fill(allpassBuffers[ch][a], 0f);
                allpassPositions[ch][a] = 0;
            }
        }
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
