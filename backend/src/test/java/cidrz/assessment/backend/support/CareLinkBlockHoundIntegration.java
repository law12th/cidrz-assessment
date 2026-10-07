package cidrz.assessment.backend.support;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

/**
 * The accepted blocking calls on event-loop threads. Anything else that blocks on an event loop
 * fails the test — which is the point: in a reactive service one blocking call in a hot path stalls
 * every request sharing that thread, and it is almost impossible to spot by reading the diff.
 *
 * <ul>
 *   <li>Writing a log line to the console blocks briefly; in production the appender is asynchronous.
 *   <li>{@code UUID.randomUUID} reads from the operating system's entropy source.
 *   <li>Host name lookup for the test database may read {@code /etc/hosts}.
 * </ul>
 */
public class CareLinkBlockHoundIntegration implements BlockHoundIntegration {

    @Override
    public void applyTo(BlockHound.Builder builder) {
        builder.allowBlockingCallsInside("ch.qos.logback.classic.Logger", "callAppenders");
        builder.allowBlockingCallsInside("java.util.UUID", "randomUUID");
        builder.allowBlockingCallsInside("java.net.InetAddress", "getAllByName");
    }
}
