package com.eveinnovation.streamingplatform.util;

import java.awt.image.BufferedImage;
import java.io.*;

import static com.eveinnovation.streamingplatform.util.FFmpeg4VideoImageGrabber.DETAULT_FORMAT;

public class Main {

    public static int width=1920;
    public static int height=1080;
    public static int size = width*height*3;

    public static void write(OutputStream outputStream) throws IOException {
//        String url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
        String url = "rtsp://ovidiu:parola86@192.168.1.182/stream1";
        bytesImageSample3(url, 5, 100, outputStream);
        outputStream.close();
    }

    public static void read(InputStream inputStream) {

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            int nRead;
            byte[] data = new byte[1024];
            int f_idx = 0;

            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
                if (buffer.size() == Main.size) {
                    String filename = String.format("frame%d_.jpg", f_idx);

                    byte[] img = buffer.toByteArray();
                    BufferedImage image = JavaImgConverter.BGR2BufferedImage(img, width, height);

                    JavaImgConverter.saveImage(image, DETAULT_FORMAT, filename);
                    buffer.reset();
                    f_idx++;
                }
            }
        } catch (IOException x) {
            x.printStackTrace();
        }

    }


    public static void main(String[] args) throws InterruptedException, IOException {

        final PipedOutputStream out = new PipedOutputStream();
        final PipedInputStream in = new PipedInputStream(out);

        Runnable runA = new Runnable() {
            public void run() {
                try {
                    write(out);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        Thread threadA = new Thread(runA, "threadA");
        threadA.start();

        Runnable runB = new Runnable() {
            public void run() {
                read(in);
            }
        };

        Thread threadB = new Thread(runB, "threadB");
        threadB.start();

    }

    public static byte[][] bytesImageSample3(String url, int sum, int interval, OutputStream outputStream) throws IOException {
        FFmpeg4VideoImageGrabber grabber = new FFmpeg4VideoImageGrabber();
        return grabber.grabBytes(url, sum, interval, outputStream);
    }

    public static String base64ImageSample3(String url) throws IOException {
        FFmpeg4VideoImageGrabber grabber = new FFmpeg4VideoImageGrabber();
        //默认格式是jpg
        return grabber.shotAndGetBase64Image(url, "test1.jpg");
    }
}
