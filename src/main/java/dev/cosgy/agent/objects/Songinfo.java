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
        "TITLE",
        "ARTIST",
        "ALBUM",
        "YEAR",
        "CIRCLE",
        "ALBUMART"
})
public class Songinfo {

    @JsonProperty("TITLE")
    private String title;
    @JsonProperty("ARTIST")
    private String artist;
    @JsonProperty("ALBUM")
    private String album;
    @JsonProperty("YEAR")
    private String year;
    @JsonProperty("CIRCLE")
    private String circle;
    @JsonProperty("ALBUMART")
    private String albumArt;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("TITLE")
    public String getTitle() {
        return title;
    }

    @JsonProperty("TITLE")
    public void setTitle(String title) {
        this.title = title;
    }

    @JsonProperty("ARTIST")
    public String getArtist() {
        return artist;
    }

    @JsonProperty("ARTIST")
    public void setArtist(String artist) {
        this.artist = artist;
    }

    @JsonProperty("ALBUM")
    public String getAlbum() {
        return album;
    }

    @JsonProperty("ALBUM")
    public void setAlbum(String album) {
        this.album = album;
    }

    @JsonProperty("YEAR")
    public String getYear() {
        return year;
    }

    @JsonProperty("YEAR")
    public void setYear(String year) {
        this.year = year;
    }

    @JsonProperty("CIRCLE")
    public String getCircle() {
        return circle;
    }

    @JsonProperty("CIRCLE")
    public void setCircle(String circle) {
        this.circle = circle;
    }

    @JsonProperty("ALBUMART")
    public String getAlbumArt() {
        return albumArt;
    }

    @JsonProperty("ALBUMART")
    public void setAlbumArt(String albumArt) {
        this.albumArt = albumArt;
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
        return "Songinfo{" +
               "title='" + title + '\'' +
               ", artist='" + artist + '\'' +
               ", album='" + album + '\'' +
               ", year='" + year + '\'' +
               ", circle='" + circle + '\'' +
               ", albumArt='" + albumArt + '\'' +
               '}';
    }
}
