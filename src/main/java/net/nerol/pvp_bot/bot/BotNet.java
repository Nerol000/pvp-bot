package net.nerol.pvp_bot.bot;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.lang.reflect.Method;

public final class BotNet {

    public static class SilentConnection extends Connection {
        public SilentConnection(PacketFlow side) {
            super(side);
        }

        @Override
        public void send(Packet<?> packet) {
            // swallow
        }

        @Override
        public boolean isConnected() {
            return true;
        }
    }

    public static class SilentGameListener extends ServerGamePacketListenerImpl {
        public SilentGameListener(MinecraftServer server, Connection conn, ServerPlayer player, CommonListenerCookie cookie) {
            super(server, conn, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {
            // swallow
        }


        @Override
        public void resumeFlushing() {
            // no-op
        }
    }

    /**
     * Version-proof: attach listener to Connection even if method isn't named setListener.
     * We try to find any public/protected method that takes a PacketListener (or supertype)
     * and invoke it.
     */
    public static void attachListener(Connection conn, PacketListener listener) {
        // First, try common obvious names (cheap)
        String[] names = { "setListener", "setPacketListener", "setListenerForServerboundHandshake" };

        for (String n : names) {
            try {
                Method m = conn.getClass().getMethod(n, PacketListener.class);
                m.invoke(conn, listener);
                return;
            } catch (NoSuchMethodException ignored) {
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed invoking " + n + " on Connection", e);
            }
        }

        // Otherwise: brute-force scan for a single-arg method compatible with PacketListener
        for (Method m : conn.getClass().getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && p[0].isAssignableFrom(listener.getClass())) {
                try {
                    m.invoke(conn, listener);
                    return;
                } catch (ReflectiveOperationException ignored) {
                }
            }
            if (p.length == 1 && PacketListener.class.isAssignableFrom(p[0])) {
                try {
                    m.invoke(conn, listener);
                    return;
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }

        // If we get here, your Connection truly has no setter-like method exposed.
        // That's still OK in many cases because we swallow send(), but placeNewPlayer
        // might expect it. We'll fail loudly so you see it early.
        throw new IllegalStateException(
                "Could not find any method to attach PacketListener to Connection in this Minecraft version. " +
                        "Paste Connection methods containing 'listener' and I'll wire it precisely."
        );
    }

    private BotNet() {}
}