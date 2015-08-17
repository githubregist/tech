package com.app;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

public class QRCodeGenerator {
	
	/**
	 * 产生二维码
	 * 
	 * @param version
	 * @return
	 */
	public static int[][] generateQr(String message) throws Exception {
		
//		message = new String(message.getBytes("UTF-8"), "ISO-8859-1");
		
		// 版本
		int version = 1;
		
		// 定义级别
		ErrorCodeLevel level = ErrorCodeLevel.H;
		
		// mask方式
		MaskPattern maskPattern = MaskPattern.M010;
		
		// 根据内容长度确定版本
		int msgLen = message.length();
		for(int i = 1; i <= 40; i++) {
			int[] errorCodeBlockCfg = getErrorCodeBlock(i, level);
			int capacity = errorCodeBlockCfg[0] * errorCodeBlockCfg[2] + errorCodeBlockCfg[1] * errorCodeBlockCfg[3] - 16;
			if(capacity >= msgLen) {
				version = i;
				break;
			}
		}
		
		// +++++++++++++++
//		 version = 2;
		// +++++++++++++++
		
		int[] errorCodeBlockCfg = getErrorCodeBlock(version, level);

		// 宽度高度
		int width = (version - 1) * 4 + 21;
		int height = width;
		
		int[][] pixels = new int[width][height];
		
		// 选择编码模式
		CodeFormat codeFormat = chooseMode(message);
		
		// 数据编码，每一个8bits叫一个codeword
		// 编码模式的编码，bit长度4
		int formatCode = codeFormat.getValue();
		// 字符个数的编码，bit长度getNumberOfBits
		int msgLength = message.length();
		String dataCodeWords = appendLeftZero(Integer.toBinaryString(formatCode), 4) 
				+ appendLeftZero(Integer.toBinaryString(msgLength), getNumberOfBits(codeFormat, version));
		if (CodeFormat.Alphanumeric.equals(codeFormat)) {
			// 字符两两分组，然后转成下表的45进制，然后转成11bits的二进制，如果最后有一个落单的，那就转成6bits的二进制
			for (int i = 0; i < msgLength;) {
				String str = message.substring(i, i + 2 < msgLength ? i + 2 : msgLength);
				// 两两分组
				if (str.length() == 2) {
					String firstStr = str.substring(0, 1);
					String lastStr = str.substring(1, 2);
					// 转成45进制
					int codeInt = getAlphanumbericCode(firstStr) * 45 + getAlphanumbericCode(lastStr);
					// 转成11bits
					dataCodeWords += appendLeftZero(Integer.toBinaryString(codeInt), 11);
				} else {
					int codeInt = getAlphanumbericCode(str);
					// 转成6bits
					dataCodeWords += appendLeftZero(Integer.toBinaryString(codeInt), 6);
				}
				i += 2;
			}
		} else if (CodeFormat.Byte.equals(codeFormat)) {
			// 每个字符8位
			for (int i = 0; i < message.length(); i++) {
				dataCodeWords += get8BitsCode(message.substring(i, i + 1));
			}
		} else if (CodeFormat.GB2312.equals(codeFormat)) {
			for (int i = 0; i < message.length(); i++) {
				String str = message.substring(i, i + 1);
				if (isExitsDoubleByteGB2312(str)) {
					dataCodeWords += getGB2312Code(str);
				} else {
					dataCodeWords += get8BitsCode(str);
				}
			}
		} 
		
		// 结束符
		dataCodeWords += "0000";
		
		// 按8bits重排
		int length = dataCodeWords.length();
		if (length % 8 != 0) {
			for (int i = 0; i < (8 - length % 8); i++) {
				dataCodeWords += "0";
			}
		}
		// 补齐码（Padding Bytes）
		// 如果如果还没有达到我们最大的bits数的限制，我们还要加一些补齐码（Padding Bytes），
		length = dataCodeWords.length();
		// 编码最大长度
		int totalDataBits = (errorCodeBlockCfg[0] * errorCodeBlockCfg[2] + errorCodeBlockCfg[1] * errorCodeBlockCfg[3]) * 8;
		String[] paddingBlocks = {"11101100", "00010001"};
		if (length < totalDataBits) {
			for (int n = 0; n < (totalDataBits - length) / 8; n++) {
				if (n % 2 == 0) {
					dataCodeWords += paddingBlocks[0];
				} else {
					dataCodeWords += paddingBlocks[1];
				}
			}
		}
		// 对数据的编码结束！！
		
		// 纠错编码
		// block块数
		int blockSize = errorCodeBlockCfg[0] + errorCodeBlockCfg[1];
		// 编码分块
		String[] dataCodeBlocks = new String[blockSize];
		int blockStart = 0;
		for (int n = 0; n < errorCodeBlockCfg[0]; n++) {
			dataCodeBlocks[n] = dataCodeWords.substring(blockStart, blockStart + errorCodeBlockCfg[2] * 8);
			blockStart += errorCodeBlockCfg[2] * 8;
		}
		for (int n = 0; n < errorCodeBlockCfg[1]; n++) {
			dataCodeBlocks[n + errorCodeBlockCfg[0]] = dataCodeWords.substring(blockStart, blockStart + errorCodeBlockCfg[3] * 8);
			blockStart += errorCodeBlockCfg[3] * 8;
		}
		
		// 每块纠错编码的长度
		int errCodeBLockLength = errorCodeBlockCfg[4] / blockSize;
		// 纠错码分块
		String[] errorCodeBlocks = new String[blockSize];
		// 纠错码编码
		for(int n = 0; n < dataCodeBlocks.length; n++) {
			String dataCode = dataCodeBlocks[n];
			int[] gen = GF.gx(errCodeBLockLength);
			int[] msgout = new int[dataCode.length() / 8 + errCodeBLockLength];
			for (int i = 0 ;i < dataCode.length() / 8; i++) {
				// 8bits转换成数字
				msgout[i] = Integer.valueOf(dataCode.substring(i * 8, (i + 1) * 8), 2);
			}
			for (int i = 0; i < dataCode.length() / 8; i++) {
				int c = msgout[i];
				if(c != 0){
					for(int j = 0; j < gen.length; j++) {
						msgout[i + j] ^= GF.multi(gen[j], c);
					}
				}
			}
			for (int i = 0 ;i < dataCode.length() / 8; i++) {
				// 8bits转换成数字
				msgout[i] = Integer.valueOf(dataCode.substring(i * 8, (i + 1) * 8), 2);
			}
			errorCodeBlocks[n] = "";
			for (int i = dataCode.length() / 8; i < msgout.length; i++) {
				// 数字转换成8bits
				errorCodeBlocks[n] += appendLeftZero(Integer.toBinaryString(msgout[i]), 8);
			}
		}
		// 纠错码编码结束！！
		
		// 穿插放置
		// 对于数据码：把每个块的第一个codewords先拿出来按顺度排列好，然后再取第一块的第二个，如此类推
		int maxDataBlockLength = dataCodeBlocks[0].length();
		for (int n = 0; n < dataCodeBlocks.length; n++) {
			if (dataCodeBlocks[n].length() >= maxDataBlockLength) {
				maxDataBlockLength = dataCodeBlocks[n].length();
			}
		}
		/*
		 * 顺序
		 1 4 7 10 13 16
		 2 5 8 11 14 17
		 3 6 9 12 15 19
		 */
		dataCodeWords = "";
		for (int i = 0; i < maxDataBlockLength / 8; i++) {
			for (int j = 0; j < dataCodeBlocks.length; j++) {
				if (dataCodeBlocks[j].length() >= (i + 1) * 8) {
					dataCodeWords += dataCodeBlocks[j].substring(i * 8, (i + 1) * 8);
				}
			}
		}
		// 纠错码安插位置
		int maxErrorBlockLength = errorCodeBlocks[0].length();
		for (int n = 0; n < errorCodeBlocks.length; n++) {
			if (errorCodeBlocks[n].length() >= maxErrorBlockLength) {
				maxErrorBlockLength = errorCodeBlocks[n].length();
			}
		}
		String errorCodeWords = "";
		for (int i = 0; i < maxErrorBlockLength / 8; i++) {
			for (int j = 0; j < errorCodeBlocks.length; j++) {
				if (errorCodeBlocks[j].length() >= (i + 1) * 8) {
					errorCodeWords += errorCodeBlocks[j].substring(i * 8, (i + 1) * 8);
				}
			}
		}
		
		// 最终数据区
		String finalDataCode = dataCodeWords + errorCodeWords;
		
		System.out.println("最终编码：");
		System.out.println("版本:" + version + ", 级别:" + level.name() + ", 编码长度:" + (dataCodeWords.length() + errorCodeWords.length()));
		System.out.println(finalDataCode);
		
		// 字符串反转
//		finalDataCode = reverseString(finalDataCode);
		
		// 标记一：是否已画
		Map<Integer, Boolean> isDrawed = new HashMap<Integer, Boolean>();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				isDrawed.put(y * width + x, false);
			}
		}
		// 标记二：是否数据区
		Map<Integer, Boolean> isData = new HashMap<Integer, Boolean>();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				isData.put(y * width + x, true);
			}
		}
		
		// 【】Position Detection Pattern 是定位图案，用于标记二维码的矩形大小。之所以三个而不是四个意思就是三个就可以标识一个矩形了。
		// 这三个定位图案有白边叫 Separators for Postion Detection Patterns。
		// 左上角为原点
		drawPositionDetection(pixels, 0, 0, width, isDrawed, isData);// 左上
		drawPositionDetection(pixels, width - 7, 0, width, isDrawed, isData);// 右上
		drawPositionDetection(pixels, 0, height - 7, width, isDrawed, isData);// 左下
		
		// 【】Alignment Patterns 只有 Version 2 以上（包括 Version2）的二维码需要这个东东，同样是为了定位用的。
		int[] numberOfalignment = getNumberOfAlignmentPatterns(version);
		for (int i = 0; i < numberOfalignment.length; i++) {
			for (int j = 0; j < numberOfalignment.length; j++) {
				if (i == 0 && j == 0 || i == 0 && j == numberOfalignment.length - 1 || i == numberOfalignment.length - 1 && j == 0) {
					// 与Position Detection Pattern重复的位置不画
					continue;
				}
				drawAlignmentPatterns(pixels, numberOfalignment[i] - 2, numberOfalignment[j] - 2, width, isDrawed, isData);
			}
		}
		
		// 【】Timing Patterns 也是用于定位的。原因是二维码有 40 种尺寸，尺寸过大了后需要有根标准线，不然扫描的时候可能会扫歪了。
		drawTimingPixels(pixels, width, height, isDrawed, isData);
		
		// 【】Format Information 存在于所有的尺寸中，用于存放一些格式化数据的。
		int formatInfo = 0;
		// 15个bits中包括：
		// 5个数据bits：其中，2个bits用于表示使用什么样的Error Correction Level， 3个bits表示使用什么样的Mask 
		int fiveBitCode = (level.getValue() << 3) ^ maskPattern.getValue();
		// 10个纠错bits。主要通过BCH Code来计算 
		int bchErrorCode = getBchCode(fiveBitCode);
		// 然后15个bits还要与101010000010010做XOR操作
		int maskCode = 0x5412;	//101010000010010
		formatInfo = ((fiveBitCode << 10) ^ bchErrorCode) ^ maskCode;
		// 补够15位，开始填充
		String formatInfoStr = appendLeftZero(Integer.toBinaryString(formatInfo), 15);
		// 字符串反转
		formatInfoStr = reverseString(formatInfoStr);
		// 转换成数字数组
		int[] formatInfoInt = new int[15];
		for (int i = 0; i < formatInfoStr.length(); i++) {
			formatInfoInt[i] = Integer.valueOf(formatInfoStr.substring(i, i + 1));
		}
		drawFormatInfo(pixels, formatInfoInt, width, height, isDrawed, isData);
		
		// 【】Version Information 在 >= Version 7 以上，需要预留两块 3 x 6 的区域存放一些版本信息。
		// Version Information一共是18个bits，其中包括6个bits的版本号以及12个bits的纠错码
		if (version >= 7) {
			String versionInfoStr = getVersionInfo(version);
			// 字符串反转
			versionInfoStr = reverseString(versionInfoStr);
			// 转换成数字数组
			int[] versionInfoInt = new int[versionInfoStr.length()];
			for (int i = 0; i < versionInfoStr.length(); i++) {
				versionInfoInt[i] = Integer.valueOf(versionInfoStr.substring(i, i + 1));
			}
			drawVersionInfo(pixels, versionInfoInt, width, height, isDrawed, isData);
		} 
		
		// Remainder Bits
		// 【】设置剩余位为非数据区
		int remainders = getRemainderBits(version);
		int st_y = width - 9;
		switch(remainders){
			case 7:
				isData.put(0 + width * (st_y - 3), false);
				isData.put(0 + width * (st_y - 2), false);
				isData.put(1 + width * (st_y - 2), false);
			case 4:
				isData.put(1 + width * (st_y - 1), false);
			case 3:
				isData.put(0 + width * st_y, false);
				isData.put(1 + width * st_y, false);
				isData.put(0 + width * (st_y - 1), false);
				break;
		}
		
		// 【】数据区画图！！
		drawDataArea(finalDataCode, pixels, isData);
		
		// 【】叠加mask
		mask(maskPattern, pixels, isData);
		
		// 中央图标
		
		return pixels;
		
	}
	
	/**
	 * 画一个大定位矩形及空白边界（共三个）
	 * 
	 * @param positionDetectionPixels
	 * @param startX
	 * @param startY
	 * @param width
	 * @param isDrawed
	 * @param isData
	 * @param isTurnDirection
	 * @return
	 */
	public static void drawPositionDetection(int[][] positionDetectionPixels, int startX, int startY, int width, 
			Map<Integer, Boolean> isDrawed, Map<Integer, Boolean> isData) {
		/**
		 ＠＠＠＠＠＠＠
		 ＠　　　　　＠
		 ＠　＠＠＠　＠
		 ＠　＠＠＠　＠
		 ＠　＠＠＠　＠
		 ＠　　　　　＠
		 ＠＠＠＠＠＠＠
		 */
		int centerX = startX + 3;
		int centerY = startY + 3;
		for (int x = startX - 1; x < startX + 8; x++) {
			for (int y = startY - 1; y < startY + 8; y++) {
				// 超出边界或已画过
				if (x < 0 || y < 0 || x >= width || y >= width) {
					continue;
				}
				if (isDrawed.get(y * width + x) == null || isDrawed.get(y * width + x)) {
					continue;
				}
				if (Math.abs(centerX - x) == 2 && Math.abs(centerY - y) <= 2 
						|| Math.abs(centerY - y) == 2 && Math.abs(centerX - x) <= 2
						|| Math.abs(centerX - x) == 4 || Math.abs(centerY - y) == 4 // 画大定位矩形外的白边
						) {
					positionDetectionPixels[x][y] = 0;
				} else {
					positionDetectionPixels[x][y] = 1;// 填充
				}
				isDrawed.put(y * width + x, true);
				isData.put(y * width + x, false);
			}
		}
	}
	
	/**
	 * 画一个小定位矩形（数量不确定）
	 * 
	 * @param alignmentPixels
	 * @param startX
	 * @param startY
	 * @param width
	 * @param isDrawed
	 * @param isData
	 */
	public static void drawAlignmentPatterns(int[][] alignmentPixels, int startX, int startY, int width, 
			Map<Integer, Boolean> isDrawed, Map<Integer, Boolean> isData) {
		/**
		 ＠＠＠＠＠
		 ＠　　　＠
		 ＠　＠　＠
		 ＠　　　＠
		 ＠＠＠＠＠
		 */
		for (int y = startY; y < startY + 5; y++) {
			for (int x = startX; x < startX + 5; x++) {
				if (isDrawed.get(y * width + x)) {
					continue;
				}
				if ((x == startX + 1 || x == startX + 3) && y >= startY + 1 && y <= startY + 3
						|| (y == startY + 1 || y == startY + 3) && x >= startX + 1 && x <= startX + 3) {
					alignmentPixels[x][y] = 0;
				} else {
					alignmentPixels[x][y] = 1;//填充
				}
				isDrawed.put(y * width + x, true);
				isData.put(y * width + x, false);
			}
		}
	}
	
	/**
	 * 画定位线
	 * 
	 * @param timingPixels
	 * @param width
	 * @param height
	 * @param isDrawed
	 * @param isData
	 */
	public static void drawTimingPixels(int[][]timingPixels, int width, int height, 
			Map<Integer, Boolean> isDrawed, Map<Integer, Boolean> isData) {
		/**
		 ＠　＠　＠
		 
		 ＠
		 
		 ＠
		 */
		for (int x = 6; x < width - 7; x++) {
			if (isDrawed.get(6 * width + x)) {
				continue;
			}
			if (x % 2 == 0) {
				timingPixels[x][6] = 1;
			} else {
				timingPixels[x][6] = 0;
			}
			isDrawed.put(6 * width + x, true);
			isData.put(6 * width + x, false);
		}
		for (int y = 6; y < height - 7; y++) {
			if (isDrawed.get(y * width + 6)) {
				continue;
			}
			if (y % 2 == 0) {
				timingPixels[6][y] = 1;
			} else {
				timingPixels[6][y] = 0;
			}
			isDrawed.put(y * width + 6, true);
			isData.put(y * width + 6, false);
		}
	}
	
	/**
	 * 画格式信息
	 * 
	 * @param pixels
	 * @param formatInfoInt
	 * @param width
	 * @param height
	 * @param isDrawed
	 * @param isData
	 */
	public static void drawFormatInfo(int[][] pixels, int[] formatInfoInt, int width, int height, 
			Map<Integer, Boolean> isDrawed, Map<Integer, Boolean> isData) {
		// 指定位置
		int _count = 0;
		for (int y = 0; y < 9; y++) {
			int x = 8;
			if (isDrawed.get(y * width + x)) {
				continue;
			}
			pixels[x][y] = formatInfoInt[_count];
			_count++;
			isDrawed.put(y * width + x, true);
			isData.put(y * width + x, false);
		}
		for (int x = 7; x >= 0; x--) {
			int y = 8;
			if (isDrawed.get(y * width + x)) {
				continue;
			}
			pixels[x][y] = formatInfoInt[_count];
			_count++;
			isDrawed.put(y * width + x, true);
			isData.put(y * width + x, false);
		}
		_count = 0;
		for (int x = width - 1; x >= width - 8; x--) {
			int y = 8;
			if (isDrawed.get(y * width + x)) {
				continue;
			}
			pixels[x][y] = formatInfoInt[_count];
			_count++;
			isDrawed.put(y * width + x, true);
			isData.put(y * width + x, false);
		}
		// dark module永远出现，位置(8, height-8)
		pixels[8][height - 8] = 1;
		isDrawed.put((height - 8) * width + 8, true);
		isData.put((height - 8) * width + 8, false);
		for (int y = height - 7; y < height; y++) {
			int x = 8;
			if (isDrawed.get(y * width + x)) {
				continue;
			}
			pixels[x][y] = formatInfoInt[_count];
			_count++;
			isDrawed.put(y * width + x, true);
			isData.put(y * width + x, false);
		}
	}
	
	/**
	 * 画版本信息
	 * 
	 * @param pixels
	 * @param formatInfoInt
	 * @param width
	 * @param height
	 * @param isDrawed
	 * @param isData
	 */
	public static void drawVersionInfo(int[][] pixels, int[] versionInfoInt, int width, int height, 
			Map<Integer, Boolean> isDrawed, Map<Integer, Boolean> isData) {
		int _count = 0;
		for (int x = 0; x < 6; x++) {
			for (int i = 0; i < 3; i++) {
				int y = i + height - 11;
				if (isDrawed.get(y * width + x)) {
					continue;
				}
				pixels[x][y] = versionInfoInt[_count];
				_count++;
				isDrawed.put(y * width + x, true);
				isData.put(y * width + x, false);
			}
		}
		_count = 0;
		for (int y = 0; y < 6; y++) {
			for (int i = 0; i < 3; i++) {
				int x = i + width - 11;
				if (isDrawed.get(y * width + x)) {
					continue;
				}
				pixels[x][y] = versionInfoInt[_count];
				_count++;
				isDrawed.put(y * width + x, true);
				isData.put(y * width + x, false);
			}
		}
	}
	
	/**
	 * 数据区画图
	 * 
	 * @param dataCode
	 * @param pixels
	 * @param isData
	 */
	public static void drawDataArea(String dataCode, int[][] pixels, Map<Integer, Boolean> isData) {
		// 从右下角开始画图
		// 注意几个位置：
		// 超出边界或遇到阻挡区则向左(x=x-1)
		// x>=8时，x%4=0向上，x<=5时，(x+1)%/4=0向上
		// x=8跳到x=5
		int width = pixels.length;
		// 计数
		int count = 0;
		for (int x = width - 1; x >= 0;) {
			
			// x>=8时，x%4=0向上，x<=5时，(x+1)%/4=0向上
			boolean up = true;
			if (x >= 8 && x % 4 != 0 || x <= 5 && (x + 1) % 4 != 0) {
				up = false;
			}
			
			int y = width - 1;
			int add = -1;
			if (!up) {
				y = 0;
				add = 1;
			}
			
			for (; y >= 0 || y < width; ) {
				// 结束
				if (/*y == width - 11 && x == 1 || */dataCode.length() < count + 1) {
					x = -1;
					break;
				}
				// 超界停止y轴移动
				if (y < 0 || y >= width) {
					break;
				}
				
				// 非数据区，穿过
				if (!isData.get(y * width + x)) {
					// 如果左边能走，不穿过
					if (isData.get(y * width + x - 1)) {
						pixels[x - 1][y] = Integer.valueOf(dataCode.substring(count, count + 1));
						// 落点
						count++;
//						System.out.println((x-1) + "," + y + "-----" + pixels[x - 1][y] + "----计数" + count);
					}
					y += add;
					continue;
				}
				
				// 左边一点的坐标
				int drawX = x;
				int drawY = y;
				// 画数据
				String data = dataCode.substring(count, count + 1);
				pixels[drawX][drawY] = Integer.valueOf(data);
				// 落点
				count++;
//				System.out.print(drawX + "," + drawY + " " + "-----" + data + "----计数" + count + "  ");
				
				// 结束
				if (dataCode.length() < count + 1) {
					x = -1;
					break;
				}
				
				// 左边一点的坐标
				drawX = x - 1;
				drawY = y;
				// 画数据
				data = dataCode.substring(count, count + 1);
				pixels[drawX][drawY] = Integer.valueOf(data);
				// 落点
				count++;
//				System.out.println(drawX + "," + drawY + "-----" + data + "----计数" + count);
				
				y += add;
			}
			
			// x=8跳到x=5
			if (x == 8) {
				x = 5;
			} else {
				x -= 2;
			}
		}
		
	}
	
	/**
	 * 叠加
	 * 
	 * @param pattern
	 * @param pixels
	 * @param isData
	 */
	public static void mask(MaskPattern pattern, int[][] pixels, Map<Integer, Boolean> isData) {
		int width = pixels.length;
		for (int x = 0; x < pixels.length; x++) {
			for (int y = 0; y < pixels.length; y++) {
				if (!isData.get(y * width + x)) {
					// 非数据区域不叠加
					continue;
				}
				int result = getMaskResult(pattern, y, x);
				if ((pixels[x][y] ^ result) == 1) {
					pixels[x][y] = 1;
				} else {
					pixels[x][y] = 0;
				}
			}
		}
	}
	
	/**
	 * 左补0
	 * 
	 * @param value
	 * @param length
	 * @return
	 */
	public static String appendLeftZero(Object value, int length) {
		if (value == null || "".equals(value)) {
			return "";
		} else {
			String str = String.valueOf(value);
			int strLen = str.length();
	        if (strLen < length) {
	            while (strLen < length) {
	                StringBuffer sb = new StringBuffer();
	                sb.append("0").append(str);
	                str = sb.toString();
	                strLen = str.length();
	            }
	        }
			return str;
		}
	}
	
	/**
	 * BCH纠错码
	 * 
	 * @param value
	 * @return
	 */
	public static int getBchCode(int value) {
		value = value << 10;
		int gen = 0x537;	//generate polymonial(10100110111): x^10 + x^8 + x^5 + x^4 + x^2 + x + 1
		for(int i = 4; i > -1; i--){
			if((value & (1 << (i + 10))) != 0) {
				value ^=  gen << i;
			}
		}
		return value;
	}
	
	/**
	 * 字符串反转
	 * 
	 * @param str
	 * @return
	 */
	public static String reverseString(String str) {
		String newStr = "";
		for (int i = 0; i < str.length(); i++) {
			newStr += str.substring(str.length() - 1 - i, str.length() - i);
		}
		return newStr;
	}
	
	/**
	 * 选择编码模式
	 * 
	 * @param content
	 * @param encoding
	 * @return
	 */
	public static CodeFormat chooseMode(String content) {
		if(isExitsDoubleByteGB2312(content)) {
			return CodeFormat.GB2312;
		}
		boolean hasNumeric = false;
		boolean hasAlphanumeric = false;
		for (int i = 0; i < content.length(); i++) {
			char c = content.charAt(i);
			if ((c >= '0') && (c <= '9'))
				hasNumeric = true;
			else if (getAlphanumericCode(c) != -1)
				hasAlphanumeric = true;
			else {
				return CodeFormat.Byte;
			}
		}
		if (hasAlphanumeric) {
			return CodeFormat.Alphanumeric;
		}
		if (hasNumeric) {
//			return CodeFormat.Numberic;
			return CodeFormat.Alphanumeric;
		}
		return CodeFormat.Byte;
	}
	
	private static final int[] ALPHANUMERIC_TABLE = { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 36, -1, -1, -1, 37, 38, -1, -1, -1, -1, 39, 40, -1, 41, 42, 43, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 44, -1, -1, -1, -1, -1, -1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, -1, -1, -1, -1, -1 };
	  
	public static int getAlphanumericCode(int code) {
		if (code < ALPHANUMERIC_TABLE.length) {
			return ALPHANUMERIC_TABLE[code];
		}
		return -1;
	}

	/**
	 * 是否存在汉字
	 * 
	 * @param content
	 * @return
	 */
	private static boolean isExitsDoubleByteGB2312(String content) {
//		byte[] bytes;
//		try {
//			bytes = content.getBytes("Shift_JIS");
//		} catch (UnsupportedEncodingException uee) {
//			return false;
//		}
//		boolean flag = false;
//		int length = bytes.length;
//		for (int i = 0; i < length; i += 2) {
//			int byte1 = bytes[i] & 0xFF;
//			if (((byte1 < 129) || (byte1 > 159))
//					&& ((byte1 < 224) || (byte1 > 235))) {
//			} else {
//				flag = true;
//			}
//		}
//		return false;
		for (int i = 0; i < content.length(); i++) {
			String str = content.substring(i, i + 1);
			try {
				get8BitCode(str);
			} catch (Exception e) {
				return true;
			}
		}
		return false;
	}
	
	
	public static String get8BitsCode(String str) {
		// 每个字符8位
		int codeInt = get8BitCode(str);
		return appendLeftZero(Integer.toBinaryString(codeInt), 8);
	}

	/**
	 * 日文汉字编码
	 * 
	 * @param str
	 * @return
	 * @throws Exception
	 */
	public static String getKanjiCode(String str) throws Exception {
		String dataCodeWords = "";
		// 在0X8140 to 0X9FFC中的字符会减去8140，在0XE040到0XEBBF中的字符要减去0XC140
		// 然后把结果前两个16进制位拿出来乘以0XC0，然后再加上后两个16进制位，最后转成13bit的编码
	    byte[] bytes;
	    try {
			bytes = str.getBytes("GB2312");
		} catch (UnsupportedEncodingException e) {
			throw new Exception(e.toString());
		}
	    int length = bytes.length;
	    try {
		    for (int i = 0; i < length; i += 2) {
		    	int byte1 = bytes[i] & 0xFF;
		    	int byte2 = bytes[(i + 1)] & 0xFF;
		    	int code = byte1 << 8 | byte2;
		    	int subtracted = -1;
		    	if ((code >= 33088) && (code <= 40956))
		    		subtracted = code - 33088;
		    	else if ((code >= 57408) && (code <= 60351)) {
		    		subtracted = code - 49472;
		    	}
		    	if (subtracted == -1) {
		    		throw new Exception("Invalid byte sequence");
		    	}
		    	int encoded = (subtracted >> 8) * 192 + (subtracted & 0xFF);
		    	dataCodeWords += appendLeftZero(Integer.toBinaryString(encoded), 13);
		    }
	    } catch (Exception e) {
	    	return "";
	    }
	    return dataCodeWords;
	}
	
	/**
	 * 中文汉字编码
	 * 
	 * @param str
	 * @return
	 * @throws Exception
	 */
	public static String getGB2312Code(String str) throws Exception {
		String dataCodeWords = "";
	    byte[] bytes;
	    try {
			bytes = str.getBytes("GB2312");
		} catch (UnsupportedEncodingException e) {
			throw new Exception(e.toString());
		}
	    int length = bytes.length;
	    try {
		    for (int i = 0; i < length; i += 2) {
		    	int byte1 = bytes[i] & 0xFF;
		    	int byte2 = bytes[(i + 1)] & 0xFF;
		    	int encoded = 0;
		    	if (byte1 >= 0xA1 && byte1 <= 0xAA && byte2 >= 0xA1 && byte2 <= 0xFE) {
		    		encoded = (byte1 - 0xA1) * 0x60 + (byte2 - 0xA1);
		    	} else if (byte1 >= 0xB0 && byte1 <= 0xFA && byte2 >= 0xA1 && byte2 <= 0xFE) {
		    		encoded = (byte1 - 0xA6) * 0x60 + (byte2 - 0xA1);
		    	}
		    	dataCodeWords += appendLeftZero(Integer.toBinaryString(encoded), 13);
		    }
	    } catch (Exception e) {
	    	return "";
	    }
	    return dataCodeWords;
	}

	// ++++++++++++++++++++++ 二维码格式定义数据 ++++++++++++++++++++++++++
	/**
	 * 最高版本
	 */
	public final static int MAX_VERSION = 40;
	
	/**
	 * 定义各个编码格式的编号
	 */
	public enum CodeFormat {
		Numberic(1),// 数字
		Alphanumeric(2),// 字符
		Byte(4),// 字节
		Kanji(8),// 日文汉字
		GB2312(13);// 中文汉字
		
		private int value;
		
		CodeFormat(int value) {
			this.value = value;
		}
		
		public int getValue() {
			return this.value;
		}
	}
	
	/**
	 * 纠错级别
	 */
	public enum ErrorCodeLevel {
		L(1), M(0), Q(3), H(2);
		
		private int value;
		
		ErrorCodeLevel(int value) {
			this.value = value;
		}
		
		public int getValue() {
			return this.value;
		}
	}
	
	/**
	 * mask方式
	 */
	public enum MaskPattern {
		M000(0),M001(1),M010(2),M011(3),M100(4),M101(5),M110(6),M111(7);
		
		private int value;
		
		MaskPattern(int value) {
			this.value = value;
		}
		
		public int getValue() {
			return this.value;
		}
	}
	
	/**
	 * 根据mask类型计算结果
	 * 
	 * @param pattern
	 * @param i
	 * @param j
	 * @return
	 */
	public static int getMaskResult(MaskPattern pattern, int i, int j) {
		int result = 0;
		switch (pattern) {
			case M000 : {
				result = (i + j) % 2 == 0 ? 1 : 0;
				break;
			}
			case M001 : {
				result =  i % 2 == 0 ? 1 : 0;
				break;
			}
			case M010 : {
				result =  j % 3 == 0 ? 1 : 0;
				break;
			}
			case M011 : {
				result =  (i + j) % 3 == 0 ? 1 : 0;
				break;
			}
			case M100 : {
				result =  (i / 2 + j / 3) % 2 == 0 ? 1 : 0;
				break;
			}
			case M101 : {
				result =  (i * j) % 2 + (i * j) % 3 == 0 ? 1 : 0;
				break;
			}
			case M110 : {
				result =  ((i * j) % 2 + (i * j) % 3) % 2 == 0 ? 1 : 0;
				break;
			}
			case M111 : {
				result =  ((i * j) % 3 + (i + j) % 2) % 2 == 0 ? 1 : 0;
				break;
			}
		}
		return result;
	}
	
	/**
	 * 不同版本（尺寸）的二维码，对于，数字，字符，字节和 Kanji 模式下，对于单个编码的 2 进制的位数
	 * 
	 * @param format
	 * @param version
	 * @return
	 */
	public static int getNumberOfBits(CodeFormat format, int version) {
		/*
		版本		N	A	B	K
		----------------------
		1-9		10	9	8	8
		10-26	12	11	16	10
		27-40	14 	13 	16 	12
		*/
		int numberOfBits = 0;
		Map<CodeFormat, Integer[]> bitsMap = new HashMap<CodeFormat, Integer[]>();
		bitsMap.put(CodeFormat.Numberic, new Integer[] { 10, 12, 14});
		bitsMap.put(CodeFormat.Alphanumeric, new Integer[] { 9, 11, 13});
		bitsMap.put(CodeFormat.Byte, new Integer[] { 8, 16, 16});
		bitsMap.put(CodeFormat.Kanji, new Integer[] { 8, 10, 12});
		bitsMap.put(CodeFormat.GB2312, new Integer[] { 8, 10, 12});
		if (version <= 9) {
			numberOfBits = bitsMap.get(format)[0];
		} else if (version <= 26) {
			numberOfBits = bitsMap.get(format)[1];
		} else if (version <= MAX_VERSION) {
			numberOfBits = bitsMap.get(format)[2];
		}
		return numberOfBits;
	}
	
	/**
	 * 根据版本获取AlignmentPattern的定位
	 * 
	 * @param version
	 * @return
	 */
	public static int[]  getNumberOfAlignmentPatterns(int version) {
		int[] [] numberOfPatterns = new int[40][];
		numberOfPatterns[0] = new int[] {};
		numberOfPatterns[1] = new int[] { 6, 18 };
		numberOfPatterns[2] = new int[] { 6, 22 };
		numberOfPatterns[3] = new int[] { 6, 26 };
		numberOfPatterns[4] = new int[] { 6, 30 };
		numberOfPatterns[5] = new int[] { 6, 34 };
		numberOfPatterns[6] = new int[] { 6, 22, 38 };
		numberOfPatterns[7] = new int[] { 6, 24, 42 };
		numberOfPatterns[8] = new int[] { 6, 26, 46 };
		numberOfPatterns[9] = new int[] { 6, 28, 50 };
		numberOfPatterns[10] = new int[] { 6, 30, 54 };
		numberOfPatterns[11] = new int[] { 6, 32, 58 };
		numberOfPatterns[12] = new int[] { 6, 34, 62 };
		numberOfPatterns[13] = new int[] { 6, 26, 46, 66 };
		numberOfPatterns[14] = new int[] { 6, 26, 48, 70 };
		numberOfPatterns[15] = new int[] { 6, 26, 50, 74 };
		numberOfPatterns[16] = new int[] { 6, 30, 54, 78 };
		numberOfPatterns[17] = new int[] { 6, 30, 56, 82 };
		numberOfPatterns[18] = new int[] { 6, 30, 58, 86 };
		numberOfPatterns[19] = new int[] { 6, 34, 62, 90 };
		numberOfPatterns[20] = new int[] { 6, 28, 50, 72, 94 };
		numberOfPatterns[21] = new int[] { 6, 26, 50, 74, 98 };
		numberOfPatterns[22] = new int[] { 6, 30, 54, 78, 102 };
		numberOfPatterns[23] = new int[] { 6, 28, 54, 80, 106 };
		numberOfPatterns[24] = new int[] { 6, 32, 58, 84, 110 };
		numberOfPatterns[25] = new int[] { 6, 30, 58, 86, 114 };
		numberOfPatterns[26] = new int[] { 6, 34, 62, 90, 118 };
		numberOfPatterns[27] = new int[] { 6, 26, 50, 74, 98, 122 };
		numberOfPatterns[28] = new int[] { 6, 30, 54, 78, 102, 126 };
		numberOfPatterns[29] = new int[] { 6, 26, 52, 78, 104, 130 };
		numberOfPatterns[30] = new int[] { 6, 30, 56, 82, 108, 134 };
		numberOfPatterns[31] = new int[] { 6, 34, 60, 86, 112, 138 };
		numberOfPatterns[32] = new int[] { 6, 30, 58, 86, 114, 142 };
		numberOfPatterns[33] = new int[] { 6, 34, 62, 90, 118, 146 };
		numberOfPatterns[34] = new int[] { 6, 30, 54, 78, 102, 126, 150 };
		numberOfPatterns[35] = new int[] { 6, 24, 50, 76, 102, 128, 154 };
		numberOfPatterns[36] = new int[] { 6, 28, 54, 80, 106, 132, 158 };
		numberOfPatterns[37] = new int[] { 6, 32, 58, 84, 110, 136, 162 };
		numberOfPatterns[38] = new int[] { 6, 26, 54, 82, 110, 138, 166 };
		numberOfPatterns[39] = new int[] { 6, 30, 58, 86, 114, 142, 170 };
		return numberOfPatterns[version - 1];
	}
	
	/**
	 * Number of Error Code Correction Blocks ：需要分多少个块
	 * Error Correction Code Per Blocks：每一个块中的code个数，所谓的code的个数，也就是有多少个8bits的字节。 
	 * 编码字节数=errorCodeBlock[0]*errorCodeBlock[2]+errorCodeBlock[1]*errorCodeBlock[2]
	 * 纠错码字节数=errorCodeBlock[4]
	 * 
	 * @param version
	 * @return
	 */
	public static int[] getErrorCodeBlock(int version, ErrorCodeLevel level) {
		Map<String, int[]> errorCodeBlock = new HashMap<String, int[]>();
		errorCodeBlock.put("1L", new int[] { 1, 0, 19, 0, 7, 24 });
		errorCodeBlock.put("1M", new int[] { 1, 0, 16, 0, 10, 20 });
		errorCodeBlock.put("1Q", new int[] { 1, 0, 13, 0, 13, 15 });
		errorCodeBlock.put("1H", new int[] { 1, 0, 9, 0, 17, 10 });
		errorCodeBlock.put("2L", new int[] { 1, 0, 34, 0, 10, 49 });
		errorCodeBlock.put("2M", new int[] { 1, 0, 28, 0, 16, 40 });
		errorCodeBlock.put("2Q", new int[] { 1, 0, 22, 0, 22, 31 });
		errorCodeBlock.put("2H", new int[] { 1, 0, 16, 0, 28, 20 });
		errorCodeBlock.put("3L", new int[] { 1, 0, 55, 0, 15, 79 });
		errorCodeBlock.put("3M", new int[] { 1, 0, 44, 0, 26, 60 });
		errorCodeBlock.put("3Q", new int[] { 2, 0, 17, 0, 36, 49 });
		errorCodeBlock.put("3H", new int[] { 2, 0, 13, 0, 44, 31 });
		errorCodeBlock.put("4L", new int[] { 1, 0, 80, 0, 20, 113 });
		errorCodeBlock.put("4M", new int[] { 2, 0, 32, 0, 36, 84 });
		errorCodeBlock.put("4Q", new int[] { 2, 0, 24, 0, 52, 69 });
		errorCodeBlock.put("4H", new int[] { 4, 0, 9, 0, 64, 46 });
		errorCodeBlock.put("5L", new int[] { 1, 0, 108, 0, 26, 154 });
		errorCodeBlock.put("5M", new int[] { 2, 0, 43, 0, 48, 116 });
		// 分块1，分块2，分块1数据，分块2数据，纠错码长度，
		errorCodeBlock.put("5Q", new int[] { 2, 2, 15, 16, 72, 95 });
		errorCodeBlock.put("5H", new int[] { 2, 2, 11, 12, 88, 63 });
		errorCodeBlock.put("6L", new int[] { 2, 0, 68, 0, 36, 194 });
		errorCodeBlock.put("6M", new int[] { 4, 0, 27, 0, 64, 151 });
		errorCodeBlock.put("6Q", new int[] { 4, 0, 19, 0, 96, 122 });
		errorCodeBlock.put("6H", new int[] { 4, 0, 15, 0, 112, 81 });
		errorCodeBlock.put("7L", new int[] { 2, 0, 78, 0, 40, 244 });
		errorCodeBlock.put("7M", new int[] { 4, 0, 31, 0, 72, 188 });
		errorCodeBlock.put("7Q", new int[] { 2, 4, 14, 15, 108, 154 });
		errorCodeBlock.put("7H", new int[] { 4, 1, 13, 14, 130, 101 });
		errorCodeBlock.put("8L", new int[] { 2, 0, 97, 0, 48, 299 });
		errorCodeBlock.put("8M", new int[] { 2, 2, 38, 39, 88, 229 });
		errorCodeBlock.put("8Q", new int[] { 4, 2, 18, 19, 132, 183 });
		errorCodeBlock.put("8H", new int[] { 4, 2, 14, 15, 156, 123 });
		errorCodeBlock.put("9L", new int[] { 2, 0, 116, 0, 60, 354 });
		errorCodeBlock.put("9M", new int[] { 3, 2, 36, 37, 110, 267 });
		errorCodeBlock.put("9Q", new int[] { 4, 4, 16, 17, 160, 223 });
		errorCodeBlock.put("9H", new int[] { 4, 4, 12, 13, 192, 145 });
		errorCodeBlock.put("10L", new int[] { 2, 2, 68, 69, 72, 418 });
		errorCodeBlock.put("10M", new int[] { 4, 1, 43, 44, 130, 319 });
		errorCodeBlock.put("10Q", new int[] { 6, 2, 19, 20, 192, 262 });
		errorCodeBlock.put("10H", new int[] { 6, 2, 15, 16, 224, 176 });
		errorCodeBlock.put("11L", new int[] { 4, 0, 81, 0, 80, 485 });
		errorCodeBlock.put("11M", new int[] { 1, 4, 50, 51, 150, 368 });
		errorCodeBlock.put("11Q", new int[] { 4, 4, 22, 23, 224, 299 });
		errorCodeBlock.put("11H", new int[] { 3, 8, 12, 13, 264, 207 });
		errorCodeBlock.put("12L", new int[] { 2, 2, 92, 93, 96, 555 });
		errorCodeBlock.put("12M", new int[] { 6, 2, 36, 37, 176, 421 });
		errorCodeBlock.put("12Q", new int[] { 4, 6, 20, 21, 260, 351 });
		errorCodeBlock.put("12H", new int[] { 7, 4, 14, 15, 308, 236 });
		errorCodeBlock.put("13L", new int[] { 4, 0, 107, 0, 104, 624 });
		errorCodeBlock.put("13M", new int[] { 8, 1, 37, 38, 198, 479 });
		errorCodeBlock.put("13Q", new int[] { 8, 4, 20, 21, 288, 398 });
		errorCodeBlock.put("13H", new int[] { 12, 4, 11, 12, 352, 275 });
		errorCodeBlock.put("14L", new int[] { 3, 1, 115, 116, 120, 707 });
		errorCodeBlock.put("14M", new int[] { 4, 5, 40, 41, 216, 531 });
		errorCodeBlock.put("14Q", new int[] { 11, 5, 16, 17, 320, 447 });
		errorCodeBlock.put("14H", new int[] { 11, 5, 12, 13, 384, 302 });
		errorCodeBlock.put("15L", new int[] { 5, 1, 87, 88, 132 });
		errorCodeBlock.put("15M", new int[] { 5, 5, 41, 42, 240 });
		errorCodeBlock.put("15Q", new int[] { 5, 7, 24, 25, 360 });
		errorCodeBlock.put("15H", new int[] { 11, 7, 12, 13, 432 });
		errorCodeBlock.put("16L", new int[] { 5, 1, 98, 99, 144 });
		errorCodeBlock.put("16M", new int[] { 7, 3, 45, 46, 280 });
		errorCodeBlock.put("16Q", new int[] { 15, 2, 19, 20, 408 });
		errorCodeBlock.put("16H", new int[] { 3, 13, 15, 16, 480 });
		errorCodeBlock.put("17L", new int[] { 1, 5, 107, 108, 168 });
		errorCodeBlock.put("17M", new int[] { 10, 1, 46, 47, 308 });
		errorCodeBlock.put("17Q", new int[] { 1, 15, 22, 23, 448 });
		errorCodeBlock.put("17H", new int[] { 2, 17, 14, 15, 532 });
		errorCodeBlock.put("18L", new int[] { 5, 1, 120, 121, 180 });
		errorCodeBlock.put("18M", new int[] { 9, 4, 43, 44, 338 });
		errorCodeBlock.put("18Q", new int[] { 17, 1, 22, 23, 504 });
		errorCodeBlock.put("18H", new int[] { 2, 19, 14, 15, 588 });
		errorCodeBlock.put("19L", new int[] { 3, 4, 113, 114, 196 });
		errorCodeBlock.put("19M", new int[] { 3, 11, 44, 45, 364 });
		errorCodeBlock.put("19Q", new int[] { 17, 4, 21, 22, 546 });
		errorCodeBlock.put("19H", new int[] { 9, 16, 13, 14, 650 });
		errorCodeBlock.put("20L", new int[] { 3, 5, 107, 108, 224 });
		errorCodeBlock.put("20M", new int[] { 3, 13, 41, 42, 416 });
		errorCodeBlock.put("20Q", new int[] { 15, 5, 24, 25, 600 });
		errorCodeBlock.put("20H", new int[] { 15, 10, 15, 16, 700 });
		errorCodeBlock.put("21L", new int[] { 4, 4, 116, 117, 224 });
		errorCodeBlock.put("21M", new int[] { 17, 0, 42, 0, 442 });
		errorCodeBlock.put("21Q", new int[] { 17, 6, 22, 23, 644 });
		errorCodeBlock.put("21H", new int[] { 19, 6, 16, 17, 750 });
		errorCodeBlock.put("22L", new int[] { 2, 7, 111, 112, 252 });
		errorCodeBlock.put("22M", new int[] { 17, 0, 46, 0, 476 });
		errorCodeBlock.put("22Q", new int[] { 7, 16, 24, 25, 690 });
		errorCodeBlock.put("22H", new int[] { 34, 0, 13, 0, 816 });
		errorCodeBlock.put("23L", new int[] { 4, 5, 121, 122, 270 });
		errorCodeBlock.put("23M", new int[] { 4, 14, 47, 48, 504 });
		errorCodeBlock.put("23Q", new int[] { 11, 14, 24, 25, 750 });
		errorCodeBlock.put("23H", new int[] { 16, 14, 15, 16, 900 });
		errorCodeBlock.put("24L", new int[] { 6, 4, 117, 118, 300 });
		errorCodeBlock.put("24M", new int[] { 6, 14, 45, 46, 560 });
		errorCodeBlock.put("24Q", new int[] { 11, 16, 24, 25, 810 });
		errorCodeBlock.put("24H", new int[] { 30, 2, 16, 17, 960 });
		errorCodeBlock.put("25L", new int[] { 8, 4, 106, 107, 312 });
		errorCodeBlock.put("25M", new int[] { 8, 13, 47, 48, 588 });
		errorCodeBlock.put("25Q", new int[] { 7, 22, 24, 25, 870 });
		errorCodeBlock.put("25H", new int[] { 22, 13, 15, 16, 1050 });
		errorCodeBlock.put("26L", new int[] { 10, 2, 114, 115, 336 });
		errorCodeBlock.put("26M", new int[] { 19, 4, 46, 47, 644 });
		errorCodeBlock.put("26Q", new int[] { 28, 6, 22, 23, 952 });
		errorCodeBlock.put("26H", new int[] { 33, 4, 16, 17, 1110 });
		errorCodeBlock.put("27L", new int[] { 8, 4, 122, 123, 360 });
		errorCodeBlock.put("27M", new int[] { 22, 3, 45, 46, 700 });
		errorCodeBlock.put("27Q", new int[] { 8, 26, 23, 24, 1020 });
		errorCodeBlock.put("27H", new int[] { 12, 28, 15, 16, 1200 });
		errorCodeBlock.put("28L", new int[] { 3, 10, 117, 118, 390 });
		errorCodeBlock.put("28M", new int[] { 3, 23, 45, 46, 728 });
		errorCodeBlock.put("28Q", new int[] { 4, 31, 24, 25, 1050 });
		errorCodeBlock.put("28H", new int[] { 11, 31, 15, 16, 1260 });
		errorCodeBlock.put("29L", new int[] { 7, 7, 116, 117, 420 });
		errorCodeBlock.put("29M", new int[] { 21, 7, 45, 46, 784 });
		errorCodeBlock.put("29Q", new int[] { 1, 37, 23, 24, 1140 });
		errorCodeBlock.put("29H", new int[] { 19, 26, 15, 16, 1350 });
		errorCodeBlock.put("30L", new int[] { 5, 10, 115, 116, 450 });
		errorCodeBlock.put("30M", new int[] { 19, 10, 47, 48, 812 });
		errorCodeBlock.put("30Q", new int[] { 15, 25, 24, 25, 1200 });
		errorCodeBlock.put("30H", new int[] { 23, 25, 15, 16, 1440 });
		errorCodeBlock.put("31L", new int[] { 13, 3, 115, 116, 480 });
		errorCodeBlock.put("31M", new int[] { 2, 29, 46, 47, 868 });
		errorCodeBlock.put("31Q", new int[] { 42, 1, 24, 25, 1290 });
		errorCodeBlock.put("31H", new int[] { 23, 28, 15, 16, 1530 });
		errorCodeBlock.put("32L", new int[] { 17, 0, 115, 0, 510 });
		errorCodeBlock.put("32M", new int[] { 10, 23, 46, 47, 924 });
		errorCodeBlock.put("32Q", new int[] { 10, 35, 24, 25, 1350 });
		errorCodeBlock.put("32H", new int[] { 19, 35, 15, 16, 1620 });
		errorCodeBlock.put("33L", new int[] { 17, 1, 115, 116, 540 });
		errorCodeBlock.put("33M", new int[] { 14, 21, 46, 47, 980 });
		errorCodeBlock.put("33Q", new int[] { 29, 19, 24, 25, 1440 });
		errorCodeBlock.put("33H", new int[] { 11, 46, 15, 16, 1710 });
		errorCodeBlock.put("34L", new int[] { 13, 6, 115, 116, 570 });
		errorCodeBlock.put("34M", new int[] { 14, 23, 46, 47, 1036 });
		errorCodeBlock.put("34Q", new int[] { 44, 7, 24, 25, 1530 });
		errorCodeBlock.put("34H", new int[] { 59, 1, 16, 17, 1800 });
		errorCodeBlock.put("35L", new int[] { 12, 7, 121, 122, 570 });
		errorCodeBlock.put("35M", new int[] { 12, 26, 47, 48, 1064 });
		errorCodeBlock.put("35Q", new int[] { 39, 14, 24, 25, 1590 });
		errorCodeBlock.put("35H", new int[] { 22, 41, 15, 16, 1890 });
		errorCodeBlock.put("36L", new int[] { 6, 14, 121, 122, 600 });
		errorCodeBlock.put("36M", new int[] { 6, 34, 47, 48, 1120 });
		errorCodeBlock.put("36Q", new int[] { 46, 10, 24, 25, 1680 });
		errorCodeBlock.put("36H", new int[] { 2, 64, 15, 16, 1980 });
		errorCodeBlock.put("37L", new int[] { 17, 4, 122, 123, 630 });
		errorCodeBlock.put("37M", new int[] { 29, 14, 46, 47, 1204 });
		errorCodeBlock.put("37Q", new int[] { 49, 10, 24, 25, 1770 });
		errorCodeBlock.put("37H", new int[] { 24, 46, 15, 16, 2100 });
		errorCodeBlock.put("38L", new int[] { 4, 18, 122, 123, 660 });
		errorCodeBlock.put("38M", new int[] { 13, 32, 46, 47, 1260 });
		errorCodeBlock.put("38Q", new int[] { 48, 14, 24, 25, 1860 });
		errorCodeBlock.put("38H", new int[] { 42, 32, 15, 16, 2220 });
		errorCodeBlock.put("39L", new int[] { 20, 4, 117, 118, 720 });
		errorCodeBlock.put("39M", new int[] { 40, 7, 47, 48, 1316 });
		errorCodeBlock.put("39Q", new int[] { 43, 22, 24, 25, 1950 });
		errorCodeBlock.put("39H", new int[] { 10, 67, 15, 16, 2310 });
		errorCodeBlock.put("40L", new int[] { 19, 6, 118, 119, 750 });
		errorCodeBlock.put("40M", new int[] { 18, 31, 47, 48, 1372 });
		errorCodeBlock.put("40Q", new int[] { 34, 34, 24, 25, 2040 });
		errorCodeBlock.put("40H", new int[] { 20, 61, 15, 16, 2430 });
		return errorCodeBlock.get(version + level.name());
	}
	
	/**
	 * 剩余空位数（作为功能区，填充0，白色）
	 * 
	 * @param version
	 * @return
	 */
	public static int getRemainderBits(int version) {
		int len = 0;
		switch(version){
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
				len = 7;
				break;
			case 14:
			case 15:
			case 16:
			case 17:
			case 18:
			case 19:
			case 20:
			case 28:
			case 29:
			case 30:
			case 31:
			case 32:
			case 33:
			case 34:
				len = 3;
				break;
			case 21:
			case 22:
			case 23:
			case 24:
			case 25:
			case 26:
			case 27:
				len = 4;
				break;
			default:
				len = 0;
				break;
		}
		return len;
	}
	
	/**
	 * 功能区的容量，如版本2=235，总量625，格式区31，编码区=625-235-31=359
	 * 而版本2H定义的编码和纠错码共：(16+28)*8=352，另有7个版本编码
	 * 版本7及以上，格式区67
	 * @param version
	 * @return
	 */
	public static int getFuncZoneSize(int version) {
		int[] QRHELPER_CONST_FPM = new int[41];
		QRHELPER_CONST_FPM[1] = 202;
		QRHELPER_CONST_FPM[2] = 235;
		QRHELPER_CONST_FPM[3] = 243;
		QRHELPER_CONST_FPM[4] = 251;
		QRHELPER_CONST_FPM[5] = 259;
		QRHELPER_CONST_FPM[6] = 267;
		QRHELPER_CONST_FPM[7] = 390;
		QRHELPER_CONST_FPM[8] = 398;
		QRHELPER_CONST_FPM[9] = 406;
		QRHELPER_CONST_FPM[10] = 414;
		QRHELPER_CONST_FPM[11] = 422;
		QRHELPER_CONST_FPM[12] = 430;
		QRHELPER_CONST_FPM[13] = 438;
		QRHELPER_CONST_FPM[14] = 611;
		QRHELPER_CONST_FPM[15] = 619;
		QRHELPER_CONST_FPM[16] = 627;
		QRHELPER_CONST_FPM[17] = 635;
		QRHELPER_CONST_FPM[18] = 643;
		QRHELPER_CONST_FPM[19] = 651;
		QRHELPER_CONST_FPM[20] = 659;
		QRHELPER_CONST_FPM[21] = 882;
		QRHELPER_CONST_FPM[22] = 890;
		QRHELPER_CONST_FPM[23] = 898;
		QRHELPER_CONST_FPM[24] = 906;
		QRHELPER_CONST_FPM[25] = 914;
		QRHELPER_CONST_FPM[26] = 922;
		QRHELPER_CONST_FPM[27] = 930;
		QRHELPER_CONST_FPM[28] = 1203;
		QRHELPER_CONST_FPM[29] = 1211;
		QRHELPER_CONST_FPM[30] = 1219;
		QRHELPER_CONST_FPM[31] = 1227;
		QRHELPER_CONST_FPM[32] = 1235;
		QRHELPER_CONST_FPM[33] = 1243;
		QRHELPER_CONST_FPM[34] = 1251;
		QRHELPER_CONST_FPM[35] = 1574;
		QRHELPER_CONST_FPM[36] = 1582;
		QRHELPER_CONST_FPM[37] = 1590;
		QRHELPER_CONST_FPM[38] = 1598;
		QRHELPER_CONST_FPM[39] = 1606;
		QRHELPER_CONST_FPM[40] = 1614;
		return QRHELPER_CONST_FPM[version];
	}
	
	/**
	 * Alphanumeric mode 字符编码映射
	 * 
	 * @param str
	 * @return
	 */
	public static int getAlphanumbericCode(String str) {
		Map<String, Integer> map = new HashMap<String, Integer>();
		map.put("0", 0); 
		map.put("1", 1); 
		map.put("2", 2); 
		map.put("3", 3); 
		map.put("4", 4); 
		map.put("5", 5); 
		map.put("6", 6); 
		map.put("7", 7); 
		map.put("8", 8); 
		map.put("9", 9); 
		map.put("A", 10); 
		map.put("B", 11); 
		map.put("C", 12); 
		map.put("D", 13); 
		map.put("E", 14); 
		map.put("F", 15); 
		map.put("G", 16); 
		map.put("H", 17); 
		map.put("I", 18); 
		map.put("J", 19); 
		map.put("K", 20); 
		map.put("L", 21); 
		map.put("M", 22); 
		map.put("N", 23); 
		map.put("O", 24); 
		map.put("P", 25); 
		map.put("Q", 26); 
		map.put("R", 27); 
		map.put("S", 28); 
		map.put("T", 29); 
		map.put("U", 30); 
		map.put("V", 31); 
		map.put("W", 32); 
		map.put("X", 33); 
		map.put("Y", 34); 
		map.put("Z", 35); 
		map.put(" ", 36); 
		map.put("$", 37); 
		map.put("%", 38); 
		map.put("*", 39); 
		map.put("+", 40); 
		map.put("-", 41); 
		map.put(".", 42); 
		map.put("/", 43); 
		map.put(":", 44);
		return map.get(str);
	}
	
	/**
	 * Byte 字符编码映射
	 * 
	 * @param str
	 * @return
	 */
	public static int get8BitCode(String str) {
		Map<String, Integer> map = new HashMap<String, Integer>();
		map.put(" ", 0x20);
		map.put("!", 0x21);
		map.put("\"", 0x22);
		map.put("#", 0x23);
		map.put("$", 0x24);
		map.put("%", 0x25);
		map.put("&", 0x26);
		map.put("'", 0x27);
		map.put("(", 0x28);
		map.put(")", 0x29);
		map.put("*", 0x2A);
		map.put("+", 0x2B);
		map.put(",", 0x2C);
		map.put("-", 0x2D);
		map.put(".", 0x2E);
		map.put("/", 0x2F);
		map.put("0", 0x30);
		map.put("1", 0x31);
		map.put("2", 0x32);
		map.put("3", 0x33);
		map.put("4", 0x34);
		map.put("5", 0x35);
		map.put("6", 0x36);
		map.put("7", 0x37);
		map.put("8", 0x38);
		map.put("9", 0x39);
		map.put(":", 0x3A);
		map.put(";", 0x3B);
		map.put("<", 0x3C);
		map.put("=", 0x3D);
		map.put(">", 0x3E);
		map.put("?", 0x3F);
		map.put("@", 0x40);
		map.put("A", 0x41);
		map.put("B", 0x42);
		map.put("C", 0x43);
		map.put("D", 0x44);
		map.put("E", 0x45);
		map.put("F", 0x46);
		map.put("G", 0x47);
		map.put("H", 0x48);
		map.put("I", 0x49);
		map.put("J", 0x4A);
		map.put("K", 0x4B);
		map.put("L", 0x4C);
		map.put("M", 0x4D);
		map.put("N", 0x4E);
		map.put("O", 0x4F);
		map.put("P", 0x50);
		map.put("Q", 0x51);
		map.put("R", 0x52);
		map.put("S", 0x53);
		map.put("T", 0x54);
		map.put("U", 0x55);
		map.put("V", 0x56);
		map.put("W", 0x57);
		map.put("X", 0x58);
		map.put("Y", 0x59);
		map.put("Z", 0x5A);
		map.put("[", 0x5B);
		map.put("¥", 0x5C);
		map.put("]", 0x5D);
		map.put("^", 0x5E);
		map.put("_", 0x5F);
		map.put("`", 0x60);
		map.put("a", 0x61);
		map.put("b", 0x62);
		map.put("c", 0x63);
		map.put("d", 0x64);
		map.put("e", 0x65);
		map.put("f", 0x66);
		map.put("g", 0x67);
		map.put("h", 0x68);
		map.put("i", 0x69);
		map.put("j", 0x6A);
		map.put("k", 0x6B);
		map.put("l", 0x6C);
		map.put("m", 0x6D);
		map.put("n", 0x6E);
		map.put("o", 0x6F);
		map.put("p", 0x70);
		map.put("q", 0x71);
		map.put("r", 0x72);
		map.put("s", 0x73);
		map.put("t", 0x74);
		map.put("u", 0x75);
		map.put("v", 0x76);
		map.put("w", 0x77);
		map.put("x", 0x78);
		map.put("y", 0x79);
		map.put("z", 0x7A);
		map.put("{", 0x7B);
		map.put("|", 0x7C);
		map.put("}", 0x7D);
		map.put("¯", 0x7E);
		return map.get(str);
	}
	
	/**
	 * 获取版本的编码
	 * 
	 * @param version
	 * @return
	 */
	public static String getVersionInfo(int version) {
		String[] versionInfoArray = new String[41];
		versionInfoArray[7]="000111110010010100";
		versionInfoArray[8]="001000010110111100";
		versionInfoArray[9]="001001101010011001";
		versionInfoArray[10]="001010010011010011";
		versionInfoArray[11]="001011101111110110";
		versionInfoArray[12]="001100011101100010";
		versionInfoArray[13]="001101100001000111";
		versionInfoArray[14]="001110011000001101";
		versionInfoArray[15]="001111100100101000";
		versionInfoArray[16]="010000101101111000";
		versionInfoArray[17]="010001010001011101";
		versionInfoArray[18]="010010101000010111";
		versionInfoArray[19]="010011010100110010";
		versionInfoArray[20]="010100100110100110";
		versionInfoArray[21]="010101011010000011";
		versionInfoArray[22]="010110100011001001";
		versionInfoArray[23]="010111011111101100";
		versionInfoArray[24]="011000111011000100";
		versionInfoArray[25]="011001000111100001";
		versionInfoArray[26]="011010111110101011";
		versionInfoArray[27]="011011000010001110";
		versionInfoArray[28]="011100110000011010";
		versionInfoArray[29]="011101001100111111";
		versionInfoArray[30]="011110110101110101";
		versionInfoArray[31]="011111001001010000";
		versionInfoArray[32]="100000100111010101";
		versionInfoArray[33]="100001011011110000";
		versionInfoArray[34]="100010100010111010";
		versionInfoArray[35]="100011011110011111";
		versionInfoArray[36]="100100101100001011";
		versionInfoArray[37]="100101010000101110";
		versionInfoArray[38]="100110101001100100";
		versionInfoArray[39]="100111010101000001";
		versionInfoArray[40]="101000110001101001";
		return versionInfoArray[version];
	}
	
}

