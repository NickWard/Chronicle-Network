package net.openhft.chronicle.network.connection;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.io.Closeable;
import net.openhft.chronicle.wire.WireOut;
import net.openhft.chronicle.wire.Wires;
import net.openhft.chronicle.wire.YamlLogging;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.function.Consumer;

/**
 * Created by peter.lawrey on 09/07/2015.
 */
public class WireOutPublisher implements Closeable {
    private static final int WARN_QUEUE_LENGTH = 50;
    private final Queue<Consumer<WireOut>> publisher = new LinkedTransferQueue<>();
    private static final Logger LOG = LoggerFactory.getLogger(WireOutPublisher.class);
    private volatile boolean closed;

    /**
     * Apply waiting messages and return false if there was none.
     *
     * @param out buffer to write to.
     */
    public void applyAction(@NotNull WireOut out, @NotNull Runnable runnable) {
        if (publisher.isEmpty()) {
            synchronized (this) {
                runnable.run();
            }
        }
        while (out.bytes().writePosition() < out.bytes().realCapacity() / 4) {
            Consumer<WireOut> wireConsumer = publisher.poll();
            if (wireConsumer == null)
                break;
            wireConsumer.accept(out);


            if (Jvm.IS_DEBUG && YamlLogging.showServerWrites)
                try {


                    LOG.info("\nServer Publishes (from async publisher ) :\n" +
                            Wires.fromSizePrefixedBinaryToText(out.bytes()));

                } catch (Exception e) {
                    LOG.info("\nServer Publishes ( from async publisher - corrupted ) :\n" +
                            out.bytes().toDebugString());
                    e.printStackTrace();
                }
        }
    }

    public void add(Consumer<WireOut> outConsumer) {

        if (closed) {
            throw new IllegalStateException("Closed");

        } else {
            int size = publisher.size();
            if (size > WARN_QUEUE_LENGTH)
                System.out.println("publish length: " + size);

            publisher.add(outConsumer);
        }
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }
}