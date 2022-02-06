# Streaming platform

## Profiles

* empty: Normal usage
* agent: Run as remote device agent, you could edit [profile](./src/main/resources/application-agent.yml) to control proxy port and which device is proxy-able
* debug: Run as remote device debug client, you could edit [profile](./src/main/resources/application-debug.yml) to control adb server's host and port

# Project setup
```bash
 # Normal usage
 mvn spring-boot:run
 # Run as remote device agent
 mvn spring-boot:run -Dspring.profiles.active=agent
 # Run as remote device debug
 mvn spring-boot:run -Dspring.profiles.active=debug
```


#FFMPEG commands:
- ffmpeg -fflags discardcorrupt -i rtp://192.168.1.191:1240 -vframes 100 -q:v 1 frame%d_.jpg
- ffmpeg -i out.ts -q:v 4 -f image2pipe -
- write so single image: ffmpeg -fflags discardcorrupt -i rtp://192.168.1.191:1240  -q:v 1 -f image2 -r 24 -update 1 output.jpg
- generate video from frames: ffmpeg -framerate 24 -i frame%d_.jpg -c:v libx264 -crf 25 -vf "scale=500:500, format=yuv420p" -movflags +faststart output.ts
- ffmpeg -fflags discardcorrupt -i rtp://192.168.1.191:1240 -q:v 1 -r 24  -f image2pipe pipe:1 > output.jpg



documentation: https://ffmpeg.org/ffmpeg-codecs.html
Use -qscale:v to control quality
Use -qscale:v (or the alias -q:v) as an output option.

Normal range for JPEG is 2-31 with 31 being the worst quality.
The scale is linear with double the qscale being roughly half the bitrate.
Recommend trying values of 2-5.
You can use a value of 1 but you must add the -qmin 1 output option (because the default is -qmin 2).
To output a series of images:
ffmpeg -i input.mp4 -qscale:v 2 output_%03d.jpg
See the image muxer documentation for more options involving image outputs.

To output a single image at ~60 seconds duration:
ffmpeg -ss 60 -i input.mp4 -qscale:v 4 -frames:v 1 output.jpg
To continuously overwrite/update/save to a single image
Use -update 1 image muxer option. Example for once per second from a live streaming input:

#LINUX BUFFER:
sudo sysctl -w net.core.rmem_max=2097152
sudo sysctl -w net.core.rmem_default=2097152
sudo sysctl -w net.core.wmem_max=2097152
sudo sysctl -w net.core.wmem_default=2097152

#SET 
#SET maxrate and buffsize to encode rtp correctly

!smearing image:

ffmpeg -f x11grab -r 15 -s 1366x768 -i :0.0+0,0 \
-c:v libx264 -preset ultrafast -b 500k \
-tune zerolatency \
-maxrate 500k -bufsize 500k \
-pix_fmt yuv420p \
-f mpegts 'udp://192.168.1.102:6881?pkt_size=1316'

sudo sysctl -w net.core.rmem_max=16777216
sudo sysctl -w net.core.rmem_default=16777216
sudo sysctl -w net.core.wmem_max=16777216
sudo sysctl -w net.core.wmem_default=16777216