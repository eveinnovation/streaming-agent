package com.eveinnovation.streamingplatform.service;

import com.eveinnovation.streamingplatform.util.ReadFramesAsJpegStream;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class FrameService {

    private final ByteArrayOutputStream byteArrayOutputStream;
    private static final String url = "rtsp://ovidiu:parola86@192.168.1.182/stream1";
//    private static final String url = "http://localhost/The.Book.of.Boba.Fett.S01E01.Chapter.1.1080p.DSNP.WEB-DL.DDP5.1.Atmos.H.264-NOSiViD.mkv";
    private static final int width = 1920;
    private static final int height = 1920;
    private static final int size = 1920*1080*3;

    private final ReadFramesAsJpegStream readFramesAsStreamJpeg;

    public FrameService(ReadFramesAsJpegStream readFramesAsStreamJpeg) {
        this.readFramesAsStreamJpeg = readFramesAsStreamJpeg;
        byteArrayOutputStream = new ByteArrayOutputStream();
    }

    public void grab(){
        try {
            readFramesAsStreamJpeg.init(url, byteArrayOutputStream);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            byteArrayOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void clean() {
        byteArrayOutputStream.reset();
    }

    public ByteArrayOutputStream getByteArrayOutputStream() {
        return byteArrayOutputStream;
    }
}
