/*
 * MIT License
 *
 * Copyright (c) 2026 Lily Ellenvia
 * Modifications and adaptations for TransferProxy
 *
 * Based on NeoForged-Velocity-Support by Swedz (https://swedz.net/)
 * https://github.com/Swedz/NeoForged-Velocity-Support
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.transferproxy.forwarding;

import io.netty.buffer.ByteBuf;
import net.transferproxy.api.TransferProxy;
import net.transferproxy.api.network.connection.PlayerConnection;
import net.transferproxy.network.packet.login.clientbound.LoginPluginRequestPacket;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static net.transferproxy.util.BufUtil.*;

public final class VelocityForwardingUtil {

    public static final byte MAX_SUPPORTED_FORWARDING_VERSION = 4;
    public static final String PLAYER_INFO_CHANNEL = "velocity:player_info";

    private VelocityForwardingUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Record for forwarded player profile data.
     */
    public record ForwardedProfile(UUID uuid, String username) {}

    /**
     * 验证 HMAC-SHA256 签名完整性。
     * 读取 32 字节签名后，对 buf 剩余数据进行验证。
     * 调用后 buf 的 readerIndex 位于签名之后（数据起始位置）。
     */
    public static boolean checkIntegrity(final @NotNull ByteBuf buf,
                                         final @NotNull SecretKeySpec secretSpec) {
        byte[] signature = new byte[32];
        buf.readBytes(signature);

        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretSpec);
            byte[] computed = mac.doFinal(data);
            return MessageDigest.isEqual(signature, computed);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new AssertionError("HmacSHA256 should always be available", e);
        }
    }

    /**
     * 从 buf 读取玩家 IP 地址。
     */
    public static InetAddress readAddress(final @NotNull ByteBuf buf) {
        String ipString = readString(buf);
        try {
            return InetAddress.getByName(ipString);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid IP address: " + ipString, e);
        }
    }

    /**
     * 从 buf 读取玩家 UUID 和用户名。
     */
    public static ForwardedProfile readProfile(final @NotNull ByteBuf buf) {
        UUID uuid = readUUID(buf);
        String username = readString(buf, 16);
        return new ForwardedProfile(uuid, username);
    }

    /**
     * 跳过 Profile Properties（TransferProxy 不使用 GameProfile 类）。
     */
    public static void skipProperties(final @NotNull ByteBuf buf) {
        int count = readVarInt(buf);
        for (int i = 0; i < count; i++) {
            readString(buf);              // name
            readString(buf);              // value
            if (buf.readBoolean()) {      // has signature
                readString(buf);          // signature
            }
        }
    }

    /**
     * 检查是否启用了 forwarding，如果是则发送 Custom Query Request 并暂停登录。
     * @return true 表示已接管登录流程（调用方应 return），false 表示未启用、走正常流程
     */
    public static boolean maybeStartForwarding(final @NotNull PlayerConnection connection,
                                           final @NotNull String name,
                                           final @NotNull UUID uuid) {
        final var forwarding = TransferProxy.getInstance().getConfiguration().getForwarding();
        if (!forwarding.isEnabled()) {
            return false;
        }

        final int transactionId = ThreadLocalRandom.current().nextInt();

        connection.getChannel()
            .attr(VelocityForwardingState.KEY)
            .set(new VelocityForwardingState(transactionId, name, uuid));

        connection.sendPacket(new LoginPluginRequestPacket(
            transactionId,
            PLAYER_INFO_CHANNEL,
            new byte[] { MAX_SUPPORTED_FORWARDING_VERSION }
        ));

        return true;
    }
}
