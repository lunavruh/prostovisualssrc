package dev.prostovisuals.client.spatial;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class DesktopCaptureSource implements CaptureSource {
    private final Rectangle bounds;
    private final String id;
    private final String name;
    private volatile Robot robot;

    public DesktopCaptureSource(String id, String name, Rectangle bounds) {
        this.id = id;
        this.name = name;
        this.bounds = new Rectangle(bounds);
    }

    public Rectangle bounds() { return new Rectangle(bounds); }
    @Override public String id() { return id; }
    @Override public String name() { return name; }

    @Override
    public BufferedImage capture() throws Exception {
        Robot r = robot;
        if (r == null) {
            synchronized (this) {
                r = robot;
                if (r == null) robot = r = new Robot();
            }
        }
        return r.createScreenCapture(bounds);
    }
}
