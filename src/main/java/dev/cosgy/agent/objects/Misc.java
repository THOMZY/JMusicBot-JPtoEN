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
        "CIRCLELINK",
        "ALBUMART",
        "CIRCLEART",
        "OFFSET",
        "OFFSETTIME"
})

public class Misc {

    @JsonProperty("CIRCLELINK")
    private String circlelink;
    @JsonProperty("ALBUMART")
    private String albumart;
    @JsonProperty("CIRCLEART")
    private String circleart;
    @JsonProperty("OFFSET")
    private String offset;
    @JsonProperty("OFFSETTIME")
    private Integer offsettime;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("CIRCLELINK")
    public String getCirclelink() {
        return circlelink;
    }

    @JsonProperty("CIRCLELINK")
    public void setCirclelink(String circlelink) {
        this.circlelink = circlelink;
    }

    @JsonProperty("ALBUMART")
    public String getAlbumart() {
        return albumart;
    }

    @JsonProperty("ALBUMART")
    public void setAlbumart(String albumart) {
        this.albumart = albumart;
    }

    @JsonProperty("CIRCLEART")
    public String getCircleart() {
        return circleart;
    }

    @JsonProperty("CIRCLEART")
    public void setCircleart(String circleart) {
        this.circleart = circleart;
    }

    @JsonProperty("OFFSET")
    public String getOffset() {
        return offset;
    }

    @JsonProperty("OFFSET")
    public void setOffset(String offset) {
        this.offset = offset;
    }

    @JsonProperty("OFFSETTIME")
    public Integer getOffsettime() {
        return offsettime;
    }

    @JsonProperty("OFFSETTIME")
    public void setOffsettime(Integer offsettime) {
        this.offsettime = offsettime;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    /**
     * Get the full URL to the album artwork
     * @return The complete URL to the album artwork
     */
    public String getFullAlbumArtUrl() {
        if (albumart == null || albumart.isEmpty()) {
            return "";
        }
        
        return "https://gensokyoradio.net/images/albums/500/" + albumart;
    }
    
    @Override
    public String toString() {
        return "Misc{" +
               "circlelink='" + circlelink + '\'' +
               ", albumart='" + albumart + '\'' +
               ", circleart='" + circleart + '\'' +
               ", offset='" + offset + '\'' +
               ", offsettime=" + offsettime +
               '}';
    }
}
