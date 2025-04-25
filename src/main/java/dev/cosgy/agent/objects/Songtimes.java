/*
 *  Copyright 2022 Cosgy Dev (info@cosgy.dev).
 * Edit 2025 THOMZY
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package dev.cosgy.agent.objects;

import com.fasterxml.jackson.annotation.*;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "DURATION",
        "PLAYED",
        "REMAINING",
        "SONGSTART",
        "SONGEND"
})

public class Songtimes {

    @JsonProperty("DURATION")
    private Integer duration;
    @JsonProperty("PLAYED")
    private Integer played;
    @JsonProperty("REMAINING")
    private Integer remaining;
    @JsonProperty("SONGSTART")
    private Integer songstart;
    @JsonProperty("SONGEND")
    private Integer songend;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("DURATION")
    public Integer getDuration() {
        return duration;
    }

    @JsonProperty("DURATION")
    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    @JsonProperty("PLAYED")
    public Integer getPlayed() {
        return played;
    }

    @JsonProperty("PLAYED")
    public void setPlayed(Integer played) {
        this.played = played;
    }

    @JsonProperty("REMAINING")
    public Integer getRemaining() {
        return remaining;
    }

    @JsonProperty("REMAINING")
    public void setRemaining(Integer remaining) {
        this.remaining = remaining;
    }

    @JsonProperty("SONGSTART")
    public Integer getSongstart() {
        return songstart;
    }

    @JsonProperty("SONGSTART")
    public void setSongstart(Integer songstart) {
        this.songstart = songstart;
    }

    @JsonProperty("SONGEND")
    public Integer getSongend() {
        return songend;
    }

    @JsonProperty("SONGEND")
    public void setSongend(Integer songend) {
        this.songend = songend;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    @Override
    public String toString() {
        return "Songtimes{" +
               "duration=" + duration +
               ", played=" + played +
               ", remaining=" + remaining +
               ", songstart=" + songstart +
               ", songend=" + songend +
               '}';
    }
}
