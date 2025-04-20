/*
 *  Copyright 2022 Cosgy Dev (info@cosgy.dev).
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
        "SERVERINFO",
        "SONGINFO",
        "SONGTIMES",
        "SONGDATA",
        "MISC"
})

public class ResultSet {

    @JsonProperty("SERVERINFO")
    private Serverinfo serverinfo;
    @JsonProperty("SONGINFO")
    private Songinfo songinfo;
    @JsonProperty("SONGTIMES")
    private Songtimes songtimes;
    @JsonProperty("SONGDATA")
    private Songdata songdata;
    @JsonProperty("MISC")
    private Misc misc;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("SERVERINFO")
    public Serverinfo getServerinfo() {
        return serverinfo;
    }

    @JsonProperty("SERVERINFO")
    public void setServerinfo(Serverinfo serverinfo) {
        this.serverinfo = serverinfo;
    }

    @JsonProperty("SONGINFO")
    public Songinfo getSonginfo() {
        return songinfo;
    }

    @JsonProperty("SONGINFO")
    public void setSonginfo(Songinfo songinfo) {
        this.songinfo = songinfo;
    }

    @JsonProperty("SONGTIMES")
    public Songtimes getSongtimes() {
        return songtimes;
    }

    @JsonProperty("SONGTIMES")
    public void setSongtimes(Songtimes songtimes) {
        this.songtimes = songtimes;
    }

    @JsonProperty("SONGDATA")
    public Songdata getSongdata() {
        return songdata;
    }

    @JsonProperty("SONGDATA")
    public void setSongdata(Songdata songdata) {
        this.songdata = songdata;
    }

    @JsonProperty("MISC")
    public Misc getMisc() {
        return misc;
    }

    @JsonProperty("MISC")
    public void setMisc(Misc misc) {
        this.misc = misc;
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
        return "ResultSet{" +
               "serverinfo=" + (serverinfo != null ? serverinfo : "null") +
               ", songinfo=" + (songinfo != null ? songinfo : "null") +
               ", songtimes=" + (songtimes != null ? songtimes : "null") +
               ", songdata=" + (songdata != null ? songdata : "null") +
               ", misc=" + (misc != null ? misc : "null") +
               '}';
    }
}
