package com.eveinnovation.streamingplatform.util;

import java.awt.image.BufferedImage;
import java.io.*;

import static com.eveinnovation.streamingplatform.util.FFmpeg4VideoImageGrabber.DETAULT_FORMAT;

public class Main {

    public static int width = 1920;
    public static int height = 1080;
    public static int size = width * height * 3;
    public static volatile ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    public static int f_idx = 0;

    public static void write(OutputStream outputStream) throws IOException {
//        String url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
        String url = "rtsp://ovidiu:parola86@192.168.1.182/stream1";
        bytesImageSample3(url, 5, 100, outputStream);
        outputStream.close();
    }

    public static void read(InputStream inputStream) {
        try {
            int readSize;
            byte[] buffer = new byte[1024];

            while ((readSize = inputStream.read(buffer, 0, buffer.length)) != -1) {
                byteArrayOutputStream.write(buffer, 0, readSize);
                if (byteArrayOutputStream.size() == Main.size) {
                    saveFrame();
                    byteArrayOutputStream.flush();
                }
            }
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    private static int saveFrame() throws IOException {
        if (byteArrayOutputStream.size() == Main.size) {
            String filename = String.format("frame%d_.jpg", f_idx);

            byte[] img = byteArrayOutputStream.toByteArray();
            BufferedImage image = JavaImgConverter.BGR2BufferedImage(img, width, height);

            JavaImgConverter.saveImage(image, DETAULT_FORMAT, filename);
            f_idx++;
            byteArrayOutputStream.reset();
        }
        return f_idx;
    }


    public static void main(String[] args) throws InterruptedException, IOException {

        final PipedOutputStream out = new PipedOutputStream();
        final PipedInputStream in = new PipedInputStream(out);

        Runnable runA = () -> {
            try {
                write(out);
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        Thread threadA = new Thread(runA, "threadA");
        threadA.start();

        read(in);
    }

    public static void bytesImageSample3(String url, int sum, int interval, OutputStream outputStream) throws IOException {
        FFmpeg4VideoImageGrabber grabber = new FFmpeg4VideoImageGrabber();
        grabber.grabBytes(url, sum, interval, outputStream);
    }
}
