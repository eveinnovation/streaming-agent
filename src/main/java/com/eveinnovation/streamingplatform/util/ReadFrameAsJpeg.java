package com.eveinnovation.streamingplatform.util;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacpp.PointerPointer;

import java.io.IOException;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

public class ReadFrameAsJpeg {

    public static void main(String[] args) throws Exception {
        ReadFrameAsJpeg.test("rtp://192.168.1.191:1240");
    }

    static void save_frame(AVFrame pFrame, int width, int height, int f_idx) throws IOException {

        int ret;

        AVFormatContext pFormatCtx = avformat_alloc_context();
        pFormatCtx.oformat(av_guess_format("mjpeg", null, null));

        AVIOContext pb = new AVIOContext(null);
        String szFilename = String.format("/home/ovidiu/test-out/frame%d_.jpg", f_idx);
        ret = avio_open(pb, szFilename, AVIO_FLAG_WRITE);
        if (ret < 0) {
            System.out.println("Cannot open io context");
            return;
        }
        pFormatCtx.pb(pb);

        AVCodec codec = avcodec_find_encoder(AV_CODEC_ID_MJPEG);
        if (codec == null) {
            System.out.println("Codec not found.");
            return;
        }

        // Get a new Stream from the indicated format context
        AVStream pAVStream = avformat_new_stream(pFormatCtx, codec);
        if (pAVStream == null) {
            return;
        }

        AVCodecContext pCodecCtx = avcodec_alloc_context3(codec);

        pCodecCtx.width(width);
        pCodecCtx.height(height);
        pCodecCtx.codec_id(pFormatCtx.oformat().video_codec());
        pCodecCtx.codec_type(AVMEDIA_TYPE_VIDEO);
        AVRational ratio = new AVRational();
        ratio.num(1);
        ratio.den(24);
        pCodecCtx.time_base(ratio);
        pCodecCtx.flags(AV_CODEC_FLAG_QSCALE);
        pCodecCtx.global_quality(FF_QP2LAMBDA*4);
        pCodecCtx.pix_fmt(AV_PIX_FMT_YUVJ420P);

        // Open the codec
        if (avcodec_open2(pCodecCtx, codec, (PointerPointer) null) < 0) {
            System.out.println("Could not open codec.");
            return;
        }

        //buffer size encoding
        AVDictionary metadata = new AVDictionary();
        av_dict_set(metadata, "buffsize", "2000000", 0);
        av_dict_set(metadata, "maxrate", "2000000", 0);

        pAVStream.metadata(metadata);

        // assign the codec context to the stream parameters.
        avcodec_parameters_from_context(pAVStream.codecpar(), pCodecCtx);

        avformat_write_header(pFormatCtx, (AVDictionary) null);

        int y_size = (pCodecCtx.width()) * (pCodecCtx.height());

        // assign large enough space
        AVPacket pkt = av_packet_alloc();

//        av_new_packet(pkt, y_size * 3);
        ret = avcodec_send_frame(pCodecCtx, pFrame);

        if (ret < 0) {
            System.out.println("Encode Error.\n");
            return;
        } else {
            avcodec_receive_packet(pCodecCtx, pkt);
            av_write_frame(pFormatCtx, pkt);
//            System.out.println("Encode Success.\n");
        }

        av_interleaved_write_frame(pFormatCtx, pkt);
        av_packet_unref(pkt);
        av_write_trailer(pFormatCtx);
        avcodec_close(pCodecCtx);
        avio_close(pFormatCtx.pb());
        avformat_free_context(pFormatCtx);
        avcodec_free_context(pCodecCtx);
    }

    public static void test(String file) throws Exception {
        System.out.println("Read few frame and write to image");

        int ret, i, v_stream_idx = -1;
        AVFormatContext fmt_ctx = new AVFormatContext(null);
        AVPacket pkt = new AVPacket();

        AVDictionary metadata = new AVDictionary();
        av_dict_set(metadata, "buffer_size", "10000000", 0);
        av_dict_set(metadata, "fflags", "discardcorrupt", 0);

        ret = avformat_open_input(fmt_ctx, file, null, metadata);
        if (ret < 0) {
            System.out.printf("Open video file %s failed \n", file);
            throw new IllegalStateException();
        }

        // i dont know but without this function, sws_getContext does not work
        if (avformat_find_stream_info(fmt_ctx, (PointerPointer) null) < 0) {
            System.exit(-1);
        }

        av_dump_format(fmt_ctx, 0, file, 0);

        for (i = 0; i < fmt_ctx.nb_streams(); i++) {
            if (fmt_ctx.streams(i).codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) {
                v_stream_idx = i;
                break;
            }
        }
        if (v_stream_idx == -1) {
            System.out.println("Cannot find video stream");
            throw new IllegalStateException();
        } else {
            System.out.printf("Video stream %d with resolution %dx%d\n", v_stream_idx,
                    fmt_ctx.streams(i).codecpar().width(),
                    fmt_ctx.streams(i).codecpar().height());
        }

        AVCodecContext codec_ctx = avcodec_alloc_context3(null);
        avcodec_parameters_to_context(codec_ctx, fmt_ctx.streams(v_stream_idx).codecpar());

        AVCodec codec = avcodec_find_decoder(codec_ctx.codec_id());
        if (codec == null) {
            System.out.println("Unsupported codec for video file");
            throw new IllegalStateException();
        }
        ret = avcodec_open2(codec_ctx, codec, (PointerPointer) null);
        if (ret < 0) {
            System.out.println("Can not open codec");
            throw new IllegalStateException();
        }

        AVFrame frm = av_frame_alloc();

        // Allocate an AVFrame structure
        AVFrame pFrameRGB = av_frame_alloc();
        if (pFrameRGB == null) {
            System.exit(-1);
        }

        i = 0;
        int ret1 = -1, ret2 = -1, fi = -1;
        while (av_read_frame(fmt_ctx, pkt) >= 0) {


            if (pkt.stream_index() == v_stream_idx) {
                ret1 = avcodec_send_packet(codec_ctx, pkt);
                ret2 = avcodec_receive_frame(codec_ctx, frm);
                if (ret1 < 0) {
                    break;
                }

                if (ret2 >= 0) {
                    ++i;
                    save_frame(frm, codec_ctx.width(), codec_ctx.height(), i);
                }

                if (i >= 1000) {
                    break;
                }
            }

            av_packet_unref(pkt);


        }

        av_frame_free(frm);

        avcodec_close(codec_ctx);
        avcodec_free_context(codec_ctx);

        avformat_close_input(fmt_ctx);
        System.out.println("Shutdown");
//        System.exit(0);
    }
}