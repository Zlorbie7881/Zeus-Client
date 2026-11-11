package com.zlorbie788.zeus.client;

import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public final class WindowIcon {
    private WindowIcon() {}

    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static long applied;
    private static boolean failed;

    public static void tick(Minecraft mc) {
        if (failed) return;
        try {
            long handle = mc.getWindow().handle();
            if (handle == 0L || handle == applied) return;
            apply(handle);
            applied = handle;
            mc.updateTitle();
        } catch (Throwable t) {
            failed = true;
            Zeus.LOG.warn("The Zeus window icon could not be set; the game's own icon stays", t);
        }
    }

    private static void apply(long handle) throws IOException {
        BufferedImage src;
        try (InputStream in = WindowIcon.class.getResourceAsStream("/assets/zeus/icon.png")) {
            if (in == null) throw new IOException("assets/zeus/icon.png is missing");
            src = ImageIO.read(in);
        }
        GLFWImage.Buffer images = GLFWImage.malloc(SIZES.length);
        ByteBuffer[] pixels = new ByteBuffer[SIZES.length];
        try {
            for (int i = 0; i < SIZES.length; i++) {
                int s = SIZES[i];
                BufferedImage scaled = scale(src, s);
                ByteBuffer buf = MemoryUtil.memAlloc(s * s * 4);
                for (int y = 0; y < s; y++) {
                    for (int x = 0; x < s; x++) {
                        int argb = scaled.getRGB(x, y);
                        buf.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
                    }
                }
                buf.flip();
                pixels[i] = buf;
                images.position(i);
                images.width(s);
                images.height(s);
                images.pixels(buf);
            }
            images.position(0);
            GLFW.glfwSetWindowIcon(handle, images);
        } finally {
            images.free();
            for (ByteBuffer b : pixels) if (b != null) MemoryUtil.memFree(b);
        }
    }

    private static BufferedImage scale(BufferedImage src, int size) {
        BufferedImage cur = src;
        while (cur.getWidth() / 2 >= size) cur = step(cur, cur.getWidth() / 2);
        return cur.getWidth() == size ? cur : step(cur, size);
    }

    private static BufferedImage step(BufferedImage in, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(in, 0, 0, size, size, null);
        g.dispose();
        return out;
    }
}
