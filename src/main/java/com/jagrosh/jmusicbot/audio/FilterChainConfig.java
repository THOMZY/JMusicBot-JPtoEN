/*
 * Copyright 2026 THOMZY
 */

package com.jagrosh.jmusicbot.audio;

import com.github.natanbc.lavadsp.channelmix.ChannelMixPcmAudioFilter;
import com.github.natanbc.lavadsp.distortion.DistortionPcmAudioFilter;
import com.github.natanbc.lavadsp.karaoke.KaraokePcmAudioFilter;
import com.github.natanbc.lavadsp.lowpass.LowPassPcmAudioFilter;
import com.github.natanbc.lavadsp.rotation.RotationPcmAudioFilter;
import com.github.natanbc.lavadsp.timescale.TimescalePcmAudioFilter;
import com.github.natanbc.lavadsp.tremolo.TremoloPcmAudioFilter;
import com.github.natanbc.lavadsp.vibrato.VibratoPcmAudioFilter;
import com.jagrosh.jmusicbot.audio.filter.EqualizerPcmAudioFilter;
import com.jagrosh.jmusicbot.audio.filter.ReverbPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;

import java.util.*;

public class FilterChainConfig {

    private final TimescaleConfig timescale = new TimescaleConfig();
    private final TremoloConfig tremolo = new TremoloConfig();
    private final VibratoConfig vibrato = new VibratoConfig();
    private final KaraokeConfig karaoke = new KaraokeConfig();
    private final RotationConfig rotation = new RotationConfig();
    private final DistortionConfig distortion = new DistortionConfig();
    private final ChannelMixConfig channelMix = new ChannelMixConfig();
    private final LowPassConfig lowPass = new LowPassConfig();
    private final ReverbConfig reverb = new ReverbConfig();
    private final EqualizerConfig equalizer = new EqualizerConfig();

    public boolean isAnyEnabled() {
        return timescale.enabled || tremolo.enabled || vibrato.enabled || karaoke.enabled
                || rotation.enabled || distortion.enabled || channelMix.enabled || lowPass.enabled
                || reverb.enabled || equalizer.enabled;
    }

    public List<AudioFilter> buildChain(AudioDataFormat format, FloatPcmAudioFilter output) {
        List<AudioFilter> filters = new ArrayList<>();
        FloatPcmAudioFilter current = output;
        int channels = format.channelCount;
        int sampleRate = format.sampleRate;

        if (equalizer.enabled) {
            EqualizerPcmAudioFilter f = new EqualizerPcmAudioFilter(current, channels, sampleRate);
            f.setGains(equalizer.bandGains);
            current = f;
            filters.add(f);
        }
        if (reverb.enabled) {
            ReverbPcmAudioFilter f = new ReverbPcmAudioFilter(current, channels, sampleRate);
            f.setRoomSize(reverb.roomSize);
            f.setDamping(reverb.damping);
            f.setWetLevel(reverb.wetLevel);
            current = f;
            filters.add(f);
        }
        if (lowPass.enabled) {
            LowPassPcmAudioFilter f = new LowPassPcmAudioFilter(current, channels);
            f.setSmoothing(lowPass.smoothing);
            current = f;
            filters.add(f);
        }
        if (channelMix.enabled) {
            ChannelMixPcmAudioFilter f = new ChannelMixPcmAudioFilter(current);
            f.setLeftToLeft(channelMix.leftToLeft);
            f.setLeftToRight(channelMix.leftToRight);
            f.setRightToLeft(channelMix.rightToLeft);
            f.setRightToRight(channelMix.rightToRight);
            current = f;
            filters.add(f);
        }
        if (distortion.enabled) {
            DistortionPcmAudioFilter f = new DistortionPcmAudioFilter(current, channels);
            f.setSinOffset(distortion.sinOffset);
            f.setSinScale(distortion.sinScale);
            f.setCosOffset(distortion.cosOffset);
            f.setCosScale(distortion.cosScale);
            f.setTanOffset(distortion.tanOffset);
            f.setTanScale(distortion.tanScale);
            f.setOffset(distortion.offset);
            f.setScale(distortion.scale);
            current = f;
            filters.add(f);
        }
        if (rotation.enabled) {
            RotationPcmAudioFilter f = new RotationPcmAudioFilter(current, sampleRate);
            f.setRotationSpeed(rotation.rotationHz);
            current = f;
            filters.add(f);
        }
        if (karaoke.enabled) {
            KaraokePcmAudioFilter f = new KaraokePcmAudioFilter(current, channels, sampleRate);
            f.setLevel(karaoke.level);
            f.setMonoLevel(karaoke.monoLevel);
            f.setFilterBand(karaoke.filterBand);
            f.setFilterWidth(karaoke.filterWidth);
            current = f;
            filters.add(f);
        }
        if (vibrato.enabled) {
            VibratoPcmAudioFilter f = new VibratoPcmAudioFilter(current, channels, sampleRate);
            f.setFrequency(vibrato.frequency);
            f.setDepth(vibrato.depth);
            current = f;
            filters.add(f);
        }
        if (tremolo.enabled) {
            TremoloPcmAudioFilter f = new TremoloPcmAudioFilter(current, channels, sampleRate);
            f.setFrequency(tremolo.frequency);
            f.setDepth(tremolo.depth);
            current = f;
            filters.add(f);
        }
        if (timescale.enabled) {
            TimescalePcmAudioFilter f = new TimescalePcmAudioFilter(current, channels, sampleRate);
            f.setSpeed(timescale.speed);
            f.setPitch(timescale.pitch);
            f.setRate(timescale.rate);
            current = f;
            filters.add(f);
        }

        // The list must be ordered with the entry point (outermost wrapper) first.
        // Lavaplayer's UserProvidedAudioFilters reverses this list and uses the last
        // element as the chain input, so entry-point-first is the correct convention.
        Collections.reverse(filters);
        return filters;
    }