// 计算纠错码的工具类
class GF {
	
	// values of alpha^0, alpha^1,..., alpha^255 in the GF(2^8), index is the power number.
	public static int[] aTo =  { 
		1,2,4,8,16,32,64,128,29,58,116,232,205,135,19,38,76,152,45,90,180,117,
		234,201,143,3,6,12,24,48,96,192,157,39,78,156,37,74,148,53,106,212,181,
		119,238,193,159,35,70,140,5,10,20,40,80,160,93,186,105,210,185,111,222,
		161,95,190,97,194,153,47,94,188,101,202,137,15,30,60,120,240,253,231,211,
		187,107,214,177,127,254,225,223,163,91,182,113,226,217,175,67,134,17,34,
		68,136,13,26,52,104,208,189,103,206,129,31,62,124,248,237,199,147,59,118,
		236,197,151,51,102,204,133,23,46,92,184,109,218,169,79,158,33,66,132,21,42,
		84,168,77,154,41,82,164,85,170,73,146,57,114,228,213,183,115,230,209,191,
		99,198,145,63,126,252,229,215,179,123,246,241,255,227,219,171,75,150,49,
		98,196,149,55,110,220,165,87,174,65,130,25,50,100,200,141,7,14,28,56,112,
		224,221,167,83,166,81,162,89,178,121,242,249,239,195,155,43,86,172,69,138,
		9,18,36,72,144,61,122,244,245,247,243,251,235,203,139,11,22,44,88,176,125,
		250,233,207,131,27,54,108,216,173,71,142
	};
	
