package net.nerol.pvp_bot.bot;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class BotNet {

    public static class SilentConnection extends Connection {
        public SilentConnection(PacketFlow side) {
            super(side);
            injectFakeChannel();
        }

        /**
         * Inject a Netty EmbeddedChannel so Connection's internal channel field is non-null.
         * Without this, any code path that accesses channel.writeAndFlush() directly
         * (rather than through our overridden send()) will NPE inside placeNewPlayer.
         */
        private void injectFakeChannel() {
            EmbeddedChannel fake = new EmbeddedChannel() {
                @Override public boolean isOpen()   { return true; }
                @Override public boolean isActive() { return true; }
            };
            fake.config().setAutoRead(false);

            Class<?> cls = Connection.class;
            while (cls != null) {
                for (Field f : cls.getDeclaredFields()) {
                    if (io.netty.channel.Channel.class.isAssignableFrom(f.getType())) {
                        try {
                            f.setAccessible(true);
                            f.set(this, fake);
                            return;
                        } catch (IllegalAccessException ignored) {}
                    }
                }
                cls = cls.getSuperclass();
            }
        }

        @Override
        public void send(Packet<?> packet) { /* swallow */ }

        @Override
        public void send(Packet<?> packet, @Nullable final ChannelFutureListener listener) { /* swallow */ }

        @Override
        public boolean isConnected() { return true; }
    }

    public static class SilentGameListener extends ServerGamePacketListenerImpl {
        public SilentGameListener(MinecraftServer server, Connection conn, ServerPlayer player, CommonListenerCookie cookie) {
            super(server, conn, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) { /* swallow */ }

        @Override
        public void send(Packet<?> packet, @Nullable final ChannelFutureListener listener) { /* swallow */ }

        @Override
        public void resumeFlushing() { /* no-op */ }
    }

    /**
     * Try to attach the listener to the Connection. In MC 26.x the API changed to
     * setupInboundProtocol(ProtocolInfo, PacketListener) — a 2-arg method — so a
     * single-arg scan won't find it. We attempt known names and fall back to a scan,
     * but never throw: the send() overrides already swallow all packets so the bot
     * is safe without a listener wired into the Connection.
     */
    public static void attachListener(Connection conn, PacketListener listener) {
        String[] singleArgNames = { "setListener", "setPacketListener", "setListenerForServerboundHandshake" };

        for (String n : singleArgNames) {
            try {
                Method m = conn.getClass().getMethod(n, PacketListener.class);
                m.invoke(conn, listener);
                return;
            } catch (ReflectiveOperationException ignored) {
                // Method exists but failed to invoke (e.g. wrong protocol state) — try next
            }
        }

        // Brute-force: single-arg method accepting a PacketListener subtype
        for (Method m : conn.getClass().getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && PacketListener.class.isAssignableFrom(p[0])) {
                try {
                    m.invoke(conn, listener);
                    return;
                } catch (ReflectiveOperationException ignored) {}
            }
        }

        // Not fatal — send() overrides prevent any packet from reaching a real channel.
    }

    private BotNet() {}
}