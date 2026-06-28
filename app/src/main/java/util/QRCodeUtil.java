package tv.biliclassic.util;

/*
 * Created by liupe on 2018/11/20.
/*
 * 本软件基于以下项目修改，致谢前辈：
 *   - 哔哩终端 (BiliTerminal) by RobinNotBad
 *   - 腕上哔哩 (WristBilibili) by luern0313
 *
 * 本程序是自由软件，遵循 GNU 通用公共许可证第 3 版（或更高版本）发布。
 * 你可以重新分发或修改它，希望它能为你带来快乐。
 *
 * 详情请参阅 GNU 通用公共许可证：
 * <https://www.gnu.org/licenses/>
 *
 * 修改者：一只毛子球 (BiliClassic)
 * 修改时间：2026年6月19日
 *
 * 移植到 BiliClassic (Java 6 兼容)
 * 改用 SwetakeQRCode（纯 Java 实现，兼容 Android 2.2）
 */

import android.graphics.Bitmap;
import android.graphics.Color;

import com.swetake.util.Qrcode;

public class QRCodeUtil {

    /**
     * 创建二维码位图
     *
     * @param content 字符串内容
     * @param width   位图宽度(单位:px)
     * @param height  位图高度(单位:px)
     */
    public static Bitmap createQRCodeBitmap(String content, int width, int height) {
        if (content == null || content.length() == 0) {
            return null;
        }

        if (width < 0 || height < 0) {
            return null;
        }

        try {
            // 设置二维码参数
            Qrcode qrcode = new Qrcode();
            qrcode.setQrcodeErrorCorrect('M');  // 纠错等级 (L, M, Q, H)
            qrcode.setQrcodeEncodeMode('B');    // 编码模式 (B, A, N)
            qrcode.setQrcodeVersion(10);        // 版本 1-40

            // 生成二维码矩阵
            byte[] bytes = content.getBytes("UTF-8");
            boolean[][] code = qrcode.calQrcode(bytes);

            // 计算每个模块的大小
            int codeSize = code.length;
            int moduleSize = Math.min(width, height) / codeSize;
            if (moduleSize < 1) {
                moduleSize = 1;
            }

            // 计算实际二维码占用的宽高
            int realSize = codeSize * moduleSize;
            int offsetX = (width - realSize) / 2;
            int offsetY = (height - realSize) / 2;

            // 创建位图
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            bitmap.eraseColor(Color.WHITE);

            // 绘制二维码
            for (int y = 0; y < codeSize; y++) {
                for (int x = 0; x < codeSize; x++) {
                    if (code[y][x]) {
                        int startX = offsetX + x * moduleSize;
                        int startY = offsetY + y * moduleSize;
                        for (int dy = 0; dy < moduleSize; dy++) {
                            for (int dx = 0; dx < moduleSize; dx++) {
                                int px = startX + dx;
                                int py = startY + dy;
                                if (px < width && py < height) {
                                    bitmap.setPixel(px, py, Color.BLACK);
                                }
                            }
                        }
                    }
                }
            }

            return bitmap;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}