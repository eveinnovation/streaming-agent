package com.eveinnovation.streamingplatform.util;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

public class ImageView {
	/**
	 * 使用窗口显示BufferedImage图片
	 * @param image -BufferedImage
	 */
	public static void show(BufferedImage image) {
		int width=image.getWidth(),height=image.getHeight();
		Console.log(width+","+height);
		JLabel label = new JLabel();
		label.setSize(width, height);
		
		label.setIcon(new ImageIcon(image));

		JFrame frame = new JFrame();
		frame.setSize(width, height);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.add(label);
		frame.setVisible(true);
	}
	
	/**
	 * 使用窗口显示BGR图像
	 * @param width
	 * @param height
	 * @param src
	 */
	public static void showBGR(int width,int height,ByteBuffer src) {
		BufferedImage image =JavaImgConverter.BGR2BufferedImage(src, width, height);
		show(image);
	}
	
	/**
	 * 使用窗口显示BGR图像
	 * @param width
	 * @param height
	 * @param src
	 */
	public static void showBGR(int width,int height,byte[] src) {
		Console.log(src.length);
		BufferedImage image = JavaImgConverter.BGR2BufferedImage(src, width, height);
		show(image);
	}
	
	/**
	 * 使用窗口显示RGB图像
	 * @param width
	 * @param height
	 * @param rgbarr -int 
	 */
	public static void showRGB(int[] rgbarr,int width,int height){
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, width, height, rgbarr, 0,height);
		show(image);
	}

}