	// values of power, index is the value in GF(2^8), return the power.
	public static int[] expOf = {0,
		255,1,25,2,50,26,198,3,223,51,238,27,104,199,75,4,100,224,14,52,141,239,
		129,28,193,105,248,200,8,76,113,5,138,101,47,225,36,15,33,53,147,142,218,
		240,18,130,69,29,181,194,125,106,39,249,185,201,154,9,120,77,228,114,166,
		6,191,139,98,102,221,48,253,226,152,37,179,16,145,34,136,54,208,148,206,
		143,150,219,189,241,210,19,92,131,56,70,64,30,66,182,163,195,72,126,110,
		107,58,40,84,250,133,186,61,202,94,155,159,10,21,121,43,78,212,229,172,
		115,243,167,87,7,112,192,247,140,128,99,13,103,74,222,237,49,197,254,24,
		227,165,153,119,38,184,180,124,17,68,146,217,35,32,137,46,55,63,209,91,149,
		188,207,205,144,135,151,178,220,252,190,97,242,86,211,171,20,42,93,158,132,
		60,57,83,71,109,65,162,31,45,67,216,183,123,164,118,196,23,73,236,127,12,
		111,246,108,161,59,82,41,157,85,170,251,96,134,177,187,204,62,90,203,89,
		95,176,156,169,160,81,11,245,22,235,122,117,44,215,79,174,213,233,230,
		231,173,232,116,214,244,234,168,80,88,175};
	
