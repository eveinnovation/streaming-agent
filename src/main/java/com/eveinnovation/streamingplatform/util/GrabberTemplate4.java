package com.eveinnovation.streamingplatform.util;


import com.eveinnovation.streamingplatform.util.exception.CodecNotFoundExpception;
import com.eveinnovation.streamingplatform.util.exception.FileNotOpenException;
import com.eveinnovation.streamingplatform.util.exception.StreamInfoNotFoundException;
import com.eveinnovation.streamingplatform.util.exception.StreamNotFoundException;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;


import java.io.IOException;
import java.io.OutputStream;

import static com.eveinnovation.streamingplatform.util.FFmpeg4VideoImageGrabber.save_frame;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.*;


/**
 * Based on the latest features of ffmpeg4.0
 *
 * @author eguid
 */
public abstract class GrabberTemplate4 implements Grabber {

    /*
     * Register all formats and codecs
     */
    static {
        avformat_network_init();
        av_log_set_level(AV_LOG_DEBUG);//set log level
    }

    protected Integer width;
    protected Integer height;
    private int srcWidth;
    private int srcHeight;

    private AVFormatContext pFormatCtx;//视频格式上下文
    private AVCodecContext pCodecCtx;//编解码上下文
    private int videoStreamIndex;//视频流所在的通道

    private int align = 1;// The assumed linesize alignment

    private AVPacket packet;//临时的未解码的视频帧数据
    private AVFrame pFrame;//临时的视频帧解码后的图像像素数据，默认yuv420

    private SwsContext sws_ctx;//图像缩放和像素格式转换上下文
    private AVFrame outFrameRGB;//用于存储转换后的RGB像素数据，默认转换成RGB

    public GrabberTemplate4() {
        super();
    }

    public GrabberTemplate4(Integer width, Integer height) {
        super();
        this.width = width;
        this.height = height;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }


    protected AVFormatContext openInput(String url) throws FileNotOpenException {
        AVFormatContext pFormatCtx = new AVFormatContext(null);
        if (avformat_open_input(pFormatCtx, url, null, null) == 0) {
            return pFormatCtx;
        }
        throw new FileNotOpenException("Didn't open video file");
    }


    protected AVFormatContext findStreamInfo(AVFormatContext formatCtx, AVDictionary options) throws StreamInfoNotFoundException {
        if (avformat_find_stream_info(formatCtx, options == null ? (AVDictionary) null : options) >= 0) {
            return formatCtx;
        }
        throw new StreamInfoNotFoundException("Didn't retrieve stream information");
    }


    protected int findVideoStreamIndex(AVFormatContext formatCtx) {
        int size = formatCtx.nb_streams();
        for (int i = 0; i < size; i++) {
            AVStream stream = formatCtx.streams(i);
            AVCodecParameters codec = stream.codecpar();
            int type = codec.codec_type();
            if (type == AVMEDIA_TYPE_VIDEO) {
                return i;
            }
        }
        return -1;
    }


    protected int findAudioStreamIndex(AVFormatContext formatCtx) {
        int size = formatCtx.nb_streams();
        for (int i = 0; i < size; i++) {
            AVStream stream = formatCtx.streams(i);
            AVCodecParameters codec = stream.codecpar();
            int type = codec.codec_type();
            if (type == AVMEDIA_TYPE_AUDIO) {
                return i;
            }
        }
        return -1;
    }


    protected AVCodecParameters findVideoParameters(AVFormatContext formatCtx, int videoStreamIndex) throws StreamNotFoundException {
        if (videoStreamIndex >= 0) {
            // Get a pointer to the codec context for the video stream
            AVStream stream = formatCtx.streams(videoStreamIndex);
//			AVCodecContext pCodecCtx = stream.codec();
            AVCodecParameters codecParam = stream.codecpar();
            return codecParam;
        }
        //if no stream,throws Excetion.
        throw new StreamNotFoundException("Didn't open video file");
    }

    /**
     * 查找并尝试打开解码器
     *
     * @return
     */
    protected AVCodecContext findAndOpenCodec(AVFormatContext formatCtx, int videoStreamIndex) {
        // Find codec param
        AVCodecParameters codecParameters = findVideoParameters(formatCtx, videoStreamIndex);

        // Find the decoder for the video stream
        AVCodec codec = avcodec_find_decoder(codecParameters.codec_id());

        if (codec == null) {
            Console.err("Codec not found!");
            throw new CodecNotFoundExpception("Codec not found!");
        }
        AVDictionary optionsDict = null;
        AVCodecContext codecCtx = avcodec_alloc_context3(codec);

        //convert to codecContext
        if (avcodec_parameters_to_context(codecCtx, codecParameters) < 0) {
            Console.err("Could not convert parameter to codecContext!");
            throw new CodecNotFoundExpception("Could not convert parameter to codecContext!"); // Could not open codec
        }

        // Open codec
        if (avcodec_open2(codecCtx, codec, optionsDict) < 0) {
            Console.err("Could not open codec!");
            throw new CodecNotFoundExpception("Could not open codec!"); // Could not open codec
        }

        return codecCtx;
    }


