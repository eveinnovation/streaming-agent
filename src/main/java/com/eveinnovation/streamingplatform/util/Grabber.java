package com.eveinnovation.streamingplatform.util;

import org.bytedeco.ffmpeg.avutil.AVFrame;


public interface Grabber {

	byte[] saveFrame(AVFrame pFrameRGB, int width, int height);
}