	public static int exp(int p_of_a) {
		return aTo[p_of_a];
	}
	
	public static int lg(int gfv) {
		return GF.expOf[gfv];
	}
	
	public static int inverse(int gfv) {
		return GF.exp(255-GF.lg(gfv));
	}
	
	public static int add(int a, int b) {
		return a ^ b;
	}
	
	public static int sub (int a, int b) {
		return GF.add(a, b);
	}
	
	public static int multi(int a, int b) {
		if(a == 0 || b == 0) {
			return 0;
		}
		if(a == 1) {
			return b;
		}
		if(b == 1) {
			return a;
		}
		return GF.exp((GF.lg(a) + GF.lg(b)) % 255);
	}
	
	public static int devide(int a, int b) {
		if(b == 0) {
			return 0;
		}
		if(a == 0 ) {
			return 0;
		}
		if(b == 1) {
			return a;
		}
		return GF.exp(Math.abs(GF.lg(a)-GF.lg(b))%255);
	}
	
	public static int[] multi(int[] p, int[] q) {
		int[] t = new int[p.length + q.length - 1];
		for(int j = 0; j < q.length; j++){
			for(int i = 0; i < p.length; i++){
				t[i+j] ^= GF.multi(p[i],q[j]);
			}
		}
		return t;
	}
	
	// get the generator polynomial. ([1,alpha^0]*[1,alpha^1]*...*[1,alpha^(n-1)]) n is the number of error correction codeblocks
	public static int[] gx(int nsym){
		int[] g = {1, GF.exp(0)};
		for(int i = 1; i < nsym; i++){
			int[] q = {1, GF.exp(i)};
			g = multi(g, q);
		}
		return g;
	}
	
}