    public void resetAll() {
        timescale.reset();
        tremolo.reset();
        vibrato.reset();
        karaoke.reset();
        rotation.reset();
        distortion.reset();
        channelMix.reset();
        lowPass.reset();
        reverb.reset();
        equalizer.reset();
    }

    // region Getters
    public TimescaleConfig getTimescale() { return timescale; }
    public TremoloConfig getTremolo() { return tremolo; }
    public VibratoConfig getVibrato() { return vibrato; }
    public KaraokeConfig getKaraoke() { return karaoke; }
    public RotationConfig getRotation() { return rotation; }
    public DistortionConfig getDistortion() { return distortion; }
    public ChannelMixConfig getChannelMix() { return channelMix; }
    public LowPassConfig getLowPass() { return lowPass; }
    public ReverbConfig getReverb() { return reverb; }
    public EqualizerConfig getEqualizer() { return equalizer; }
    // endregion

    @SuppressWarnings("unchecked")
    public void fromMap(Map<String, Object> data) {
        if (data.containsKey("timescale")) timescale.fromMap((Map<String, Object>) data.get("timescale"));
        if (data.containsKey("tremolo")) tremolo.fromMap((Map<String, Object>) data.get("tremolo"));
        if (data.containsKey("vibrato")) vibrato.fromMap((Map<String, Object>) data.get("vibrato"));
        if (data.containsKey("karaoke")) karaoke.fromMap((Map<String, Object>) data.get("karaoke"));
        if (data.containsKey("rotation")) rotation.fromMap((Map<String, Object>) data.get("rotation"));
        if (data.containsKey("distortion")) distortion.fromMap((Map<String, Object>) data.get("distortion"));
        if (data.containsKey("channelMix")) channelMix.fromMap((Map<String, Object>) data.get("channelMix"));
        if (data.containsKey("lowPass")) lowPass.fromMap((Map<String, Object>) data.get("lowPass"));
        if (data.containsKey("reverb")) reverb.fromMap((Map<String, Object>) data.get("reverb"));
        if (data.containsKey("equalizer")) equalizer.fromMap((Map<String, Object>) data.get("equalizer"));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timescale", timescale.toMap());
        map.put("tremolo", tremolo.toMap());
        map.put("vibrato", vibrato.toMap());
        map.put("karaoke", karaoke.toMap());
        map.put("rotation", rotation.toMap());
        map.put("distortion", distortion.toMap());
        map.put("channelMix", channelMix.toMap());
        map.put("lowPass", lowPass.toMap());
        map.put("reverb", reverb.toMap());
        map.put("equalizer", equalizer.toMap());
        return map;
    }

    // ========================= Filter Config Classes =========================

    public static class TimescaleConfig {
        boolean enabled = false;
        double speed = 1.0, pitch = 1.0, rate = 1.0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean e) { this.enabled = e; }
        public double getSpeed() { return speed; }
        public void setSpeed(double v) { this.speed = Math.max(0.1, Math.min(v, 10.0)); }
        public double getPitch() { return pitch; }
        public void setPitch(double v) { this.pitch = Math.max(0.1, Math.min(v, 10.0)); }
        public double getRate() { return rate; }
        public void setRate(double v) { this.rate = Math.max(0.1, Math.min(v, 10.0)); }