    protected AVCodecContext findAndOpenCodec(AVCodecContext codecCtx) {
        // Find the decoder for the video stream
        AVCodec pCodec = avcodec_find_decoder(codecCtx.codec_id());
        if (pCodec == null) {
            Console.err("Codec not found!");
            throw new CodecNotFoundExpception("Codec not found!");
        }
        AVDictionary optionsDict = null;
        // Open codec
        if (avcodec_open2(codecCtx, pCodec, optionsDict) < 0) {
            Console.err("Could not open codec!");
            throw new CodecNotFoundExpception("Could not open codec!"); // Could not open codec
        }
        return codecCtx;
    }


    private void freeAndClose() {
        av_packet_unref(packet);// Free the packet that was allocated by av_read_frame

        av_free(pFrame);// Free the YUV frame
        av_free(outFrameRGB);// Free the RGB image

        sws_freeContext(sws_ctx);//Free SwsContext
        avcodec_close(pCodecCtx);// Close the codec
        avformat_close_input(pFormatCtx);// Close the video file
    }


    private boolean initGrabber(String url, int fmt) {

        // Open video file
        pFormatCtx = openInput(url);

        // Find video info
        findStreamInfo(pFormatCtx, null);

        // Find a video stream
        videoStreamIndex = findVideoStreamIndex(pFormatCtx);

        // Find the decoder for the video stream
        pCodecCtx = findAndOpenCodec(pFormatCtx, videoStreamIndex);

        //set image size
        srcWidth = pCodecCtx.width();
        srcHeight = pCodecCtx.height();

        //if width/height is null,use the width/height of video source as the default
        if (width == null || height == null) {
            width = srcWidth;
            height = srcHeight;
        }

        //scaling/conversion operations by using sws_scale().
        DoublePointer param = null;
        sws_ctx = sws_getContext(srcWidth, srcHeight, pCodecCtx.pix_fmt(), width, height, fmt, SWS_FAST_BILINEAR, null, null, param);

        packet = new AVPacket();

        // Allocate video frame
        pFrame = av_frame_alloc();

        // Allocate an AVFrame structure
        outFrameRGB = av_frame_alloc();
        outFrameRGB.width(width);
        outFrameRGB.height(height);
        outFrameRGB.format(fmt);

        return true;
    }


    public byte[] grabVideoFrame(String url, int fmt) throws IOException {

        if (!initGrabber(url, fmt)) {
            return null;
        }

        // Determine required buffer size and allocate buffer
        BytePointer buffer = new BytePointer(av_malloc(av_image_get_buffer_size(fmt, width, height, align)));

        // Assign appropriate parts of buffer to image planes in pFrameRGB.
        av_image_fill_arrays(outFrameRGB.data(), outFrameRGB.linesize(), buffer, fmt, width, height, align);

        try {
            while (av_read_frame(pFormatCtx, packet) == 0) {
                // Is this a packet from the video stream?
                if (packet.stream_index() == videoStreamIndex) {
                    if (avcodec_send_packet(pCodecCtx, packet) == 0) {
                        if (avcodec_receive_frame(pCodecCtx, pFrame) == 0) {
                            sws_scale(sws_ctx, pFrame.data(), pFrame.linesize(), 0, srcHeight, outFrameRGB.data(), outFrameRGB.linesize());
                            return saveFrame(outFrameRGB, width, height);
                        }
                    }
                }
                av_packet_unref(packet);
            }
            return null;
        } finally {
            av_free(buffer);//Don't free buffer?	Of course not!
            freeAndClose();
        }
    }

    public byte[][] grabVideoFrame(String url, int fmt, int sum, int interval, OutputStream outputStream) throws IOException {

        byte[][] byteBuffers = null;
        if (sum > 0) {
            byteBuffers = new byte[sum][];
        } else {
            return byteBuffers;
        }
        if (!initGrabber(url, fmt)) {
            return null;
        }
        try {
            int i=0;
            while (av_read_frame(pFormatCtx, packet) >= 0 ) {

                // Is this a packet from the video stream?
                if (packet.stream_index() == videoStreamIndex) {


                        if (avcodec_send_packet(pCodecCtx, packet) == 0) {
                            //Receive decoded video frame
                            if (avcodec_receive_frame(pCodecCtx, pFrame) == 0) {
                                // Determine required buffer size and allocate buffer
                                BytePointer buffer = new BytePointer(av_malloc(av_image_get_buffer_size(fmt, width, height, align)));

                                // Assign appropriate parts of buffer to image planes in pFrameRGB.
                                av_image_fill_arrays(outFrameRGB.data(), outFrameRGB.linesize(), buffer, fmt, width, height, align);

                                //Sucesss.
                                // Convert the image from its native format to BGR
                                sws_scale(sws_ctx, pFrame.data(), pFrame.linesize(), 0, srcHeight, outFrameRGB.data(), outFrameRGB.linesize());
                                //Convert BGR to ByteBuffer
                                save_frame(outFrameRGB, width, height, i, outputStream);

                                i++;

                                av_free(buffer);//free buffer
                                    //brake at frame 100
//                                if (i >= 100) {
//                                    break;
//                                }
                            }
                        }
                    }


                // Free the packet that was allocated by av_read_frame
                av_packet_unref(packet);
            }

            return byteBuffers;
        } finally {
            freeAndClose();
        }
    }

}
