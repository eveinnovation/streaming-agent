package com.eveinnovation.streamingplatform.handler;

import com.eveinnovation.streamingplatform.common.Constants;
import com.eveinnovation.streamingplatform.common.NamedThreadFactory;
import com.eveinnovation.streamingplatform.config.WebRtcTurnConfig;
import com.eveinnovation.streamingplatform.use.UseContext;
import bbm.webrtc.rtc4j.core.DataChannel;
import bbm.webrtc.rtc4j.core.RTC;
import bbm.webrtc.rtc4j.core.audio.AudioCapturer;
import bbm.webrtc.rtc4j.core.observer.PeerConnectionObserver;
import bbm.webrtc.rtc4j.core.video.VideoCapturer;
import bbm.webrtc.rtc4j.model.DataBuffer;
import bbm.webrtc.rtc4j.model.DataChannelConfig;
import bbm.webrtc.rtc4j.model.IceCandidate;
import bbm.webrtc.rtc4j.model.SessionDescription;
import bbm.webrtc.rtc4j.model.SignalingState;
import bbm.webrtc.rtc4j.model.VideoFrame;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.eveinnovation.streamingplatform.util.ImageUtils;
import com.eveinnovation.streamingplatform.util.JavaImgConverter;
import com.eveinnovation.streamingplatform.util.Main;
import com.eveinnovation.streamingplatform.util.ReadFramesAsStreamJpeg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author bbm
 */
@SuppressWarnings("unused")
@Component
@Slf4j
public class MessageHandler {

    private static final int THREAD_SIZE = 10;
    //aici white list
    private static final String WHITE_PRIVATE_IP_PREFIX = "19";
    private static final int MIN_PORT = 50000;
    private static final int MAX_PORT = 51000;
    private static final boolean HARDWARE_ACCELERATE = true;
    private static final int MAX_BIT_RATE = 2 * 1024 * 1024;
    private int f_idx = 1;
    private boolean isDefaultImage = false;
    private ByteArrayOutputStream byteArrayOutputStream;
    private final PipedOutputStream out;
    private final PipedInputStream in;
    private int readSize;
    private final byte[] buffer = new byte[1024];

    @Autowired
    private WebRtcTurnConfig webRtcTurnConfig;

    @Autowired
    private ReadFramesAsStreamJpeg readFramesAsStreamJpeg;


    private final Map<UUID, UseContext> useContextMap = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor;