        void reset() {
            enabled = false;
            speed = 1.0;
            pitch = 1.0;
            rate = 1.0;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enabled", enabled);
            m.put("speed", speed);
            m.put("pitch", pitch);
            m.put("rate", rate);
            return m;
        }

        void fromMap(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = Boolean.parseBoolean(m.get("enabled").toString());
            if (m.containsKey("speed")) setSpeed(Double.parseDouble(m.get("speed").toString()));
            if (m.containsKey("pitch")) setPitch(Double.parseDouble(m.get("pitch").toString()));
            if (m.containsKey("rate")) setRate(Double.parseDouble(m.get("rate").toString()));
        }
    }

    public static class TremoloConfig {
        boolean enabled = false;
        float frequency = 2.0f, depth = 0.5f;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean e) { this.enabled = e; }
        public float getFrequency() { return frequency; }
        public void setFrequency(float v) { this.frequency = Math.max(0.1f, Math.min(v, 100.0f)); }
        public float getDepth() { return depth; }
        public void setDepth(float v) { this.depth = Math.max(0.01f, Math.min(v, 1.0f)); }

        void reset() {
            enabled = false;
            frequency = 2.0f;
            depth = 0.5f;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enabled", enabled);
            m.put("frequency", frequency);
            m.put("depth", depth);
            return m;
        }

        void fromMap(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = Boolean.parseBoolean(m.get("enabled").toString());
            if (m.containsKey("frequency")) setFrequency(Float.parseFloat(m.get("frequency").toString()));
            if (m.containsKey("depth")) setDepth(Float.parseFloat(m.get("depth").toString()));
        }
    }

    public static class VibratoConfig {
        boolean enabled = false;
        float frequency = 2.0f, depth = 0.5f;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean e) { this.enabled = e; }
        public float getFrequency() { return frequency; }
        public void setFrequency(float v) { this.frequency = Math.max(0.1f, Math.min(v, 14.0f)); }
        public float getDepth() { return depth; }
        public void setDepth(float v) { this.depth = Math.max(0.01f, Math.min(v, 1.0f)); }

        void reset() {
            enabled = false;
            frequency = 2.0f;
            depth = 0.5f;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enabled", enabled);
            m.put("frequency", frequency);
            m.put("depth", depth);
            return m;
        }

        void fromMap(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = Boolean.parseBoolean(m.get("enabled").toString());
            if (m.containsKey("frequency")) setFrequency(Float.parseFloat(m.get("frequency").toString()));
            if (m.containsKey("depth")) setDepth(Float.parseFloat(m.get("depth").toString()));
        }
    }

    public static class KaraokeConfig {
        boolean enabled = false;
        float level = 1.0f, monoLevel = 1.0f, filterBand = 220.0f, filterWidth = 100.0f;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean e) { this.enabled = e; }
        public float getLevel() { return level; }
        public void setLevel(float v) { this.level = Math.max(0.0f, Math.min(v, 1.0f)); }
        public float getMonoLevel() { return monoLevel; }
        public void setMonoLevel(float v) { this.monoLevel = Math.max(0.0f, Math.min(v, 1.0f)); }
        public float getFilterBand() { return filterBand; }
        public void setFilterBand(float v) { this.filterBand = Math.max(0.0f, Math.min(v, 1000.0f)); }
        public float getFilterWidth() { return filterWidth; }
        public void setFilterWidth(float v) { this.filterWidth = Math.max(0.0f, Math.min(v, 1000.0f)); }

        void reset() {
            enabled = false;
            level = 1.0f;
            monoLevel = 1.0f;
            filterBand = 220.0f;
            filterWidth = 100.0f;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enabled", enabled);
            m.put("level", level);
            m.put("monoLevel", monoLevel);
            m.put("filterBand", filterBand);
            m.put("filterWidth", filterWidth);
            return m;
        }

        void fromMap(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = Boolean.parseBoolean(m.get("enabled").toString());
            if (m.containsKey("level")) setLevel(Float.parseFloat(m.get("level").toString()));
            if (m.containsKey("monoLevel")) setMonoLevel(Float.parseFloat(m.get("monoLevel").toString()));
            if (m.containsKey("filterBand")) setFilterBand(Float.parseFloat(m.get("filterBand").toString()));
            if (m.containsKey("filterWidth")) setFilterWidth(Float.parseFloat(m.get("filterWidth").toString()));
        }
    }

    public static class RotationConfig {
        boolean enabled = false;
        double rotationHz = 5.0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean e) { this.enabled = e; }
        public double getRotationHz() { return rotationHz; }
        public void setRotationHz(double v) { this.rotationHz = Math.max(0.0, Math.min(v, 50.0)); }

        void reset() {
            enabled = false;
            rotationHz = 5.0;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enabled", enabled);
            m.put("rotationHz", rotationHz);
            return m;
        }

        void fromMap(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = Boolean.parseBoolean(m.get("enabled").toString());
            if (m.containsKey("rotationHz")) setRotationHz(Double.parseDouble(m.get("rotationHz").toString()));
        }
    }

    public static class DistortionConfig {
        boolean enabled = false;
        float sinOffset = 0f, sinScale = 1f, cosOffset = 0f, cosScale = 1f;
        float tanOffset = 0f, tanScale = 1f, offset = 0f, scale = 1f;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean e) { this.enabled = e; }
        public float getSinOffset() { return sinOffset; }
        public void setSinOffset(float v) { this.sinOffset = v; }
        public float getSinScale() { return sinScale; }
        public void setSinScale(float v) { this.sinScale = v; }
        public float getCosOffset() { return cosOffset; }
        public void setCosOffset(float v) { this.cosOffset = v; }
        public float getCosScale() { return cosScale; }
        public void setCosScale(float v) { this.cosScale = v; }
        public float getTanOffset() { return tanOffset; }
        public void setTanOffset(float v) { this.tanOffset = v; }
        public float getTanScale() { return tanScale; }
        public void setTanScale(float v) { this.tanScale = v; }
        public float getOffset() { return offset; }
        public void setOffset(float v) { this.offset = v; }
        public float getScale() { return scale; }
        public void setScale(float v) { this.scale = v; }

        void reset() {
            enabled = false;
            sinOffset = 0f;
            sinScale = 1f;
            cosOffset = 0f;
            cosScale = 1f;
            tanOffset = 0f;
            tanScale = 1f;
            offset = 0f;
            scale = 1f;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enabled", enabled);
            m.put("sinOffset", sinOffset);
            m.put("sinScale", sinScale);
            m.put("cosOffset", cosOffset);
            m.put("cosScale", cosScale);
            m.put("tanOffset", tanOffset);
            m.put("tanScale", tanScale);
            m.put("offset", offset);
            m.put("scale", scale);
            return m;
        }

        void fromMap(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = Boolean.parseBoolean(m.get("enabled").toString());
            if (m.containsKey("sinOffset")) sinOffset = Float.parseFloat(m.get("sinOffset").toString());
            if (m.containsKey("sinScale")) sinScale = Float.parseFloat(m.get("sinScale").toString());
            if (m.containsKey("cosOffset")) cosOffset = Float.parseFloat(m.get("cosOffset").toString());
            if (m.containsKey("cosScale")) cosScale = Float.parseFloat(m.get("cosScale").toString());
            if (m.containsKey("tanOffset")) tanOffset = Float.parseFloat(m.get("tanOffset").toString());
            if (m.containsKey("tanScale")) tanScale = Float.parseFloat(m.get("tanScale").toString());
            if (m.containsKey("offset")) offset = Float.parseFloat(m.get("offset").toString());
            if (m.containsKey("scale")) scale = Float.parseFloat(m.get("scale").toString());
        }
    }

    public static class ChannelMixConfig {
        boolean enabled = false;
        float leftToLeft = 1.0f, leftToRight = 0.0f, rightToLeft = 0.0f, rightToRight = 1.0f;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean e) { this.enabled = e; }
        public float getLeftToLeft() { return leftToLeft; }
        public void setLeftToLeft(float v) { this.leftToLeft = Math.max(0f, Math.min(v, 1f)); }
        public float getLeftToRight() { return leftToRight; }
        public void setLeftToRight(float v) { this.leftToRight = Math.max(0f, Math.min(v, 1f)); }
        public float getRightToLeft() { return rightToLeft; }
        public void setRightToLeft(float v) { this.rightToLeft = Math.max(0f, Math.min(v, 1f)); }
        public float getRightToRight() { return rightToRight; }
        public void setRightToRight(float v) { this.rightToRight = Math.max(0f, Math.min(v, 1f)); }

        void reset() {
            enabled = false;
            leftToLeft = 1.0f;
            leftToRight = 0.0f;
            rightToLeft = 0.0f;
            rightToRight = 1.0f;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enabled", enabled);
            m.put("leftToLeft", leftToLeft);
            m.put("leftToRight", leftToRight);
            m.put("rightToLeft", rightToLeft);
            m.put("rightToRight", rightToRight);
            return m;
        }

        void fromMap(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = Boolean.parseBoolean(m.get("enabled").toString());
            if (m.containsKey("leftToLeft")) setLeftToLeft(Float.parseFloat(m.get("leftToLeft").toString()));
            if (m.containsKey("leftToRight")) setLeftToRight(Float.parseFloat(m.get("leftToRight").toString()));
            if (m.containsKey("rightToLeft")) setRightToLeft(Float.parseFloat(m.get("rightToLeft").toString()));
            if (m.containsKey("rightToRight")) setRightToRight(Float.parseFloat(m.get("rightToRight").toString()));
        }
    }

    public static class LowPassConfig {
        boolean enabled = false;
        float smoothing = 20.0f;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean e) { this.enabled = e; }
        public float getSmoothing() { return smoothing; }
        public void setSmoothing(float v) { this.smoothing = Math.max(1.0f, Math.min(v, 100.0f)); }

        void reset() {
            enabled = false;
            smoothing = 20.0f;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enabled", enabled);
            m.put("smoothing", smoothing);
            return m;
        }

        void fromMap(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = Boolean.parseBoolean(m.get("enabled").toString());
            if (m.containsKey("smoothing")) setSmoothing(Float.parseFloat(m.get("smoothing").toString()));
        }
    }

    public static class ReverbConfig {
        boolean enabled = false;
        float roomSize = 0.7f, damping = 0.5f, wetLevel = 0.3f;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean e) { this.enabled = e; }
        public float getRoomSize() { return roomSize; }
        public void setRoomSize(float v) { this.roomSize = Math.max(0.0f, Math.min(v, 1.0f)); }
        public float getDamping() { return damping; }
        public void setDamping(float v) { this.damping = Math.max(0.0f, Math.min(v, 1.0f)); }
        public float getWetLevel() { return wetLevel; }
        public void setWetLevel(float v) { this.wetLevel = Math.max(0.0f, Math.min(v, 1.0f)); }

        void reset() {
            enabled = false;
            roomSize = 0.7f;
            damping = 0.5f;
            wetLevel = 0.3f;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enabled", enabled);
            m.put("roomSize", roomSize);
            m.put("damping", damping);
            m.put("wetLevel", wetLevel);
            return m;
        }

        void fromMap(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = Boolean.parseBoolean(m.get("enabled").toString());
            if (m.containsKey("roomSize")) setRoomSize(Float.parseFloat(m.get("roomSize").toString()));
            if (m.containsKey("damping")) setDamping(Float.parseFloat(m.get("damping").toString()));
            if (m.containsKey("wetLevel")) setWetLevel(Float.parseFloat(m.get("wetLevel").toString()));
        }
    }

    public static class EqualizerConfig {
        boolean enabled = false;
        float[] bandGains = new float[EqualizerPcmAudioFilter.BAND_COUNT]; // all 0.0 by default

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean e) { this.enabled = e; }
        public float getGain(int band) {
            if (band < 0 || band >= EqualizerPcmAudioFilter.BAND_COUNT) return 0f;
            return bandGains[band];
        }
        public void setGain(int band, float v) {
            if (band < 0 || band >= EqualizerPcmAudioFilter.BAND_COUNT) return;
            bandGains[band] = Math.max(-0.25f, Math.min(v, 1.0f));
        }
        public float[] getBandGains() { return bandGains; }

        void reset() {
            enabled = false;
            bandGains = new float[EqualizerPcmAudioFilter.BAND_COUNT];
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enabled", enabled);
            List<Float> gains = new ArrayList<>();
            for (float g : bandGains) gains.add(g);
            m.put("bandGains", gains);
            // Also expose individual band keys for web UI compatibility
            for (int i = 0; i < EqualizerPcmAudioFilter.BAND_COUNT; i++) {
                m.put("band" + i, bandGains[i]);
            }
            return m;
        }

        void fromMap(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = Boolean.parseBoolean(m.get("enabled").toString());
            if (m.containsKey("bandGains")) {
                List<?> list = (List<?>) m.get("bandGains");
                for (int i = 0; i < Math.min(list.size(), EqualizerPcmAudioFilter.BAND_COUNT); i++) {
                    setGain(i, Float.parseFloat(list.get(i).toString()));
                }
            }
            // Also accept individual band keys from web UI (band0, band1, ..., band14)
            for (int i = 0; i < EqualizerPcmAudioFilter.BAND_COUNT; i++) {
                String key = "band" + i;
                if (m.containsKey(key)) {
                    setGain(i, Float.parseFloat(m.get(key).toString()));
                }
            }
        }
    }
}
