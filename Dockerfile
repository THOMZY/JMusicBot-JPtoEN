#　JMusicBot JP Docker container configuration file
#  Maintained by CyberRex (CyberRex0)
#  Edited by kichirouhoshino for JMusicBot-JPtoEN

FROM eclipse-temurin:25-jdk

# DO NOT EDIT UNDER THIS LINE
RUN mkdir -p /opt/jmusicbot

WORKDIR /opt/jmusicbot

RUN \
    printf "JMusicBot-JP Docker Container Builder v1.1\nMaintained by CyberRex (CyberRex0)\n" && \
    echo "Preconfiguring apt..." && apt-get update > /dev/null && \
    echo "Installing packages..." && apt-get install -y ffmpeg curl jq > /dev/null && \
    rm -rf /var/lib/apt/lists/* && \
    echo "Downloading latest version of JMusicBot-JP..." && \
    curl -fsSL "$(curl -fsSL https://api.github.com/repos/THOMZY/JMusicBot-JPtoEN/releases/latest | jq -r '.assets[] | select(.browser_download_url | contains(".jar")) | .browser_download_url')" -o /opt/jmusicbot/jmusicbot.jar && \
    echo "cd /opt/jmusicbot && java --enable-native-access=ALL-UNNAMED -Dnogui=true -jar jmusicbot.jar" > /opt/jmusicbot/execute.bash && \
    echo "Build Completed."

CMD ["bash", "/opt/jmusicbot/execute.bash"]