    public MessageHandler() throws IOException {
        this.out = new PipedOutputStream();
        this.in = new PipedInputStream(out);
        executor = new ThreadPoolExecutor(
                THREAD_SIZE, THREAD_SIZE,
                Integer.MAX_VALUE, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), new NamedThreadFactory("Message", THREAD_SIZE));
    }

    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        stopUse(client);
    }

    @SuppressWarnings("WeakerAccess")
    @OnEvent(Constants.STOP_USE)
    public void stopUse(SocketIOClient client) {
        synchronized (useContextMap) {
            UseContext previousContext = useContextMap.remove(client.getSessionId());
            if (!Objects.isNull(previousContext)) {
                stop(previousContext);
            }
        }
    }

    @OnEvent(Constants.BEGIN_USE)
    public void beginUse(SocketIOClient client) {
        synchronized (useContextMap) {
            UseContext newUseContext = new UseContext(client);
            UseContext previousContext = useContextMap.put(client.getSessionId(), newUseContext);
            if (!Objects.isNull(previousContext)) {
                stop(previousContext);
            }
            start(newUseContext);
        }
    }

    @OnEvent(Constants.BEGIN_WEB_RTC)
    public void beginWebRtc(SocketIOClient client) {
        getContextAndRunAsync(client.getSessionId(), context -> context.executeInLock(() -> {
            log.info("Create rtc core...");
            this.isDefaultImage = false;
            byteArrayOutputStream = new ByteArrayOutputStream();
            this.f_idx = 1;
            try {
                Runnable runA = () -> {
                    try {
                        write(out);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                };

                Thread threadA = new Thread(runA, "threadA");
                threadA.start();

            } catch (Exception e) {
                e.printStackTrace();
            }
            context.setRtc(new RTC(new AudioCapturer() {

                private volatile InputStream testAudio;
                private volatile ByteBuffer directByteBuffer;

                @Override
                public void close() {

                }

                @Override
                public int samplingFrequency() {
                    return 44100;
                }

                @Override
                public ByteBuffer capture(int size) {
                    if (directByteBuffer == null) {
                        directByteBuffer = ByteBuffer.allocateDirect(size);
                    }
                    directByteBuffer.clear();
                    try {
                        byte[] data = new byte[size];
                        while (directByteBuffer.hasRemaining()) {
                            if (testAudio == null) {
                                // 16-bit 44100Hz mono
                                testAudio = this.getClass().getResourceAsStream("/gong.wav");
                            }
                            try {
                                int length = testAudio.read(data, 0, directByteBuffer.remaining());
                                directByteBuffer.put(data, 0, length);
                                if (testAudio.available() == 0) {
                                    testAudio.close();
                                    testAudio = null;
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return directByteBuffer;
                }
            }, new VideoCapturer() {

                private volatile ByteBuffer sourceBuffer;
                private volatile int totalSize;
                private volatile ByteBuffer sourceBuffer2;
                private volatile int totalSize2;

                @Override
                public void close() {

                }

                @Override
                public int getWidth() {
                    return 1920;
                }

                @Override
                public int getHeight() {
                    return 1080;
                }

                @Override
                public int getFps() {
                    return 24;
                }

                @Override
                public VideoFrame capture() {


                    try {
                        while ((readSize = in.read(buffer, 0, buffer.length)) != -1) {
                            byteArrayOutputStream.write(buffer, 0, readSize);
                            if (byteArrayOutputStream.size() == Main.size) {
                                byte[] img = byteArrayOutputStream.toByteArray();
                                BufferedImage image = JavaImgConverter.BGR2BufferedImage(img, Main.width, Main.height);
                                byte[] bytes = ImageUtils.toByteArray(image, "jpeg");

                                try (InputStream imageStream = new ByteArrayInputStream(bytes)) {
                                    sourceBuffer = ByteBuffer.allocateDirect(imageStream.available());
                                    totalSize = 0;
                                    byte[] buffer = new byte[1024];
                                    while (imageStream.available() > 0) {
                                        int readSize = imageStream.read(buffer);
                                        byteArrayOutputStream.reset();
                                        if (readSize <= 0) {
                                            break;
                                        }
                                        totalSize += readSize;
                                        sourceBuffer.put(buffer, 0, readSize);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    isDefaultImage = true;
                                    return new VideoFrame(0, System.currentTimeMillis(), sourceBuffer, totalSize);
                                }


                                byteArrayOutputStream.flush();

                                return new VideoFrame(0, System.currentTimeMillis(), sourceBuffer, totalSize);
                            }
                        }

                    } catch (IOException x) {
                        x.printStackTrace();
                    }




                    return new VideoFrame(0, System.currentTimeMillis(), sourceBuffer, totalSize);

                }
            }, WHITE_PRIVATE_IP_PREFIX, MIN_PORT, MAX_PORT, HARDWARE_ACCELERATE, client.getSessionId().toString()));
            log.info("Create peer connection...");
            context.setPeerConnection(context.getRtc().createPeerConnection(new PeerConnectionObserver() {
                @Override
                public void onIceCandidate(IceCandidate iceCandidate) {
                    log.info("on ice candidate, {}", iceCandidate);
                    executor.submit(() -> client.sendEvent(Constants.ON_CANDIDATE, iceCandidate));
                }

                @Override
                public void onSignalingChange(int state) {
                    log.info("On signaling change, {}", SignalingState.getByIndex(state));
                }

                @Override
                public void onDataChannel(DataChannel dataChannel) {
                    context.setDataChannel(dataChannel);
                    log.info("On data channel, {}", dataChannel);
                }

                @Override
                public void onRenegotiationNeeded() {
                    log.info("On renegotiation needed");
                }
            }, webRtcTurnConfig.getTurnServers(), MAX_BIT_RATE));
            log.info("Start peer connection transport...");
            context.getPeerConnection().createDataChannel("test", new DataChannelConfig(),
                    dataBuffer -> {
                        log.info("Received data channel data, {}", dataBuffer);
                        context.getDataChannel().send(new DataBuffer("pong".getBytes(), false));
                    });
            context.getPeerConnection().startTransport();
            log.info("Create peer connection offer...");
            context.getPeerConnection().createOffer(sdp ->
                    executor.submit(() -> {
                        try {
                            context.getPeerConnection().setLocalDescription(sdp);
                            client.sendEvent(Constants.OFFER_SDP, sdp);
                        } catch (Exception e) {
                            log.error("Handle create offer error", e);
                        }
                    }));
        }));
    }


    @OnEvent(Constants.BANDWIDTH)
    public void changeBandwidth(SocketIOClient client, int newBandwidth) {
        getContextAndRunAsync(client.getSessionId(), context ->
                context.executeInLock(() -> {
                    try {
                        context.getPeerConnection().changeBitrate(newBandwidth);
                    } catch (Exception e) {
                        log.error("Handle change bandwidth error", e);
                    }
                }));
    }

    @OnEvent(Constants.ANSWER_SDP)
    public void answerSdp(SocketIOClient client, SessionDescription sessionDescription) {
        log.info("Received a answer sdp, {}", sessionDescription.getType());
        getContextAndRunAsync(client.getSessionId(), context ->
                context.executeInLock(() -> {
                    try {
                        context.getPeerConnection().setRemoteDescription(sessionDescription);
                    } catch (Exception e) {
                        log.error("Handle answer sdp error", e);
                    }
                }));
    }

    @OnEvent(Constants.ON_CANDIDATE)
    public void onCandidate(SocketIOClient client, IceCandidate iceCandidate) {
        log.info("Received a ice candidate, {}", iceCandidate);
        getContextAndRunAsync(client.getSessionId(), context ->
                context.executeInLock(() -> {
                    try {
                        context.getPeerConnection().addIceCandidate(iceCandidate);
                    } catch (Exception e) {
                        log.error("Handle ice candidate error", e);
                    }
                }));
    }

    private void start(UseContext context) {
        context.executeInLock(() -> {
            log.info("Begin use, {}", context.getClient().getSessionId());
            context.getClient().sendEvent(Constants.STATUS, true);
        });
    }

    private void stop(UseContext context) {
        context.executeInLock(() -> {
            log.info("Stop use, {}", context.getClient().getSessionId());
            context.releaseRtc();
        });
    }

    private void getContextAndRunAsync(UUID uuid, Function function) {
        //synchronized block
        synchronized (useContextMap) {
            UseContext context = useContextMap.get(uuid);
            if (!Objects.isNull(context)) {
                executor.submit(() -> function.apply(context));
            }
        }
    }

    public void write(OutputStream outputStream) throws IOException {
        String url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
//        String url = "rtsp://ovidiu:parola86@192.168.1.182/stream1";
        Main.bytesImageSample3(url, 5, 100, outputStream);
        outputStream.close();
    }

    interface Function {
        void apply(UseContext useContext);
    }

    private String getFrameImage() {
        if (!isDefaultImage) {
            return String.format("/home/ovidiu/test-out/frame%d_.jpg", f_idx++);
        } else {
            return "/home/ovidiu/test-out/output.jpg";
        }
    }
}
