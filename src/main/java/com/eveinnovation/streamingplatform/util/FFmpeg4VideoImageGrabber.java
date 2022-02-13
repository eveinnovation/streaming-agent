package com.eveinnovation.streamingplatform.util;


import org.apache.tomcat.util.codec.binary.Base64;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.ffmpeg.avutil.AVFrame;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24;


/**
 * Updated the code according to the new ffmpeg api to better support the screenshot function
 * @author eguid
 */
public class FFmpeg4VideoImageGrabber extends GrabberTemplate4 {

	public final static String DETAULT_FORMAT = "jpg";
	
	
	public byte[] saveFrame(AVFrame frameRGB, int width, int height) {
		BytePointer data = frameRGB.data(0);
		int size = width * height * 3;
		byte[] bytes=new byte[size];
		data.position(0).limit(size).get(bytes,0,size);
		return bytes;
	}

	static void save_frame(AVFrame pFrame, int width, int height, int f_idx, OutputStream outputStream) throws IOException {

		BytePointer data = pFrame.data(0);
		int size = width * height * 3;
		byte[] bytes=new byte[size];
		data.position(0).limit(size).get(bytes,0,size);
		outputStream.write(bytes, 0, size);
		outputStream.flush();

//		BufferedImage image = JavaImgConverter.BGR2BufferedImage(bytes,width,height);
//
//		String filename = String.format("frame%d_.jpg", f_idx);
//		JavaImgConverter.saveImage(image, DETAULT_FORMAT, filename);
	}
	
	/*
	 * 验证并初始化
	 * @param url
	 * @param fmt
	 * @return
	 */
	private boolean validateAndInit(String url,Integer fmt) {
		if (url == null) {
			throw new IllegalArgumentException("Didn't open video file");
		}
		if(fmt == null) {
			this.fmt=AV_PIX_FMT_BGR24;
		}
		return true;
	}
	
	
	public byte[] grabBytes() throws IOException {
		return grabBytes(this.url);
	}

	
	public byte[] grabBytes(String url) throws IOException {
		return grabBytes(url,null);
	}

	
	public byte[] grabBytes(String url, Integer fmt) throws IOException {
		byte[] buf=null;
		if(validateAndInit(url,fmt)) {
			buf = grabVideoFrame(url,this.fmt);
		}
		return buf;
	}

	
	public byte[][] grabBytes(String url, int sum, int interval, OutputStream outputStream) throws IOException {
		return grabBytes(url,null,sum,interval, outputStream);
	}
	
	
	public byte[][] grabBytes(String url, Integer fmt, int sum, int interval, OutputStream outputStream) throws IOException {
		byte[][] bufs=null;
		if(validateAndInit(url,fmt)) {
			bufs= grabVideoFrame(url, this.fmt, sum, interval, outputStream);
		}
		return bufs;
	}
	
	
	public ByteBuffer grabBuffer() throws IOException {
		return grabBuffer(this.url);
	}

	
	public ByteBuffer grabBuffer(String url) throws IOException {
		return grabBuffer(url,null);
	}

	
	public ByteBuffer grabBuffer(String url, Integer fmt) throws IOException {
		byte[] bytes=grabBytes(url, fmt);
		ByteBuffer buf=ByteBuffer.wrap(bytes);
		return buf;
	}

	public ByteBuffer[] grabBuffers(String url, int sum, int interval, OutputStream outputStream) throws IOException {
		return grabBuffers(url,null,sum,interval, outputStream);
	}

	public ByteBuffer[] grabBuffers(String url, Integer fmt, int sum, int interval, OutputStream outputStream) throws IOException {
		if(sum>0) {
			byte[][] bytes=grabBytes(url,fmt, sum, interval, outputStream);
			if(bytes!=null) {
				ByteBuffer[] bufs=new ByteBuffer[sum];
				for(int i=0;i<bytes.length;i++) {
					bufs[i]=ByteBuffer.wrap(bytes[i]);
				}
				return bufs;
			}
		}
		return null;
	}
	
	public BufferedImage grabBufferImage() throws IOException {
		return grabBufferImage(this.url,null);
	}

	public BufferedImage grabBufferImage(String url) throws IOException {
		return grabBufferImage(url,null);
	}

	public BufferedImage grabBufferImage(String url, Integer fmt) throws IOException {
		BufferedImage image=null;
		byte[] buf=grabBytes(url,fmt);
		image= JavaImgConverter.BGR2BufferedImage(buf,this.width,this.height);
		return image;
	}
	
	public BufferedImage[] grabBufferImages(String url, int sum, int interval, OutputStream outputStream) throws IOException {
		return grabBufferImages(url,null,sum,interval, outputStream);
	}

	public BufferedImage[] grabBufferImages(String url, Integer fmt, int sum, int interval, OutputStream outputStream) throws IOException {
		BufferedImage[] images=null;
		if(sum>0) {
			byte[][] bytes=grabBytes(url,fmt, sum, interval, outputStream);
			if(bytes!=null) {
				images=new BufferedImage[sum];
				for(int i=0;i<bytes.length;i++) {
					images[i]= JavaImgConverter.BGR2BufferedImage(bytes[i],this.width,this.height);
				}
				return images;
			}
		}
		return null;
	}

	
	public String getBase64Image(String url) throws IOException {
		return getBase64Image(url, null);
	}

	public String getBase64Image(String url, String format) throws IOException {
		return getBase64Image(url, format,this.width,this.height);
	}

	public String getBase64Image(String url, String format, Integer width, Integer height) throws IOException {
		if (format == null) {
			format =DETAULT_FORMAT;
		}
		BufferedImage img =grabBufferImage(url);
		if (img!= null) {
			String base64=JavaImgConverter.bufferedImage2Base64(img, format);
			return base64;
		}
		return null;
	}

	public String shotAndGetBase64Image(String url, String imgurl) throws IOException {
		return shotAndGetBase64Image(url, imgurl, null);
	}

	public String shotAndGetBase64Image(String url, String imgurl, String format) throws IOException {
		return shotAndGetBase64Image(url, imgurl, format,null,null);
	}

	public String shotAndGetBase64Image(String url, String imgurl, String format, Integer width, Integer height)
			throws IOException {
		if (format == null) {
			format = DETAULT_FORMAT;
		}
		BufferedImage img =grabBufferImage(url);
		if (img != null) {
			JavaImgConverter.saveImage(img, format, imgurl);
			return JavaImgConverter.bufferedImage2Base64(img, format);
		}
		return null;
	}
	
	private String url;//视频地址
	private Integer fmt;//图像数据结构
	
	public FFmpeg4VideoImageGrabber() {}
	
	public FFmpeg4VideoImageGrabber(String url) {
		this.url=url;
	}
	
	public FFmpeg4VideoImageGrabber(String url, Integer fmt) {
		super();
		this.url = url;
		this.fmt = fmt;
	}
	
	public FFmpeg4VideoImageGrabber(String url, Integer fmt, Integer width, Integer height) {
		super(width,height);
		this.url = url;
		this.fmt = fmt;
		this.width=width;
		this.height=height;
	}

	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}

	public int getFmt() {
		return fmt;
	}

	public void setFmt(int fmt) {
		this.fmt = fmt;
	}
	
}
