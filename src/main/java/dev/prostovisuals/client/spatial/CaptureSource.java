package dev.prostovisuals.client.spatial;

import java.awt.image.BufferedImage;

public interface CaptureSource extends AutoCloseable {
    String id();
    String name();
    BufferedImage capture() throws Exception;
    default boolean isAvailable() { return true; }
    @Override default void close() throws Exception {}
}
